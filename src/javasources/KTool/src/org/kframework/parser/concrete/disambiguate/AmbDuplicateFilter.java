// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.parser.concrete.disambiguate;

import org.kframework.kil.ASTNode;
import org.kframework.kil.Ambiguity;
import org.kframework.kil.Term;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.BasicTransformer;
import org.kframework.kil.visitors.exceptions.ParseFailedException;

import java.util.ArrayList;

public class AmbDuplicateFilter extends BasicTransformer {
    public AmbDuplicateFilter(Context context) {
        super("Remove ambiguity duplicates", context);
    }

    @Override
    public ASTNode visit(Ambiguity amb, Void _) throws ParseFailedException {

        // remove duplicate ambiguities
        // should be applied after updating something like variable declarations
        java.util.List<Term> children = new ArrayList<Term>();
        for (Term t1 : amb.getContents()) {
            boolean unique = true;
            for (Term t2 : children)
                if (t1 != t2 && t1.equals(t2))
                    unique = false;
            if (unique)
                children.add(t1);
        }

        if (children.size() > 1) {
            amb.setContents(children);
            return super.visit(amb, _);
        } else
            return super.visit(children.get(0), _);
    }
}
