// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/NfaState.java, org/javacc/parser/RStringLiteral.java

package org.hivevm.waggle.lexer;

import org.hivevm.waggle.model.RChoice;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.RStringLiteral;
import org.hivevm.waggle.model.TokenKind;
import org.hivevm.waggle.model.TokenProduction;
import org.hivevm.waggle.model.RegExprSpec;

import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    static void buildLexer(LexerData data, Map<String, List<TokenProduction>> allTpsForState,
                           List<RChoice> choices) {
        for (String key : allTpsForState.keySet()) {
            NfaStateData stateData = data.newStateData(key);
            int idx = stateData.getStateIndex();
            NfaState init = stateData.getInitialState();

            data.singlesToSkip[idx] = new NfaState(stateData);

            if (key.equals("DEFAULT")) {
                data.defaultLexState = idx;
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
                    RExpression curRE = respec.rexp;
                    int ord = curRE.getOrdinal();

                    data.rexprs[data.curKind = ord] = curRE;
                    data.lexStates[ord] = idx;
                    data.ignoreCase[ord] = ignore;

                    if (curRE.isPrivateExp()) {
                        continue;
                    }

                    if (!data.getNoDfa() && (curRE instanceof RStringLiteral literal)
                            && !literal.getImage().isEmpty()) {
                        StringLiteralAnalyzer.generateDfa(stateData, literal);
                        if ((i != 0) && !stateData.isMixedState() && (ignoring != ignore)) {
                            stateData.hasMixed = true;
                        }
                    } else if (curRE.CanMatchAnyChar()) {
                        if ((data.canMatchAnyChar[idx] == -1) || (data.canMatchAnyChar[idx] > ord)) {
                            data.canMatchAnyChar[idx] = ord;
                        }
                    } else {
                        if (curRE instanceof RChoice choice) {
                            choices.add(choice);
                        }

                        Nfa temp = new NfaVisitor(data.ignoreCase() || ignore)
                                .nfa(curRE, stateData);
                        temp.end().isFinal = true;
                        temp.end().kind = ord;
                        init.AddMove(temp.start());
                    }

                    if ((respec.nextState != null)
                            && !respec.nextState.equals(data.getStateName(idx))) {
                        data.newLexState[ord] = respec.nextState;
                    }

                    if ((respec.act != null) && !respec.act.getActionTokens().isEmpty()) {
                        data.actions[ord] = respec.act;
                    }

                    switch (kind) {
                        case SPECIAL:
                            data.hasSkipActions |=
                                    (data.actions[ord] != null) || (data.newLexState[ord] != null);
                            data.hasSpecial = true;
                            Bits.set(data.toSpecial, ord);
                            Bits.set(data.toSkip, ord);
                            break;
                        case SKIP:
                            data.hasSkipActions |= (data.actions[ord] != null);
                            data.hasSkip = true;
                            Bits.set(data.toSkip, ord);
                            break;
                        case MORE:
                            data.hasMoreActions |= (data.actions[ord] != null);
                            data.hasMore = true;
                            Bits.set(data.toMore, ord);

                            if (data.newLexState[ord] != null) {
                                data.canReachOnMore[data.getStateIndex(data.newLexState[ord])] = true;
                            } else {
                                data.canReachOnMore[idx] = true;
                            }
                            break;
                        case TOKEN:
                            data.hasTokenActions |= (data.actions[ord] != null);
                            Bits.set(data.toToken, ord);
                            break;
                    }
                }
            }

            NfaState.ComputeClosures(stateData);

            for (int i = 0; i < init.epsilonMoves.size(); i++) {
                init.epsilonMoves.get(i).GenerateCode();
            }

            stateData.hasNFA = (stateData.generatedStates() != 0);
            if (stateData.hasNFA) {
                init.GenerateCode();
                init.GetEpsilonMovesString();
                if (init.epsilonMovesString == null) {
                    init.epsilonMovesString = "null;";
                }
                stateData.addCompositeStateSet(init.epsilonMovesString);
            }

            if ((init.kind != Integer.MAX_VALUE) && (init.kind != 0)) {
                if (data.isSkip(init.kind) || data.isSpecial(init.kind)) {
                    data.hasSkipActions = true;
                } else if (data.isMore(init.kind)) {
                    data.hasMoreActions = true;
                } else {
                    data.hasTokenActions = true;
                }

                if ((data.initMatch[idx] == 0) || (data.initMatch[idx] > init.kind)) {
                    data.initMatch[idx] = init.kind;
                    data.hasEmptyMatch = true;
                }
            } else if (data.initMatch[idx] == 0) {
                data.initMatch[idx] = Integer.MAX_VALUE;
            }

            StringLiteralAnalyzer.fillSubString(stateData);

            if (stateData.hasNFA && !stateData.isMixedState()) {
                generateNfaStartStates(stateData, init);
            }

            if (data.stateSetSize < stateData.generatedStates()) {
                data.stateSetSize = stateData.generatedStates();
            }
        }
    }

    /**
     * Computes NFA start state sets for string literal matching.
     */
    private static void generateNfaStartStates(NfaStateData data, NfaState initialState) {
        int idx = data.getStateIndex();
        int anyChar = data.global.canMatchAnyChar[idx];
        int maxKindsReqd = (data.maxStrKind / 64) + 1;
        Set<String> stateSets = new LinkedHashSet<>();
        String stateSetString = "";
        List<NfaState> newStates = new ArrayList<>();

        data.statesForPos = NfaStateData.newStatesForPos(data.maxLen);
        data.intermediateKinds = new int[data.maxStrKind + 1][];
        data.intermediateMatchedPos = new int[data.maxStrKind + 1][];
        if (initialState.epsilonMoves.isEmpty()) {
            return;
        }

        // Precompute image -> smallest matching string kind for this lexical state, so the inner
        // loop below does an O(1) lookup instead of rescanning every string kind (the former
        // getStrKind was O(maxStrKind), making the whole pass O(maxStrKind^2 * maxLen)).
        Map<String, Integer> strKindByImage = new HashMap<>();
        for (int k = 0; k < data.maxStrKind; k++) {
            if (data.global.getState(k) != idx) {
                continue;
            }
            String img = data.global.getImage(k);
            if (img != null) {
                strKindByImage.putIfAbsent(img, k);
            }
        }

        for (int i = 0; i < data.maxStrKind; i++) {
            String image = data.global.getImage(i);
            if ((data.global.getState(i) != idx) || (image == null) || image.isEmpty()) {
                continue;
            }

            List<NfaState> oldStates = new ArrayList<>(initialState.epsilonMoves);
            int[] kinds = data.intermediateKinds[i] = new int[image.length()];
            int[] matchedPos = data.intermediateMatchedPos[i] = new int[image.length()];
            int jjmatchedPos = 0;

            for (int j = 0; j < image.length(); j++) {
                int kind;
                if (oldStates.isEmpty()) {
                    kind = kinds[j] = kinds[j - 1];
                    jjmatchedPos = matchedPos[j] = matchedPos[j - 1];
                } else {
                    kind = NfaState.MoveFromSet(image.charAt(j), oldStates, newStates);
                    oldStates.clear();

                    if ((j == 0) && (kind != Integer.MAX_VALUE) && (anyChar != -1) && (kind > anyChar)) {
                        kind = anyChar;
                    }

                    if (strKindByImage.getOrDefault(image.substring(0, j + 1), Integer.MAX_VALUE) < kind) {
                        kinds[j] = kind = Integer.MAX_VALUE;
                        jjmatchedPos = 0;
                    } else if (kind != Integer.MAX_VALUE) {
                        kinds[j] = kind;
                        jjmatchedPos = matchedPos[j] = j;
                    } else if (j == 0) {
                        kind = kinds[j] = Integer.MAX_VALUE;
                    } else {
                        kind = kinds[j] = kinds[j - 1];
                        jjmatchedPos = matchedPos[j] = matchedPos[j - 1];
                    }

                    stateSetString = epsilonMovesString(data, newStates);
                }

                if ((kind == Integer.MAX_VALUE) && newStates.isEmpty()) {
                    continue;
                }

                // A stop set is one more set its states occur in, as a next set is. A state that
                // occurs in another set as well must not name the composite state of this one:
                // the move code of that name would then run for the other set too.
                if (stateSets.add(stateSetString)) {
                    for (NfaState state : newStates) {
                        state.inNextOf++;
                    }
                }

                List<NfaState> jjtmpStates = oldStates;
                oldStates = newStates;
                (newStates = jjtmpStates).clear();

                if (data.statesForPos[j] == null) {
                    data.statesForPos[j] = new Hashtable<>();
                }

                String key = new NfaStateData.StopKey(kind, jjmatchedPos, stateSetString).key();
                Bits.set(data.statesForPos[j].computeIfAbsent(key, k -> new long[maxKindsReqd]), i);
            }
        }
    }

    /**
     * Computes non-ASCII move indices and bit vectors for a single NFA state.
     */
    static void getNonAsciiMoves(LexerData data, NfaState state) {
        if (((state.charMoves == null) || (state.charMoves[0] == 0))
                && ((state.rangeMoves == null) || (state.rangeMoves[0] == 0))) {
            return;
        }

        // The low bytes the state moves on, per high byte.
        BitSet[] loBytes = new BitSet[256];
        Arrays.setAll(loBytes, i -> new BitSet(256));

        if (state.charMoves != null) {
            for (char c : state.charMoves) {
                if (c == 0) {
                    break;
                }
                loBytes[c >> 8].set(c & 0xff);
            }
        }

        if (state.rangeMoves != null) {
            for (int i = 0; (i < state.rangeMoves.length) && (state.rangeMoves[i] != 0); i += 2) {
                char left = state.rangeMoves[i];
                char right = state.rangeMoves[i + 1];
                for (int hiByte = left >> 8; hiByte <= (right >> 8); hiByte++) {
                    int from = (hiByte == (left >> 8)) ? (left & 0xff) : 0;
                    int to = (hiByte == (right >> 8)) ? (right & 0xff) : 0xff;
                    loBytes[hiByte].set(from, to + 1);
                }
            }
        }

        boolean[] done = new boolean[256];
        int[] tmpIndices = new int[512];
        int cnt = 0;

        for (int i = 0; i < 256; i++) {
            if (done[i] || (done[i] = loBytes[i].isEmpty())) {
                continue;
            }

            BitSet common = null;
            for (int j = i + 1; j < 256; j++) {
                if (!done[j] && loBytes[i].equals(loBytes[j])) {
                    done[j] = true;
                    if (common == null) {
                        done[i] = true;
                        common = new BitSet(256);
                        common.set(i);
                    }
                    common.set(j);
                }
            }

            if (common != null) {
                tmpIndices[cnt++] = internBitVector(data, common);
                tmpIndices[cnt++] = internBitVector(data, loBytes[i]);
            }
        }

        state.nonAsciiMoveIndices = Arrays.copyOf(tmpIndices, cnt);

        for (int i = 0; i < 256; i++) {
            if (!done[i]) {
                state.loByteVec.add(i);
                state.loByteVec.add(internBitVector(data, loBytes[i]));
            }
        }
        updateDuplicateNonAsciiMoves(data, state);
    }

    /** Interns a 256-bit lo/hi byte vector in the shared bit-vector tables and returns its index. */
    private static int internBitVector(LexerData data, BitSet bits) {
        var vector = new BitVector(Arrays.copyOf(bits.toLongArray(), 4));
        return data.bitVectorIndex.computeIfAbsent(vector, key -> {
            data.bitVectors.add(key);
            return data.bitVectors.size() - 1;
        });
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static String epsilonMovesString(NfaStateData data, List<NfaState> states) {
        if ((states == null) || (states.isEmpty())) {
            return "null;";
        }

        int[] set = states.stream().mapToInt(state -> state.stateName).toArray();
        String epsilonMovesString = NfaState.stateSetKey(set);
        data.setNextStates(epsilonMovesString, set);
        return epsilonMovesString;
    }

    private static void updateDuplicateNonAsciiMoves(LexerData data, NfaState state) {
        for (int i = 0; i < data.nonAsciiTableForMethod.size(); i++) {
            NfaState tmp = data.nonAsciiTableForMethod.get(i);
            if (state.loByteVec.equals(tmp.loByteVec)
                    && Arrays.equals(state.nonAsciiMoveIndices, tmp.nonAsciiMoveIndices)) {
                state.nonAsciiMethod = i;
                return;
            }
        }

        state.nonAsciiMethod = data.nonAsciiTableForMethod.size();
        data.nonAsciiTableForMethod.add(state);
    }
}
