// Copyright (c) 2015-2016 K Team. All Rights Reserved.
package org.kframework.kompile;

import org.kframework.Collections;
import org.kframework.attributes.Att;
import org.kframework.builtin.KLabels;
import org.kframework.builtin.Sorts;
import org.kframework.definition.Definition;
import org.kframework.definition.Module;
import org.kframework.kore.KApply;
import org.kframework.kore.KORE;
import org.kframework.kore.KToken;
import org.kframework.kore.Sort;
import org.kframework.kore.VisitK;
import org.kframework.parser.UserParser;
import org.kframework.parser.ModuleDerivedParser;
import org.kframework.parser.concrete2kore.ParseInModule;
import org.kframework.parser.concrete2kore.generator.RuleGrammarGenerator;
import org.kframework.utils.errorsystem.KExceptionManager;
import scala.Option;

import java.util.HashMap;

public class ParsedDefinitionWrapper {
    public final KompileOptions kompileOptions;
    public final Definition parsedDefinition;        /*The parsed but uncompiled definition*/
    public final HashMap<String, Sort> configurationVariableDefaultSorts = new HashMap<>();

    public ParsedDefinitionWrapper(KompileOptions options, Definition parsedDefinition) {
        this.kompileOptions = options;
        this.parsedDefinition = parsedDefinition;
    }

    public UserParser getModuleDerviedParser(String moduleName, KExceptionManager kem) {
        Module seedModule = programParsingModuleFor(moduleName, kem).get();
        ParseInModule parseInModule = RuleGrammarGenerator.getCombinedGrammar(seedModule, kompileOptions.strict());
        return new ModuleDerivedParser(moduleName, parseInModule);
    }

    public String mainSyntaxModuleName() { return parsedDefinition.att().<String>getOptional(Att.syntaxModule()).get(); }

    /**
     * @return the module used for generating the program (i.e. ground) parser for the module named moduleName
     * It automatically generates this module unless the user has already defined a module postfixed with
     * {@link RuleGrammarGenerator#POSTFIX}. In latter case, it uses the user-defined module.
     */
    public Option<Module> programParsingModuleFor(String moduleName, KExceptionManager kem) {
        Option<Module> moduleOption;

        if(moduleName.endsWith(RuleGrammarGenerator.POSTFIX)) {
            moduleOption = parsedDefinition.getModule(moduleName);
        } else {
            moduleOption = parsedDefinition.getModule(moduleName + RuleGrammarGenerator.POSTFIX);
            if (moduleOption.isDefined()) {
                kem.registerInternalHiddenWarning("Module " + moduleOption.get().name() + " is user-defined.");
            } else {
                moduleOption = parsedDefinition.getModule(moduleName);
                if (moduleOption.isDefined()) {
                    kem.registerInternalHiddenWarning("Module " + moduleOption.get().name() + " has been automatically generated.");
                }
            }
        }
        Option<Module> programParsingModuleOption = moduleOption.isDefined() ?
                Option.apply(RuleGrammarGenerator.getProgramsGrammar(moduleOption.get(), parsedDefinition)) :
                Option.empty();
        return programParsingModuleOption;
    }

}