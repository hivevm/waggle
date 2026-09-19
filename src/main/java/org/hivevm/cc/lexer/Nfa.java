// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.lexer;

import org.hivevm.cc.model.RChoice;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.RStringLiteral;
import org.hivevm.cc.model.TokenKind;
import org.hivevm.cc.model.TokenProduction;
import org.hivevm.cc.model.RegExprSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/**
 * A Non-deterministic Finite Automaton.
 */
record Nfa(NfaState start, NfaState end) {

    Nfa(NfaStateData data) {
        this(new NfaState(data), new NfaState(data));
    }

    /**
     * Main NFA construction loop: processes all token productions and builds the NFA transitions.
     */
    public static void buildLexer(LexerData data, Hashtable<String, List<TokenProduction>> allTpsForState,
                                  List<RExpression> choices) {
        RExpression curRE;
        TokenKind[] kinds = new TokenKind[data.maxOrdinal];

        for (String key : allTpsForState.keySet()) {
            NfaStateData stateData = data.newStateData(key);
            stateData.getInitialState();

            data.singlesToSkip[stateData.getStateIndex()] = new NfaState(stateData);
            data.singlesToSkip[stateData.getStateIndex()].dummy = true;

            if (key.equals("DEFAULT")) {
                data.defaultLexState = stateData.getStateIndex();
            }

            boolean ignoring = false;
            List<TokenProduction> allTps = allTpsForState.get(key);
            for (int i = 0; i < allTps.size(); i++) {
                TokenProduction tp = allTps.get(i);
                TokenKind kind = tp.getKind();
                boolean ignore = tp.isIgnoreCase();

                if (i == 0) {
                    ignoring = ignore;
                }

                for (RegExprSpec respec : tp.getRespecs()) {
                    curRE = respec.rexp;

                    data.rexprs[data.curKind = curRE.getOrdinal()] = curRE;
                    data.lexStates[curRE.getOrdinal()] = stateData.getStateIndex();
                    data.ignoreCase[curRE.getOrdinal()] = ignore;

                    if (curRE.isPrivateExp()) {
                        kinds[curRE.getOrdinal()] = null;
                        continue;
                    }

                    if (!data.options().withoutNoDfa() && (curRE instanceof RStringLiteral)
                            && !((RStringLiteral) curRE).getImage().isEmpty()) {
                        StringLiteralAnalyzer.generateDfa(stateData, (RStringLiteral) curRE);
                        if ((i != 0) && !stateData.isMixedState() && (ignoring != ignore)) {
                            stateData.hasMixed = true;
                        }
                    } else if (curRE.CanMatchAnyChar()) {
                        if ((data.canMatchAnyChar[stateData.getStateIndex()] == -1)
                                || (data.canMatchAnyChar[stateData.getStateIndex()]
                                > curRE.getOrdinal())) {
                            data.canMatchAnyChar[stateData.getStateIndex()] = curRE.getOrdinal();
                        }
                    } else {
                        Nfa temp;

                        if (curRE instanceof RChoice) {
                            choices.add(curRE);
                        }

                        temp = curRE.accept(new NfaVisitor(ignore), stateData);
                        temp.end().isFinal = true;
                        temp.end().kind = curRE.getOrdinal();
                        stateData.getInitialState().AddMove(temp.start());
                    }

                    if (kinds.length < curRE.getOrdinal()) {
                        TokenKind[] tmp = new TokenKind[curRE.getOrdinal() + 1];
                        System.arraycopy(kinds, 0, tmp, 0, kinds.length);
                        kinds = tmp;
                    }
                    kinds[curRE.getOrdinal()] = kind;

                    if ((respec.nextState != null) && !respec.nextState.equals(
                            data.getStateName(stateData.getStateIndex()))) {
                        data.newLexState[curRE.getOrdinal()] = respec.nextState;
                    }

                    if ((respec.act != null) && !respec.act.getActionTokens().isEmpty()) {
                        data.actions[curRE.getOrdinal()] = respec.act;
                    }

                    switch (kind) {
                        case SPECIAL:
                            data.hasSkipActions |=
                                    (data.actions[curRE.getOrdinal()] != null) || (
                                            data.newLexState[curRE.getOrdinal()] != null);
                            data.hasSpecial = true;
                            data.toSpecial[curRE.getOrdinal() / 64] |=
                                    1L << (curRE.getOrdinal() % 64);
                            data.toSkip[curRE.getOrdinal() / 64] |= 1L << (curRE.getOrdinal() % 64);
                            break;
                        case SKIP:
                            data.hasSkipActions |= (data.actions[curRE.getOrdinal()] != null);
                            data.hasSkip = true;
                            data.toSkip[curRE.getOrdinal() / 64] |= 1L << (curRE.getOrdinal() % 64);
                            break;
                        case MORE:
                            data.hasMoreActions |= (data.actions[curRE.getOrdinal()] != null);
                            data.hasMore = true;
                            data.toMore[curRE.getOrdinal() / 64] |= 1L << (curRE.getOrdinal() % 64);

                            if (data.newLexState[curRE.getOrdinal()] != null) {
                                data.canReachOnMore[data.getStateIndex(
                                        data.newLexState[curRE.getOrdinal()])] = true;
                            } else {
                                data.canReachOnMore[stateData.getStateIndex()] = true;
                            }
                            break;
                        case TOKEN:
                            data.hasTokenActions |= (data.actions[curRE.getOrdinal()] != null);
                            data.toToken[curRE.getOrdinal() / 64] |=
                                    1L << (curRE.getOrdinal() % 64);
                            break;
                    }
                }
            }

            NfaState.ComputeClosures(stateData);

            for (int i = 0; i < stateData.getInitialState().epsilonMoves.size(); i++) {
                stateData.getInitialState().epsilonMoves.elementAt(i).GenerateCode();
            }

            stateData.hasNFA = (stateData.generatedStates() != 0);
            if (stateData.hasNFA) {
                stateData.getInitialState().GenerateCode();
                stateData.getInitialState().GetEpsilonMovesString();
                if (stateData.getInitialState().epsilonMovesString == null) {
                    stateData.getInitialState().epsilonMovesString = "null;";
                }
                stateData.addCompositeStateSet(stateData.getInitialState().epsilonMovesString);
            }

            if ((stateData.getInitialState().kind != Integer.MAX_VALUE) && (
                    stateData.getInitialState().kind != 0)) {
                if (((data.toSkip[stateData.getInitialState().kind / 64] & (1L
                        << stateData.getInitialState().kind)) != 0L)
                        || ((data.toSpecial[stateData.getInitialState().kind / 64]
                        & (1L << stateData.getInitialState().kind)) != 0L)) {
                    data.hasSkipActions = true;
                } else if ((data.toMore[stateData.getInitialState().kind / 64]
                        & (1L << stateData.getInitialState().kind)) != 0L) {
                    data.hasMoreActions = true;
                } else {
                    data.hasTokenActions = true;
                }

                if ((data.initMatch[stateData.getStateIndex()] == 0)
                        || (data.initMatch[stateData.getStateIndex()]
                        > stateData.getInitialState().kind)) {
                    data.initMatch[stateData.getStateIndex()] = stateData.getInitialState().kind;
                    data.hasEmptyMatch = true;
                }
            } else if (data.initMatch[stateData.getStateIndex()] == 0) {
                data.initMatch[stateData.getStateIndex()] = Integer.MAX_VALUE;
            }

            StringLiteralAnalyzer.fillSubString(stateData);

            if (stateData.hasNFA && !stateData.isMixedState()) {
                generateNfaStartStates(stateData, stateData.getInitialState());
            }

            data.totalNumStates += stateData.generatedStates();
            if (data.stateSetSize < stateData.generatedStates()) {
                data.stateSetSize = stateData.generatedStates();
            }
        }
    }

    /**
     * Computes NFA start state sets for string literal matching.
     */
    private static void generateNfaStartStates(NfaStateData data, NfaState initialState) {
        boolean[] seen = new boolean[data.generatedStates()];
        Hashtable<String, String> stateSets = new Hashtable<>();
        String stateSetString = "";
        int i, j, kind, jjmatchedPos = 0;
        int maxKindsReqd = (data.maxStrKind / 64) + 1;
        long[] actives;
        List<NfaState> newStates = new ArrayList<>();
        List<NfaState> oldStates = null, jjtmpStates;

        data.statesForPos = new Hashtable[data.maxLen];
        data.intermediateKinds = new int[data.maxStrKind + 1][];
        data.intermediateMatchedPos = new int[data.maxStrKind + 1][];

        // Precompute image -> smallest matching string kind for this lexical state, so the inner
        // loop below does an O(1) lookup instead of rescanning every string kind (the former
        // getStrKind was O(maxStrKind), making the whole pass O(maxStrKind^2 * maxLen)).
        Map<String, Integer> strKindByImage = new HashMap<>();
        for (int k = 0; k < data.maxStrKind; k++) {
            if (data.global.getState(k) != data.getStateIndex()) {
                continue;
            }
            String img = data.global.getImage(k);
            if (img != null) {
                strKindByImage.putIfAbsent(img, k);
            }
        }

        for (i = 0; i < data.maxStrKind; i++) {
            if (data.global.getState(i) != data.getStateIndex()) {
                continue;
            }

            String image = data.global.getImage(i);
            if ((image == null) || (image.isEmpty())) {
                continue;
            }

            oldStates = new ArrayList<>(initialState.epsilonMoves);
            if (oldStates.isEmpty()) {
                return;
            }

            data.intermediateKinds[i] = new int[image.length()];
            data.intermediateMatchedPos[i] = new int[image.length()];
            jjmatchedPos = 0;
            kind = Integer.MAX_VALUE;

            for (j = 0; j < image.length(); j++) {
                if ((oldStates == null) || oldStates.isEmpty()) {
                    kind = data.intermediateKinds[i][j] = data.intermediateKinds[i][j - 1];
                    jjmatchedPos = data.intermediateMatchedPos[i][j] = data.intermediateMatchedPos[i][j - 1];
                } else {
                    kind = NfaState.MoveFromSet(image.charAt(j), oldStates, newStates);
                    oldStates.clear();

                    if ((j == 0) && (kind != Integer.MAX_VALUE) && (
                            data.global.canMatchAnyChar[data.getStateIndex()] != -1)
                            && (kind > data.global.canMatchAnyChar[data.getStateIndex()])) {
                        kind = data.global.canMatchAnyChar[data.getStateIndex()];
                    }

                    if (strKindByImage.getOrDefault(image.substring(0, j + 1), Integer.MAX_VALUE) < kind) {
                        data.intermediateKinds[i][j] = kind = Integer.MAX_VALUE;
                        jjmatchedPos = 0;
                    } else if (kind != Integer.MAX_VALUE) {
                        data.intermediateKinds[i][j] = kind;
                        jjmatchedPos = data.intermediateMatchedPos[i][j] = j;
                    } else if (j == 0) {
                        kind = data.intermediateKinds[i][j] = Integer.MAX_VALUE;
                    } else {
                        kind = data.intermediateKinds[i][j] = data.intermediateKinds[i][j - 1];
                        jjmatchedPos = data.intermediateMatchedPos[i][j] = data.intermediateMatchedPos[i][j - 1];
                    }

                    stateSetString = epsilonMovesString(data, newStates);
                }

                if ((kind == Integer.MAX_VALUE) && ((newStates == null) || (newStates.isEmpty()))) {
                    continue;
                }

                int p;
                if (stateSets.get(stateSetString) == null) {
                    stateSets.put(stateSetString, stateSetString);
                    for (p = 0; p < newStates.size(); p++) {
                        if (seen[newStates.get(p).stateName]) {
                            newStates.get(p).inNextOf++;
                        } else {
                            seen[newStates.get(p).stateName] = true;
                        }
                    }
                } else {
                    for (p = 0; p < newStates.size(); p++) {
                        seen[newStates.get(p).stateName] = true;
                    }
                }

                jjtmpStates = oldStates;
                oldStates = newStates;
                (newStates = jjtmpStates).clear();

                if (data.statesForPos[j] == null) {
                    data.statesForPos[j] = new Hashtable<>();
                }

                if ((actives = (data.statesForPos[j].get(
                        kind + ", " + jjmatchedPos + ", " + stateSetString))) == null) {
                    actives = new long[maxKindsReqd];
                    data.statesForPos[j].put(kind + ", " + jjmatchedPos + ", " + stateSetString,
                            actives);
                }

                actives[i / 64] |= 1L << (i % 64);
            }
        }
    }

    /**
     * Computes non-ASCII move indices and bit vectors for a single NFA state.
     */
    public static void getNonAsciiMoves(LexerData data, NfaState state) {
        int i = 0, j = 0;
        char hiByte;
        int cnt = 0;
        long[][] loBytes = new long[256][4];

        if (((state.charMoves == null) || (state.charMoves[0] == 0))
                && ((state.rangeMoves == null) || (state.rangeMoves[0] == 0))) {
            return;
        }

        if (state.charMoves != null) {
            for (i = 0; i < state.charMoves.length; i++) {
                if (state.charMoves[i] == 0) {
                    break;
                }

                hiByte = (char) (state.charMoves[i] >> 8);
                loBytes[hiByte][(state.charMoves[i] & 0xff) / 64] |= (1L << ((state.charMoves[i] & 0xff) % 64));
            }
        }

        if (state.rangeMoves != null) {
            for (i = 0; i < state.rangeMoves.length; i += 2) {
                if (state.rangeMoves[i] == 0) {
                    break;
                }

                char c, r;

                r = (char) (state.rangeMoves[i + 1] & 0xff);
                hiByte = (char) (state.rangeMoves[i] >> 8);

                if (hiByte == (char) (state.rangeMoves[i + 1] >> 8)) {
                    for (c = (char) (state.rangeMoves[i] & 0xff); c <= r; c++) {
                        loBytes[hiByte][c / 64] |= (1L << (c % 64));
                    }
                    continue;
                }

                for (c = (char) (state.rangeMoves[i] & 0xff); c <= 0xff; c++) {
                    loBytes[hiByte][c / 64] |= (1L << (c % 64));
                }

                while (++hiByte < (char) (state.rangeMoves[i + 1] >> 8)) {
                    loBytes[hiByte][0] |= 0xffffffffffffffffL;
                    loBytes[hiByte][1] |= 0xffffffffffffffffL;
                    loBytes[hiByte][2] |= 0xffffffffffffffffL;
                    loBytes[hiByte][3] |= 0xffffffffffffffffL;
                }

                for (c = 0; c <= r; c++) {
                    loBytes[hiByte][c / 64] |= (1L << (c % 64));
                }
            }
        }

        long[] common = null;
        boolean[] done = new boolean[256];
        int[] tmpIndices = new int[512];

        for (i = 0; i <= 255; i++) {
            if (done[i]
                    || (done[i] =
                    (loBytes[i][0] == 0) && (loBytes[i][1] == 0) && (loBytes[i][2] == 0) && (
                            loBytes[i][3] == 0))) {
                continue;
            }

            for (j = i + 1; j < 256; j++) {
                if (done[j]) {
                    continue;
                }

                if ((loBytes[i][0] == loBytes[j][0]) && (loBytes[i][1] == loBytes[j][1]) && (
                        loBytes[i][2] == loBytes[j][2]) && (loBytes[i][3] == loBytes[j][3])) {
                    done[j] = true;
                    if (common == null) {
                        done[i] = true;
                        common = new long[4];
                        common[i / 64] |= (1L << (i % 64));
                    }
                    common[j / 64] |= (1L << (j % 64));
                }
            }

            if (common != null) {
                tmpIndices[cnt++] = internBitVector(data,
                        new long[]{common[0], common[1], common[2], common[3]});
                tmpIndices[cnt++] = internBitVector(data,
                        new long[]{loBytes[i][0], loBytes[i][1], loBytes[i][2], loBytes[i][3]});
                common = null;
            }
        }

        state.nonAsciiMoveIndices = new int[cnt];
        System.arraycopy(tmpIndices, 0, state.nonAsciiMoveIndices, 0, cnt);

        for (i = 0; i < 256; i++) {
            if (done[i]) {
                loBytes[i] = null;
            } else {
                state.loByteVec.add(i);
                state.loByteVec.add(internBitVector(data,
                        new long[]{loBytes[i][0], loBytes[i][1], loBytes[i][2], loBytes[i][3]}));
            }
        }
        updateDuplicateNonAsciiMoves(data, state);
    }

    /**
     * Formats a four-word lo/hi byte vector as its {@code {0x..L, ..}} source string and interns it
     * in the shared bit-vector tables, returning its index. Previously this format-and-intern block
     * was written out three times inside {@link #getNonAsciiMoves}.
     */
    private static int internBitVector(LexerData data, long[] vec) {
        String tmp = "{\n   0x" + Long.toHexString(vec[0]) + "L, " + "0x" + Long.toHexString(vec[1])
                + "L, " + "0x" + Long.toHexString(vec[2]) + "L, " + "0x" + Long.toHexString(vec[3])
                + "L\n};";
        Integer ind = data.lohiByteTab.get(tmp);
        if (ind == null) {
            data.allBitVectors.add(tmp);
            if (!NfaState.AllBitsSet(tmp)) {
                data.lohiByte.put(data.lohiByteCnt, vec);
            }
            data.lohiByteTab.put(tmp, ind = data.lohiByteCnt++);
        }
        return ind;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static String epsilonMovesString(NfaStateData data, List<NfaState> states) {
        if ((states == null) || (states.isEmpty())) {
            return "null;";
        }

        int[] set = new int[states.size()];
        var sb = new StringBuilder("{ ");
        for (int i = 0; i < states.size(); ) {
            int k;
            sb.append(k = states.get(i).stateName).append(", ");
            set[i] = k;

            if ((i++ > 0) && ((i % 16) == 0)) {
                sb.append("\n");
            }
        }

        sb.append("};");
        String epsilonMovesString = sb.toString();
        data.setNextStates(epsilonMovesString, set);
        return epsilonMovesString;
    }


    private static void updateDuplicateNonAsciiMoves(LexerData data, NfaState state) {
        for (int i = 0; i < data.nonAsciiTableForMethod.size(); i++) {
            NfaState tmp = data.nonAsciiTableForMethod.get(i);
            if (NfaState.EqualLoByteVectors(state.loByteVec, tmp.loByteVec)
                    && NfaState.EqualNonAsciiMoveIndices(state.nonAsciiMoveIndices,
                    tmp.nonAsciiMoveIndices)) {
                state.nonAsciiMethod = i;
                return;
            }
        }

        state.nonAsciiMethod = data.nonAsciiTableForMethod.size();
        data.nonAsciiTableForMethod.add(state);
    }
}
