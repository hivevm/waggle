// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.ParserRequest;
import org.hivevm.cc.lexer.Nfa;
import org.hivevm.cc.lexer.NfaState;
import org.hivevm.cc.lexer.NfaVisitor;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.RChoice;
import org.hivevm.cc.parser.RStringLiteral;
import org.hivevm.cc.parser.RStringLiteral.KindInfo;
import org.hivevm.cc.parser.RegExprSpec;
import org.hivevm.cc.parser.RegularExpression;
import org.hivevm.cc.parser.TokenProduction;
import org.hivevm.cc.parser.TokenProduction.Kind;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

/**
 * The {@link LexerBuilder} class.
 */
public class LexerBuilder {

  public LexerData build(ParserRequest request) throws IOException {
    if (JavaCCErrors.hasError()) {
      return null;
    }

    Hashtable<String, List<TokenProduction>> allTpsForState = new Hashtable<>();
    LexerData data = buildLexStatesTable(request, allTpsForState);

    List<RegularExpression> choices = new ArrayList<>();
    buildLexer(data, allTpsForState, choices);

    choices.forEach(c -> LexerUtility.CheckUnmatchability((RChoice) c, data));

    LexerBuilder.CheckEmptyStringMatch(data);

    for (String key : data.stateNames) {
      NfaStateData stateData = data.getStateData(key);
      if (stateData.hasNFA && !stateData.isMixedState()) {
        CalcNfaStartStatesCode(stateData, stateData.statesForPos);
      }
    }

    for (String stateName : data.stateNames) {
      NfaStateData stateData = data.getStateData(stateName);
      if (stateData.hasNFA) {
        for (int i = 0; i < stateData.getAllStateCount(); i++) {
          GetNonAsciiMoves(data, stateData.getAllState(i));
        }
      }

      GetDfaCode(stateData);
      if (stateData.hasNFA) {
        GetMoveNfa(stateData);
      }
    }
    return data;
  }

  private final void AddCharToSkip(NfaStateData data, NfaState[] singlesToSkip, char c, int kind) {
    singlesToSkip[data.getStateIndex()].AddChar(c);
    singlesToSkip[data.getStateIndex()].kind = kind;
  }

  private final void buildLexer(LexerData data, Hashtable<String, List<TokenProduction>> allTpsForState,
      List<RegularExpression> choices) {

    // IMPORTANT: Init after buildLexStatesTable
    RegularExpression curRE = null;
    Kind[] kinds = new Kind[data.maxOrdinal];
    Hashtable<String, NfaState> initStates = new Hashtable<>();

    for (String key : allTpsForState.keySet()) {
      NfaStateData stateData = data.newStateData(key);
      initStates.put(key, stateData.getInitialState());

      data.singlesToSkip[stateData.getStateIndex()] = new NfaState(stateData);
      data.singlesToSkip[stateData.getStateIndex()].dummy = true;

      if (key.equals("DEFAULT")) {
        data.defaultLexState = stateData.getStateIndex();
      }

      boolean ignoring = false;
      List<TokenProduction> allTps = allTpsForState.get(key);
      for (int i = 0; i < allTps.size(); i++) {
        TokenProduction tp = allTps.get(i);
        Kind kind = tp.getKind();
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

          if (!data.options().getNoDfa() && (curRE instanceof RStringLiteral)
              && !((RStringLiteral) curRE).getImage().equals("")) {
            GenerateDfa(stateData, ((RStringLiteral) curRE));
            if ((i != 0) && !stateData.isMixedState() && (ignoring != ignore)) {
              stateData.hasMixed = true;
            }
          } else if (curRE.CanMatchAnyChar()) {
            if ((data.canMatchAnyChar[stateData.getStateIndex()] == -1)
                || (data.canMatchAnyChar[stateData.getStateIndex()] > curRE.getOrdinal())) {
              data.canMatchAnyChar[stateData.getStateIndex()] = curRE.getOrdinal();
            }
          } else {
            Nfa temp;

            if (curRE instanceof RChoice) {
              choices.add(curRE);
            }

            temp = curRE.accept(new NfaVisitor(ignore), stateData);
            temp.end.isFinal = true;
            temp.end.kind = curRE.getOrdinal();
            stateData.getInitialState().AddMove(temp.start);
          }

          if (kinds.length < curRE.getOrdinal()) {
            Kind[] tmp = new Kind[curRE.getOrdinal() + 1];
            System.arraycopy(kinds, 0, tmp, 0, kinds.length);
            kinds = tmp;
          }
          kinds[curRE.getOrdinal()] = kind;

          if ((respec.nextState != null) && !respec.nextState.equals(data.getStateName(stateData.getStateIndex()))) {
            data.newLexState[curRE.getOrdinal()] = respec.nextState;
          }

          if ((respec.act != null) && !respec.act.getActionTokens().isEmpty()) {
            data.actions[curRE.getOrdinal()] = respec.act;
          }

          switch (kind) {
            case SPECIAL:
              data.hasSkipActions |=
                  (data.actions[curRE.getOrdinal()] != null) || (data.newLexState[curRE.getOrdinal()] != null);
              data.hasSpecial = true;
              data.toSpecial[curRE.getOrdinal() / 64] |= 1L << (curRE.getOrdinal() % 64);
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
                data.canReachOnMore[data.getStateIndex(data.newLexState[curRE.getOrdinal()])] = true;
              } else {
                data.canReachOnMore[stateData.getStateIndex()] = true;
              }

              break;
            case TOKEN:
              data.hasTokenActions |= (data.actions[curRE.getOrdinal()] != null);
              data.toToken[curRE.getOrdinal() / 64] |= 1L << (curRE.getOrdinal() % 64);
              break;
          }
        }
      }

      // Generate a static block for initializing the nfa transitions
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
        AddCompositeStateSet(stateData, stateData.getInitialState().epsilonMovesString, true);
      }

      if ((stateData.getInitialState().kind != Integer.MAX_VALUE) && (stateData.getInitialState().kind != 0)) {
        if (((data.toSkip[stateData.getInitialState().kind / 64] & (1L << stateData.getInitialState().kind)) != 0L)
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
            || (data.initMatch[stateData.getStateIndex()] > stateData.getInitialState().kind)) {
          data.initMatch[stateData.getStateIndex()] = stateData.getInitialState().kind;
          data.hasEmptyMatch = true;
        }
      } else if (data.initMatch[stateData.getStateIndex()] == 0) {
        data.initMatch[stateData.getStateIndex()] = Integer.MAX_VALUE;
      }

      FillSubString(stateData);

      if (stateData.hasNFA && !stateData.isMixedState()) {
        GenerateNfaStartStates(stateData, stateData.getInitialState());
      }

      data.totalNumStates += stateData.generatedStates();
      if (data.stateSetSize < stateData.generatedStates()) {
        data.stateSetSize = stateData.generatedStates();
      }
    }
  }


  private final LexerData buildLexStatesTable(ParserRequest request,
      Hashtable<String, List<TokenProduction>> allTpsForState) {
    String[] tmpLexStateName = new String[request.getStateCount()];
    int maxOrdinal = 1;
    int maxLexStates = 0;
    for (TokenProduction tp : request.getTokenProductions()) {
      List<RegExprSpec> respecs = tp.getRespecs();
      List<TokenProduction> tps;

      for (String lexState : tp.getLexStates()) {
        if ((tps = allTpsForState.get(lexState)) == null) {
          tmpLexStateName[maxLexStates++] = lexState;
          allTpsForState.put(lexState, tps = new ArrayList<>());
        }
        tps.add(tp);
      }

      if ((respecs == null) || (respecs.size() == 0)) {
        continue;
      }

      RegularExpression re;
      for (RegExprSpec respec : respecs) {
        if (maxOrdinal <= (re = respec.rexp).getOrdinal()) {
          maxOrdinal = re.getOrdinal() + 1;
        }
      }
    }

    LexerData data = new LexerData(request, maxOrdinal, maxLexStates, allTpsForState.keySet());
    System.arraycopy(tmpLexStateName, 0, data.lexStateNames, 0, data.maxLexStates);
    return data;
  }

  // --------------------------------------- RString

  private void GenerateNfaStartStates(NfaStateData data, NfaState initialState) {
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

    for (i = 0; i < data.maxStrKind; i++) {
      if (data.global.getState(i) != data.getStateIndex()) {
        continue;
      }

      String image = data.global.getImage(i);
      if ((image == null) || (image.length() < 1)) {
        continue;
      }

      try {
        oldStates = (List<NfaState>) initialState.epsilonMoves.clone();
        if ((oldStates == null) || oldStates.isEmpty()) {
          return;
        }
      } catch (Exception e) {
        JavaCCErrors.semantic_error("Error cloning state vector");
      }

      data.intermediateKinds[i] = new int[image.length()];
      data.intermediateMatchedPos[i] = new int[image.length()];
      jjmatchedPos = 0;
      kind = Integer.MAX_VALUE;

      for (j = 0; j < image.length(); j++) {
        if ((oldStates == null) || oldStates.isEmpty()) {
          // Here, j > 0
          kind = data.intermediateKinds[i][j] = data.intermediateKinds[i][j - 1];
          jjmatchedPos = data.intermediateMatchedPos[i][j] = data.intermediateMatchedPos[i][j - 1];
        } else {
          kind = NfaState.MoveFromSet(image.charAt(j), oldStates, newStates);
          oldStates.clear();

          if ((j == 0) && (kind != Integer.MAX_VALUE) && (data.global.canMatchAnyChar[data.getStateIndex()] != -1)
              && (kind > data.global.canMatchAnyChar[data.getStateIndex()])) {
            kind = data.global.canMatchAnyChar[data.getStateIndex()];
          }

          if (GetStrKind(data, image.substring(0, j + 1)) < kind) {
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

        if ((kind == Integer.MAX_VALUE) && ((newStates == null) || (newStates.size() == 0))) {
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

        if ((actives = (data.statesForPos[j].get(kind + ", " + jjmatchedPos + ", " + stateSetString))) == null) {
          actives = new long[maxKindsReqd];
          data.statesForPos[j].put(kind + ", " + jjmatchedPos + ", " + stateSetString, actives);
        }

        actives[i / 64] |= 1L << (i % 64);
      }
    }
  }


  private int GetStrKind(NfaStateData data, String str) {
    for (int i = 0; i < data.maxStrKind; i++) {
      if (data.global.getState(i) != data.getStateIndex()) {
        continue;
      }

      String image = data.global.allImages[i];
      if ((image != null) && image.equals(str)) {
        return i;
      }
    }

    return Integer.MAX_VALUE;
  }

  private String epsilonMovesString(NfaStateData data, List<NfaState> states) {
    if ((states == null) || (states.size() == 0)) {
      return "null;";
    }

    int[] set = new int[states.size()];
    String epsilonMovesString = "{ ";
    for (int i = 0; i < states.size();) {
      int k;
      epsilonMovesString += (k = states.get(i).stateName) + ", ";
      set[i] = k;

      if ((i++ > 0) && ((i % 16) == 0)) {
        epsilonMovesString += "\n";
      }
    }

    epsilonMovesString += "};";
    data.setNextStates(epsilonMovesString, set);
    return epsilonMovesString;
  }


  private final static String[] ReArrange(Hashtable<String, ?> tab) {
    String[] ret = new String[tab.size()];
    Enumeration<String> e = tab.keys();
    int cnt = 0;

    while (e.hasMoreElements()) {
      int i = 0, j;
      String s;
      char c = (s = e.nextElement()).charAt(0);

      while ((i < cnt) && (ret[i].charAt(0) < c)) {
        i++;
      }

      if (i < cnt) {
        for (j = cnt - 1; j >= i; j--) {
          ret[j + 1] = ret[j];
        }
      }

      ret[i] = s;
      cnt++;
    }

    return ret;
  }


  private final int GetStateSetForKind(NfaStateData data, int pos, int kind) {
    if (data.isMixedState() || (data.generatedStates() == 0)) {
      return -1;
    }

    Hashtable<String, long[]> allStateSets = data.statesForPos[pos];
    if (allStateSets == null) {
      return -1;
    }

    Enumeration<String> e = allStateSets.keys();
    while (e.hasMoreElements()) {
      String s = e.nextElement();
      long[] actives = allStateSets.get(s);

      s = s.substring(s.indexOf(", ") + 2);
      s = s.substring(s.indexOf(", ") + 2);
      if (s.equals("null;")) {
        continue;
      }

      if ((actives != null) && ((actives[kind / 64] & (1L << (kind % 64))) != 0L)) {
        return AddCompositeStateSet(data, s, true);
      }
    }
    return -1;
  }

  private final boolean CanStartNfaUsingAscii(NfaStateData data, char c) {
    if (c >= 128) {
      throw new Error("JavaCC Bug: Please send mail to sankar@cs.stanford.edu");
    }

    String s = data.getInitialState().GetEpsilonMovesString();
    if ((s == null) || s.equals("null;")) {
      return false;
    }

    int[] states = data.getNextStates(s);
    for (int state : states) {
      NfaState tmp = data.getIndexedState(state);
      if ((tmp.asciiMoves[c / 64] & (1L << (c % 64))) != 0L) {
        return true;
      }
    }
    return false;
  }


  /**
   * Used for top level string literals.
   */
  private void GenerateDfa(NfaStateData data, RStringLiteral rstring) {
    String s;
    Hashtable<String, KindInfo> temp;
    KindInfo info;
    int len;

    if (data.maxStrKind <= rstring.getOrdinal()) {
      data.maxStrKind = rstring.getOrdinal() + 1;
    }

    if ((len = rstring.getImage().length()) > data.maxLen) {
      data.maxLen = len;
    }

    char c;
    for (int i = 0; i < len; i++) {
      if (data.ignoreCase()) {
        s = ("" + (c = rstring.getImage().charAt(i))).toLowerCase(Locale.ENGLISH);
      } else {
        s = "" + (c = rstring.getImage().charAt(i));
      }

      if (i >= data.charPosKind.size()) { // Kludge, but OK
        data.charPosKind.add(temp = new Hashtable<>());
      } else { // Kludge, but OK
        temp = data.charPosKind.get(i);
      }

      if ((info = temp.get(s)) == null) {
        temp.put(s, info = rstring.new KindInfo(data.global.maxOrdinal));
      }

      if ((i + 1) == len) {
        info.InsertFinalKind(rstring.getOrdinal());
      } else {
        info.InsertValidKind(rstring.getOrdinal());
      }

      if (!data.ignoreCase() && data.global.ignoreCase[rstring.getOrdinal()] && (c != Character.toLowerCase(c))) {
        s = ("" + rstring.getImage().charAt(i)).toLowerCase(Locale.ENGLISH);

        if (i >= data.charPosKind.size()) { // Kludge, but OK
          data.charPosKind.add(temp = new Hashtable<>());
        } else { // Kludge, but OK
          temp = data.charPosKind.get(i);
        }

        if ((info = temp.get(s)) == null) {
          temp.put(s, info = rstring.new KindInfo(data.global.maxOrdinal));
        }

        if ((i + 1) == len) {
          info.InsertFinalKind(rstring.getOrdinal());
        } else {
          info.InsertValidKind(rstring.getOrdinal());
        }
      }

      if (!data.ignoreCase() && data.global.ignoreCase[rstring.getOrdinal()] && (c != Character.toUpperCase(c))) {
        s = ("" + rstring.getImage().charAt(i)).toUpperCase();

        if (i >= data.charPosKind.size()) { // Kludge, but OK
          data.charPosKind.add(temp = new Hashtable<>());
        } else { // Kludge, but OK
          temp = data.charPosKind.get(i);
        }

        if ((info = temp.get(s)) == null) {
          temp.put(s, info = rstring.new KindInfo(data.global.maxOrdinal));
        }

        if ((i + 1) == len) {
          info.InsertFinalKind(rstring.getOrdinal());
        } else {
          info.InsertValidKind(rstring.getOrdinal());
        }
      }
    }

    data.maxLenForActive[rstring.getOrdinal() / 64] =
        Math.max(data.maxLenForActive[rstring.getOrdinal() / 64], len - 1);
    data.global.allImages[rstring.getOrdinal()] = rstring.getImage();
  }

  // ////////////////////////// NFaState

  private final void ReArrange(NfaStateData data) {
    List<NfaState> v = data.cloneAllStates();

    if (data.getAllStateCount() != data.generatedStates()) {
      throw new Error("What??");
    }

    for (NfaState tmp : v) {
      if ((tmp.stateName != -1) && !tmp.dummy) {
        data.setAllState(tmp.stateName, tmp);
      }
    }
  }

  private final void FixStateSets(NfaStateData data) {
    Hashtable<String, int[]> fixedSets = new Hashtable<>();
    Enumeration<String> e = data.stateSetsToFix.keys();
    int[] tmp = new int[data.generatedStates()];
    int i;

    while (e.hasMoreElements()) {
      String s;
      int[] toFix = data.stateSetsToFix.get(s = e.nextElement());
      int cnt = 0;

      // System.out.print("Fixing : ");
      for (i = 0; i < toFix.length; i++) {
        // System.out.print(toFix[i] + ", ");
        if (toFix[i] != -1) {
          tmp[cnt++] = toFix[i];
        }
      }

      int[] fixed = new int[cnt];
      System.arraycopy(tmp, 0, fixed, 0, cnt);
      fixedSets.put(s, fixed);
      data.setNextStates(s, fixed);
    }

    for (i = 0; i < data.getAllStateCount(); i++) {
      NfaState tmpState = data.getAllState(i);
      int[] newSet;

      if ((tmpState.next == null) || (tmpState.next.usefulEpsilonMoves == 0)) {
        continue;
      }

      if ((newSet = fixedSets.get(tmpState.next.epsilonMovesString)) != null) {
        tmpState.FixNextStates(newSet);
      }
    }
  }


  private final int StateNameForComposite(NfaStateData data, String stateSetString) {
    return data.stateNameForComposite.get(stateSetString).intValue();
  }

  private final Vector<List<NfaState>> PartitionStatesSetForAscii(NfaStateData data, int[] states, int byteNum) {
    int[] cardinalities = new int[states.length];
    Vector<NfaState> original = new Vector<>();
    Vector<List<NfaState>> partition = new Vector<>();
    NfaState tmp;

    original.setSize(states.length);
    int cnt = 0;
    for (int i = 0; i < states.length; i++) {
      tmp = data.getAllState(states[i]);

      if (tmp.asciiMoves[byteNum] != 0L) {
        int j;
        int p = LexerBuilder.NumberOfBitsSet(tmp.asciiMoves[byteNum]);

        for (j = 0; j < i; j++) {
          if (cardinalities[j] <= p) {
            break;
          }
        }

        for (int k = i; k > j; k--) {
          cardinalities[k] = cardinalities[k - 1];
        }

        cardinalities[j] = p;
        original.insertElementAt(tmp, j);
        cnt++;
      }
    }

    original.setSize(cnt);

    while (original.size() > 0) {
      tmp = original.get(0);
      original.removeElement(tmp);

      long bitVec = tmp.asciiMoves[byteNum];
      List<NfaState> subSet = new ArrayList<>();
      subSet.add(tmp);

      for (int j = 0; j < original.size(); j++) {
        NfaState tmp1 = original.get(j);

        if ((tmp1.asciiMoves[byteNum] & bitVec) == 0L) {
          bitVec |= tmp1.asciiMoves[byteNum];
          subSet.add(tmp1);
          original.removeElementAt(j--);
        }
      }

      partition.add(subSet);
    }

    return partition;
  }

  private static int NumberOfBitsSet(long l) {
    int ret = 0;
    for (int i = 0; i < 63; i++) {
      if (((l >> i) & 1L) != 0L) {
        ret++;
      }
    }
    return ret;
  }

  private final void UpdateDuplicateNonAsciiMoves(LexerData data, NfaState state) {
    for (int i = 0; i < data.nonAsciiTableForMethod.size(); i++) {
      NfaState tmp = data.nonAsciiTableForMethod.get(i);
      if (NfaState.EqualLoByteVectors(state.loByteVec, tmp.loByteVec)
          && NfaState.EqualNonAsciiMoveIndices(state.nonAsciiMoveIndices, tmp.nonAsciiMoveIndices)) {
        state.nonAsciiMethod = i;
        return;
      }
    }

    state.nonAsciiMethod = data.nonAsciiTableForMethod.size();
    data.nonAsciiTableForMethod.add(state);
  }


  /**
   * Returns true if s1 starts with s2 (ignoring case for each character).
   */
  static private boolean StartsWithIgnoreCase(String s1, String s2) {
    if (s1.length() < s2.length()) {
      return false;
    }

    for (int i = 0; i < s2.length(); i++) {
      char c1 = s1.charAt(i), c2 = s2.charAt(i);

      if ((c1 != c2) && (Character.toLowerCase(c2) != c1) && (Character.toUpperCase(c2) != c1)) {
        return false;
      }
    }

    return true;
  }

  private final void FillSubString(NfaStateData data) {
    String image;
    data.subString = new boolean[data.maxStrKind + 1];
    data.subStringAtPos = new boolean[data.maxLen];

    for (int i = 0; i < data.maxStrKind; i++) {
      data.subString[i] = false;

      if (((image = data.global.getImage(i)) == null) || (data.global.getState(i) != data.getStateIndex())) {
        continue;
      }

      if (data.isMixedState()) {
        // We will not optimize for mixed case
        data.subString[i] = true;
        data.subStringAtPos[image.length() - 1] = true;
        continue;
      }

      for (int j = 0; j < data.maxStrKind; j++) {
        if ((j != i) && (data.global.getState(j) == data.getStateIndex()) && ((data.global.getImage(j)) != null)) {
          if (data.global.getImage(j).indexOf(image) == 0) {
            data.subString[i] = true;
            data.subStringAtPos[image.length() - 1] = true;
            break;
          } else if (data.ignoreCase() && LexerBuilder.StartsWithIgnoreCase(data.global.getImage(j), image)) {
            data.subString[i] = true;
            data.subStringAtPos[image.length() - 1] = true;
            break;
          }
        }
      }
    }
  }

  private final int AddCompositeStateSet(NfaStateData data, String stateSetString, boolean starts) {
    Integer stateNameToReturn;

    if ((stateNameToReturn = data.stateNameForComposite.get(stateSetString)) != null) {
      return stateNameToReturn.intValue();
    }

    int toRet = 0;
    int[] nameSet = data.getNextStates(stateSetString);

    if (!starts) {
      data.stateBlockTable.put(stateSetString, stateSetString);
    }

    if (nameSet == null) {
      throw new Error("JavaCC Bug: Please file a bug at: https://github.com/javacc/javacc/issues");
    }

    if (nameSet.length == 1) {
      stateNameToReturn = Integer.valueOf(nameSet[0]);
      data.stateNameForComposite.put(stateSetString, stateNameToReturn);
      return nameSet[0];
    }

    for (int element : nameSet) {
      if (element == -1) {
        continue;
      }

      NfaState st = data.getIndexedState(element);
      st.isComposite = true;
      st.compositeStates = nameSet;
    }

    while ((toRet < nameSet.length) && (starts && (data.getIndexedState(nameSet[toRet]).inNextOf > 1))) {
      toRet++;
    }

    Enumeration<String> e = data.compositeStateTable.keys();
    String s;
    while (e.hasMoreElements()) {
      s = e.nextElement();
      if (!s.equals(stateSetString) && NfaState.Intersect(data, stateSetString, s)) {
        int[] other = data.compositeStateTable.get(s);

        while ((toRet < nameSet.length) && ((starts && (data.getIndexedState(nameSet[toRet]).inNextOf > 1))
            || (NfaState.ElemOccurs(nameSet[toRet], other) >= 0))) {
          toRet++;
        }
      }
    }

    int tmp;

    if (toRet >= nameSet.length) {
      if (data.dummyStateIndex == -1) {
        tmp = data.dummyStateIndex = data.generatedStates();
      } else {
        tmp = ++data.dummyStateIndex;
      }
    } else {
      tmp = nameSet[toRet];
    }

    stateNameToReturn = Integer.valueOf(tmp);
    data.stateNameForComposite.put(stateSetString, stateNameToReturn);
    data.compositeStateTable.put(stateSetString, nameSet);
    return tmp;
  }

  private static void CheckEmptyStringMatch(LexerData data) {
    int i, j, k, len;
    boolean[] seen = new boolean[data.maxLexStates];
    boolean[] done = new boolean[data.maxLexStates];
    String cycle;
    String reList;

    Outer:
    for (i = 0; i < data.maxLexStates; i++) {
      if (done[i] || (data.initMatch[i] == 0) || (data.initMatch[i] == Integer.MAX_VALUE)
          || (data.canMatchAnyChar[i] != -1)) {
        continue;
      }

      done[i] = true;
      len = 0;
      cycle = "";
      reList = "";

      for (k = 0; k < data.maxLexStates; k++) {
        seen[k] = false;
      }

      j = i;
      seen[i] = true;
      cycle += data.getStateName(j) + "-->";
      while (data.newLexState[data.initMatch[j]] != null) {
        cycle += data.newLexState[data.initMatch[j]];
        if (seen[j = data.getStateIndex(data.newLexState[data.initMatch[j]])]) {
          break;
        }

        cycle += "-->";
        done[j] = true;
        seen[j] = true;
        if ((data.initMatch[j] == 0) || (data.initMatch[j] == Integer.MAX_VALUE) || (data.canMatchAnyChar[j] != -1)) {
          continue Outer;
        }
        if (len != 0) {
          reList += "; ";
        }
        reList += "line " + data.rexprs[data.initMatch[j]].getLine() + ", column "
            + data.rexprs[data.initMatch[j]].getColumn();
        len++;
      }

      if (data.newLexState[data.initMatch[j]] == null) {
        cycle += data.getStateName(data.getState(data.initMatch[j]));
      }

      for (k = 0; k < data.maxLexStates; k++) {
        data.canLoop[k] |= seen[k];
      }

      data.hasLoop = true;
      if (len == 0) {
        JavaCCErrors.warning(data.rexprs[data.initMatch[i]],
            "Regular expression"
                + ((data.rexprs[data.initMatch[i]].getLabel().equals("")) ? ""
                    : (" for " + data.rexprs[data.initMatch[i]].getLabel()))
                + " can be matched by the empty string (\"\") in lexical state " + data.getStateName(i)
                + ". This can result in an endless loop of " + "empty string matches.");
      } else {
        JavaCCErrors.warning(data.rexprs[data.initMatch[i]],
            "Regular expression"
                + ((data.rexprs[data.initMatch[i]].getLabel().equals("")) ? ""
                    : (" for " + data.rexprs[data.initMatch[i]].getLabel()))
                + " can be matched by the empty string (\"\") in lexical state " + data.getStateName(i)
                + ". This regular expression along with the " + "regular expressions at " + reList
                + " forms the cycle \n   " + cycle + "\ncontaining regular expressions with empty matches."
                + " This can result in an endless loop of empty string matches.");
      }
    }
  }

  private final void CalcNfaStartStatesCode(NfaStateData data, Hashtable<String, long[]>[] statesForPos) {
    if (data.maxStrKind == 0) { // No need to generate this function
      return;
    }

    int i;
    boolean condGenerated = false;
    int ind = 0;

    for (i = 0; i < (data.maxLen - 1); i++) {
      if (statesForPos[i] == null) {
        continue;
      }
      Enumeration<String> e = statesForPos[i].keys();
      while (e.hasMoreElements()) {
        String stateSetString = e.nextElement();
        if (condGenerated) {
          String afterKind = stateSetString.substring(ind + 2);
          afterKind = stateSetString.substring(ind + 2);
          stateSetString = afterKind.substring(afterKind.indexOf(", ") + 2);
          if (stateSetString.equals("null;")) {} else {
            AddCompositeStateSet(data, stateSetString, true);
          }
          condGenerated = false;
        }
      }
    }
  }


  private void GetNoBreak(NfaStateData data, NfaState state, int byteNum, boolean[] dumped) {
    if (state.inNextOf != 1) {
      throw new Error("JavaCC Bug: Please send mail to sankar@cs.stanford.edu");
    }

    dumped[state.stateName] = true;

    if (byteNum >= 0) {
      if (state.asciiMoves[byteNum] != 0L) {
        GetAsciiMoveForCompositeState(data, state, byteNum);
      }
    } else if (state.nonAsciiMethod != -1) {
      GetNonAsciiMoveForCompositeState(data, state);
    }
  }

  private void GetCompositeStatesNonAsciiMoves(NfaStateData data, String key, boolean[] dumped) {
    int i;
    int[] nameSet = data.getNextStates(key);

    if ((nameSet.length == 1) || dumped[StateNameForComposite(data, key)]) {
      return;
    }

    NfaState toBePrinted = null;
    int neededStates = 0;
    NfaState tmp;
    NfaState stateForCase = null;
    boolean stateBlock = (data.stateBlockTable.get(key) != null);

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

      if (tmp.stateForCase != null) {
        if (stateForCase != null) {
          throw new Error("JavaCC Bug: Please send mail to sankar@cs.stanford.edu : ");
        }

        stateForCase = tmp.stateForCase;
      }
    }

    if (stateForCase != null) {
      GetNoBreak(data, stateForCase, -1, dumped);
    }

    if (neededStates == 0) {
      return;
    }

    if (neededStates == 1) {
      dumped[toBePrinted.stateName] = true;
      GetNonAsciiMove(data, toBePrinted, dumped);
      return;
    }

    int keyState = StateNameForComposite(data, key);
    if (keyState < data.generatedStates()) {
      dumped[keyState] = true;
    }

    for (i = 0; i < nameSet.length; i++) {
      tmp = data.getAllState(nameSet[i]);

      if (tmp.nonAsciiMethod != -1) {
        if (stateBlock) {
          dumped[tmp.stateName] = true;
        }
        GetNonAsciiMoveForCompositeState(data, tmp);
      }
    }
  }

  private void GetAsciiMove(NfaStateData data, NfaState state, int byteNum, boolean dumped[]) {
    boolean nextIntersects = state.selfLoop() && state.isComposite;
    boolean onlyState = true;

    for (NfaState element : data.getAllStates()) {
      NfaState temp1 = element;

      if ((state == temp1) || (temp1.stateName == -1) || temp1.dummy || (state.stateName == temp1.stateName)
          || (temp1.asciiMoves[byteNum] == 0L)) {
        continue;
      }

      if (onlyState && ((state.asciiMoves[byteNum] & temp1.asciiMoves[byteNum]) != 0L)) {
        onlyState = false;
      }

      if (!nextIntersects && NfaState.Intersect(data, temp1.next.epsilonMovesString, state.next.epsilonMovesString)) {
        nextIntersects = true;
      }

      if (!dumped[temp1.stateName] && !temp1.isComposite && (state.asciiMoves[byteNum] == temp1.asciiMoves[byteNum])
          && (state.kindToPrint == temp1.kindToPrint)
          && ((state.next.epsilonMovesString == temp1.next.epsilonMovesString)
              || ((state.next.epsilonMovesString != null) && (temp1.next.epsilonMovesString != null)
                  && state.next.epsilonMovesString.equals(temp1.next.epsilonMovesString)))) {
        dumped[temp1.stateName] = true;
      }
    }

    if ((state.asciiMoves[byteNum] != 0xffffffffffffffffL)
        && (((state.next == null) || (state.next.usefulEpsilonMoves == 0))
            && (state.kindToPrint != Integer.MAX_VALUE))) {
      return;
    }

    if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
      if (state.next.usefulEpsilonMoves == 1) {} else if ((state.next.usefulEpsilonMoves == 2)
          && nextIntersects) {} else {
        int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
        boolean notTwo = ((indices[0] + 1) != indices[1]);

        if (nextIntersects) {
          if (notTwo) {
            data.global.jjCheckNAddStatesDualNeeded = true;
          } else {
            data.global.jjCheckNAddStatesUnaryNeeded = true;
          }
        }
      }
    }
  }


  private void GetAsciiMoveForCompositeState(NfaStateData data, NfaState state, int byteNum) {
    boolean nextIntersects = state.selfLoop();

    for (NfaState temp1 : data.getAllStates()) {
      if ((state == temp1) || (temp1.stateName == -1) || temp1.dummy || (state.stateName == temp1.stateName)
          || (temp1.asciiMoves[byteNum] == 0L)) {
        continue;
      }

      if (!nextIntersects && NfaState.Intersect(data, temp1.next.epsilonMovesString, state.next.epsilonMovesString)) {
        nextIntersects = true;
        break;
      }
    }

    if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
      if (state.next.usefulEpsilonMoves == 1) {} else if ((state.next.usefulEpsilonMoves == 2)
          && nextIntersects) {} else {
        int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
        boolean notTwo = ((indices[0] + 1) != indices[1]);
        if (nextIntersects) {
          if (notTwo) {
            data.global.jjCheckNAddStatesDualNeeded = true;
          } else {
            data.global.jjCheckNAddStatesUnaryNeeded = true;
          }
        }
      }
    }
  }

  private final void GetNonAsciiMoveForCompositeState(NfaStateData data, NfaState state) {
    boolean nextIntersects = state.selfLoop();
    for (NfaState temp1 : data.getAllStates()) {
      if ((state == temp1) || (temp1.stateName == -1) || temp1.dummy || (state.stateName == temp1.stateName)
          || (temp1.nonAsciiMethod == -1)) {
        continue;
      }

      if (!nextIntersects && NfaState.Intersect(data, temp1.next.epsilonMovesString, state.next.epsilonMovesString)) {
        nextIntersects = true;
        break;
      }
    }

    if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
      if (state.next.usefulEpsilonMoves == 1) {} else if ((state.next.usefulEpsilonMoves == 2)
          && nextIntersects) {} else {
        int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
        boolean notTwo = ((indices[0] + 1) != indices[1]);
        if (nextIntersects) {
          if (notTwo) {
            data.global.jjCheckNAddStatesDualNeeded = true;
          } else {
            data.global.jjCheckNAddStatesUnaryNeeded = true;
          }
        }
      }
    }
  }

  private final void GetNonAsciiMove(NfaStateData data, NfaState state, boolean dumped[]) {
    boolean nextIntersects = state.selfLoop() && state.isComposite;

    for (NfaState element : data.getAllStates()) {
      NfaState temp1 = element;

      if ((state == temp1) || (temp1.stateName == -1) || temp1.dummy || (state.stateName == temp1.stateName)
          || (temp1.nonAsciiMethod == -1)) {
        continue;
      }

      if (!nextIntersects && NfaState.Intersect(data, temp1.next.epsilonMovesString, state.next.epsilonMovesString)) {
        nextIntersects = true;
      }

      if (!dumped[temp1.stateName] && !temp1.isComposite && (state.nonAsciiMethod == temp1.nonAsciiMethod)
          && (state.kindToPrint == temp1.kindToPrint)
          && ((state.next.epsilonMovesString == temp1.next.epsilonMovesString)
              || ((state.next.epsilonMovesString != null) && (temp1.next.epsilonMovesString != null)
                  && state.next.epsilonMovesString.equals(temp1.next.epsilonMovesString)))) {
        dumped[temp1.stateName] = true;
      }
    }

    if ((state.next == null) || (state.next.usefulEpsilonMoves <= 0)) {
      return;
    }

    if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
      if (state.next.usefulEpsilonMoves == 1) {} else if ((state.next.usefulEpsilonMoves == 2)
          && nextIntersects) {} else {
        int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
        boolean notTwo = ((indices[0] + 1) != indices[1]);

        if (nextIntersects) {
          if (notTwo) {
            data.global.jjCheckNAddStatesDualNeeded = true;
          } else {
            data.global.jjCheckNAddStatesUnaryNeeded = true;
          }
        }
      }
    }
  }


  private void GetCompositeStatesAsciiMoves(NfaStateData data, String key, int byteNum, boolean[] dumped) {
    int i;
    int[] nameSet = data.getNextStates(key);

    if ((nameSet.length == 1) || dumped[StateNameForComposite(data, key)]) {
      return;
    }

    NfaState toBePrinted = null;
    int neededStates = 0;
    NfaState tmp;
    NfaState stateForCase = null;
    boolean stateBlock = (data.stateBlockTable.get(key) != null);

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

      if (tmp.stateForCase != null) {
        if (stateForCase != null) {
          throw new Error("JavaCC Bug: Please send mail to sankar@cs.stanford.edu : ");
        }

        stateForCase = tmp.stateForCase;
      }
    }

    if (stateForCase != null) {
      GetNoBreak(data, stateForCase, byteNum, dumped);
    }

    if (neededStates == 0) {
      return;
    }

    if (neededStates == 1) {
      dumped[toBePrinted.stateName] = true;
      GetAsciiMove(data, toBePrinted, byteNum, dumped);
      return;
    }

    List<List<NfaState>> partition = PartitionStatesSetForAscii(data, nameSet, byteNum);
    int keyState = StateNameForComposite(data, key);
    if (keyState < data.generatedStates()) {
      dumped[keyState] = true;
    }

    for (i = 0; i < partition.size(); i++) {
      List<NfaState> subSet = partition.get(i);

      for (NfaState element : subSet) {
        tmp = element;

        if (stateBlock) {
          dumped[tmp.stateName] = true;
        }
        GetAsciiMoveForCompositeState(data, tmp, byteNum);
      }
    }
  }

  private final void GetAsciiMoves(NfaStateData data, int byteNum) {
    boolean[] dumped = new boolean[Math.max(data.generatedStates(), data.dummyStateIndex + 1)];
    Enumeration<String> e = data.compositeStateTable.keys();


    while (e.hasMoreElements()) {
      GetCompositeStatesAsciiMoves(data, e.nextElement(), byteNum, dumped);
    }

    for (NfaState element : data.getAllStates()) {
      NfaState temp = element;

      if (dumped[temp.stateName] || (temp.lexState != data.getStateIndex()) || !temp.HasTransitions() || temp.dummy
          || (temp.stateName == -1)) {
        continue;
      }

      if (temp.stateForCase != null) {
        if (temp.inNextOf == 1) {
          continue;
        }

        if (dumped[temp.stateForCase.stateName]) {
          continue;
        }

        GetNoBreak(data, temp.stateForCase, byteNum, dumped);

        if (temp.asciiMoves[byteNum] == 0L) {
          continue;
        }
      }

      if (temp.asciiMoves[byteNum] == 0L) {
        continue;
      }

      dumped[temp.stateName] = true;
      GetAsciiMove(data, temp, byteNum, dumped);
    }
  }


  private final void GetCharAndRangeMoves(NfaStateData data) {
    boolean[] dumped = new boolean[Math.max(data.generatedStates(), data.dummyStateIndex + 1)];
    Enumeration<String> e = data.compositeStateTable.keys();
    int i;

    while (e.hasMoreElements()) {
      GetCompositeStatesNonAsciiMoves(data, e.nextElement(), dumped);
    }

    for (i = 0; i < data.getAllStateCount(); i++) {
      NfaState temp = data.getAllState(i);
      if ((temp.stateName == -1) || dumped[temp.stateName] || (temp.lexState != data.getStateIndex())
          || !temp.HasTransitions() || temp.dummy) {
        continue;
      }

      if (temp.stateForCase != null) {
        if ((temp.inNextOf == 1) || dumped[temp.stateForCase.stateName]) {
          continue;
        }

        GetNoBreak(data, temp.stateForCase, -1, dumped);
        if (temp.nonAsciiMethod == -1) {
          continue;
        }
      }

      if (temp.nonAsciiMethod == -1) {
        continue;
      }

      dumped[temp.stateName] = true;
      GetNonAsciiMove(data, temp, dumped);
    }
  }


  private final void GetMoveNfa(NfaStateData data) {
    int i;
    int[] kindsForStates = null;

    if (data.global.kinds == null) {
      data.global.kinds = new int[data.global.maxLexStates][];
      data.global.statesForState = new int[data.global.maxLexStates][][];
    }

    ReArrange(data);

    for (i = 0; i < data.getAllStateCount(); i++) {
      NfaState temp = data.getAllState(i);

      if ((temp.lexState != data.getStateIndex()) || !temp.HasTransitions() || temp.dummy || (temp.stateName == -1)) {
        continue;
      }

      if (kindsForStates == null) {
        kindsForStates = new int[data.generatedStates()];
        data.global.statesForState[data.getStateIndex()] =
            new int[Math.max(data.generatedStates(), data.dummyStateIndex + 1)][];
      }

      kindsForStates[temp.stateName] = temp.lookingFor;
      data.global.statesForState[data.getStateIndex()][temp.stateName] = temp.compositeStates;

      // GenerateNonAsciiMoves(writer, data.global, temp);
    }

    Enumeration<String> e = data.stateNameForComposite.keys();

    while (e.hasMoreElements()) {
      String s = e.nextElement();
      int state = data.stateNameForComposite.get(s).intValue();

      if (state >= data.generatedStates()) {
        data.global.statesForState[data.getStateIndex()][state] = data.getNextStates(s);
      }
    }

    if (!data.stateSetsToFix.isEmpty()) {
      FixStateSets(data);
    }

    data.global.kinds[data.getStateIndex()] = kindsForStates;

    if (data.generatedStates() == 0) {
      return;
    }

    GetAsciiMoves(data, 0);
    GetAsciiMoves(data, 1);
    GetCharAndRangeMoves(data);
  }

  private final void GetNonAsciiMoves(LexerData data, NfaState state) {
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
    int[] tmpIndices = new int[512]; // 2 * 256

    for (i = 0; i <= 255; i++) {
      if (done[i]
          || (done[i] = (loBytes[i][0] == 0) && (loBytes[i][1] == 0) && (loBytes[i][2] == 0) && (loBytes[i][3] == 0))) {
        continue;
      }

      for (j = i + 1; j < 256; j++) {
        if (done[j]) {
          continue;
        }

        if ((loBytes[i][0] == loBytes[j][0]) && (loBytes[i][1] == loBytes[j][1]) && (loBytes[i][2] == loBytes[j][2])
            && (loBytes[i][3] == loBytes[j][3])) {
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
        Integer ind;
        String tmp;
        long[] lohiByte = { common[0], common[1], common[2], common[3] };
        tmp = "{\n   0x" + Long.toHexString(common[0]) + "L, " + "0x" + Long.toHexString(common[1]) + "L, " + "0x"
            + Long.toHexString(common[2]) + "L, " + "0x" + Long.toHexString(common[3]) + "L\n};";
        if ((ind = data.lohiByteTab.get(tmp)) == null) {
          data.allBitVectors.add(tmp);

          if (!NfaState.AllBitsSet(tmp)) {
            data.lohiByte.put(data.lohiByteCnt, lohiByte);
            // writer.println("static const unsigned long long jjbitVec" + data.lohiByteCnt + "[] =
            // " + tmp);
          }
          data.lohiByteTab.put(tmp, ind = Integer.valueOf(data.lohiByteCnt++));
        }

        tmpIndices[cnt++] = ind.intValue();
        lohiByte = new long[] { loBytes[i][0], loBytes[i][1], loBytes[i][2], loBytes[i][3] };

        tmp = "{\n   0x" + Long.toHexString(loBytes[i][0]) + "L, " + "0x" + Long.toHexString(loBytes[i][1]) + "L, "
            + "0x" + Long.toHexString(loBytes[i][2]) + "L, " + "0x" + Long.toHexString(loBytes[i][3]) + "L\n};";
        if ((ind = data.lohiByteTab.get(tmp)) == null) {
          data.allBitVectors.add(tmp);

          if (!NfaState.AllBitsSet(tmp)) {
            data.lohiByte.put(data.lohiByteCnt, lohiByte);
            // writer.println("static const unsigned long long jjbitVec" + data.lohiByteCnt + "[] =
            // " + tmp);
          }
          data.lohiByteTab.put(tmp, ind = Integer.valueOf(data.lohiByteCnt++));
        }
        tmpIndices[cnt++] = ind.intValue();
        common = null;
      }
    }

    state.nonAsciiMoveIndices = new int[cnt];
    System.arraycopy(tmpIndices, 0, state.nonAsciiMoveIndices, 0, cnt);

    for (i = 0; i < 256; i++) {
      if (done[i]) {
        loBytes[i] = null;
      } else {
        // System.out.print(i + ", ");
        String tmp;
        Integer ind;

        long[] lohiByte = { loBytes[i][0], loBytes[i][1], loBytes[i][2], loBytes[i][3] };
        tmp = "{\n   0x" + Long.toHexString(loBytes[i][0]) + "L, " + "0x" + Long.toHexString(loBytes[i][1]) + "L, "
            + "0x" + Long.toHexString(loBytes[i][2]) + "L, " + "0x" + Long.toHexString(loBytes[i][3]) + "L\n};";

        if ((ind = data.lohiByteTab.get(tmp)) == null) {
          data.allBitVectors.add(tmp);

          if (!NfaState.AllBitsSet(tmp)) {
            data.lohiByte.put(data.lohiByteCnt, lohiByte);
            // writer.println("static const unsigned long long jjbitVec" + data.lohiByteCnt + "[] =
            // " + tmp);
          }
          data.lohiByteTab.put(tmp, ind = Integer.valueOf(data.lohiByteCnt++));
        }

        state.loByteVec.add(Integer.valueOf(i));
        state.loByteVec.add(ind);
      }
    }
    UpdateDuplicateNonAsciiMoves(data, state);
  }

  private final void GetDfaCode(NfaStateData data) {
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
      for (String key2 : LexerBuilder.ReArrange(tab)) {
        key = key2;
        info = (KindInfo) tab.get(key);
        char c = key.charAt(0);

        if ((i == 0) && (c < 128) && (info.finalKindCnt != 0)
            && ((data.generatedStates() == 0) || !CanStartNfaUsingAscii(data, c))) {
          int kind;
          for (j = 0; j < maxLongsReqd; j++) {
            if (info.finalKinds[j] != 0L) {
              break;
            }
          }

          for (k = 0; k < 64; k++) {
            if (((info.finalKinds[j] & (1L << k)) != 0L) && !data.subString[kind = ((j * 64) + k)]) {
              if (((data.intermediateKinds != null) && (data.intermediateKinds[((j * 64) + k)] != null)
                  && (data.intermediateKinds[((j * 64) + k)][i] < ((j * 64) + k))
                  && (data.intermediateMatchedPos != null) && (data.intermediateMatchedPos[((j * 64) + k)][i] == i))
                  || ((data.global.canMatchAnyChar[data.getStateIndex()] >= 0)
                      && (data.global.canMatchAnyChar[data.getStateIndex()] < ((j * 64) + k)))) {
                break;
              } else if (((data.global.toSkip[kind / 64] & (1L << (kind % 64))) != 0L)
                  && ((data.global.toSpecial[kind / 64] & (1L << (kind % 64))) == 0L)
                  && (data.global.actions[kind] == null) && (data.global.newLexState[kind] == null)) {
                AddCharToSkip(data, data.global.singlesToSkip, c, kind);

                if (data.ignoreCase()) {
                  if (c != Character.toUpperCase(c)) {
                    AddCharToSkip(data, data.global.singlesToSkip, Character.toUpperCase(c), kind);
                  }

                  if (c != Character.toLowerCase(c)) {
                    AddCharToSkip(data, data.global.singlesToSkip, Character.toLowerCase(c), kind);
                  }
                }
                continue CaseLoop;
              }
            }
          }
        }

        long matchedKind;
        if (info.finalKindCnt != 0) {
          for (j = 0; j < maxLongsReqd; j++) {
            if ((matchedKind = info.finalKinds[j]) == 0L) {
              continue;
            }

            for (k = 0; k < 64; k++) {
              if ((matchedKind & (1L << k)) == 0L) {
                continue;
              }

              if (!data.subString[((j * 64) + k)]) {
                int stateSetName = GetStateSetForKind(data, i, (j * 64) + k);
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
}
