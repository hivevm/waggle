// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.cpp;

import java.util.List;

/**
 * What the C++ lexer declares in its header for each lexical state, its literal arrays, and the
 * tables its DEBUG_TOKEN_MANAGER trace reads, as the templates write them (ADR-0031). Every value is already
 * spelled for C++.
 */
public final class CppDebugTables {

    private CppDebugTables() {
    }

    /**
     * {@code kindForState}: the kind each NFA state of a lexical state accepts.
     *
     * @param nfa  whether any lexical state has an NFA
     * @param rows one array per lexical state that has one
     * @param refs one entry of the table per lexical state
     */
    public record KindForState(boolean nfa, List<KindRow> rows, List<KindRef> refs) {
    }

    /**
     * The kinds of one lexical state.
     *
     * @param index  the lexical state
     * @param values its kinds
     */
    public record KindRow(int index, String values) {
    }

    /**
     * The entry of a lexical state in {@code kindForState}: its array, or nullptr without one.
     *
     * @param index   the lexical state
     * @param present whether it has an NFA
     */
    public record KindRef(int index, boolean present) {
    }

    /**
     * {@code statesForState} and {@code statesForStateLen}: the NFA states each composite state
     * stands for.
     *
     * @param nfa     whether any lexical state has an NFA
     * @param sets    the sets of each lexical state that has an NFA
     * @param refs    one entry of {@code statesForState} per lexical state
     * @param lenRefs one entry of {@code statesForStateLen} per lexical state
     */
    public record StatesForState(boolean nfa, List<StateSets> sets, List<StateSetsRef> refs,
                                 List<StateSetsLenRef> lenRefs) {
    }

    /**
     * The sets of one lexical state.
     *
     * @param index   the lexical state
     * @param members one array per state
     * @param refs    one entry of the table of those arrays per state
     * @param lengths their lengths
     */
    public record StateSets(int index, List<StateSet> members, List<StateSetRef> refs,
                            String lengths) {
    }

    /**
     * The states one state stands for.
     *
     * @param index  the lexical state
     * @param state  the state
     * @param values the states it stands for
     */
    public record StateSet(int index, int state, String values) {
    }

    /** The entry of state {@code state} of lexical state {@code index} in its table of sets. */
    public record StateSetRef(int index, int state) {
    }

    /**
     * The entry of a lexical state in {@code statesForState}: its sets, or nullptr without them.
     *
     * @param index   the lexical state
     * @param present whether it has an NFA
     */
    public record StateSetsRef(int index, boolean present) {
    }

    /** The entry of a lexical state in {@code statesForStateLen}, as {@link StateSetsRef}. */
    public record StateSetsLenRef(int index, boolean present) {
    }

    /**
     * What the header declares of a lexical state's functions.
     *
     * @param suffix          what tells this state's functions from the others'
     * @param stop            whether it has a stop DFA, and with it jjStartNfa
     * @param activeParams    the active vectors as parameters
     * @param startWithStates whether it has jjStartNfaWithStates
     * @param moveNfa         whether it hands over to an NFA
     * @param trivial         whether its string-literal DFA is only the trivial first position
     * @param stopAtPos       whether it defines the jjStopAtPos all states share
     * @param positions       one declaration per position of the string-literal DFA
     */
    public record StateDecls(String suffix, boolean stop, String activeParams,
                             boolean startWithStates, boolean moveNfa, boolean trivial,
                             boolean stopAtPos, List<DfaDecl> positions) {
    }

    /**
     * The declaration of one {@code jjMoveStringLiteralDfa<pos>}.
     *
     * @param pos    the position
     * @param params its parameters
     */
    public record DfaDecl(int pos, String params) {
    }

    /**
     * The characters of the literal of token kind {@code kind}, as the array the lexer compares
     * against; {@code elements} is empty for a kind without a literal.
     */
    public record LiteralChars(int kind, String elements) {
    }

    /** The entry of token kind {@code kind} in the table of those arrays. */
    public record LiteralRef(int kind) {
    }
}
