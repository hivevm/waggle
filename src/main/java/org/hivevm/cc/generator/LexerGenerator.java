// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.lexer.NfaState;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.RStringLiteral;
import org.hivevm.cc.parser.RegularExpression;
import org.hivevm.cc.parser.Token;
import org.hivevm.cc.utils.Encoding;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

/**
 * The {@link LexerGenerator} class.
 */
public abstract class LexerGenerator extends CodeGenerator {

  final void start(LexerData request) throws IOException {
    if (JavaCCErrors.hasError()) {
      return;
    }

    dumpAll(request);
  }

  // --------------------------------------- RString

  protected abstract void dumpAll(LexerData data) throws IOException;


  protected final static String[] ReArrange(Hashtable<String, ?> tab) {
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

  protected final int GetLine(LexerData data, int kind) {
    return data.rexprs[kind].getLine();
  }

  protected final int GetColumn(LexerData data, int kind) {
    return data.rexprs[kind].getColumn();
  }

  protected final int GetStateSetForKind(LexerStateData data, int pos, int kind) {
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

  protected final boolean CanStartNfaUsingAscii(LexerStateData data, char c) {
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


  // ////////////////////////// NFaState

  protected final void ReArrange(LexerStateData data) {
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

  protected final void FixStateSets(LexerStateData data) {
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


  private final int StateNameForComposite(LexerStateData data, String stateSetString) {
    return data.stateNameForComposite.get(stateSetString).intValue();
  }

  private final Vector<List<NfaState>> PartitionStatesSetForAscii(LexerStateData data, int[] states, int byteNum) {
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
        int p = LexerGenerator.NumberOfBitsSet(tmp.asciiMoves[byteNum]);

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

  protected final int InitStateName(LexerStateData data) {
    String s = data.getInitialState().GetEpsilonMovesString();

    if (data.getInitialState().usefulEpsilonMoves != 0) {
      return StateNameForComposite(data, s);
    }
    return -1;
  }


  protected final int getCompositeStateSet(LexerStateData data, String stateSetString, boolean starts) {
    Integer stateNameToReturn = data.stateNameForComposite.get(stateSetString);

    if (stateNameToReturn != null) {
      return stateNameToReturn.intValue();
    }

    int[] nameSet = data.getNextStates(stateSetString);

    if (nameSet.length == 1) {
      return nameSet[0];
    }

    int toRet = 0;
    while ((toRet < nameSet.length) && (starts && (data.getIndexedState(nameSet[toRet]).inNextOf > 1))) {
      toRet++;
    }

    for (String s : data.compositeStateTable.keySet()) {
      if (!s.equals(stateSetString) && NfaState.Intersect(data, stateSetString, s)) {
        int[] other = data.compositeStateTable.get(s);
        while ((toRet < nameSet.length) && ((starts && (data.getIndexedState(nameSet[toRet]).inNextOf > 1))
            || (NfaState.ElemOccurs(nameSet[toRet], other) >= 0))) {
          toRet++;
        }
      }
    }
    return nameSet[toRet];
  }

  private final int AddCompositeStateSet(LexerStateData data, String stateSetString, boolean starts) {
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


  protected final void genToken(PrintWriter writer, Token t) {
    writer.print(getStringToPrint(t));
  }


  protected final String GetLabel(LexerData data, int kind) {
    RegularExpression re = data.rexprs[kind];
    if (re instanceof RStringLiteral) {
      return " \"" + Encoding.escape(((RStringLiteral) re).getImage()) + "\"";
    } else if (!re.getLabel().equals("")) {
      return " <" + re.getLabel() + ">";
    } else {
      return " <token of kind " + kind + ">";
    }
  }


  private String PrintNoBreak(PrintWriter writer, LexerStateData data, NfaState state, int byteNum, boolean[] dumped) {
    if (state.inNextOf != 1) {
      throw new Error("JavaCC Bug: Please send mail to sankar@cs.stanford.edu");
    }

    dumped[state.stateName] = true;

    if (byteNum >= 0) {
      if (state.asciiMoves[byteNum] != 0L) {
        writer.println("               case " + state.stateName + ":");
        DumpAsciiMoveForCompositeState(writer, data, state, byteNum, false);
        return "";
      }
    } else if (state.nonAsciiMethod != -1) {
      writer.println("               case " + state.stateName + ":");
      DumpNonAsciiMoveForCompositeState(writer, data, state);
      return "";
    }

    return ("               case " + state.stateName + ":\n");
  }

  protected final void DumpNullStrLiterals(PrintWriter writer, LexerStateData data) {
    writer.println("{");

    if (data.generatedStates() > 0) {
      writer.println("   return jjMoveNfa" + data.lexStateSuffix + "(" + InitStateName(data) + ", 0);");
    } else {
      writer.println("   return 1;");
    }

    writer.println("}");
  }

  private void DumpCompositeStatesNonAsciiMoves(PrintWriter writer, LexerStateData data, String key, boolean[] dumped) {
    int i;
    int[] nameSet = data.getNextStates(key);

    if ((nameSet.length == 1) || dumped[StateNameForComposite(data, key)]) {
      return;
    }

    NfaState toBePrinted = null;
    int neededStates = 0;
    NfaState tmp;
    NfaState stateForCase = null;
    String toPrint = "";
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
      toPrint = PrintNoBreak(writer, data, stateForCase, -1, dumped);
    }

    if (neededStates == 0) {
      if ((stateForCase != null) && toPrint.equals("")) {
        writer.println("                  break;");
      }

      return;
    }

    if (neededStates == 1) {
      if (!toPrint.equals("")) {
        writer.print(toPrint);
      }

      writer.println("               case " + StateNameForComposite(data, key) + ":");

      if (!dumped[toBePrinted.stateName] && !stateBlock && (toBePrinted.inNextOf > 1)) {
        writer.println("               case " + toBePrinted.stateName + ":");
      }

      dumped[toBePrinted.stateName] = true;
      DumpNonAsciiMove(writer, data, toBePrinted, dumped);
      return;
    }

    if (!toPrint.equals("")) {
      writer.print(toPrint);
    }

    int keyState = StateNameForComposite(data, key);
    writer.println("               case " + keyState + ":");
    if (keyState < data.generatedStates()) {
      dumped[keyState] = true;
    }

    for (i = 0; i < nameSet.length; i++) {
      tmp = data.getAllState(nameSet[i]);

      if (tmp.nonAsciiMethod != -1) {
        if (stateBlock) {
          dumped[tmp.stateName] = true;
        }
        DumpNonAsciiMoveForCompositeState(writer, data, tmp);
      }
    }

    writer.println("                  break;");
  }

  private void DumpAsciiMove(PrintWriter writer, LexerStateData data, NfaState state, int byteNum, boolean dumped[]) {
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
        writer.println("               case " + temp1.stateName + ":");
      }
    }

    // if (onlyState)
    // nextIntersects = false;

    int oneBit = NfaState.OnlyOneBitSet(state.asciiMoves[byteNum]);
    if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
      if (((state.next == null) || (state.next.usefulEpsilonMoves == 0)) && (state.kindToPrint != Integer.MAX_VALUE)) {
        String kindCheck = "";

        if (!onlyState) {
          kindCheck = " && kind > " + state.kindToPrint;
        }

        if (oneBit != -1) {
          writer.println("                  if (curChar == " + ((64 * byteNum) + oneBit) + kindCheck + ")");
        } else {
          writer.println("                  if ((0x" + Long.toHexString(state.asciiMoves[byteNum]) + "L & l) != 0L"
              + kindCheck + ")");
        }

        writer.println("                     kind = " + state.kindToPrint + ";");

        if (onlyState) {
          writer.println("                  break;");
        } else {
          writer.println("                  break;");
        }

        return;
      }
    }

    String prefix = "";
    if (state.kindToPrint != Integer.MAX_VALUE) {

      if (oneBit != -1) {
        writer.println("                  if (curChar != " + ((64 * byteNum) + oneBit) + ")");
        writer.println("                     break;");
      } else if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
        writer.println("                  if ((0x" + Long.toHexString(state.asciiMoves[byteNum]) + "L & l) == 0L)");
        writer.println("                     break;");
      }

      if (onlyState) {
        writer.println("                  kind = " + state.kindToPrint + ";");
      } else {
        writer.println("                  if (kind > " + state.kindToPrint + ")");
        writer.println("                     kind = " + state.kindToPrint + ";");
      }
    } else if (oneBit != -1) {
      writer.println("                  if (curChar == " + ((64 * byteNum) + oneBit) + ")");
      prefix = "   ";
    } else if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
      writer.println("                  if ((0x" + Long.toHexString(state.asciiMoves[byteNum]) + "L & l) != 0L)");
      prefix = "   ";
    }

    if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
      int[] stateNames = data.getNextStates(state.next.epsilonMovesString);
      if (state.next.usefulEpsilonMoves == 1) {
        int name = stateNames[0];
        if (nextIntersects) {
          writer.println(prefix + "                  { jjCheckNAdd(" + name + "); }");
        } else {
          writer.println(prefix + "                  jjstateSet[jjnewStateCnt++] = " + name + ";");
        }
      } else if ((state.next.usefulEpsilonMoves == 2) && nextIntersects) {
        writer.println(
            prefix + "                  { jjCheckNAddTwoStates(" + stateNames[0] + ", " + stateNames[1] + "); }");
      } else {
        int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
        boolean notTwo = ((indices[0] + 1) != indices[1]);

        if (nextIntersects) {
          writer.print(prefix + "                  { jjCheckNAddStates(" + indices[0]);
          if (notTwo) {
            data.global.jjCheckNAddStatesDualNeeded = true;
            writer.print(", " + indices[1]);
          } else {
            data.global.jjCheckNAddStatesUnaryNeeded = true;
          }
          writer.println("); }");
        } else {
          writer.println(prefix + "                  { jjAddStates(" + indices[0] + ", " + indices[1] + "); }");
        }
      }
    }

    if (onlyState) {
      writer.println("                  break;");
    } else {
      writer.println("                  break;");
    }
  }


  private void DumpAsciiMoveForCompositeState(PrintWriter writer, LexerStateData data, NfaState state, int byteNum,
      boolean elseNeeded) {
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

    // System.out.println(stateName + " \'s nextIntersects : " + nextIntersects);
    String prefix = "";
    if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
      int oneBit = NfaState.OnlyOneBitSet(state.asciiMoves[byteNum]);

      if (oneBit != -1) {
        writer.println(
            "                  " + (elseNeeded ? "else " : "") + "if (curChar == " + ((64 * byteNum) + oneBit) + ")");
      } else {
        writer.println("                  " + (elseNeeded ? "else " : "") + "if ((0x"
            + Long.toHexString(state.asciiMoves[byteNum]) + "L & l) != 0L)");
      }
      prefix = "   ";
    }

    if (state.kindToPrint != Integer.MAX_VALUE) {
      if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
        writer.println("                  {");
      }

      writer.println(prefix + "                  if (kind > " + state.kindToPrint + ")");
      writer.println(prefix + "                     kind = " + state.kindToPrint + ";");
    }

    if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
      int[] stateNames = data.getNextStates(state.next.epsilonMovesString);
      if (state.next.usefulEpsilonMoves == 1) {
        int name = stateNames[0];

        if (nextIntersects) {
          writer.println(prefix + "                  { jjCheckNAdd(" + name + "); }");
        } else {
          writer.println(prefix + "                  jjstateSet[jjnewStateCnt++] = " + name + ";");
        }
      } else if ((state.next.usefulEpsilonMoves == 2) && nextIntersects) {
        writer.println(
            prefix + "                  { jjCheckNAddTwoStates(" + stateNames[0] + ", " + stateNames[1] + "); }");
      } else {
        int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
        boolean notTwo = ((indices[0] + 1) != indices[1]);

        if (nextIntersects) {
          writer.print(prefix + "                  { jjCheckNAddStates(" + indices[0]);
          if (notTwo) {
            data.global.jjCheckNAddStatesDualNeeded = true;
            writer.print(", " + indices[1]);
          } else {
            data.global.jjCheckNAddStatesUnaryNeeded = true;
          }
          writer.println("); }");
        } else {
          writer.println(prefix + "                  { jjAddStates(" + indices[0] + ", " + indices[1] + "); }");
        }
      }
    }

    if ((state.asciiMoves[byteNum] != 0xffffffffffffffffL) && (state.kindToPrint != Integer.MAX_VALUE)) {
      writer.println("                  }");
    }
  }

  private final void DumpNonAsciiMoveForCompositeState(PrintWriter writer, LexerStateData data, NfaState state) {
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

    writer.println("                  if (jjCanMove_" + state.nonAsciiMethod + "(hiByte, i1, i2, l1, l2))");

    if (state.kindToPrint != Integer.MAX_VALUE) {
      writer.println("                  {");
      writer.println("                     if (kind > " + state.kindToPrint + ")");
      writer.println("                        kind = " + state.kindToPrint + ";");
    }

    if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
      int[] stateNames = data.getNextStates(state.next.epsilonMovesString);
      if (state.next.usefulEpsilonMoves == 1) {
        int name = stateNames[0];
        if (nextIntersects) {
          writer.println("                     { jjCheckNAdd(" + name + "); }");
        } else {
          writer.println("                     jjstateSet[jjnewStateCnt++] = " + name + ";");
        }
      } else if ((state.next.usefulEpsilonMoves == 2) && nextIntersects) {
        writer.println("                     { jjCheckNAddTwoStates(" + stateNames[0] + ", " + stateNames[1] + "); }");
      } else {
        int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
        boolean notTwo = ((indices[0] + 1) != indices[1]);

        if (nextIntersects) {
          writer.print("                     { jjCheckNAddStates(" + indices[0]);
          if (notTwo) {
            data.global.jjCheckNAddStatesDualNeeded = true;
            writer.print(", " + indices[1]);
          } else {
            data.global.jjCheckNAddStatesUnaryNeeded = true;
          }
          writer.println("); }");
        } else {
          writer.println("                     { jjAddStates(" + indices[0] + ", " + indices[1] + "); }");
        }
      }
    }

    if (state.kindToPrint != Integer.MAX_VALUE) {
      writer.println("                  }");
    }
  }

  private final void DumpNonAsciiMove(PrintWriter writer, LexerStateData data, NfaState state, boolean dumped[]) {
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
        writer.println("               case " + temp1.stateName + ":");
      }
    }

    if ((state.next == null) || (state.next.usefulEpsilonMoves <= 0)) {
      String kindCheck = " && kind > " + state.kindToPrint;


      writer.println(
          "                  if (jjCanMove_" + state.nonAsciiMethod + "(hiByte, i1, i2, l1, l2)" + kindCheck + ")");
      writer.println("                     kind = " + state.kindToPrint + ";");
      writer.println("                  break;");
      return;
    }

    String prefix = "   ";
    if (state.kindToPrint != Integer.MAX_VALUE) {
      writer.println("                  if (!jjCanMove_" + state.nonAsciiMethod + "(hiByte, i1, i2, l1, l2))");
      writer.println("                     break;");

      writer.println("                  if (kind > " + state.kindToPrint + ")");
      writer.println("                     kind = " + state.kindToPrint + ";");
      prefix = "";
    } else {
      writer.println("                  if (jjCanMove_" + state.nonAsciiMethod + "(hiByte, i1, i2, l1, l2))");
    }

    if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
      int[] stateNames = data.getNextStates(state.next.epsilonMovesString);
      if (state.next.usefulEpsilonMoves == 1) {
        int name = stateNames[0];
        if (nextIntersects) {
          writer.println(prefix + "                  { jjCheckNAdd(" + name + "); }");
        } else {
          writer.println(prefix + "                  jjstateSet[jjnewStateCnt++] = " + name + ";");
        }
      } else if ((state.next.usefulEpsilonMoves == 2) && nextIntersects) {
        writer.println(
            prefix + "                  { jjCheckNAddTwoStates(" + stateNames[0] + ", " + stateNames[1] + "); }");
      } else {
        int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
        boolean notTwo = ((indices[0] + 1) != indices[1]);

        if (nextIntersects) {
          writer.print(prefix + "                  { jjCheckNAddStates(" + indices[0]);
          if (notTwo) {
            data.global.jjCheckNAddStatesDualNeeded = true;
            writer.print(", " + indices[1]);
          } else {
            data.global.jjCheckNAddStatesUnaryNeeded = true;
          }
          writer.println("); }");
        } else {
          writer.println(prefix + "                  { jjAddStates(" + indices[0] + ", " + indices[1] + "); }");
        }
      }
    }

    writer.println("                  break;");
  }


  private void DumpCompositeStatesAsciiMoves(PrintWriter writer, LexerStateData data, String key, int byteNum,
      boolean[] dumped) {
    int i;
    int[] nameSet = data.getNextStates(key);

    if ((nameSet.length == 1) || dumped[StateNameForComposite(data, key)]) {
      return;
    }

    NfaState toBePrinted = null;
    int neededStates = 0;
    NfaState tmp;
    NfaState stateForCase = null;
    String toPrint = "";
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
      toPrint = PrintNoBreak(writer, data, stateForCase, byteNum, dumped);
    }

    if (neededStates == 0) {
      if ((stateForCase != null) && toPrint.equals("")) {
        writer.println("                  break;");
      }
      return;
    }

    if (neededStates == 1) {
      if (!toPrint.equals("")) {
        writer.print(toPrint);
      }

      writer.println("               case " + StateNameForComposite(data, key) + ":");

      if (!dumped[toBePrinted.stateName] && !stateBlock && (toBePrinted.inNextOf > 1)) {
        writer.println("               case " + toBePrinted.stateName + ":");
      }

      dumped[toBePrinted.stateName] = true;
      DumpAsciiMove(writer, data, toBePrinted, byteNum, dumped);
      return;
    }

    List<List<NfaState>> partition = PartitionStatesSetForAscii(data, nameSet, byteNum);

    if (!toPrint.equals("")) {
      writer.print(toPrint);
    }

    int keyState = StateNameForComposite(data, key);
    writer.println("               case " + keyState + ":");
    if (keyState < data.generatedStates()) {
      dumped[keyState] = true;
    }

    for (i = 0; i < partition.size(); i++) {
      List<NfaState> subSet = partition.get(i);

      for (int j = 0; j < subSet.size(); j++) {
        tmp = subSet.get(j);

        if (stateBlock) {
          dumped[tmp.stateName] = true;
        }
        DumpAsciiMoveForCompositeState(writer, data, tmp, byteNum, j != 0);
      }
    }

    writer.println("                  break;");
  }

  protected abstract void DumpHeadForCase(PrintWriter writer, int byteNum);

  protected final void DumpAsciiMoves(PrintWriter writer, LexerStateData data, int byteNum) {
    boolean[] dumped = new boolean[Math.max(data.generatedStates(), data.dummyStateIndex + 1)];
    Enumeration<String> e = data.compositeStateTable.keys();

    DumpHeadForCase(writer, byteNum);

    while (e.hasMoreElements()) {
      DumpCompositeStatesAsciiMoves(writer, data, e.nextElement(), byteNum, dumped);
    }

    for (NfaState element : data.getAllStates()) {
      NfaState temp = element;

      if (dumped[temp.stateName] || (temp.lexState != data.getStateIndex()) || !temp.HasTransitions() || temp.dummy
          || (temp.stateName == -1)) {
        continue;
      }

      String toPrint = "";

      if (temp.stateForCase != null) {
        if (temp.inNextOf == 1) {
          continue;
        }

        if (dumped[temp.stateForCase.stateName]) {
          continue;
        }

        toPrint = PrintNoBreak(writer, data, temp.stateForCase, byteNum, dumped);

        if (temp.asciiMoves[byteNum] == 0L) {
          if (toPrint.equals("")) {
            writer.println("                  break;");
          }

          continue;
        }
      }

      if (temp.asciiMoves[byteNum] == 0L) {
        continue;
      }

      if (!toPrint.equals("")) {
        writer.print(toPrint);
      }

      dumped[temp.stateName] = true;
      writer.println("               case " + temp.stateName + ":");
      DumpAsciiMove(writer, data, temp, byteNum, dumped);
    }

    if ((byteNum != 0) && (byteNum != 1)) {
      writer.println("               default : if (i1 == 0 || l1 == 0 || i2 == 0 ||  l2 == 0) break; else break;");
    } else {
      writer.println("               default : break;");
    }

    writer.println("            }");
    writer.println("         } while(i != startsAt);");
  }


  protected final void DumpCharAndRangeMoves(PrintWriter writer, LexerStateData data) {
    boolean[] dumped = new boolean[Math.max(data.generatedStates(), data.dummyStateIndex + 1)];
    Enumeration<String> e = data.compositeStateTable.keys();
    int i;

    DumpHeadForCase(writer, -1);

    while (e.hasMoreElements()) {
      DumpCompositeStatesNonAsciiMoves(writer, data, e.nextElement(), dumped);
    }

    for (i = 0; i < data.getAllStateCount(); i++) {
      NfaState temp = data.getAllState(i);

      if ((temp.stateName == -1) || dumped[temp.stateName] || (temp.lexState != data.getStateIndex())
          || !temp.HasTransitions() || temp.dummy) {
        continue;
      }

      String toPrint = "";

      if (temp.stateForCase != null) {
        if (temp.inNextOf == 1) {
          continue;
        }

        if (dumped[temp.stateForCase.stateName]) {
          continue;
        }

        toPrint = PrintNoBreak(writer, data, temp.stateForCase, -1, dumped);

        if (temp.nonAsciiMethod == -1) {
          if (toPrint.equals("")) {
            writer.println("                  break;");
          }

          continue;
        }
      }

      if (temp.nonAsciiMethod == -1) {
        continue;
      }

      if (!toPrint.equals("")) {
        writer.print(toPrint);
      }

      dumped[temp.stateName] = true;
      // System.out.println("case : " + temp.stateName);
      writer.println("               case " + temp.stateName + ":");
      DumpNonAsciiMove(writer, data, temp, dumped);
    }


    writer.println("               default : if (i1 == 0 || l1 == 0 || i2 == 0 ||  l2 == 0) break; else break;");
    writer.println("            }");
    writer.println("         } while(i != startsAt);");
  }
}
