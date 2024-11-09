// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.JavaCCRequest;
import org.hivevm.cc.lexer.NfaState;
import org.hivevm.cc.parser.Action;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.parser.RegularExpression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@link LexerData} provides the request data for the lexer generator.
 */
public class LexerData {

  private final JavaCCRequest request;
  public final int            maxOrdinal;
  public final int            maxLexStates;
  public final Set<String>    stateNames;


  final int[]    lexStates;
  final String[] lexStateNames;

  int            curKind;


  int                               lohiByteCnt;
  public final Map<Integer, long[]> lohiByte;
  final Hashtable<String, Integer>  lohiByteTab;

  public List<NfaState>             nonAsciiTableForMethod;
  public List<String>               allBitVectors;
  public int[][]                    kinds;
  public int[][][]                  statesForState;


  public boolean                        jjCheckNAddStatesUnaryNeeded;
  public boolean                        jjCheckNAddStatesDualNeeded;
  public int                            lastIndex;
  public final Hashtable<String, int[]> tableToDump;
  public final List<int[]>              orderedStateSet;


  public boolean                            boilerPlateDumped;
  private final Map<String, LexerStateData> stateData = new HashMap<>();


  // RString
  final String[] allImages;

  // Additional attributes
  public final int[]        maxLongsReqd;

  public final String[]     newLexState;
  public final boolean[]    ignoreCase;
  public final Action[]     actions;
  public int                stateSetSize;
  public int                totalNumStates;
  public final NfaState[]   singlesToSkip;

  public final long[]       toSkip;
  public final long[]       toSpecial;
  public final long[]       toMore;
  public final long[]       toToken;
  public int                defaultLexState;
  final RegularExpression[] rexprs;
  public final int[]        initMatch;
  public final int[]        canMatchAnyChar;
  public boolean            hasEmptyMatch;
  public final boolean[]    canLoop;
  public boolean            hasLoop        = false;
  public final boolean[]    canReachOnMore;
  public boolean            hasSkipActions = false;
  public boolean            hasMoreActions = false;
  public boolean            hasTokenActions;
  public boolean            hasSpecial     = false;
  public boolean            hasSkip        = false;
  public boolean            hasMore        = false;
  public boolean            keepLineCol;

  /**
   * Constructs an instance of {@link LexerData}.
   *
   * @param request
   * @param maxOrdinal
   * @param maxLexStates
   */
  LexerData(JavaCCRequest request, int maxOrdinal, int maxLexStates, Set<String> stateNames) {
    this.request = request;
    this.maxOrdinal = maxOrdinal;
    this.maxLexStates = maxLexStates;
    this.stateNames = stateNames;

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

    this.maxLongsReqd = new int[this.maxLexStates];
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

  public final boolean ignoreCase() {
    return this.request.ignoreCase();
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
  final LexerStateData newStateData(String name) {
    this.stateData.put(name, new LexerStateData(this, name));
    return this.stateData.get(name);
  }

  /**
   * Reset the {@link LexerData} for another cycle.
   */
  public final LexerStateData getStateData(String name) {
    return this.stateData.get(name);
  }

}
