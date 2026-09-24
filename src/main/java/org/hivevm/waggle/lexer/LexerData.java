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
import java.util.TreeMap;

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
    final Map<Integer, long[]> lohiByte;
    final Map<BitVector, Integer> lohiByteTab;

    final List<NfaState> nonAsciiTableForMethod;
    final List<Boolean> allBitsSet;
    int[][] kinds;
    int[][][] statesForState;

    boolean jjCheckNAddStatesUnaryNeeded;
    boolean jjCheckNAddStatesDualNeeded;

    // public for NFA
    int lastIndex;
    final Map<String, int[]> tableToDump;
    final List<int[]> orderedStateSet;

    private final Map<String, NfaStateData> stateData = new HashMap<>();

    // RString
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

    /**
     * Constructs an instance of {@link LexerData}.
     */
    LexerData(ParserRequest request, int maxOrdinal, int maxLexStates) {
        this.request = request;
        this.maxOrdinal = maxOrdinal;
        this.maxLexStates = maxLexStates;

        this.curKind = 0;
        this.nonAsciiTableForMethod = new ArrayList<>();
        this.lohiByte = new TreeMap<>();
        this.lohiByteTab = new HashMap<>();
        this.allBitsSet = new ArrayList<>();

        this.kinds = null;
        this.statesForState = null;

        this.tableToDump = new LinkedHashMap<>();
        this.orderedStateSet = new ArrayList<>();
        this.lastIndex = 0;
        this.jjCheckNAddStatesUnaryNeeded = false;
        this.jjCheckNAddStatesDualNeeded = false;

        // additionals
        this.defaultLexState = 0;
        this.hasLoop = false;
        this.hasMore = false;
        this.hasMoreActions = false;
        this.hasSkip = false;
        this.hasSkipActions = false;
        this.hasSpecial = false;
        this.parserOptions = ParserOptions.from(request.options());
        this.stateSetSize = 0;

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
        this.hasEmptyMatch = false;
        this.lexStates = new int[this.maxOrdinal];
        this.ignoreCase = new boolean[this.maxOrdinal];
        this.rexprs = new RExpression[this.maxOrdinal];
        this.allImages = new String[this.maxOrdinal];
        this.canReachOnMore = new boolean[this.maxLexStates];

        for (int i = 0; i < this.maxLexStates; i++) {
            this.canMatchAnyChar[i] = -1;
        }
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

    public final Iterable<Integer> getLohiByte() {
        return this.lohiByte.keySet();
    }

    public final List<int[]> getOrderedStateSet() {
        return this.orderedStateSet;
    }

    public final Iterable<TokenProduction> getTokenProductions() {
        return this.request.getTokenProductions();
    }

    public final Iterable<NfaState> getNonAsciiTableForMethod() {
        return this.nonAsciiTableForMethod;
    }

    public final int maxOrdinal() {
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

    public final boolean ignoreCase() {
        return this.request.ignoreCase();
    }

    public final boolean hasLoop() {
        return this.hasLoop;
    }

    public final boolean hasEmptyMatch() {
        return this.hasEmptyMatch;
    }

    public final boolean keepLineCol() {
        return this.parserOptions.keepLineColumn();
    }

    /** Whether the token manager traces its moves. */
    public final boolean getDebugTokenManager() {
        return this.parserOptions.debugTokenManager();
    }

    /** Whether the string-literal DFA is skipped and everything goes through the NFA. */
    public final boolean getNoDfa() {
        return this.parserOptions.noDfa();
    }

    public final boolean hasSkip() {
        return this.hasSkip;
    }

    public final boolean hasMore() {
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

    public final boolean canLoop(int index) {
        return this.canLoop[index];
    }

    public final boolean canReachOnMore(int index) {
        return this.canReachOnMore[index];
    }

    public final int initMatch(int index) {
        return this.initMatch[index];
    }

    public final int canMatchAnyChar(int index) {
        return this.canMatchAnyChar[index];
    }

    public final boolean hasEof() {
        return this.request.getNextStateForEof() != null || this.request.getActionForEof() != null;
    }

    public final int getStateCount() {
        return this.lexStateNames.length;
    }

    public final int getState(int index) {
        return this.lexStates[index];
    }

    public final String getStateName(int index) {
        return this.lexStateNames[index];
    }

    public final List<String> getStateNames() {
        return Arrays.asList(this.lexStateNames);
    }

    public final int getCurrentKind() {
        return this.curKind;
    }

    public final int getImageCount() {
        return this.allImages.length;
    }

    public final String getImage(int index) {
        return this.allImages[index];
    }

    public final int getStateIndex(String name) {
        for (int i = 0; i < this.lexStateNames.length; i++) {
            if ((this.lexStateNames[i] != null) && this.lexStateNames[i].equals(name)) {
                return i;
            }
        }
        throw new IllegalStateException("Unknown lexical state: " + name);
    }

    /**
     * Reset the {@link LexerData} for another cycle.
     */
    final NfaStateData newStateData(String name) {
        NfaStateData data = new NfaStateData(this, name);
        this.stateData.put(name, data);
        return data;
    }

    /**
     * Reset the {@link LexerData} for another cycle.
     */
    public final NfaStateData getStateData(String name) {
        return this.stateData.get(name);
    }

    /** Whether the bit vector at {@code index} has every bit set. */
    public final boolean hasAllBitsSet(int index) {
        return this.allBitsSet.get(index);
    }

    public final int[][] getKinds() {
        return this.kinds;
    }

    public final int[][][] getStatesForState() {
        return this.statesForState;
    }

    public final int stateSetSize() {
        return this.stateSetSize;
    }

    public final int defaultLexState() {
        return this.defaultLexState;
    }

    public final String newLexState(int index) {
        return this.newLexState[index];
    }

    public final boolean ignoreCase(int index) {
        return this.ignoreCase[index];
    }

    public final Action actions(int index) {
        return this.actions[index];
    }

    public final NfaState singlesToSkip(int index) {
        return this.singlesToSkip[index];
    }

    public final long toSkip(int index) {
        return this.toSkip[index];
    }

    public final long toSpecial(int index) {
        return this.toSpecial[index];
    }

    public final long toMore(int index) {
        return this.toMore[index];
    }

    public final long toToken(int index) {
        return this.toToken[index];
    }

    public final RExpression getRegExp(int index) {
        return this.rexprs[index];
    }

    public final long getLohiByte(int offest, int index) {
        return this.lohiByte.get(offest)[index];
    }
}
