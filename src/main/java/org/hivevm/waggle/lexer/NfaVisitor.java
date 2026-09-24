// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/RCharacterList.java, org/javacc/parser/RStringLiteral.java

package org.hivevm.waggle.lexer;

import org.hivevm.waggle.model.CharacterRange;
import org.hivevm.waggle.model.RCharacterList;
import org.hivevm.waggle.model.RChoice;
import org.hivevm.waggle.model.REndOfFile;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.RJustName;
import org.hivevm.waggle.model.ROneOrMore;
import org.hivevm.waggle.model.RRepetitionRange;
import org.hivevm.waggle.model.RSequence;
import org.hivevm.waggle.model.RStringLiteral;
import org.hivevm.waggle.model.RZeroOrMore;
import org.hivevm.waggle.model.RZeroOrOne;
import org.hivevm.waggle.model.RegularExpressionVisitor;
import org.hivevm.waggle.model.SingleCharacter;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link NfaVisitor} class.
 */
final class NfaVisitor implements RegularExpressionVisitor<Nfa, NfaStateData> {

    private final boolean ignoreCase;

    /**
     * Constructs an instance of {@link NfaVisitor}.
     */
    public NfaVisitor(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    /**
     * Return <code>true</code> if the cases are ignored.
     */
    private boolean isIgnoreCase() {
        return this.ignoreCase;
    }

    /**
     * The characters {@code expr} matches, as a list that is not negated: case-folded first, when
     * case is ignored, then complemented. The other order folds the complement, which matches the
     * excluded character in its other case. Works on a copy; the grammar's list stays as written.
     */
    private RCharacterList matched(RCharacterList expr, NfaStateData data) {
        RCharacterList list = expr.copy();
        if (data.ignoreCase() || isIgnoreCase()) {
            list.ToCaseNeutral();
            list.SortDescriptors();
        }

        if (list.isNegated_list())
            list.RemoveNegation(); // This also sorts the list
        else
            list.SortDescriptors();
        return list;
    }

    @Override
    public Nfa visit(RCharacterList expr, NfaStateData data) {
        RCharacterList list = matched(expr, data);
        if (list.getDescriptors().isEmpty()) {
            data.global.diagnostics().error(expr,
                    "Empty character set is not allowed as it will not match any character.");
            return new Nfa(data);
        }

        Nfa retVal = new Nfa(data);
        NfaState startState = retVal.start();
        NfaState finalState = retVal.end();
        int i;

        for (i = 0; i < list.getDescriptors().size(); i++) {
            if (list.getDescriptors().get(i) instanceof SingleCharacter) {
                startState.AddChar(((SingleCharacter) list.getDescriptors().get(i)).getChar());
            } else // if (descriptors.get(i) instanceof CharacterRange)
            {
                CharacterRange cr = (CharacterRange) list.getDescriptors().get(i);

                if (cr.getLeft() == cr.getRight())
                    startState.AddChar(cr.getLeft());
                else
                    startState.AddRange(cr.getLeft(), cr.getRight());
            }
        }

        startState.next = finalState;

        return retVal;
    }

    @Override
    public Nfa visit(RChoice expr, NfaStateData data) {
        List<RExpression> choices = compressed(expr, data);

        if (choices.size() == 1)
            return choices.getFirst().accept(this, data);

        Nfa retVal = new Nfa(data);
        NfaState startState = retVal.start();
        NfaState finalState = retVal.end();

        for (RExpression element : choices) {
            Nfa temp = element.accept(this, data);
            startState.AddMove(temp.start());
            temp.end().AddMove(finalState);
        }

        return retVal;
    }

    /**
     * The alternatives of {@code expr} with nested choices unrolled and every character list and
     * one-character literal merged into one list, which gives the NFA one state for them. This
     * used to rewrite the grammar's own choice (RChoice.CompressCharLists), so the unmatchability
     * check that runs afterwards saw the merged list instead of the alternatives it names.
     */
    private List<RExpression> compressed(RChoice expr, NfaStateData data) {
        List<RExpression> choices = new ArrayList<>(expr.getChoices());

        // Unroll nested choices; their alternatives go to the end, last first.
        for (int i = 0; i < choices.size(); i++) {
            RExpression curRE = choices.get(i);
            while (curRE instanceof RJustName name) {
                curRE = name.getRegexpr();
            }
            if (curRE instanceof RChoice nested) {
                choices.remove(i--);
                for (int j = nested.getChoices().size(); j-- > 0; ) {
                    choices.add(nested.getChoices().get(j));
                }
            }
        }

        RCharacterList merged = null;
        for (int i = 0; i < choices.size(); i++) {
            RExpression curRE = choices.get(i);
            while (curRE instanceof RJustName name) {
                curRE = name.getRegexpr();
            }

            List<Object> descriptors;
            if ((curRE instanceof RStringLiteral literal) && (literal.getImage().length() == 1)) {
                descriptors = List.of(new SingleCharacter(literal.getImage().charAt(0)));
            } else if (curRE instanceof RCharacterList list) {
                descriptors = list.isNegated_list() ? matched(list, data).getDescriptors()
                        : list.copy().getDescriptors();
            } else {
                continue;
            }

            if (merged == null) {
                merged = new RCharacterList();
                choices.set(i, merged);
            } else {
                choices.remove(i--);
            }
            for (int j = descriptors.size(); j-- > 0; ) {
                merged.getDescriptors().add(descriptors.get(j));
            }
        }
        return choices;
    }

    @Override
    public Nfa visit(REndOfFile expr, NfaStateData data) {
        return null;
    }

    @Override
    public Nfa visit(RJustName expr, NfaStateData data) {
        return expr.getRegexpr().accept(this, data);
    }

    @Override
    public Nfa visit(ROneOrMore expr, NfaStateData data) {
        Nfa retVal = new Nfa(data);
        NfaState startState = retVal.start();
        NfaState finalState = retVal.end();

        Nfa temp = expr.getRegexpr().accept(this, data);

        startState.AddMove(temp.start());
        temp.end().AddMove(temp.start());
        temp.end().AddMove(finalState);

        return retVal;
    }

    @Override
    public Nfa visit(RRepetitionRange expr, NfaStateData data) {
        RSequence seq = new RSequence();
        seq.setOrdinal(Integer.MAX_VALUE);
        int i;

        for (i = 0; i < expr.getMin(); i++) {
            seq.getUnits().add(expr.getRegexpr());
        }

        if (expr.hasMax() && (expr.getMax() == -1)) // Unlimited
            seq.getUnits().add(new RZeroOrMore(expr.getRegexpr()));

        while (i++ < expr.getMax()) {
            seq.getUnits().add(new RZeroOrOne(expr.getRegexpr()));
        }

        return seq.accept(this, data);
    }

    @Override
    public Nfa visit(RSequence expr, NfaStateData data) {
        if (expr.getUnits().size() == 1)
            return expr.getUnits().getFirst().accept(this, data);

        Nfa retVal = new Nfa(data);
        NfaState startState = retVal.start();
        NfaState finalState = retVal.end();
        Nfa temp1;
        Nfa temp2 = null;

        RExpression curRE;

        curRE = expr.getUnits().getFirst();
        temp1 = curRE.accept(this, data);
        startState.AddMove(temp1.start());

        for (int i = 1; i < expr.getUnits().size(); i++) {
            curRE = expr.getUnits().get(i);

            temp2 = curRE.accept(this, data);
            temp1.end().AddMove(temp2.start());
            temp1 = temp2;
        }

        temp2.end().AddMove(finalState);

        return retVal;
    }

    @Override
    public Nfa visit(RStringLiteral expr, NfaStateData data) {
        if (expr.getImage().length() == 1) {
            RCharacterList temp = new RCharacterList(expr.getImage().charAt(0));
            return temp.accept(this, data);
        }

        NfaState startState = new NfaState(data);
        NfaState theStartState = startState;
        NfaState finalState = null;

        if (expr.getImage().isEmpty())
            return new Nfa(theStartState, theStartState);

        int i;

        for (i = 0; i < expr.getImage().length(); i++) {
            finalState = new NfaState(data);
            startState.charMoves = new char[1];
            startState.AddChar(expr.getImage().charAt(i));

            if (data.ignoreCase() || isIgnoreCase()) {
                startState.AddChar(Character.toLowerCase(expr.getImage().charAt(i)));
                startState.AddChar(Character.toUpperCase(expr.getImage().charAt(i)));
            }

            startState.next = finalState;
            startState = finalState;
        }

        return new Nfa(theStartState, finalState);
    }

    @Override
    public Nfa visit(RZeroOrMore expr, NfaStateData data) {
        Nfa retVal = new Nfa(data);
        Nfa temp = expr.getRegexpr().accept(this, data);

        NfaState startState = retVal.start();
        NfaState finalState = retVal.end();
        startState.AddMove(temp.start());
        startState.AddMove(finalState);
        temp.end().AddMove(finalState);
        temp.end().AddMove(temp.start());

        return retVal;
    }

    @Override
    public Nfa visit(RZeroOrOne expr, NfaStateData data) {
        Nfa retVal = new Nfa(data);
        Nfa temp = expr.getRegexpr().accept(this, data);

        NfaState startState = retVal.start();
        NfaState finalState = retVal.end();
        startState.AddMove(temp.start());
        startState.AddMove(finalState);
        temp.end().AddMove(finalState);

        return retVal;
    }
}
