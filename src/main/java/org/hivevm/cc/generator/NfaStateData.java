// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import org.hivevm.cc.lexer.NfaState;
import org.hivevm.cc.parser.RStringLiteral.KindInfo;

/**
 * The {@link NfaStateData} class.
 */
public class NfaStateData {

  public final LexerData global;

  private final NfaState initialState;
  private final int      lexStateIndex;
  private final String   lexStateSuffix;


  // RString
  int     maxLen;
  int     maxStrKind;
  boolean subString[];
  boolean subStringAtPos[];


  int[]   maxLenForActive;
  int[][] intermediateKinds;
  int[][] intermediateMatchedPos;


  Hashtable<String, long[]>[]       statesForPos;
  List<Hashtable<String, KindInfo>> charPosKind;

  // NfaState
  public boolean               done;
  public boolean               mark[];
  public boolean               hasNFA;
  boolean                      hasMixed;
  boolean                      createStartNfa;

  private int                  idCnt;
  private int                  generatedStates;
  private List<NfaState>       allStates;
  private final List<NfaState> indexedAllStates;


  int                                    dummyStateIndex;
  private final Hashtable<String, int[]> allNextStates;
  final Hashtable<String, Integer>       stateNameForComposite;
  final Hashtable<String, int[]>         compositeStateTable;
  final Hashtable<String, String>        stateBlockTable;
  final Hashtable<String, int[]>         stateSetsToFix;
  public Hashtable<String, NfaState>     equivStatesTable;


  NfaStateData(LexerData data, String name) {
    this.global = data;
    this.lexStateIndex = this.global.getStateIndex(name);
    this.lexStateSuffix = "_" + this.lexStateIndex;

    // RString
    this.maxLen = 0;
    this.maxStrKind = 0;
    this.subString = null;
    this.subStringAtPos = null;
    this.maxLenForActive = new int[100]; // 6400 tokens
    this.intermediateKinds = null;
    this.intermediateMatchedPos = null;
    this.charPosKind = new ArrayList<>();
    this.statesForPos = null;

    // NfaState
    this.done = false;
    this.mark = null;
    this.idCnt = 0;
    this.hasNFA = false;
    this.hasMixed = false;
    this.generatedStates = 0;
    this.allStates = new ArrayList<>();
    this.indexedAllStates = new ArrayList<>();
    this.dummyStateIndex = -1;

    this.allNextStates = new Hashtable<>();
    this.stateNameForComposite = new Hashtable<>();
    this.compositeStateTable = new Hashtable<>();
    this.stateBlockTable = new Hashtable<>();
    this.stateSetsToFix = new Hashtable<>();
    this.equivStatesTable = new Hashtable<>();


    // Do at end
    this.initialState = new NfaState(this);
  }

  public final String getParserName() {
    return this.global.getParserName();
  }

  public final boolean ignoreCase() {
    return this.global.ignoreCase();
  }

  public final boolean hasNFA() {
    return this.hasNFA;
  }

  public final String getLexerStateSuffix() {
    return this.lexStateSuffix;
  }

  public final NfaState getInitialState() {
    return this.initialState;
  }

  public final boolean getCreateStartNfa() {
    return this.createStartNfa;
  }

  public final int getStateIndex() {
    return this.lexStateIndex;
  }

  public final boolean isMixedState() {
    return this.hasMixed;
  }

  public final int generatedStates() {
    return this.generatedStates;
  }

  final List<NfaState> cloneAllStates() {
    List<NfaState> v = this.allStates;
    this.allStates = new ArrayList<>(Collections.nCopies(generatedStates(), null));
    return v;
  }

  public final NfaState getIndexedState(int index) {
    return this.indexedAllStates.get(index);
  }

  public final int addIndexedState(NfaState state) {
    this.indexedAllStates.add(state);
    return this.generatedStates++;
  }

  public final int getAllStateCount() {
    return this.allStates.size();
  }

  public final NfaState getAllState(int index) {
    return this.allStates.get(index);
  }

  final void setAllState(int index, NfaState state) {
    this.allStates.set(index, state);
  }

  public final Iterable<NfaState> getAllStates() {
    return this.allStates;
  }

  public final int addAllState(NfaState state) {
    this.allStates.add(state);
    return this.idCnt++;
  }

  public final int[] getNextStates(String name) {
    return this.allNextStates.get(name);
  }

  public final void setNextStates(String name, int[] states) {
    this.allNextStates.put(name, states);
  }

  public final Hashtable<String, KindInfo> getCharPosKind(int index) {
    return this.charPosKind.get(index);
  }

  public final int getMaxLenForActive(int index) {
    return this.maxLenForActive[index];
  }

  public final int[][] getIntermediateKinds() {
    return this.intermediateKinds;
  }

  public final int[][] getIntermediateMatchedPos() {
    return this.intermediateMatchedPos;
  }


  public final int getMaxLen() {
    return this.maxLen;
  }

  public final int getMaxStrKind() {
    return this.maxStrKind;
  }

  public final boolean isSubString(int index) {
    return this.subString[index];
  }

  public final boolean isSubStringAtPos(int index) {
    return this.subStringAtPos[index];
  }
}
