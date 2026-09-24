// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/NfaState.java, org/javacc/parser/RStringLiteral.java

package org.hivevm.waggle.lexer;

import org.hivevm.waggle.lexer.NfaStateData.KindInfo;

import java.util.Hashtable;
import java.util.ArrayList;
import java.util.List;

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
        Hashtable<String, ?> tab;
        String key;
        KindInfo info;
        int maxLongsReqd = (data.maxStrKind / 64) + 1;
        int i, j, k;

        data.createStartNfa = false;
        for (i = 0; i < data.maxLen; i++) {
            tab = data.charPosKind.get(i);
            CaseLoop:
            for (String key2 : NfaStateData.reArrange(tab)) {
                key = key2;
                info = (KindInfo) tab.get(key);
                char c = key.charAt(0);

                if ((i == 0) && (c < 128) && info.hasFinalKindCnt()
                        && ((data.generatedStates() == 0) || !data.canStartNfaUsingAscii(c))) {
                    int kind;
                    for (j = 0; j < maxLongsReqd; j++) {
                        if (info.finalKinds[j] != 0L) {
                            break;
                        }
                    }

                    for (k = 0; k < 64; k++) {
                        if (((info.finalKinds[j] & (1L << k)) != 0L) && !data.subString[kind = (
                                (j * 64) + k)]) {
                            if (((data.intermediateKinds != null) && (
                                    data.intermediateKinds[((j * 64) + k)] != null)
                                    && (data.intermediateKinds[((j * 64) + k)][i] < ((j * 64) + k))
                                    && (data.intermediateMatchedPos != null) && (
                                    data.intermediateMatchedPos[((j * 64) + k)][i] == i))
                                    || ((data.global.canMatchAnyChar[data.getStateIndex()] >= 0)
                                    && (data.global.canMatchAnyChar[data.getStateIndex()] < ((j * 64) + k)))) {
                                break;
                            } else if (((data.global.toSkip[kind / 64] & (1L << (kind % 64))) != 0L)
                                    && ((data.global.toSpecial[kind / 64] & (1L << (kind % 64))) == 0L)
                                    && (data.global.actions[kind] == null) && (
                                    data.global.newLexState[kind] == null)) {
                                addCharToSkip(data, data.global.singlesToSkip, c, kind);

                                if (data.ignoreCase()) {
                                    if (c != Character.toUpperCase(c)) {
                                        addCharToSkip(data, data.global.singlesToSkip,
                                                Character.toUpperCase(c), kind);
                                    }
                                    if (c != Character.toLowerCase(c)) {
                                        addCharToSkip(data, data.global.singlesToSkip,
                                                Character.toLowerCase(c), kind);
                                    }
                                }
                                continue CaseLoop;
                            }
                        }
                    }
                }

                long matchedKind;
                if (info.hasFinalKindCnt()) {
                    for (j = 0; j < maxLongsReqd; j++) {
                        if ((matchedKind = info.finalKinds[j]) == 0L) {
                            continue;
                        }

                        for (k = 0; k < 64; k++) {
                            if ((matchedKind & (1L << k)) == 0L) {
                                continue;
                            }

                            if (!data.subString[((j * 64) + k)]) {
                                int stateSetName = getStateSetForKind(data, i, (j * 64) + k);
                                data.putStateSetName(i, (j * 64) + k, stateSetName);
                                if (stateSetName != -1) {
                                    data.createStartNfa = true;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Computes NFA move tables for a single lexer state (ASCII + char/range moves).
     */
    static void getMoveNfa(NfaStateData data) {
        int i;
        int[] kindsForStates = null;

        if (data.global.kinds == null) {
            data.global.kinds = new int[data.global.maxLexStates][];
            data.global.statesForState = new int[data.global.maxLexStates][][];
        }

        // After this, allStates holds exactly the indexed states of this lexical state: each one
        // was named because it had transitions, and dummies and unnamed states are left out.
        reArrange(data);

        for (i = 0; i < data.getAllStateCount(); i++) {
            NfaState temp = data.getAllState(i);

            if (kindsForStates == null) {
                kindsForStates = new int[data.generatedStates()];
                data.global.statesForState[data.getStateIndex()] =
                        new int[Math.max(data.generatedStates(), data.dummyStateIndex + 1)][];
            }

            kindsForStates[temp.stateName] = temp.lookingFor;
            data.global.statesForState[data.getStateIndex()][temp.stateName] = temp.compositeStates;
        }

        for (var entry : data.stateNameForComposite.entrySet()) {
            String s = entry.getKey();
            int state = entry.getValue();

            if (state >= data.generatedStates()) {
                data.global.statesForState[data.getStateIndex()][state] = data.getNextStates(s);
            }
        }

        data.global.kinds[data.getStateIndex()] = kindsForStates;

        if (data.generatedStates() == 0) {
            return;
        }

        getAsciiMoves(data, 0);
        getAsciiMoves(data, 1);
        getCharAndRangeMoves(data);
    }

    // -----------------------------------------------------------------------
    // ASCII move helpers
    // -----------------------------------------------------------------------

    private static void getAsciiMoves(NfaStateData data, int byteNum) {
        boolean[] dumped = new boolean[Math.max(data.generatedStates(), data.dummyStateIndex + 1)];
        for (String key : data.compositeStateTable.keySet()) {
            getCompositeStatesAsciiMoves(data, key, byteNum, dumped);
        }

        for (NfaState element : data.getAllStates()) {
            if (dumped[element.stateName] || (element.asciiMoves[byteNum] == 0L)) {
                continue;
            }

            dumped[element.stateName] = true;
            getAsciiMove(data, element, byteNum, dumped);
        }
    }

    private static void getCompositeStatesAsciiMoves(NfaStateData data, String key, int byteNum,
                                                     boolean[] dumped) {
        int i;
        int[] nameSet = data.getNextStates(key);

        if ((nameSet.length == 1) || dumped[stateNameForComposite(data, key)]) {
            return;
        }

        NfaState toBePrinted = null;
        int neededStates = 0;
        NfaState tmp;

        for (i = 0; i < nameSet.length; i++) {
            tmp = data.getAllState(nameSet[i]);

            if (tmp.asciiMoves[byteNum] != 0L) {
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
            getAsciiMove(data, toBePrinted, byteNum, dumped);
            return;
        }

        List<List<NfaState>> partition = partitionStatesSetForAscii(data, nameSet, byteNum);
        int keyState = stateNameForComposite(data, key);
        if (keyState < data.generatedStates()) {
            dumped[keyState] = true;
        }

        for (List<NfaState> subSet : partition) {
            for (NfaState element : subSet) {
                getAsciiMoveForCompositeState(data, element, byteNum);
            }
        }
    }

    private static void getAsciiMove(NfaStateData data, NfaState state, int byteNum,
                                     boolean[] dumped) {
        boolean nextIntersects = state.selfLoop() && state.isComposite;

        for (NfaState element : data.getAllStates()) {
            if ((state == element) || (state.stateName
                    == element.stateName) || (element.asciiMoves[byteNum] == 0L)) {
                continue;
            }

            if (!nextIntersects && NfaState.Intersect(data, element.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
            }

            if (!dumped[element.stateName] && !element.isComposite && (state.asciiMoves[byteNum]
                    == element.asciiMoves[byteNum])
                    && (state.kindToPrint == element.kindToPrint)
                    && ((state.next.epsilonMovesString == element.next.epsilonMovesString)
                    || ((state.next.epsilonMovesString != null) && (element.next.epsilonMovesString != null)
                    && state.next.epsilonMovesString.equals(element.next.epsilonMovesString)))) {
                dumped[element.stateName] = true;
            }
        }

        registerNextStateSet(data, state, nextIntersects);
    }

    private static void getAsciiMoveForCompositeState(NfaStateData data, NfaState state,
                                                      int byteNum) {
        boolean nextIntersects = state.selfLoop();

        for (NfaState temp1 : data.getAllStates()) {
            if ((state == temp1) || (state.stateName
                    == temp1.stateName) || (temp1.asciiMoves[byteNum] == 0L)) {
                continue;
            }

            if (!nextIntersects && NfaState.Intersect(data, temp1.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
                break;
            }
        }

        registerNextStateSet(data, state, nextIntersects);
    }

    // -----------------------------------------------------------------------
    // Non-ASCII move helpers
    // -----------------------------------------------------------------------

    private static void getCharAndRangeMoves(NfaStateData data) {
        boolean[] dumped = new boolean[Math.max(data.generatedStates(), data.dummyStateIndex + 1)];
        int i;

        for (String key : data.compositeStateTable.keySet()) {
            getCompositeStatesNonAsciiMoves(data, key, dumped);
        }

        for (i = 0; i < data.getAllStateCount(); i++) {
            NfaState temp = data.getAllState(i);
            if (dumped[temp.stateName] || (temp.nonAsciiMethod == -1)) {
                continue;
            }

            dumped[temp.stateName] = true;
            getNonAsciiMove(data, temp, dumped);
        }
    }

    private static void getCompositeStatesNonAsciiMoves(NfaStateData data, String key,
                                                        boolean[] dumped) {
        int i;
        int[] nameSet = data.getNextStates(key);

        if ((nameSet.length == 1) || dumped[stateNameForComposite(data, key)]) {
            return;
        }

        NfaState toBePrinted = null;
        int neededStates = 0;
        NfaState tmp;

        for (i = 0; i < nameSet.length; i++) {
            tmp = data.getAllState(nameSet[i]);

            if (tmp.nonAsciiMethod != -1) {
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
            getNonAsciiMove(data, toBePrinted, dumped);
            return;
        }

        int keyState = stateNameForComposite(data, key);
        if (keyState < data.generatedStates()) {
            dumped[keyState] = true;
        }

        for (i = 0; i < nameSet.length; i++) {
            tmp = data.getAllState(nameSet[i]);

            if (tmp.nonAsciiMethod != -1) {
                getNonAsciiMoveForCompositeState(data, tmp);
            }
        }
    }

    private static void getNonAsciiMoveForCompositeState(NfaStateData data, NfaState state) {
        boolean nextIntersects = state.selfLoop();
        for (NfaState temp1 : data.getAllStates()) {
            if ((state == temp1) || (state.stateName
                    == temp1.stateName) || (temp1.nonAsciiMethod == -1)) {
                continue;
            }

            if (!nextIntersects && NfaState.Intersect(data, temp1.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
                break;
            }
        }

        registerNextStateSet(data, state, nextIntersects);
    }

    private static void getNonAsciiMove(NfaStateData data, NfaState state, boolean[] dumped) {
        boolean nextIntersects = state.selfLoop() && state.isComposite;

        for (NfaState element : data.getAllStates()) {
            if ((state == element) || (state.stateName
                    == element.stateName) || (element.nonAsciiMethod == -1)) {
                continue;
            }

            if (!nextIntersects && NfaState.Intersect(data, element.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
            }

            if (!dumped[element.stateName] && !element.isComposite && (state.nonAsciiMethod
                    == element.nonAsciiMethod)
                    && (state.kindToPrint == element.kindToPrint)
                    && ((state.next.epsilonMovesString == element.next.epsilonMovesString)
                    || ((state.next.epsilonMovesString != null) && (element.next.epsilonMovesString != null)
                    && state.next.epsilonMovesString.equals(element.next.epsilonMovesString)))) {
                dumped[element.stateName] = true;
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

    static void reArrange(NfaStateData data) {
        List<NfaState> v = data.takeAllStatesForReindex();

        for (NfaState tmp : v) {
            if ((tmp.stateName != -1) && !tmp.dummy) {
                data.setAllState(tmp.stateName, tmp);
            }
        }
    }

    private static int stateNameForComposite(NfaStateData data, String stateSetString) {
        return data.stateNameForComposite.get(stateSetString);
    }

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

        int maxKindsReqd = (data.maxStrKind / 64) + 1;
        for (int i = 0; i < (data.maxLen - 1); i++) {
            if (data.statesForPos[i] == null) {
                continue;
            }
            for (var entry : data.statesForPos[i].entrySet()) {
                long[] actives = entry.getValue();
                boolean anyActive = false;
                for (int j = 0; j < maxKindsReqd; j++) {
                    anyActive |= (actives[j] != 0L);
                }

                String s = entry.getKey();
                s = s.substring(s.indexOf(", ") + 2);
                s = s.substring(s.indexOf(", ") + 2);
                if (anyActive && !s.equals("null;")) {
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
            String s = entry.getKey();
            long[] actives = entry.getValue();

            s = s.substring(s.indexOf(", ") + 2);
            s = s.substring(s.indexOf(", ") + 2);
            if (s.equals("null;")) {
                continue;
            }

            if ((actives != null) && ((actives[kind / 64] & (1L << (kind % 64))) != 0L)) {
                return data.addCompositeStateSet(s);
            }
        }
        return -1;
    }

    private static void addCharToSkip(NfaStateData data, NfaState[] singlesToSkip, char c,
                                      int kind) {
        singlesToSkip[data.getStateIndex()].AddChar(c);
        singlesToSkip[data.getStateIndex()].kind = kind;
    }

    private static List<List<NfaState>> partitionStatesSetForAscii(NfaStateData data, int[] states, int byteNum) {
        int[] cardinalities = new int[states.length];
        List<NfaState> original = new ArrayList<>();
        List<List<NfaState>> partition = new ArrayList<>();
        NfaState tmp;
        int cnt = 0;
        for (int i = 0; i < states.length; i++) {
            tmp = data.getAllState(states[i]);

            if (tmp.asciiMoves[byteNum] != 0L) {
                int j;
                int p = Long.bitCount(tmp.asciiMoves[byteNum]);

                for (j = 0; j < i; j++) {
                    if (cardinalities[j] <= p) {
                        break;
                    }
                }

                for (int k = i; k > j; k--) {
                    cardinalities[k] = cardinalities[k - 1];
                }

                cardinalities[j] = p;
                original.add(j, tmp);
                cnt++;
            }
        }

        while (!original.isEmpty()) {
            tmp = original.getFirst();
            original.remove(tmp);

            long bitVec = tmp.asciiMoves[byteNum];
            List<NfaState> subSet = new ArrayList<>();
            subSet.add(tmp);

            for (int j = 0; j < original.size(); j++) {
                NfaState tmp1 = original.get(j);

                if ((tmp1.asciiMoves[byteNum] & bitVec) == 0L) {
                    bitVec |= tmp1.asciiMoves[byteNum];
                    subSet.add(tmp1);
                    original.remove(j--);
                }
            }

            partition.add(subSet);
        }

        return partition;
    }
}
