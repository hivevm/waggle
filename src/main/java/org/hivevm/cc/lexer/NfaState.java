// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.lexer;

import org.hivevm.cc.generator.NfaStateData;
import org.hivevm.cc.parser.JavaCCErrors;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

/**
 * The state of a Non-deterministic Finite Automaton.
 */
public class NfaState {

  private final static String ALL_BITS =
      "{\n   0xffffffffffffffffL, 0xffffffffffffffffL, 0xffffffffffffffffL, 0xffffffffffffffffL\n};";


  public static boolean AllBitsSet(String bitVec) {
    return bitVec.equals(NfaState.ALL_BITS);
  }

  public long[]                asciiMoves         = new long[2];
  public char[]                charMoves          = null;
  public char[]                rangeMoves         = null;
  public NfaState              next               = null;
  public NfaState              stateForCase;
  public Vector<NfaState>      epsilonMoves       = new Vector<>();
  public String                epsilonMovesString;
  private NfaState[]           epsilonMoveArray;

  private final int            id;
  public int                   stateName          = -1;
  public int                   kind               = Integer.MAX_VALUE;
  public int                   lookingFor;
  public int                   usefulEpsilonMoves = 0;
  public int                   inNextOf;
  public int                   lexState;
  public int                   nonAsciiMethod     = -1;
  public int                   kindToPrint        = Integer.MAX_VALUE;
  public boolean               dummy              = false;
  public boolean               isComposite        = false;
  public int[]                 compositeStates    = null;
  public boolean               isFinal            = false;
  public Vector<Integer>       loByteVec;
  public int[]                 nonAsciiMoveIndices;
  private int                  onlyChar           = 0;
  private char                 matchSingleChar;

  private final NfaStateData data;

  public NfaState(NfaStateData data) {
    this.data = data;
    this.id = data.addAllState(this);
    this.lexState = data.getStateIndex();
    this.lookingFor = data.global.getCurrentKind();
    this.loByteVec = new Vector<>();
    this.nonAsciiMoveIndices = new int[0];
  }

  private NfaState CreateClone() {
    NfaState retVal = new NfaState(this.data);

    retVal.isFinal = this.isFinal;
    retVal.kind = this.kind;
    retVal.lookingFor = this.lookingFor;
    retVal.lexState = this.lexState;
    retVal.inNextOf = this.inNextOf;

    retVal.MergeMoves(this);

    return retVal;
  }

  private static void InsertInOrder(List<NfaState> v, NfaState s) {
    int j;

    for (j = 0; j < v.size(); j++) {
      if (v.get(j).id > s.id) {
        break;
      } else if (v.get(j).id == s.id) {
        return;
      }
    }

    v.add(j, s);
  }

  private static char[] ExpandCharArr(char[] oldArr, int incr) {
    char[] ret = new char[oldArr.length + incr];
    System.arraycopy(oldArr, 0, ret, 0, oldArr.length);
    return ret;
  }

  public void AddMove(NfaState newState) {
    if (!this.epsilonMoves.contains(newState)) {
      NfaState.InsertInOrder(this.epsilonMoves, newState);
    }
  }

  private final void AddASCIIMove(char c) {
    this.asciiMoves[c / 64] |= (1L << (c % 64));
  }

  public void AddChar(char c) {
    this.onlyChar++;
    this.matchSingleChar = c;
    int i;
    char temp;
    char temp1;

    if (c < 128) // ASCII char
    {
      AddASCIIMove(c);
      return;
    }

    if (this.charMoves == null) {
      this.charMoves = new char[10];
    }

    int len = this.charMoves.length;

    if (this.charMoves[len - 1] != 0) {
      this.charMoves = NfaState.ExpandCharArr(this.charMoves, 10);
      len += 10;
    }

    for (i = 0; i < len; i++) {
      if ((this.charMoves[i] == 0) || (this.charMoves[i] > c)) {
        break;
      }
    }

    temp = this.charMoves[i];
    this.charMoves[i] = c;

    for (i++; i < len; i++) {
      if (temp == 0) {
        break;
      }

      temp1 = this.charMoves[i];
      this.charMoves[i] = temp;
      temp = temp1;
    }
  }

  final void AddRange(char left, char right) {
    this.onlyChar = 2;
    int i;
    char tempLeft1, tempLeft2, tempRight1, tempRight2;

    if (left < 128) {
      if (right < 128) {
        for (; left <= right; left++) {
          AddASCIIMove(left);
        }

        return;
      }

      for (; left < 128; left++) {
        AddASCIIMove(left);
      }
    }

    if (this.rangeMoves == null) {
      this.rangeMoves = new char[20];
    }

    int len = this.rangeMoves.length;

    if (this.rangeMoves[len - 1] != 0) {
      this.rangeMoves = NfaState.ExpandCharArr(this.rangeMoves, 20);
      len += 20;
    }

    for (i = 0; i < len; i += 2) {
      if ((this.rangeMoves[i] == 0) || (this.rangeMoves[i] > left)
          || ((this.rangeMoves[i] == left) && (this.rangeMoves[i + 1] > right))) {
        break;
      }
    }

    tempLeft1 = this.rangeMoves[i];
    tempRight1 = this.rangeMoves[i + 1];
    this.rangeMoves[i] = left;
    this.rangeMoves[i + 1] = right;

    for (i += 2; i < len; i += 2) {
      if (tempLeft1 == 0) {
        break;
      }

      tempLeft2 = this.rangeMoves[i];
      tempRight2 = this.rangeMoves[i + 1];
      this.rangeMoves[i] = tempLeft1;
      this.rangeMoves[i + 1] = tempRight1;
      tempLeft1 = tempLeft2;
      tempRight1 = tempRight2;
    }
  }

  // From hereon down all the functions are used for code generation

  private static boolean EqualCharArr(char[] arr1, char[] arr2) {
    if (arr1 == arr2) {
      return true;
    }

    if ((arr1 != null) && (arr2 != null) && (arr1.length == arr2.length)) {
      for (int i = arr1.length; i-- > 0;) {
        if (arr1[i] != arr2[i]) {
          return false;
        }
      }

      return true;
    }

    return false;
  }

  private boolean closureDone = false;

  /**
   * This function computes the closure and also updates the kind so that any time there is a move
   * to this state, it can go on epsilon to a new state in the epsilon moves that might have a lower
   * kind of token number for the same length.
   */

  private void EpsilonClosure(NfaStateData data) {
    int i = 0;

    if (this.closureDone || data.mark[this.id]) {
      return;
    }

    data.mark[this.id] = true;

    // Recursively do closure
    for (i = 0; i < this.epsilonMoves.size(); i++) {
      this.epsilonMoves.get(i).EpsilonClosure(data);
    }

    Enumeration<NfaState> e = this.epsilonMoves.elements();

    while (e.hasMoreElements()) {
      NfaState tmp = e.nextElement();

      for (i = 0; i < tmp.epsilonMoves.size(); i++) {
        NfaState tmp1 = tmp.epsilonMoves.get(i);
        if (tmp1.UsefulState() && !this.epsilonMoves.contains(tmp1)) {
          NfaState.InsertInOrder(this.epsilonMoves, tmp1);
          data.done = false;
        }
      }

      if (this.kind > tmp.kind) {
        this.kind = tmp.kind;
      }
    }

    if (HasTransitions() && !this.epsilonMoves.contains(this)) {
      NfaState.InsertInOrder(this.epsilonMoves, this);
    }
  }

  private boolean UsefulState() {
    return this.isFinal || HasTransitions();
  }

  public boolean HasTransitions() {
    return ((this.asciiMoves[0] != 0L) || (this.asciiMoves[1] != 0L)
        || ((this.charMoves != null) && (this.charMoves[0] != 0))
        || ((this.rangeMoves != null) && (this.rangeMoves[0] != 0)));
  }

  private void MergeMoves(NfaState other) {
    // Warning : This function does not merge epsilon moves
    if (this.asciiMoves == other.asciiMoves) {
      JavaCCErrors.semantic_error(
          "Bug in JavaCC : Please send " + "a report along with the input that caused this. Thank you.");
      throw new Error();
    }

    this.asciiMoves[0] = this.asciiMoves[0] | other.asciiMoves[0];
    this.asciiMoves[1] = this.asciiMoves[1] | other.asciiMoves[1];

    if (other.charMoves != null) {
      if (this.charMoves == null) {
        this.charMoves = other.charMoves;
      } else {
        char[] tmpCharMoves = new char[this.charMoves.length + other.charMoves.length];
        System.arraycopy(this.charMoves, 0, tmpCharMoves, 0, this.charMoves.length);
        this.charMoves = tmpCharMoves;

        for (char element : other.charMoves) {
          AddChar(element);
        }
      }
    }

    if (other.rangeMoves != null) {
      if (this.rangeMoves == null) {
        this.rangeMoves = other.rangeMoves;
      } else {
        char[] tmpRangeMoves = new char[this.rangeMoves.length + other.rangeMoves.length];
        System.arraycopy(this.rangeMoves, 0, tmpRangeMoves, 0, this.rangeMoves.length);
        this.rangeMoves = tmpRangeMoves;
        for (int i = 0; i < other.rangeMoves.length; i += 2) {
          AddRange(other.rangeMoves[i], other.rangeMoves[i + 1]);
        }
      }
    }

    if (other.kind < this.kind) {
      this.kind = other.kind;
    }

    if (other.kindToPrint < this.kindToPrint) {
      this.kindToPrint = other.kindToPrint;
    }

    this.isFinal |= other.isFinal;
  }

  private NfaState CreateEquivState(List<NfaState> states) {
    NfaState newState = states.get(0).CreateClone();

    newState.next = new NfaState(this.data);

    NfaState.InsertInOrder(newState.next.epsilonMoves, states.get(0).next);

    for (int i = 1; i < states.size(); i++) {
      NfaState tmp2 = (states.get(i));

      if (tmp2.kind < newState.kind) {
        newState.kind = tmp2.kind;
      }

      newState.isFinal |= tmp2.isFinal;

      NfaState.InsertInOrder(newState.next.epsilonMoves, tmp2.next);
    }

    return newState;
  }

  private NfaState GetEquivalentRunTimeState(NfaStateData data) {
    Outer:
    for (int i = data.getAllStateCount(); i-- > 0;) {
      NfaState other = data.getAllState(i);

      if ((this != other) && (other.stateName != -1) && (this.kindToPrint == other.kindToPrint)
          && (this.asciiMoves[0] == other.asciiMoves[0]) && (this.asciiMoves[1] == other.asciiMoves[1])
          && NfaState.EqualCharArr(this.charMoves, other.charMoves)
          && NfaState.EqualCharArr(this.rangeMoves, other.rangeMoves)) {
        if (this.next == other.next) {
          return other;
        } else if (((this.next != null) && (other.next != null))
            && (this.next.epsilonMoves.size() == other.next.epsilonMoves.size())) {
          for (int j = 0; j < this.next.epsilonMoves.size(); j++) {
            if (this.next.epsilonMoves.get(j) != other.next.epsilonMoves.get(j)) {
              continue Outer;
            }
          }

          return other;
        }
      }
    }

    return null;
  }

  // generates code (without outputting it) and returns the name used.
  public void GenerateCode() {
    if (this.stateName != -1) {
      return;
    }

    if (this.next != null) {
      this.next.GenerateCode();
      if (this.next.kind != Integer.MAX_VALUE) {
        this.kindToPrint = this.next.kind;
      }
    }

    if ((this.stateName == -1) && HasTransitions()) {
      NfaState tmp = GetEquivalentRunTimeState(this.data);

      if (tmp != null) {
        this.stateName = tmp.stateName;
        // ????
        // tmp.inNextOf += inNextOf;
        // ????
        this.dummy = true;
        return;
      }

      this.stateName = this.data.addIndexedState(this);
      GenerateNextStatesCode();
    }
  }

  public static void ComputeClosures(NfaStateData data) {
    for (int i = data.getAllStateCount(); i-- > 0;) {
      NfaState tmp = data.getAllState(i);

      if (!tmp.closureDone) {
        tmp.OptimizeEpsilonMoves(data, true);
      }
    }

    for (int index = 0; index < data.getAllStateCount(); index++) {
      NfaState tmp = data.getAllState(index);
      if (!tmp.closureDone) {
        tmp.OptimizeEpsilonMoves(data, false);
      }
    }

    for (NfaState element : data.getAllStates()) {
      NfaState tmp = element;
      tmp.epsilonMoveArray = new NfaState[tmp.epsilonMoves.size()];
      tmp.epsilonMoves.copyInto(tmp.epsilonMoveArray);
    }
  }

  private void OptimizeEpsilonMoves(NfaStateData data, boolean optReqd) {
    int i;

    // First do epsilon closure
    data.done = false;
    while (!data.done) {
      if ((data.mark == null) || (data.mark.length < data.getAllStateCount())) {
        data.mark = new boolean[data.getAllStateCount()];
      }

      for (i = data.getAllStateCount(); i-- > 0;) {
        data.mark[i] = false;
      }

      data.done = true;
      EpsilonClosure(data);
    }

    for (i = data.getAllStateCount(); i-- > 0;) {
      data.getAllState(i).closureDone = data.mark[data.getAllState(i).id];
    }

    // Warning : The following piece of code is just an optimization.
    // in case of trouble, just remove this piece.

    boolean sometingOptimized = true;

    NfaState newState = null;
    NfaState tmp1, tmp2;
    int j;
    List<NfaState> equivStates = null;

    while (sometingOptimized) {
      sometingOptimized = false;
      for (i = 0; optReqd && (i < this.epsilonMoves.size()); i++) {
        if ((tmp1 = this.epsilonMoves.get(i)).HasTransitions()) {
          for (j = i + 1; j < this.epsilonMoves.size(); j++) {
            if ((tmp2 = this.epsilonMoves.get(j)).HasTransitions() && ((tmp1.asciiMoves[0] == tmp2.asciiMoves[0])
                && (tmp1.asciiMoves[1] == tmp2.asciiMoves[1]) && NfaState.EqualCharArr(tmp1.charMoves, tmp2.charMoves)
                && NfaState.EqualCharArr(tmp1.rangeMoves, tmp2.rangeMoves))) {
              if (equivStates == null) {
                equivStates = new ArrayList<>();
                equivStates.add(tmp1);
              }

              NfaState.InsertInOrder(equivStates, tmp2);
              this.epsilonMoves.removeElementAt(j--);
            }
          }
        }

        if (equivStates != null) {
          sometingOptimized = true;
          String tmp = "";
          for (NfaState equivState : equivStates) {
            tmp += String.valueOf(equivState.id) + ", ";
          }

          if ((newState = data.equivStatesTable.get(tmp)) == null) {
            newState = CreateEquivState(equivStates);
            data.equivStatesTable.put(tmp, newState);
          }

          this.epsilonMoves.removeElementAt(i--);
          this.epsilonMoves.add(newState);
          equivStates = null;
          newState = null;
        }
      }

      for (i = 0; i < this.epsilonMoves.size(); i++) {
        // if ((tmp1 = (NfaState)epsilonMoves.elementAt(i)).next == null)
        // continue;
        tmp1 = this.epsilonMoves.get(i);

        for (j = i + 1; j < this.epsilonMoves.size(); j++) {
          tmp2 = this.epsilonMoves.get(j);

          if (tmp1.next == tmp2.next) {
            if (newState == null) {
              newState = tmp1.CreateClone();
              newState.next = tmp1.next;
              sometingOptimized = true;
            }

            newState.MergeMoves(tmp2);
            this.epsilonMoves.removeElementAt(j--);
          }
        }

        if (newState != null) {
          this.epsilonMoves.removeElementAt(i--);
          this.epsilonMoves.add(newState);
          newState = null;
        }
      }
    }

    // End Warning

    // Generate an array of states for epsilon moves (not vector)
    if (this.epsilonMoves.size() > 0) {
      for (i = 0; i < this.epsilonMoves.size(); i++) {
        // Since we are doing a closure, just epsilon moves are unnecessary
        if (this.epsilonMoves.get(i).HasTransitions()) {
          this.usefulEpsilonMoves++;
        } else {
          this.epsilonMoves.removeElementAt(i--);
        }
      }
    }
  }

  private void GenerateNextStatesCode() {
    if (this.next.usefulEpsilonMoves > 0) {
      this.next.GetEpsilonMovesString();
    }
  }

  public String GetEpsilonMovesString() {
    int[] stateNames = new int[this.usefulEpsilonMoves];
    int cnt = 0;

    if (this.epsilonMovesString != null) {
      return this.epsilonMovesString;
    }

    if (this.usefulEpsilonMoves > 0) {
      NfaState tempState;
      this.epsilonMovesString = "{ ";
      for (NfaState element : this.epsilonMoves) {
        if ((tempState = element).HasTransitions()) {
          if (tempState.stateName == -1) {
            tempState.GenerateCode();
          }

          this.data.getIndexedState(tempState.stateName).inNextOf++;
          stateNames[cnt] = tempState.stateName;
          this.epsilonMovesString += tempState.stateName + ", ";
          if ((cnt++ > 0) && ((cnt % 16) == 0)) {
            this.epsilonMovesString += "\n";
          }
        }
      }

      this.epsilonMovesString += "};";
    }

    this.usefulEpsilonMoves = cnt;
    if ((this.epsilonMovesString != null) && (this.data.getNextStates(this.epsilonMovesString) == null)) {
      int[] statesToPut = new int[this.usefulEpsilonMoves];

      System.arraycopy(stateNames, 0, statesToPut, 0, cnt);
      this.data.setNextStates(this.epsilonMovesString, statesToPut);
    }

    return this.epsilonMovesString;
  }

  private final boolean CanMoveUsingChar(char c) {
    int i;

    if (this.onlyChar == 1) {
      return c == this.matchSingleChar;
    }

    if (c < 128) {
      return ((this.asciiMoves[c / 64] & (1L << (c % 64))) != 0L);
    }

    // Just check directly if there is a move for this char
    if ((this.charMoves != null) && (this.charMoves[0] != 0)) {
      for (i = 0; i < this.charMoves.length; i++) {
        if (c == this.charMoves[i]) {
          return true;
        } else if ((c < this.charMoves[i]) || (this.charMoves[i] == 0)) {
          break;
        }
      }
    }


    // For ranges, iterate through the table to see if the current char
    // is in some range
    if ((this.rangeMoves != null) && (this.rangeMoves[0] != 0)) {
      for (i = 0; i < this.rangeMoves.length; i += 2) {
        if ((c >= this.rangeMoves[i]) && (c <= this.rangeMoves[i + 1])) {
          return true;
        } else if ((c < this.rangeMoves[i]) || (this.rangeMoves[i] == 0)) {
          break;
        }
      }
    }

    // return (nextForNegatedList != null);
    return false;
  }


  private int MoveFrom(char c, List<NfaState> newStates) {
    if (CanMoveUsingChar(c)) {
      for (int i = this.next.epsilonMoves.size(); i-- > 0;) {
        NfaState.InsertInOrder(newStates, this.next.epsilonMoves.get(i));
      }

      return this.kindToPrint;
    }

    return Integer.MAX_VALUE;
  }

  public static int MoveFromSet(char c, List<NfaState> states, List<NfaState> newStates) {
    int tmp;
    int retVal = Integer.MAX_VALUE;

    for (int i = states.size(); i-- > 0;) {
      if (retVal > (tmp = states.get(i).MoveFrom(c, newStates))) {
        retVal = tmp;
      }
    }

    return retVal;
  }


  public static boolean EqualLoByteVectors(List<Integer> vec1, List<Integer> vec2) {
    if ((vec1 == null) || (vec2 == null)) {
      return false;
    }

    if (vec1 == vec2) {
      return true;
    }

    if (vec1.size() != vec2.size()) {
      return false;
    }

    for (int i = 0; i < vec1.size(); i++) {
      if (vec1.get(i).intValue() != vec2.get(i).intValue()) {
        return false;
      }
    }

    return true;
  }

  public static boolean EqualNonAsciiMoveIndices(int[] moves1, int[] moves2) {
    if (moves1 == moves2) {
      return true;
    }

    if ((moves1 == null) || (moves2 == null)) {
      return false;
    }

    if (moves1.length != moves2.length) {
      return false;
    }

    for (int i = 0; i < moves1.length; i++) {
      if (moves1[i] != moves2[i]) {
        return false;
      }
    }

    return true;
  }


  public static int[] GetStateSetIndicesForUse(NfaStateData data, String arrayString) {
    int[] ret;
    int[] set = data.getNextStates(arrayString);

    if ((ret = data.global.tableToDump.get(arrayString)) == null) {
      ret = new int[2];
      ret[0] = data.global.lastIndex;
      ret[1] = (data.global.lastIndex + set.length) - 1;
      data.global.lastIndex += set.length;
      data.global.tableToDump.put(arrayString, ret);
      data.global.orderedStateSet.add(set);
    }

    return ret;
  }

  public static int OnlyOneBitSet(long l) {
    int oneSeen = -1;
    for (int i = 0; i < 64; i++) {
      if (((l >> i) & 1L) != 0L) {
        if (oneSeen >= 0) {
          return -1;
        }
        oneSeen = i;
      }
    }

    return oneSeen;
  }

  public static int ElemOccurs(int elem, int[] arr) {
    for (int i = arr.length; i-- > 0;) {
      if (arr[i] == elem) {
        return i;
      }
    }

    return -1;
  }

  public final void FixNextStates(int[] newSet) {
    this.next.usefulEpsilonMoves = newSet.length;
    // next.epsilonMovesString = GetStateSetString(newSet);
  }

  public static boolean Intersect(NfaStateData data, String set1, String set2) {
    if ((set1 == null) || (set2 == null)) {
      return false;
    }

    int[] nameSet1 = data.getNextStates(set1);
    int[] nameSet2 = data.getNextStates(set2);

    if ((nameSet1 == null) || (nameSet2 == null)) {
      return false;
    }

    if (nameSet1 == nameSet2) {
      return true;
    }

    for (int i = nameSet1.length; i-- > 0;) {
      for (int j = nameSet2.length; j-- > 0;) {
        if (nameSet1[i] == nameSet2[j]) {
          return true;
        }
      }
    }

    return false;
  }

  public boolean selfLoop() {
    if ((this.next == null) || (this.next.epsilonMovesString == null)) {
      return false;
    }

    int[] set = this.data.getNextStates(this.next.epsilonMovesString);
    return NfaState.ElemOccurs(this.stateName, set) >= 0;
  }
}
