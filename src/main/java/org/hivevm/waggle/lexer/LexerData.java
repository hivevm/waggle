// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.lexer;

import org.hivevm.waggle.api.ParserRequest;
import org.hivevm.waggle.diag.Diagnostics;
import org.hivevm.waggle.model.Action;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.TokenProduction;
import org.hivevm.waggle.api.Options;
import org.hivevm.waggle.api.ParserOptions;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@link LexerData} provides the request data for the lexer generator.
 */
public class LexerData {

    private final ParserRequest request;

    final int maxOrdinal;
    final int maxLexStates;
    final int[] lexStates;
    final String[] lexStateNames;

    int curKind;

    // Every distinct bit vector gets an index; the ones not all set are also emitted as tables,
    // in index order.
    final List<BitVector> bitVectors;
    final Map<BitVector, Integer> bitVectorIndex;

    final List<NfaState> nonAsciiTableForMethod;
    int[][] kinds;
    int[][][] statesForState;

    boolean jjCheckNAddStatesUnaryNeeded;
    boolean jjCheckNAddStatesDualNeeded;

    int lastIndex;
    final Map<String, int[]> tableToDump;
    final List<int[]> orderedStateSet;

    private final Map<String, NfaStateData> stateData = new HashMap<>();

    final String[] allImages;

    final String[] newLexState;
    final boolean[] ignoreCase;
    final Action[] actions;
    int stateSetSize;
    final NfaState[] singlesToSkip;

    final long[] toSkip;
    final long[] toSpecial;
    final long[] toMore;
    final long[] toToken;
    int defaultLexState;

    final RExpression[] rexprs;
    final int[] initMatch;
    final int[] canMatchAnyChar;
    boolean hasEmptyMatch;
    final boolean[] canLoop;
    boolean hasLoop;
    final boolean[] canReachOnMore;
    boolean hasSkipActions;
    boolean hasMoreActions;
    boolean hasTokenActions;
    boolean hasSpecial;
    boolean hasSkip;
    boolean hasMore;
    private final ParserOptions parserOptions;
    LexerPlan plan;

    /**
     * Constructs an instance of {@link LexerData}.
     */
    LexerData(ParserRequest request, int maxOrdinal, int maxLexStates) {
        this.request = request;
        this.maxOrdinal = maxOrdinal;
        this.maxLexStates = maxLexStates;

        this.nonAsciiTableForMethod = new ArrayList<>();
        this.bitVectors = new ArrayList<>();
        this.bitVectorIndex = new HashMap<>();

        this.tableToDump = new LinkedHashMap<>();
        this.orderedStateSet = new ArrayList<>();

        this.parserOptions = ParserOptions.from(request.options());

        this.toSkip = new long[(this.maxOrdinal / 64) + 1];
        this.toSpecial = new long[(this.maxOrdinal / 64) + 1];
        this.toMore = new long[(this.maxOrdinal / 64) + 1];
        this.toToken = new long[(this.maxOrdinal / 64) + 1];
        this.toToken[0] = 1L;

        this.actions = new Action[this.maxOrdinal];
        this.actions[0] = request.getActionForEof();
        this.hasTokenActions = request.getActionForEof() != null;
        this.canMatchAnyChar = new int[this.maxLexStates];
        this.canLoop = new boolean[this.maxLexStates];
        this.lexStateNames = new String[this.maxLexStates];
        this.singlesToSkip = new NfaState[this.maxLexStates];

        this.initMatch = new int[this.maxLexStates];
        this.newLexState = new String[this.maxOrdinal];
        this.newLexState[0] = request.getNextStateForEof();
        this.lexStates = new int[this.maxOrdinal];
        this.ignoreCase = new boolean[this.maxOrdinal];
        this.rexprs = new RExpression[this.maxOrdinal];
        this.allImages = new String[this.maxOrdinal];
        this.canReachOnMore = new boolean[this.maxLexStates];

        Arrays.fill(this.canMatchAnyChar, -1);
    }

    /** The token manager with every decision taken (ADR-0029). */
    public final LexerPlan plan() {
        return this.plan;
    }

    public final Options options() {
        return this.request.options();
    }

    public final Diagnostics diagnostics() {
        return this.request.diagnostics();
    }

    public final String getParserName() {
        return this.request.getParserName();
    }

    public final Iterable<RExpression> getOrderedsTokens() {
        return this.request.getOrderedsTokens();
    }

    public final Iterable<TokenProduction> getTokenProductions() {
        return this.request.getTokenProductions();
    }

    final int maxOrdinal() {
        return this.maxOrdinal;
    }

    public final int maxLexStates() {
        return this.maxLexStates;
    }

    public final boolean jjCheckNAddStatesUnaryNeeded() {
        return this.jjCheckNAddStatesUnaryNeeded;
    }

    public final boolean jjCheckNAddStatesDualNeeded() {
        return this.jjCheckNAddStatesDualNeeded;
    }

    final boolean ignoreCase() {
        return this.request.ignoreCase();
    }

    public final boolean hasLoop() {
        return this.hasLoop;
    }

    public final boolean hasEmptyMatch() {
        return this.hasEmptyMatch;
    }

    /** Whether the token manager traces its moves. */
    final boolean getDebugTokenManager() {
        return this.parserOptions.debugTokenManager();
    }

    /** Whether the string-literal DFA is skipped and everything goes through the NFA. */
    final boolean getNoDfa() {
        return this.parserOptions.noDfa();
    }

    final boolean hasSkip() {
        return this.hasSkip;
    }

    final boolean hasMore() {
        return this.hasMore;
    }

    public final boolean hasSpecial() {
        return this.hasSpecial;
    }

    public final boolean hasMoreActions() {
        return this.hasMoreActions;
    }

    public final boolean hasSkipActions() {
        return this.hasSkipActions;
    }

    public final boolean hasTokenActions() {
        return this.hasTokenActions;
    }

    final boolean canLoop(int index) {
        return this.canLoop[index];
    }

    final boolean canReachOnMore(int index) {
        return this.canReachOnMore[index];
    }

    final int initMatch(int index) {
        return this.initMatch[index];
    }

    final int canMatchAnyChar(int index) {
        return this.canMatchAnyChar[index];
    }

    final boolean hasEof() {
        return this.request.getNextStateForEof() != null || this.request.getActionForEof() != null;
    }

    final int getState(int index) {
        return this.lexStates[index];
    }

    public final String getStateName(int index) {
        return this.lexStateNames[index];
    }

    public final List<String> getStateNames() {
        return Arrays.asList(this.lexStateNames);
    }

    final int getCurrentKind() {
        return this.curKind;
    }

    public final int getImageCount() {
        return this.allImages.length;
    }

    public final String getImage(int index) {
        return this.allImages[index];
    }

    final int getStateIndex(String name) {
        for (int i = 0; i < this.lexStateNames.length; i++) {
            if ((this.lexStateNames[i] != null) && this.lexStateNames[i].equals(name)) {
                return i;
            }
        }
        throw new IllegalStateException("Unknown lexical state: " + name);
    }

    /** Creates the automaton data of the lexical state {@code name}. */
    final NfaStateData newStateData(String name) {
        NfaStateData data = new NfaStateData(this, name);
        this.stateData.put(name, data);
        return data;
    }

    /** The automaton data of the lexical state {@code name}. */
    final NfaStateData getStateData(String name) {
        return this.stateData.get(name);
    }

    public final int stateSetSize() {
        return this.stateSetSize;
    }

    public final int defaultLexState() {
        return this.defaultLexState;
    }

    final String newLexState(int index) {
        return this.newLexState[index];
    }

    final boolean ignoreCase(int index) {
        return this.ignoreCase[index];
    }

    final Action actions(int index) {
        return this.actions[index];
    }

    final NfaState singlesToSkip(int index) {
        return this.singlesToSkip[index];
    }

    /** Whether {@code kind} is skipped: a SKIP or a SPECIAL_TOKEN. */
    final boolean isSkip(int kind) {
        return Bits.test(this.toSkip, kind);
    }

    final boolean isSpecial(int kind) {
        return Bits.test(this.toSpecial, kind);
    }

    final boolean isMore(int kind) {
        return Bits.test(this.toMore, kind);
    }

    final boolean isToken(int kind) {
        return Bits.test(this.toToken, kind);
    }

    final long toSkip(int index) {
        return this.toSkip[index];
    }

    final long toSpecial(int index) {
        return this.toSpecial[index];
    }

    final long toMore(int index) {
        return this.toMore[index];
    }

    final long toToken(int index) {
        return this.toToken[index];
    }

    final RExpression getRegExp(int index) {
        return this.rexprs[index];
    }
}
