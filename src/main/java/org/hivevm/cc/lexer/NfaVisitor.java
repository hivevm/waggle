// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.lexer;

import org.hivevm.cc.generator.NfaStateData;
import org.hivevm.cc.model.CharacterRange;
import org.hivevm.cc.model.RCharacterList;
import org.hivevm.cc.model.RChoice;
import org.hivevm.cc.model.REndOfFile;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.RJustName;
import org.hivevm.cc.model.ROneOrMore;
import org.hivevm.cc.model.RRepetitionRange;
import org.hivevm.cc.model.RSequence;
import org.hivevm.cc.model.RStringLiteral;
import org.hivevm.cc.model.RZeroOrMore;
import org.hivevm.cc.model.RZeroOrOne;
import org.hivevm.cc.model.RegularExpressionVisitor;
import org.hivevm.cc.model.SingleCharacter;
import org.hivevm.cc.parser.JavaCCErrors;

/**
 * The {@link NfaVisitor} class.
 */
public final class NfaVisitor implements RegularExpressionVisitor<Nfa, NfaStateData> {

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

    @Override
    public Nfa visit(RCharacterList expr, NfaStateData data) {
        if (!expr.isTransformed()) {
            if (data.ignoreCase() || isIgnoreCase()) {
                expr.ToCaseNeutral();
                expr.SortDescriptors();
            }

            if (expr.isNegated_list())
                expr.RemoveNegation(); // This also sorts the list
            else
                expr.SortDescriptors();
        }

        if ((expr.getDescriptors().isEmpty()) && !expr.isNegated_list()) {
            JavaCCErrors.semantic_error(this,
                    "Empty character set is not allowed as it will not match any character.");
            return new Nfa(data);
        }

        expr.setTransformed();
        Nfa retVal = new Nfa(data);
        NfaState startState = retVal.start();
        NfaState finalState = retVal.end();
        int i;

        for (i = 0; i < expr.getDescriptors().size(); i++) {
            if (expr.getDescriptors().get(i) instanceof SingleCharacter) {
                startState.AddChar(((SingleCharacter) expr.getDescriptors().get(i)).getChar());
            }
            else // if (descriptors.get(i) instanceof CharacterRange)
            {
                CharacterRange cr = (CharacterRange) expr.getDescriptors().get(i);

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
        expr.CompressCharLists();

        if (expr.getChoices().size() == 1)
            return expr.getChoices().getFirst().accept(this, data);

        Nfa retVal = new Nfa(data);
        NfaState startState = retVal.start();
        NfaState finalState = retVal.end();

        for (RExpression element : expr.getChoices()) {
            Nfa temp = element.accept(this, data);
            startState.AddMove(temp.start());
            temp.end().AddMove(finalState);
        }

        return retVal;
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

        for (i = 0; i < expr.getMin(); i++)
            seq.getUnits().add(expr.getRegexpr());

        if (expr.hasMax() && (expr.getMax() == -1)) // Unlimited
            seq.getUnits().add(new RZeroOrMore(expr.getRegexpr()));

        while (i++ < expr.getMax())
            seq.getUnits().add(new RZeroOrOne(expr.getRegexpr()));

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
