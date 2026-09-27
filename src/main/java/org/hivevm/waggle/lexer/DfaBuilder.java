// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/NfaState.java, org/javacc/parser/RStringLiteral.java

package org.hivevm.waggle.lexer;

import org.hivevm.waggle.lexer.NfaStateData.KindInfo;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Computes DFA/NFA move tables and prepares the code-generation data structures.
 */
public class DfaBuilder {

    /**
     * Prepares DFA code data for a single lexer state (charPosKind → skip/token tables).
     */
    static void getDfaCode(NfaStateData data) {
        if (data.maxLen == 0) {
            return;
        }

        data.createStartNfa = false;
        for (int i = 0; i < data.maxLen; i++) {
            for (var entry : data.getCharPosKind(i).entrySet()) {
                KindInfo info = entry.getValue();
                char c = entry.getKey();

                int skipKind = data.plainSkipKind(info, i, c);
                if (skipKind >= 0) {
                    addCharToSkip(data, c, skipKind);
                    if (data.ignoreCase()) {
                        if (c != Character.toUpperCase(c)) {
                            addCharToSkip(data, Character.toUpperCase(c), skipKind);
                        }
                        if (c != Character.toLowerCase(c)) {
                            addCharToSkip(data, Character.toLowerCase(c), skipKind);
                        }
                    }
                    continue;
                }

                for (int kind : info.finalKindsAscending()) {
                    if (!data.subString[kind]) {
                        int stateSetName = getStateSetForKind(data, i, kind);
                        data.putStateSetName(i, kind, stateSetName);
                        if (stateSetName != -1) {
                            data.createStartNfa = true;
                        }
                    }
                }
            }
        }
    }

    /**
     * Computes NFA move tables for a single lexer state (ASCII + char/range moves). Runs only for
     * a lexical state that has an NFA.
     */
    static void getMoveNfa(NfaStateData data) {
        if (data.global.kinds == null) {
            data.global.kinds = new int[data.global.maxLexStates][];
            data.global.statesForState = new int[data.global.maxLexStates][][];
        }

        data.reindexByStateName();

        int[] kindsForStates = new int[data.generatedStates()];
        int[][] statesForState = new int[data.stateNameCount()][];
        for (NfaState state : data.getAllStates()) {
            kindsForStates[state.stateName] = state.lookingFor;
            statesForState[state.stateName] = state.compositeStates;
        }

        for (var entry : data.stateNameForComposite.entrySet()) {
            int state = entry.getValue();
            if (state >= data.generatedStates()) {
                statesForState[state] = data.getNextStates(entry.getKey());
            }
        }

        data.global.kinds[data.getStateIndex()] = kindsForStates;
        data.global.statesForState[data.getStateIndex()] = statesForState;

        getMoves(data, MoveKind.ascii(0));
        getMoves(data, MoveKind.ascii(1));
        getMoves(data, MoveKind.NON_ASCII);
    }

    // -----------------------------------------------------------------------
    // Move helpers
    // -----------------------------------------------------------------------

    /**
     * One group of moves the move code dispatches on: the ASCII moves of one half of the ASCII
     * range, or the moves beyond ASCII. The three differ only in what counts as a move, when two
     * moves are the same, and the order in which the members of a composite state are emitted.
     */
    private record MoveKind(Predicate<NfaState> has, BiPredicate<NfaState, NfaState> same,
                            BiFunction<NfaStateData, int[], List<NfaState>> order) {

        static MoveKind ascii(int byteNum) {
            return new MoveKind(s -> s.asciiMoves[byteNum] != 0L,
                    (a, b) -> a.asciiMoves[byteNum] == b.asciiMoves[byteNum],
                    (data, states) -> data.asciiPartition(states, byteNum).stream()
                            .flatMap(List::stream).toList());
        }

        static final MoveKind NON_ASCII = new MoveKind(s -> s.nonAsciiMethod != -1,
                (a, b) -> a.nonAsciiMethod == b.nonAsciiMethod,
                (data, states) -> Arrays.stream(states).mapToObj(data::getAllState)
                        .filter(s -> s.nonAsciiMethod != -1).toList());
    }

    private static void getMoves(NfaStateData data, MoveKind kind) {
        boolean[] dumped = new boolean[data.stateNameCount()];
        for (String key : data.compositeStateTable.keySet()) {
            getCompositeStateMoves(data, key, kind, dumped);
        }

        for (NfaState element : data.getAllStates()) {
            if (dumped[element.stateName] || !kind.has().test(element)) {
                continue;
            }

            dumped[element.stateName] = true;
            getMove(data, element, kind, dumped);
        }
    }

    private static void getCompositeStateMoves(NfaStateData data, String key, MoveKind kind,
                                               boolean[] dumped) {
        int[] nameSet = data.getNextStates(key);

        if ((nameSet.length == 1) || dumped[data.compositeStateName(key)]) {
            return;
        }

        NfaState toBePrinted = null;
        int neededStates = 0;

        for (int name : nameSet) {
            NfaState tmp = data.getAllState(name);

            if (kind.has().test(tmp)) {
                if (neededStates++ == 1) {
                    break;
                } else {
                    toBePrinted = tmp;
                }
            } else {
                dumped[tmp.stateName] = true;
            }
        }

        if (neededStates == 0) {
            return;
        }

        if (neededStates == 1) {
            dumped[toBePrinted.stateName] = true;
            getMove(data, toBePrinted, kind, dumped);
            return;
        }

        int keyState = data.compositeStateName(key);
        if (keyState < data.generatedStates()) {
            dumped[keyState] = true;
        }

        for (NfaState element : kind.order().apply(data, nameSet)) {
            getMoveForCompositeState(data, element, kind);
        }
    }

    private static void getMove(NfaStateData data, NfaState state, MoveKind kind, boolean[] dumped) {
        boolean nextIntersects = state.selfLoop() && state.isComposite;

        for (NfaState element : data.getAllStates()) {
            if ((state == element) || (state.stateName == element.stateName)
                    || !kind.has().test(element)) {
                continue;
            }

            if (!nextIntersects && NfaState.Intersect(data, element.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
            }

            if (!dumped[element.stateName] && !element.isComposite && kind.same().test(state, element)
                    && (state.kindToPrint == element.kindToPrint)
                    && Objects.equals(state.next.epsilonMovesString, element.next.epsilonMovesString)) {
                dumped[element.stateName] = true;
            }
        }

        registerNextStateSet(data, state, nextIntersects);
    }

    private static void getMoveForCompositeState(NfaStateData data, NfaState state, MoveKind kind) {
        boolean nextIntersects = state.selfLoop();

        for (NfaState element : data.getAllStates()) {
            if (nextIntersects) {
                break;
            }
            if ((state != element) && (state.stateName != element.stateName)
                    && kind.has().test(element)
                    && NfaState.Intersect(data, element.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
            }
        }

        registerNextStateSet(data, state, nextIntersects);
    }

    /**
     * Registers the state set a move leads to, and notes which jjCheckNAddStates variant the
     * generated lexer needs for it. The registration order is the order of {@code jjnextStates}.
     */
    private static void registerNextStateSet(NfaStateData data, NfaState state,
                                             boolean nextIntersects) {
        if ((state.next == null) || (state.next.usefulEpsilonMoves <= 0)) {
            return;
        }
        int useful = state.next.usefulEpsilonMoves;
        if ((useful == 1) || ((useful == 2) && nextIntersects)) {
            return;
        }

        int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
        if (nextIntersects) {
            if ((indices[0] + 1) != indices[1]) {
                data.global.jjCheckNAddStatesDualNeeded = true;
            } else {
                data.global.jjCheckNAddStatesUnaryNeeded = true;
            }
        }
    }

    // -----------------------------------------------------------------------
    // State set helpers
    // -----------------------------------------------------------------------

    /**
     * Registers the composite state sets {@code jjStopStringLiteralDfa} returns: when the
     * string-literal DFA gives up after position i, the NFA resumes in the set recorded for i. A
     * set that is not registered is never marked composite, so the move code has no case for it
     * and the NFA continues from one member state only. Runs before {@link #getDfaCode}, which is
     * the order JavaCC registered them in.
     */
    static void registerStopStateSets(NfaStateData data) {
        if (!data.hasNFA || data.isMixedState() || (data.maxStrKind == 0)) {
            return;
        }

        for (int i = 0; i < (data.maxLen - 1); i++) {
            if (data.statesForPos[i] == null) {
                continue;
            }
            // Every key has at least one active kind: it was made for one.
            for (String key : data.statesForPos[i].keySet()) {
                String s = data.stopKey(key).stateSet();
                if (!s.equals("null;")) {
                    data.addCompositeStateSet(s);
                }
            }
        }
    }

    private static int getStateSetForKind(NfaStateData data, int pos, int kind) {
        if (data.isMixedState() || (data.generatedStates() == 0)) {
            return -1;
        }

        Hashtable<String, long[]> allStateSets = data.statesForPos[pos];
        if (allStateSets == null) {
            return -1;
        }

        for (var entry : allStateSets.entrySet()) {
            String s = data.stopKey(entry.getKey()).stateSet();
            long[] actives = entry.getValue();

            if (s.equals("null;")) {
                continue;
            }

            if (Bits.test(actives, kind)) {
                return data.addCompositeStateSet(s);
            }
        }
        return -1;
    }

    private static void addCharToSkip(NfaStateData data, char c, int kind) {
        NfaState singlesToSkip = data.global.singlesToSkip[data.getStateIndex()];
        singlesToSkip.AddChar(c);
        singlesToSkip.kind = kind;
    }
}
