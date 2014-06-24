// Copyright (c) 2013-2014 K Team. All Rights Reserved.
package org.kframework.kil;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.Visitor;

/**
 * A builtin list
 *
 * @author TraianSF
 */
public class ListBuiltin extends CollectionBuiltin {
    private final List<Term> elementsRight;

    private ListBuiltin(DataStructureSort sort, List<Term> baseTerms, List<Term> elementsLeft,
                       List<Term> elementsRight) {
        super(sort, baseTerms, elementsLeft);
        this.elementsRight = elementsRight;
    }

    public List<Term> elementsLeft() {
        return elements();
    }

    public List<Term> elementsRight() {
        return Collections.unmodifiableList(elementsRight);
    }
    
    @Override
    public List<Term> elements() {
        return Collections.unmodifiableList((List<Term>) elements);
    }
    
    @Override
    public List<Term> baseTerms() {
        return Collections.unmodifiableList((List<Term>) baseTerms);
    }
    
    @Override
    public DataStructureBuiltin shallowCopy(Collection<Term> terms) {
        return ListBuiltin.of(sort(), (List<Term>)terms, elementsLeft(), elementsRight());
    }
    
    @Override
    public CollectionBuiltin shallowCopy(Collection<Term> terms,
            Collection<Term> elements) {
        return ListBuiltin.of(sort(), (List<Term>)terms, (List<Term>)elements, elementsRight());
    }

    public static ListBuiltin of(DataStructureSort sort, List<Term> terms, List<Term> elementsLeft,
                       List<Term> elementsRight) {
        ArrayList<Term> left = new ArrayList<Term>(elementsLeft);
        ArrayList<Term> base = new ArrayList<Term>();
        ArrayList<Term> right = new ArrayList<Term>();
        boolean lhs = true;
        for (Term term : terms) {
            if (term instanceof ListBuiltin) {
                ListBuiltin listBuiltin = (ListBuiltin) term;
                assert listBuiltin.sort().equals(sort) : "inner lists are expected to have the same sort for now, found " + sort + " and " + listBuiltin.sort();
//              Recurse to make sure there are no additional nested inner ListBuiltins
                listBuiltin = ListBuiltin.of(listBuiltin.sort(), listBuiltin.baseTerms(), listBuiltin.elementsLeft(),
                        listBuiltin.elementsRight());
                Collection<Term> listBuiltinBase = listBuiltin.baseTerms();
                Collection<Term> listBuiltinLeft = listBuiltin.elementsLeft();
                Collection<Term> listBuiltinRight = listBuiltin.elementsRight();
                if (lhs) {
                    left.addAll(listBuiltinLeft);
                    if (!listBuiltinBase.isEmpty()) {
                        lhs = false;
                        base.addAll(listBuiltinBase);
                        right.addAll(listBuiltinRight);
                    } else {
                        left.addAll(listBuiltinRight);
                    }
                } else {
                    assert listBuiltinLeft.isEmpty() : "left terms no longer allowed here";
                    if (!listBuiltinBase.isEmpty()) {
                        assert right.isEmpty() : "we cannot add base terms if right terms have been already added";
                        assert listBuiltinLeft.isEmpty() : "inner list cannot have elements on the left";
                        base.addAll(listBuiltinBase);
                    } else {
                        right.addAll(listBuiltinLeft);
                    }
                    right.addAll(listBuiltinRight);
                }
            } else {
                lhs = false;
                base.add(term);
            }
        }
        right.addAll(elementsRight);
        if (base.isEmpty()) {
            left.addAll(right);
            right.clear();
        }
        return new ListBuiltin(sort, base, left, right);
    }

    @Override
    public String toString() {
        return elements().toString() + baseTerms().toString() + elementsRight.toString();
    }

    @Override
    protected <P, R, E extends Throwable> R accept(Visitor<P, R, E> visitor, P p) throws E {
        return visitor.complete(this, visitor.visit(this, p));
    }
    

    @Override
    public Collection<Term> getChildren(DataStructureBuiltin.ListChildren type) {
        switch (type) {
            case ELEMENTS_RIGHT:
                return elementsRight;
            default:
                return super.getChildren(type);
        }
    }
    
    @Override
    public Term toKApp(Context context) {
        List<Term> items = new ArrayList<>();
        for (Term element : elementsLeft()) {
            items.add(KApp.of(DataStructureSort.DEFAULT_LIST_ITEM_LABEL, element));
        }
        for (Term base : baseTerms()) {
            items.add(base);
        }
        for (Term element : elementsRight()) {
            items.add(KApp.of(DataStructureSort.DEFAULT_LIST_ITEM_LABEL, (Term) element));
        }
        return toKApp(items);
    }

}
