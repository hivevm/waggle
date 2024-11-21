// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.ParserRequest;
import org.hivevm.cc.lexer.NfaState;
import org.hivevm.cc.parser.Action;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.parser.RegularExpression;
import org.hivevm.cc.parser.TokenProduction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/**
 * The {@link LexerData} provides the request data for the lexer generator.
 */
public class LexerData {

  private final ParserRequest request;
  final int                   maxOrdinal;
  final int                   maxLexStates;


  final int[]    lexStates;
  final String[] lexStateNames;

  int            curKind;


  int                              lohiByteCnt;
  final Map<Integer, long[]>       lohiByte;
  final Hashtable<String, Integer> lohiByteTab;

  List<NfaState>                   nonAsciiTableForMethod;
  List<String>                     allBitVectors;
  int[][]                          kinds;
  int[][][]                        statesForState;


  public boolean boilerPlateDumped;


  boolean jjCheckNAddStatesUnaryNeeded;
  boolean jjCheckNAddStatesDualNeeded;

  // public for NFA
  public int                            lastIndex;
  public final Hashtable<String, int[]> tableToDump;
  public final List<int[]>              orderedStateSet;


  private final Map<String, NfaStateData> stateData = new HashMap<>();

  // RString
  final String[]            allImages;

  final String[]            newLexState;
  final boolean[]           ignoreCase;
  final Action[]            actions;
  int                       stateSetSize;
  int                       totalNumStates;
  final NfaState[]          singlesToSkip;

  final long[]              toSkip;
  final long[]              toSpecial;
  final long[]              toMore;
  final long[]              toToken;
  int                       defaultLexState;

  final RegularExpression[] rexprs;
  final int[]               initMatch;
  final int[]               canMatchAnyChar;
  boolean                   hasEmptyMatch;
  final boolean[]           canLoop;
  boolean                   hasLoop        = false;
  final boolean[]           canReachOnMore;
  boolean                   hasSkipActions = false;
  boolean                   hasMoreActions = false;
  boolean                   hasTokenActions;
  boolean                   hasSpecial     = false;
  boolean                   hasSkip        = false;
  boolean                   hasMore        = false;
  boolean                   keepLineCol;

  /**
   * Constructs an instance of {@link LexerData}.
   *
   * @param request
   * @param maxOrdinal
   * @param maxLexStates
   */
  LexerData(ParserRequest request, int maxOrdinal, int maxLexStates) {
    this.request = request;
    this.maxOrdinal = maxOrdinal;
    this.maxLexStates = maxLexStates;

    this.curKind = 0;
    this.nonAsciiTableForMethod = new ArrayList<>();
    this.lohiByteCnt = 0;
    this.lohiByte = new HashMap<>();
    this.lohiByteTab = new Hashtable<>();
    this.allBitVectors = new ArrayList<>();

    this.kinds = null;
    this.statesForState = null;

    this.tableToDump = new Hashtable<>();
    this.orderedStateSet = new ArrayList<>();
    this.lastIndex = 0;
    this.jjCheckNAddStatesUnaryNeeded = false;
    this.jjCheckNAddStatesDualNeeded = false;
    this.boilerPlateDumped = false;

    // additionals
    this.defaultLexState = 0;
    this.hasLoop = false;
    this.hasMore = false;
    this.hasMoreActions = false;
    this.hasSkip = false;
    this.hasSkipActions = false;
    this.hasSpecial = false;
    this.keepLineCol = request.options().getKeepLineColumn();
    this.stateSetSize = 0;

    this.toSkip = new long[(this.maxOrdinal / 64) + 1];
    this.toSpecial = new long[(this.maxOrdinal / 64) + 1];
    this.toMore = new long[(this.maxOrdinal / 64) + 1];
    this.toToken = new long[(this.maxOrdinal / 64) + 1];
    this.toToken[0] = 1L;

    this.actions = new Action[this.maxOrdinal];
    this.actions[0] = request.getActionForEof();
    this.hasTokenActions = getActionForEof() != null;
    this.canMatchAnyChar = new int[this.maxLexStates];
    this.canLoop = new boolean[this.maxLexStates];
    this.lexStateNames = new String[this.maxLexStates];
    this.singlesToSkip = new NfaState[this.maxLexStates];

    this.initMatch = new int[this.maxLexStates];
    this.newLexState = new String[this.maxOrdinal];
    this.newLexState[0] = getNextStateForEof();
    this.hasEmptyMatch = false;
    this.lexStates = new int[this.maxOrdinal];
    this.ignoreCase = new boolean[this.maxOrdinal];
    this.rexprs = new RegularExpression[this.maxOrdinal];
    this.allImages = new String[this.maxOrdinal];
    this.canReachOnMore = new boolean[this.maxLexStates];

    for (int i = 0; i < this.maxLexStates; i++) {
      this.canMatchAnyChar[i] = -1;
    }
  }

  public final Options options() {
    return this.request.options();
  }

  public final String getParserName() {
    return this.request.getParserName();
  }

  public final Iterable<RegularExpression> getOrderedsTokens() {
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
    return this.keepLineCol;
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

  public final String getNextStateForEof() {
    return this.request.getNextStateForEof();
  }

  public final Action getActionForEof() {
    return this.request.getActionForEof();
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
    return this.allImages == null ? -1 : this.allImages.length;
  }

  public final String getImage(int index) {
    return this.allImages[index];
  }

  public final void setImage(int index, String image) {
    this.allImages[index] = image;
  }

  public final int getStateIndex(String name) {
    for (int i = 0; i < this.lexStateNames.length; i++) {
      if ((this.lexStateNames[i] != null) && this.lexStateNames[i].equals(name)) {
        return i;
      }
    }
    throw new Error(); // Should never come here
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

  public final String getAllBitVectors(int index) {
    return this.allBitVectors.get(index);
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

  public final int totalNumStates() {
    return this.totalNumStates;
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
}
