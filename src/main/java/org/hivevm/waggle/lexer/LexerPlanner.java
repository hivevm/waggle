// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.lexer;

import org.hivevm.waggle.lexer.LexerPlan.ActionCase;
import org.hivevm.waggle.model.CodeText;
import org.hivevm.waggle.lexer.LexerPlan.ActiveMask;
import org.hivevm.waggle.lexer.LexerPlan.Actions;
import org.hivevm.waggle.lexer.LexerPlan.ByteMask;
import org.hivevm.waggle.lexer.LexerPlan.CharCase;
import org.hivevm.waggle.lexer.LexerPlan.CanMove;
import org.hivevm.waggle.lexer.LexerPlan.CanMoveArm;
import org.hivevm.waggle.lexer.LexerPlan.CanMoveCase;
import org.hivevm.waggle.lexer.LexerPlan.DfaPos;
import org.hivevm.waggle.lexer.LexerPlan.Dispatch;
import org.hivevm.waggle.lexer.LexerPlan.Exit;
import org.hivevm.waggle.lexer.LexerPlan.Final;
import org.hivevm.waggle.lexer.LexerPlan.FinalAction;
import org.hivevm.waggle.lexer.LexerPlan.Handoff;
import org.hivevm.waggle.lexer.LexerPlan.ImageSource;
import org.hivevm.waggle.lexer.LexerPlan.KindSet;
import org.hivevm.waggle.lexer.LexerPlan.KindTable;
import org.hivevm.waggle.lexer.LexerPlan.LexStatePlan;
import org.hivevm.waggle.lexer.LexerPlan.NameForm;
import org.hivevm.waggle.lexer.LexerPlan.NamedToken;
import org.hivevm.waggle.lexer.LexerPlan.Shape;
import org.hivevm.waggle.lexer.LexerPlan.SkipRange;
import org.hivevm.waggle.lexer.LexerPlan.SkipSingles;
import org.hivevm.waggle.lexer.LexerPlan.StopCase;
import org.hivevm.waggle.lexer.LexerPlan.StopMatch;
import org.hivevm.waggle.lexer.LexerPlan.StopPos;
import org.hivevm.waggle.lexer.LexerPlan.Tables;
import org.hivevm.waggle.lexer.LexerPlan.TokenLoop;
import org.hivevm.waggle.lexer.LexerPlan.TokenName;
import org.hivevm.waggle.lexer.NfaStateData.KindInfo;
import org.hivevm.waggle.model.Action;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.RStringLiteral;
import org.hivevm.waggle.model.RegExprSpec;
import org.hivevm.waggle.model.TokenProduction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

/**
 * Builds the {@link LexerPlan} from the finished automaton (ADR-0029). Every decision the back ends
 * used to take while printing is taken here, once.
 */
final class LexerPlanner {

    private LexerPlanner() {
    }

    static LexerPlan plan(LexerData data) {
        var states = new ArrayList<LexStatePlan>();
        boolean stopAtPos = true;
        for (String name : data.getStateNames()) {
            NfaStateData stateData = data.getStateData(name);
            states.add(state(stateData, stopAtPos && (stateData.maxLen > 0)));
            stopAtPos &= (stateData.maxLen == 0);
        }
        return new LexerPlan(data.getDebugTokenManager(), List.copyOf(states), tokenLoop(data),
                actions(data), tables(data),
                data.nonAsciiTableForMethod.stream().map(s -> canMove(data, s)).toList(),
                tokenNames(data), namedTokens(data), shape(data));
    }

    /** The numbers and flags the generated lexer's scaffolding is built from. */
    private static Shape shape(LexerData data) {
        var images = new ArrayList<String>();
        for (int kind = 0; kind < data.getImageCount(); kind++) {
            images.add(data.getImage(kind));
        }
        return new Shape(data.maxLexStates(), data.defaultLexState(),
                List.copyOf(data.getStateNames()), data.stateSetSize(),
                data.jjCheckNAddStatesDualNeeded(), data.jjCheckNAddStatesUnaryNeeded(),
                data.hasLoop(), data.hasSpecial(), data.hasEmptyMatch(),
                data.hasTokenActions(), data.hasSkipActions(), data.hasMoreActions(),
                Collections.unmodifiableList(images));
    }

    /** The named tokens in the order the grammar declared them, for the constants file. */
    private static List<NamedToken> namedTokens(LexerData data) {
        var tokens = new ArrayList<NamedToken>();
        data.getOrderedsTokens().forEach(re -> tokens.add(new NamedToken(re.getOrdinal(),
                re.getLabel())));
        return List.copyOf(tokens);
    }

    private static List<TokenName> tokenNames(LexerData data) {
        var names = new ArrayList<TokenName>();
        names.add(new TokenName(NameForm.EOF, 0, "", null));
        for (TokenProduction production : data.getTokenProductions()) {
            for (RegExprSpec spec : production.getRespecs()) {
                RExpression re = spec.rexp;
                if (re instanceof RStringLiteral literal) {
                    names.add(new TokenName(NameForm.LITERAL, re.getOrdinal(), re.getLabel(),
                            literal.getImage()));
                } else if (!re.getLabel().isEmpty()) {
                    names.add(new TokenName(NameForm.LABEL, re.getOrdinal(), re.getLabel(), null));
                } else {
                    // An unlabelled token still needs its entry, or every later index is off by
                    // one.
                    names.add(new TokenName(NameForm.KIND, re.getOrdinal(), "", null));
                }
            }
        }
        return List.copyOf(names);
    }

    private static TokenLoop tokenLoop(LexerData data) {
        boolean anyActions = data.hasMoreActions || data.hasSkipActions || data.hasTokenActions;
        Dispatch dispatch = (data.maxLexStates == 0) ? null
                : new Dispatch(data.hasSkip || data.hasMore || data.hasSpecial,
                data.hasSkip || data.hasSpecial, data.hasMore, data.hasSpecial,
                data.hasTokenActions, data.hasSkipActions, data.hasMoreActions,
                !data.hasMoreActions && (data.hasSkipActions || data.hasTokenActions),
                data.maxLexStates > 1);
        return new TokenLoop(data.hasEof(), anyActions, data.hasMore, data.maxLexStates > 1,
                dispatch);
    }

    private static Actions actions(LexerData data) {
        return new Actions(
                actions(data, data.toToken, i -> (i == 0) ? ImageSource.RESET : imageOf(data, i)),
                actions(data, data.toMore, i -> imageOf(data, i)),
                actions(data, data.toSkip, i -> imageOf(data, i)));
    }

    private static ImageSource imageOf(LexerData data, int kind) {
        return (data.getImage(kind) != null) ? ImageSource.LITERAL : ImageSource.MATCH;
    }

    /**
     * One case per kind of {@code kinds} that has a lexical action or can loop on the empty
     * string.
     */
    private static List<ActionCase> actions(LexerData data, long[] kinds,
                                            IntFunction<ImageSource> image) {
        var cases = new ArrayList<ActionCase>();
        for (int i = 0; i < data.maxOrdinal; i++) {
            if (!Bits.test(kinds, i)) {
                continue;
            }

            Action act = data.actions(i);
            boolean hasAction = (act != null) && !act.getActionTokens().isEmpty();
            int lexState = data.getState(i);
            boolean canLoop = data.canLoop(lexState);
            if (!hasAction && !canLoop) {
                continue;
            }

            cases.add(new ActionCase(i, lexState, (data.initMatch(lexState) == i) && canLoop,
                    hasAction ? new CodeText(act.getActionTokens()) : CodeText.NONE,
                    image.apply(i)));
        }
        return List.copyOf(cases);
    }

    /** The characters only ever skipped, or null when there are none. */
    private static SkipSingles skip(NfaState singles) {
        if (!singles.HasTransitions()) {
            return null;
        }
        long lower = singles.asciiMoves[0];
        long upper = singles.asciiMoves[1];
        if ((lower != 0L) && (upper != 0L)) {
            return new SkipSingles(SkipRange.BOTH, lower, upper, -1);
        } else if (upper == 0L) {
            return new SkipSingles(SkipRange.LOWER, lower, upper, maxChar(lower));
        }
        return new SkipSingles(SkipRange.UPPER, lower, upper, maxChar(upper) + 64);
    }

    /** The highest bit set in l, or 0xffff when there is none. */
    private static int maxChar(long l) {
        return (l == 0L) ? 0xffff : (63 - Long.numberOfLeadingZeros(l));
    }

    private static Tables tables(LexerData data) {
        List<Integer> newLexState = (data.maxLexStates <= 1) ? List.of()
                : Arrays.stream(data.newLexState)
                .map(name -> (name == null) ? -1 : data.getStateIndex(name)).toList();

        var kindTables = new ArrayList<KindTable>();
        if (data.hasSkip || data.hasMore || data.hasSpecial) {
            kindTables.add(new KindTable(KindSet.TOKEN, words(data.toToken)));
        }
        if (data.hasSkip || data.hasSpecial) {
            kindTables.add(new KindTable(KindSet.SKIP, words(data.toSkip)));
        }
        if (data.hasSpecial) {
            kindTables.add(new KindTable(KindSet.SPECIAL, words(data.toSpecial)));
        }
        if (data.hasMore) {
            kindTables.add(new KindTable(KindSet.MORE, words(data.toMore)));
        }

        var byteMasks = new ArrayList<ByteMask>();
        for (int i = 0; i < data.bitVectors.size(); i++) {
            if (!data.bitVectors.get(i).allBitsSet()) {
                byteMasks.add(new ByteMask(i, words(data.bitVectors.get(i).words())));
            }
        }

        var nextStates = data.orderedStateSet.stream()
                .flatMap(set -> Arrays.stream(set).boxed()).toList();

        var kindsForState = new ArrayList<List<Integer>>();
        var statesForState = new ArrayList<List<List<Integer>>>();
        for (int i = 0; i < data.maxLexStates; i++) {
            int[] kinds = (data.kinds == null) ? null : data.kinds[i];
            kindsForState.add((kinds == null) ? List.of() : Arrays.stream(kinds).boxed().toList());

            int[][] sets = (data.statesForState == null) ? null : data.statesForState[i];
            statesForState.add((sets == null) ? List.of() : IntStream.range(0, sets.length)
                    .mapToObj(j -> ((sets[j] == null) || (sets[j].length == 0)) ? List.of(j)
                            : Arrays.stream(sets[j]).boxed().toList())
                    .toList());
        }
        return new Tables(newLexState, List.copyOf(kindTables), List.copyOf(byteMasks),
                nextStates, data.kinds != null, List.copyOf(kindsForState),
                List.copyOf(statesForState));
    }

    private static List<Long> words(long[] words) {
        return Arrays.stream(words).boxed().toList();
    }

    private static CanMove canMove(LexerData data, NfaState state) {
        var cases = new ArrayList<CanMoveCase>();
        for (int j = 0; j < state.loByteVec.size(); j += 2) {
            int mask = state.loByteVec.get(j + 1);
            cases.add(new CanMoveCase(state.loByteVec.get(j), mask,
                    data.bitVectors.get(mask).allBitsSet()));
        }

        var arms = new ArrayList<CanMoveArm>();
        for (int j = state.nonAsciiMoveIndices.length; j > 0; j -= 2) {
            int hiMask = state.nonAsciiMoveIndices[j - 2];
            int loMask = state.nonAsciiMoveIndices[j - 1];
            arms.add(new CanMoveArm(hiMask, loMask, !data.bitVectors.get(hiMask).allBitsSet(),
                    !data.bitVectors.get(loMask).allBitsSet()));
        }
        return new CanMove(state.nonAsciiMethod, List.copyOf(cases), List.copyOf(arms));
    }

    private static LexStatePlan state(NfaStateData data, boolean stopAtPos) {
        int index = data.getStateIndex();
        int words = (data.maxStrKind / 64) + 1;

        int initMatch = data.global.initMatch(index);
        int emptyMatch = ((initMatch != 0) && (initMatch != Integer.MAX_VALUE)) ? initMatch : -1;

        Handoff handoff = !data.hasNFA ? Handoff.NONE
                : data.isMixedState() ? Handoff.MOVE_NFA : Handoff.START_NFA;

        var positions = new ArrayList<DfaPos>();
        for (int i = 0; i < data.maxLen; i++) {
            final int pos = i;
            List<CharCase> cases = data.dfaCases.get(i);
            Exit otherwise = !data.hasNFA ? Exit.RETURN : (i == 0) ? Exit.MOVE_NFA : Exit.BREAK;
            boolean tail = (i != 0) && ((otherwise == Exit.BREAK)
                    || cases.stream().anyMatch(c -> c.next() == Exit.BREAK));
            positions.add(new DfaPos(i, params(data, i, words),
                    IntStream.range(0, words).mapToObj(k -> pos <= data.getMaxLenForActive(k))
                            .toList(),
                    IntStream.range(0, words).mapToObj(k -> pos <= (data.getMaxLenForActive(k) + 1))
                            .toList(),
                    cases, otherwise, tail));
        }
        return new LexStatePlan(index, words, emptyMatch, initState(data), List.copyOf(positions),
                skip(data.global.singlesToSkip(index)), data.global.canMatchAnyChar(index),
                stopDfa(data, words, emptyMatch >= 0), handoff,
                (handoff == Handoff.START_NFA) && data.createStartNfa, stopAtPos, data.moves);
    }

    /**
     * The case of character {@code c} at position {@code i} of the string-literal DFA. Called by
     * {@link DfaBuilder#getDfaCode} for every character it does not leave to the skip loop, once
     * it has registered the state sets the literals ending there lead to.
     *
     * @param stateSets per final kind of {@code info}, ascending, the state set the NFA goes on in
     *                  when that literal is matched here, or -1
     */
    static CharCase charCase(NfaStateData data, int i, char c, KindInfo info, int[] stateSets) {
        var labels = new ArrayList<Integer>();
        if (data.ignoreCase()) {
            if (c != Character.toUpperCase(c)) {
                labels.add((int) Character.toUpperCase(c));
            }
            if (c != Character.toLowerCase(c)) {
                labels.add((int) Character.toLowerCase(c));
            }
        }

        int initMatch = data.global.initMatch(data.getStateIndex());
        boolean matchesEmpty = (initMatch != 0) && (initMatch != Integer.MAX_VALUE);
        var finals = new ArrayList<Final>();
        int[] kinds = info.finalKindsAscending();
        for (int k = 0; k < kinds.length; k++) {
            int kind = kinds[k];
            int kindToPrint = data.kindToPrint(i, kind);
            if (!data.isSubString(kind)) {
                int stateSet = stateSets[k];
                finals.add(new Final(kind / 64, 1L << (kind % 64), kindToPrint,
                        (stateSet != -1) ? FinalAction.START_NFA_WITH_STATES
                                : FinalAction.STOP_AT_POS, stateSet));
            } else {
                finals.add(new Final(kind / 64, 1L << (kind % 64), kindToPrint,
                        (matchesEmpty || (i != 0)) ? FinalAction.KIND_AND_POS : FinalAction.KIND,
                        -1));
            }
        }

        var masks = new ArrayList<ActiveMask>();
        Exit next;
        if (info.hasValidKindCnt()) {
            next = Exit.CALL;
            for (int v : params(data, i + 1, (data.maxStrKind / 64) + 1)) {
                masks.add(new ActiveMask(v, info.validKinds[v]));
            }
        } else if ((i == 0) && data.isMixedState()) {
            next = data.hasNFA ? Exit.MOVE_NFA : Exit.RETURN;
        } else if (i != 0) { // No more string literals to look for
            next = Exit.BREAK;
        } else {
            next = Exit.NONE;
        }
        return new CharCase(c, List.copyOf(labels), List.copyOf(finals), next, List.copyOf(masks));
    }

    /**
     * The cases of {@code jjStopStringLiteralDfa}, in the order of the keys of
     * {@link NfaStateData#statesForPos}: that {@link java.util.Hashtable} order is the order of
     * the tests in the generated token manager.
     */
    private static List<StopPos> stopDfa(NfaStateData data, int words, boolean matchesEmpty) {
        if (!data.hasNFA || data.isMixedState() || (data.maxStrKind == 0)) {
            return null;
        }

        var positions = new ArrayList<StopPos>();
        for (int i = 0; i < (data.maxLen - 1); i++) {
            if (data.statesForPos[i] == null) {
                continue;
            }

            var cases = new ArrayList<StopCase>();
            for (String key : data.statesForPos[i].keySet()) {
                long[] actives = data.statesForPos[i].get(key);
                var guard = new ArrayList<ActiveMask>();
                for (int j = 0; j < words; j++) {
                    if (actives[j] != 0L) {
                        guard.add(new ActiveMask(j, actives[j]));
                    }
                }

                var stop = data.stopKey(key);
                int kind = stop.kind();
                int matchedPos = stop.matchedPos();
                StopMatch match;
                if (kind == Integer.MAX_VALUE) {
                    match = StopMatch.NONE;
                } else if (i == 0) {
                    match = matchesEmpty ? StopMatch.FIRST_AFTER_EMPTY : StopMatch.FIRST;
                } else if (i == matchedPos) {
                    match = data.isSubStringAtPos(i) ? StopMatch.HERE_UNLESS_MATCHED
                            : StopMatch.HERE;
                } else {
                    match = (matchedPos > 0) ? StopMatch.EARLIER : StopMatch.EARLIER_AT_FIRST;
                }

                String stateSet = stop.stateSet();
                int resume = stateSet.equals("null;") ? -1 : data.compositeStateName(stateSet);
                cases.add(new StopCase(List.copyOf(guard), match, kind, matchedPos, resume));
            }
            positions.add(new StopPos(i, List.copyOf(cases)));
        }
        return List.copyOf(positions);
    }

    /** The composite state the NFA starts in, or -1 when it has none. */
    private static int initState(NfaStateData data) {
        NfaState init = data.getInitialState();
        if (!data.hasNFA || (init.usefulEpsilonMoves == 0)) {
            return -1;
        }
        return data.compositeStateName(init.epsilonMovesString);
    }

    /**
     * The vectors {@code jjMoveStringLiteralDfa<i>} takes: those that still hold a literal long
     * enough to reach position {@code i}. The first position takes none.
     */
    static List<Integer> params(NfaStateData data, int i, int words) {
        return IntStream.range(0, words)
                .filter(j -> (i == 1) ? (i <= data.getMaxLenForActive(j))
                        : (i > 1) && (i <= (data.getMaxLenForActive(j) + 1)))
                .boxed().toList();
    }
}
