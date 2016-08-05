// Copyright (c) 2016 K Team. All Rights Reserved.
package org.kframework;

import com.google.inject.Provider;
import org.kframework.RewriterResult;
import org.kframework.attributes.Att;
import org.kframework.attributes.Source;
import org.kframework.backend.java.compile.KOREtoBackendKIL;
import org.kframework.backend.java.kil.ConstrainedTerm;
import org.kframework.backend.java.kil.GlobalContext;
import org.kframework.backend.java.kil.KItem;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.kore.compile.ExpandMacros;
import org.kframework.backend.java.symbolic.InitializeRewriter;
import org.kframework.backend.java.symbolic.JavaBackend;
import org.kframework.backend.java.symbolic.JavaExecutionOptions;
import org.kframework.backend.java.symbolic.ProofExecutionMode;
import org.kframework.backend.java.symbolic.Stage;
import org.kframework.backend.java.symbolic.SymbolicRewriter;
import org.kframework.compile.NormalizeKSeq;
import org.kframework.definition.Definition;
import org.kframework.definition.Module;
import org.kframework.definition.Rule;
import org.kframework.kil.Attribute;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kompile.Kompile;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.K;
import org.kframework.kore.compile.KTokenVariablesToTrueVariables;
import org.kframework.krun.KRun;
import org.kframework.krun.KRunOptions;
import org.kframework.krun.api.KRunState;
import org.kframework.krun.api.io.FileSystem;
import org.kframework.krun.ioserver.filesystem.portable.PortableFileSystem;
import org.kframework.main.GlobalOptions;
import org.kframework.parser.concrete2kore.generator.RuleGrammarGenerator;
import org.kframework.rewriter.Rewriter;
import org.kframework.utils.Stopwatch;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.options.SMTOptions;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.math.BigInteger;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;

/**
 * KRunAPI
 */
public class KRunAPI {

    public static CompiledDefinition kompile(String def, String mainModuleName) {
        // tier-1 dependencies
        GlobalOptions globalOptions = new GlobalOptions();
        KompileOptions kompileOptions = new KompileOptions();
        KRunOptions krunOptions = new KRunOptions();
        JavaExecutionOptions javaExecutionOptions = new JavaExecutionOptions();

        // tier-2 dependencies
        KExceptionManager kem = new KExceptionManager(globalOptions);
        FileUtil files = FileUtil.get(globalOptions, System.getenv());

        Definition d = DefinitionParser.from(def, mainModuleName);

        Kompile kompile = new Kompile(kompileOptions, files, kem, false);
        Function<Definition, Definition> pipeline = new JavaBackend(kem, files, globalOptions, kompileOptions).steps(kompile);
        CompiledDefinition compiledDef = Kompile.run(d, kompileOptions, pipeline); // Kompile.runDefaultSteps(d, kompileOptions, kem);

        return compiledDef;
    }

    public static RewriterResult krun(CompiledDefinition compiledDef, String programText, Integer depth, String prove, String prelude) {

        GlobalOptions globalOptions = new GlobalOptions();
        KompileOptions kompileOptions = new KompileOptions();
        KRunOptions krunOptions = new KRunOptions();
        JavaExecutionOptions javaExecutionOptions = new JavaExecutionOptions();

        KExceptionManager kem = new KExceptionManager(globalOptions);
        FileUtil files = FileUtil.get(globalOptions, System.getenv());
        boolean ttyStdin = false;

        FileSystem fs = new PortableFileSystem(kem, files);
        Map<String, Provider<MethodHandle>> hookProvider = HookProvider.get(kem); // new HashMap<>();
        InitializeRewriter.InitializeDefinition initializeDefinition = new InitializeRewriter.InitializeDefinition();

        BiFunction<String, Source, K> programParser = compiledDef.getProgramParser(kem);
        K pgm = programParser.apply(programText, Source.apply("generated by api"));
        K program = KRun.parseConfigVars(krunOptions, compiledDef, kem, files, ttyStdin, pgm);

        /* TODO: figure out if it is needed
        program = new KTokenVariablesToTrueVariables()
                .apply(compiledDef.kompiledDefinition.getModule(compiledDef.mainSyntaxModuleName()).get(), program);
         */

        Rewriter rewriter = (InitializeRewriter.SymbolicRewriterGlue)
            new InitializeRewriter(
                fs,
                javaExecutionOptions,
                globalOptions,
                kem,
                kompileOptions.experimental.smt,
                hookProvider,
                kompileOptions,
                krunOptions,
                files,
                initializeDefinition)
            .apply(compiledDef.executionModule());

        if (prove == null) {
            RewriterResult result = ((InitializeRewriter.SymbolicRewriterGlue) rewriter).execute(program, Optional.ofNullable(depth));
            return result;
        } else {
            Stopwatch sw = new Stopwatch(globalOptions);
            krunOptions.experimental.prove = prove;
            krunOptions.experimental.smt.smtPrelude = prelude;
            ProofExecutionMode mode = new ProofExecutionMode(kem, krunOptions, sw, files, globalOptions);
            java.util.List<K> result = mode.execute(program, rewriter, compiledDef);
            System.out.println(result);
            return null;
        }
    }

    public static void kprint(CompiledDefinition compiledDef, RewriterResult result) {
        // tier-1 dependencies
        GlobalOptions globalOptions = new GlobalOptions();
        KompileOptions kompileOptions = new KompileOptions();
        KRunOptions krunOptions = new KRunOptions();
        JavaExecutionOptions javaExecutionOptions = new JavaExecutionOptions();

        // tier-2 dependencies
        KExceptionManager kem = new KExceptionManager(globalOptions);
        FileUtil files = FileUtil.get(globalOptions, System.getenv());

        // print output
        // from org.kframework.krun.KRun.run()
        KRun.prettyPrint(compiledDef, krunOptions.output, s -> KRun.outputFile(s, krunOptions, files), result.k());
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("usage: <def> <main-module> <pgm>");
            return;
        }
        String def = FileUtil.load(new File(args[0])); // "require \"domains.k\" module A syntax KItem ::= \"run\" endmodule"
        String pgm = FileUtil.load(new File(args[2])); // "run"

        String mainModuleName = args[1]; // "A"

        // kompile
        CompiledDefinition compiledDef = kompile(def, mainModuleName);

        // krun
        RewriterResult result = krun(compiledDef, pgm, null, null, null);
        kprint(compiledDef, result);

        // kprove
        String prove = args[3];
        String prelude = args[4];
        //krun(compiledDef, pgm, null, prove, prelude);
        kprove(compiledDef, compiledDef, prove, prelude);

        return;
    }

    /**
     * compiledDef0: for parsing spec rules
     * compiledDef1: for symbolic execution
     */
    public static void kprove(CompiledDefinition compiledDef0, CompiledDefinition compiledDef1, String proofFile, String prelude) {

        GlobalOptions globalOptions = new GlobalOptions();
        KompileOptions kompileOptions = new KompileOptions();
        KRunOptions krunOptions = new KRunOptions();
        JavaExecutionOptions javaExecutionOptions = new JavaExecutionOptions();

        KExceptionManager kem = new KExceptionManager(globalOptions);
        Stopwatch sw = new Stopwatch(globalOptions);
        FileUtil files = FileUtil.get(globalOptions, System.getenv());

        FileSystem fs = new PortableFileSystem(kem, files);
        Map<String, Provider<MethodHandle>> hookProvider = HookProvider.get(kem); // new HashMap<>();
        InitializeRewriter.InitializeDefinition initializeDefinition = new InitializeRewriter.InitializeDefinition();

        //// setting options

        krunOptions.experimental.prove = proofFile;
        krunOptions.experimental.smt.smtPrelude = prelude;

        SMTOptions smtOptions = krunOptions.experimental.smt;

        //// creating rewritingContext

        GlobalContext initializingContextGlobal = new GlobalContext(fs, javaExecutionOptions, globalOptions, krunOptions, kem, smtOptions, hookProvider, files, Stage.INITIALIZING);
        TermContext initializingContext = TermContext.builder(initializingContextGlobal).freshCounter(0).build();
        org.kframework.backend.java.kil.Definition evaluatedDef0 = initializeDefinition.invoke(compiledDef0.executionModule(), kem, initializingContext.global());
        org.kframework.backend.java.kil.Definition evaluatedDef1 = initializeDefinition.invoke(compiledDef1.executionModule(), kem, initializingContext.global());

        GlobalContext rewritingContextGlobal = new GlobalContext(fs, javaExecutionOptions, globalOptions, krunOptions, kem, smtOptions, hookProvider, files, Stage.REWRITING);
        rewritingContextGlobal.setDefinition(evaluatedDef1);
        TermContext rewritingContext = TermContext.builder(rewritingContextGlobal).freshCounter(initializingContext.getCounterValue()).build();

        //// parse spec file

        Kompile kompile = new Kompile(kompileOptions, globalOptions, files, kem, sw, false);
        Module specModule = kompile.parseModule(compiledDef0, files.resolveWorkingDirectory(proofFile).getAbsoluteFile());

        scala.collection.Set<Module> alsoIncluded = Stream.of("K-TERM", "K-REFLECTION", RuleGrammarGenerator.ID_PROGRAM_PARSING)
                .map(mod -> compiledDef0.getParsedDefinition().getModule(mod).get())
                .collect(org.kframework.Collections.toSet());

        specModule = new JavaBackend(kem, files, globalOptions, kompileOptions)
                .stepsForProverRules()
                .apply(Definition.apply(specModule, org.kframework.Collections.add(specModule, alsoIncluded), Att.apply()))
                .getModule(specModule.name()).get();

        ExpandMacros macroExpander = new ExpandMacros(compiledDef0.executionModule(), kem, files, globalOptions, kompileOptions);

        List<Rule> specRules = stream(specModule.localRules())
                .filter(r -> r.toString().contains("spec.k"))
                .map(r -> (Rule) macroExpander.expand(r))
                .map(r -> ProofExecutionMode.transformFunction(JavaBackend::ADTKVariableToSortedVariable, r))
                .map(r -> ProofExecutionMode.transformFunction(JavaBackend::convertKSeqToKApply, r))
                .map(r -> ProofExecutionMode.transform(NormalizeKSeq.self(), r))
                        //.map(r -> kompile.compileRule(compiledDefinition, r))
                .collect(Collectors.toList());

        //// massage spec rules

        KOREtoBackendKIL converter = new KOREtoBackendKIL(compiledDef0.executionModule(), evaluatedDef0, rewritingContext.global(), false);
        List<org.kframework.backend.java.kil.Rule> javaRules = specRules.stream()
                .map(r -> converter.convert(Optional.<Module>empty(), r))
                .map(r -> new org.kframework.backend.java.kil.Rule(
                        r.label(),
                        r.leftHandSide().evaluate(rewritingContext),
                        r.rightHandSide().evaluate(rewritingContext),
                        r.requires(),
                        r.ensures(),
                        r.freshConstants(),
                        r.freshVariables(),
                        r.lookups(),
                        r.isCompiledForFastRewriting(),
                        r.lhsOfReadCell(),
                        r.rhsOfWriteCell(),
                        r.cellsToCopy(),
                        r.matchingInstructions(),
                        r,
                        rewritingContext.global()))
                .collect(Collectors.toList());
        List<org.kframework.backend.java.kil.Rule> allRules = javaRules.stream()
                .map(org.kframework.backend.java.kil.Rule::renameVariables)
                .collect(Collectors.toList());

        // rename all variables again to avoid any potential conflicts with the rules in the semantics
        javaRules = javaRules.stream()
                .map(org.kframework.backend.java.kil.Rule::renameVariables)
                .collect(Collectors.toList());

        //// prove spec rules

        SymbolicRewriter rewriter = new SymbolicRewriter(rewritingContextGlobal, kompileOptions, javaExecutionOptions, new KRunState.Counter(), converter);

        List<ConstrainedTerm> proofResults = javaRules.stream()
                .filter(r -> !r.containsAttribute(Attribute.TRUSTED_KEY))
                .map(r -> rewriter.proveRule(r.createLhsPattern(rewritingContext), r.createRhsPattern(), allRules))
                .flatMap(List::stream)
                .collect(Collectors.toList());

        //// print result

        //System.out.println(proofResults);

        List<K> result = proofResults.stream()
                .map(ConstrainedTerm::term)
                .map(t -> (KItem) t)
                .collect(Collectors.toList());

        System.out.println(result);
        return;
    }

}
