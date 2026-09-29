// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/NfaState.java, org/javacc/parser/RStringLiteral.java

package org.hivevm.waggle.lexer;

import org.hivevm.waggle.lexer.LexerPlan.Accept;
import org.hivevm.waggle.lexer.LexerPlan.ArmShape;
import org.hivevm.waggle.lexer.LexerPlan.CharCase;
import org.hivevm.waggle.lexer.LexerPlan.Guard;
import org.hivevm.waggle.lexer.LexerPlan.Move;
import org.hivevm.waggle.lexer.LexerPlan.MoveShape;
import org.hivevm.waggle.lexer.LexerPlan.MoveArm;
import org.hivevm.waggle.lexer.LexerPlan.NextForm;
import org.hivevm.waggle.lexer.LexerPlan.NextStates;
import org.hivevm.waggle.lexer.LexerPlan.NfaMoves;
import org.hivevm.waggle.lexer.NfaStateData.KindInfo;

import java.util.ArrayList;
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
class DfaBuilder {

    /**
     * Prepares DFA code data for a single lexer state (charPosKind → skip/token tables).
     */
    static void getDfaCode(NfaStateData data) {
        if (data.maxLen == 0) {
            return;
        }

        data.createStartNfa = false;
        for (int i = 0; i < data.maxLen; i++) {
            var cases = new ArrayList<CharCase>();
            data.dfaCases.add(cases);
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
                cases.add(LexerPlanner.charCase(data, i, c, info));
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

        data.moves = new NfaMoves(data.generatedStates(), getMoves(data, MoveKind.ascii(0)),
                getMoves(data, MoveKind.ascii(1)), getMoves(data, MoveKind.NON_ASCII));
    }

    // -----------------------------------------------------------------------
    // Move helpers
    // -----------------------------------------------------------------------

    /**
     * One group of moves the move code dispatches on: the ASCII moves of one half of the ASCII
     * range, or the moves beyond ASCII. The three differ only in what counts as a move, when two
     * moves are the same, and the order in which the members of a composite state are emitted -
     * for ASCII, groups of members with disjoint moves, each an if-else chain.
     *
     * @param byteNum the half of ASCII, or -1 beyond ASCII
     */
    private record MoveKind(int byteNum, Predicate<NfaState> has, BiPredicate<NfaState, NfaState> same,
                            BiFunction<NfaStateData, int[], List<List<NfaState>>> order) {

        static MoveKind ascii(int byteNum) {
            return new MoveKind(byteNum, s -> s.asciiMoves[byteNum] != 0L,
                    (a, b) -> a.asciiMoves[byteNum] == b.asciiMoves[byteNum],
                    (data, states) -> data.asciiPartition(states, byteNum));
        }

        static final MoveKind NON_ASCII = new MoveKind(-1, s -> s.nonAsciiMethod != -1,
                (a, b) -> a.nonAsciiMethod == b.nonAsciiMethod,
                (data, states) -> Arrays.stream(states).mapToObj(data::getAllState)
                        .filter(s -> s.nonAsciiMethod != -1).map(List::of).toList());
    }

    /**
     * The arms of the switch over the states for one group of moves, in output order. This one
     * walk decides the arms and registers the state sets they move into, so the order of
     * {@code jjnextStates} is the order of the arms (ADR-0029).
     */
    private static List<MoveArm> getMoves(NfaStateData data, MoveKind kind) {
        var arms = new ArrayList<MoveArm>();
        boolean[] dumped = new boolean[data.stateNameCount()];
        for (String key : data.compositeStateTable.keySet()) {
            getCompositeStateMoves(data, key, kind, dumped, arms);
        }

        for (NfaState element : data.getAllStates()) {
            if (dumped[element.stateName] || !kind.has().test(element)) {
                continue;
            }

            dumped[element.stateName] = true;
            var labels = new ArrayList<Integer>();
            labels.add(element.stateName);
            Move move = getMove(data, element, kind, dumped, labels);
            arms.add(new MoveArm(ArmShape.SINGLE, List.copyOf(labels), 1, List.of(move)));
        }
        return List.copyOf(arms);
    }

    private static void getCompositeStateMoves(NfaStateData data, String key, MoveKind kind,
                                               boolean[] dumped, List<MoveArm> arms) {
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
            var labels = new ArrayList<Integer>();
            labels.add(data.compositeStateName(key));
            if (!dumped[toBePrinted.stateName] && (toBePrinted.inNextOf > 1)) {
                labels.add(toBePrinted.stateName);
            }
            int leading = labels.size();

            dumped[toBePrinted.stateName] = true;
            Move move = getMove(data, toBePrinted, kind, dumped, labels);
            arms.add(new MoveArm(ArmShape.COMPOSITE_ONE, List.copyOf(labels), leading,
                    List.of(move)));
            return;
        }

        int keyState = data.compositeStateName(key);
        if (keyState < data.generatedStates()) {
            dumped[keyState] = true;
        }

        var moves = new ArrayList<Move>();
        for (List<NfaState> group : kind.order().apply(data, nameSet)) {
            for (int j = 0; j < group.size(); j++) {
                moves.add(getMoveForCompositeState(data, group.get(j), kind, j != 0));
            }
        }
        arms.add(new MoveArm(ArmShape.COMPOSITE, List.of(keyState), 1, List.copyOf(moves)));
    }

    /**
     * The move of a state that has an arm of its own. The states that move exactly like it and
     * have none yet join its arm: they are added to {@code labels} and marked as dumped.
     */
    private static Move getMove(NfaStateData data, NfaState state, MoveKind kind, boolean[] dumped,
                                List<Integer> labels) {
        boolean nextIntersects = state.selfLoop() && state.isComposite;
        boolean onlyState = true;

        for (NfaState element : data.getAllStates()) {
            if ((state == element) || (state.stateName == element.stateName)
                    || !kind.has().test(element)) {
                continue;
            }

            if (onlyState && (kind.byteNum() >= 0) && ((state.asciiMoves[kind.byteNum()]
                    & element.asciiMoves[kind.byteNum()]) != 0L)) {
                onlyState = false;
            }

            if (!nextIntersects && NfaState.Intersect(data, element.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
            }

            if (!dumped[element.stateName] && !element.isComposite && kind.same().test(state, element)
                    && (state.kindToPrint == element.kindToPrint)
                    && Objects.equals(state.next.epsilonMovesString, element.next.epsilonMovesString)) {
                dumped[element.stateName] = true;
                labels.add(element.stateName);
            }
        }

        // Beyond ASCII no state is ever the only mover: onlyState tests the ASCII masks, which
        // that group has none of, so it would report "only" for every state there.
        return move(data, state, kind, (kind.byteNum() < 0) || !onlyState, false, nextIntersects);
    }

    private static Move getMoveForCompositeState(NfaStateData data, NfaState state, MoveKind kind,
                                                 boolean elseIf) {
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

        // A member of a composite state shares its characters with the other members, so it never
        // takes a kind outright either.
        return move(data, state, kind, true, elseIf && (kind.byteNum() >= 0), nextIntersects);
    }

    private static Move move(NfaStateData data, NfaState state, MoveKind kind, boolean raise,
                             boolean elseIf, boolean nextIntersects) {
        var guard = guard(state, kind.byteNum());
        var accept = (state.kindToPrint == Integer.MAX_VALUE) ? null
                : new Accept(state.kindToPrint, raise);
        var next = nextStates(data, state, nextIntersects);
        return new Move(shape(guard, accept, next), guard, accept, elseIf, next);
    }

    /**
     * What a move tests: beyond ASCII the {@code jjCanMove} function of the state, inside it the
     * characters of its half of the range - one of them, a group of them, or all of them.
     */
    private static Guard guard(NfaState state, int byteNum) {
        if (byteNum < 0) {
            return new Guard.NonAscii(state.nonAsciiMethod);
        }
        long mask = state.asciiMoves[byteNum];
        if (mask == 0xffffffffffffffffL) {
            return new Guard.Always();
        }
        int oneBit = state.onlyOneAsciiMove(byteNum);
        return (oneBit != -1) ? new Guard.OneChar((64 * byteNum) + oneBit) : new Guard.Mask(mask);
    }

    /**
     * How the move is laid out. A move that accepts nothing only advances; one that accepts and
     * advances has to leave the arm when its guard does not hold, and so tests the guard the other
     * way round; one that only accepts folds the guard and the kind into a single condition.
     */
    private static MoveShape shape(Guard guard, Accept accept, NextStates next) {
        if (accept == null) {
            return MoveShape.ADVANCE;
        }
        return ((next == null) && !(guard instanceof Guard.Always)) ? MoveShape.ACCEPT
                : MoveShape.MATCH;
    }

    /**
     * The states a move leads to, and how they are added. A set of more than two, or of two that
     * need no check, is registered in {@code jjnextStates}, in the order of this walk; the
     * jjCheckNAddStates variant the generated lexer then needs is noted.
     */
    private static NextStates nextStates(NfaStateData data, NfaState state,
                                         boolean nextIntersects) {
        if ((state.next == null) || (state.next.usefulEpsilonMoves <= 0)) {
            return null;
        }

        int[] stateNames = data.getNextStates(state.next.epsilonMovesString);
        int useful = state.next.usefulEpsilonMoves;
        if (useful == 1) {
            return new NextStates(nextIntersects ? NextForm.CHECK_ADD : NextForm.ADD,
                    stateNames[0], -1, false);
        }
        if ((useful == 2) && nextIntersects) {
            return new NextStates(NextForm.CHECK_ADD_TWO, stateNames[0], stateNames[1], false);
        }

        int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
        boolean isRange = (indices[0] + 1) != indices[1];
        if (!nextIntersects) {
            return new NextStates(NextForm.ADD_STATES, indices[0], indices[1], isRange);
        }
        if (isRange) {
            data.global.jjCheckNAddStatesDualNeeded = true;
        } else {
            data.global.jjCheckNAddStatesUnaryNeeded = true;
        }
        return new NextStates(NextForm.CHECK_ADD_STATES, indices[0], indices[1], isRange);
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
