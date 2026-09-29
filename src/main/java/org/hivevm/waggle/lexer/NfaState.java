// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/NfaState.java

package org.hivevm.waggle.lexer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The state of a Non-deterministic Finite Automaton.
 */
class NfaState {

    public final long[] asciiMoves = new long[2];
    char[] charMoves = null;
    char[] rangeMoves = null;
    public NfaState next = null;
    final List<NfaState> epsilonMoves = new ArrayList<>();
    public String epsilonMovesString;

    private final int id;
    public int stateName = -1;
    int kind = Integer.MAX_VALUE;
    /** Package-private: nothing outside the lexer stage reads it. */
    int lookingFor;
    public int usefulEpsilonMoves = 0;
    public int inNextOf;
    public int nonAsciiMethod = -1;
    public int kindToPrint = Integer.MAX_VALUE;
    public boolean isComposite = false;
    int[] compositeStates = null;
    boolean isFinal = false;
    public final List<Integer> loByteVec;
    public int[] nonAsciiMoveIndices;
    private int onlyChar = 0;
    private char matchSingleChar;

    private final NfaStateData data;

    NfaState(NfaStateData data) {
        this.data = data;
        this.id = data.addAllState(this);
        this.lookingFor = data.global.getCurrentKind();
        this.loByteVec = new ArrayList<>();
        this.nonAsciiMoveIndices = new int[0];
    }

    private NfaState CreateClone() {
        var retVal = new NfaState(this.data);
        retVal.isFinal = this.isFinal;
        retVal.kind = this.kind;
        retVal.lookingFor = this.lookingFor;
        retVal.inNextOf = this.inNextOf;
        retVal.MergeMoves(this);
        return retVal;
    }

    /** Inserts {@code s} by id unless it is there already; ids are unique within a lexical state. */
    private static boolean InsertInOrder(List<NfaState> v, NfaState s) {
        int j;

        for (j = 0; j < v.size(); j++) {
            if (v.get(j).id > s.id)
                break;
            else if (v.get(j).id == s.id)
                return false;
        }

        v.add(j, s);
        return true;
    }

    void AddMove(NfaState newState) {
        // InsertInOrder already returns early on a duplicate id, so no separate contains() scan.
        NfaState.InsertInOrder(this.epsilonMoves, newState);
    }

    private void AddASCIIMove(char c) {
        Bits.set(this.asciiMoves, c);
    }

    void AddChar(char c) {
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

        if (this.charMoves == null)
            this.charMoves = new char[10];

        int len = this.charMoves.length;

        if (this.charMoves[len - 1] != 0) {
            this.charMoves = Arrays.copyOf(this.charMoves, this.charMoves.length + 10);
            len += 10;
        }

        for (i = 0; i < len; i++) {
            if ((this.charMoves[i] == 0) || (this.charMoves[i] > c))
                break;
        }

        temp = this.charMoves[i];
        this.charMoves[i] = c;

        for (i++; i < len; i++) {
            if (temp == 0)
                break;

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

        if (this.rangeMoves == null)
            this.rangeMoves = new char[20];

        int len = this.rangeMoves.length;

        if (this.rangeMoves[len - 1] != 0) {
            this.rangeMoves = Arrays.copyOf(this.rangeMoves, this.rangeMoves.length + 20);
            len += 20;
        }

        for (i = 0; i < len; i += 2) {
            if ((this.rangeMoves[i] == 0) || (this.rangeMoves[i] > left)
                    || ((this.rangeMoves[i] == left) && (this.rangeMoves[i + 1] > right)))
                break;
        }

        tempLeft1 = this.rangeMoves[i];
        tempRight1 = this.rangeMoves[i + 1];
        this.rangeMoves[i] = left;
        this.rangeMoves[i + 1] = right;

        for (i += 2; i < len; i += 2) {
            if (tempLeft1 == 0)
                break;

            tempLeft2 = this.rangeMoves[i];
            tempRight2 = this.rangeMoves[i + 1];
            this.rangeMoves[i] = tempLeft1;
            this.rangeMoves[i + 1] = tempRight1;
            tempLeft1 = tempLeft2;
            tempRight1 = tempRight2;
        }
    }

    // From hereon down all the functions are used for code generation

    private boolean closureDone = false;

    /**
     * This function computes the closure and also updates the kind so that any time there is a move
     * to this state, it can go on epsilon to a new state in the epsilon moves that might have a
     * lower kind of token number for the same length. Returns whether an epsilon move was added,
     * in which case the closure has to be computed again.
     *
     * @param mark the states this pass has visited, by id
     */
    private boolean EpsilonClosure(boolean[] mark) {
        int i;

        if (this.closureDone || mark[this.id])
            return false;

        mark[this.id] = true;

        // Recursively do closure
        boolean grown = false;
        for (i = 0; i < this.epsilonMoves.size(); i++) {
            grown |= this.epsilonMoves.get(i).EpsilonClosure(mark);
        }

        // Indexed, not iterated: InsertInOrder below grows the very list being walked, which a
        // for-each would answer with a ConcurrentModificationException.
        for (int k = 0; k < this.epsilonMoves.size(); k++) {
            NfaState tmp = this.epsilonMoves.get(k);

            for (i = 0; i < tmp.epsilonMoves.size(); i++) {
                NfaState tmp1 = tmp.epsilonMoves.get(i);
                if (tmp1.UsefulState() && NfaState.InsertInOrder(this.epsilonMoves, tmp1)) {
                    grown = true;
                }
            }

            if (this.kind > tmp.kind)
                this.kind = tmp.kind;
        }

        if (HasTransitions())
            NfaState.InsertInOrder(this.epsilonMoves, this);
        return grown;
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
            throw new IllegalStateException(
                    "Cannot merge an NFA state with itself: both share the same asciiMoves");
        }

        this.asciiMoves[0] = this.asciiMoves[0] | other.asciiMoves[0];
        this.asciiMoves[1] = this.asciiMoves[1] | other.asciiMoves[1];

        if (other.charMoves != null) {
            if (this.charMoves == null)
                this.charMoves = other.charMoves;
            else {
                char[] tmpCharMoves = new char[this.charMoves.length + other.charMoves.length];
                System.arraycopy(this.charMoves, 0, tmpCharMoves, 0, this.charMoves.length);
                this.charMoves = tmpCharMoves;

                for (char element : other.charMoves) {
                    AddChar(element);
                }
            }
        }

        if (other.rangeMoves != null) {
            if (this.rangeMoves == null)
                this.rangeMoves = other.rangeMoves;
            else {
                char[] tmpRangeMoves = new char[this.rangeMoves.length + other.rangeMoves.length];
                System.arraycopy(this.rangeMoves, 0, tmpRangeMoves, 0, this.rangeMoves.length);
                this.rangeMoves = tmpRangeMoves;
                for (int i = 0; i < other.rangeMoves.length; i += 2) {
                    AddRange(other.rangeMoves[i], other.rangeMoves[i + 1]);
                }
            }
        }

        if (other.kind < this.kind)
            this.kind = other.kind;

        if (other.kindToPrint < this.kindToPrint)
            this.kindToPrint = other.kindToPrint;

        this.isFinal |= other.isFinal;
    }

    private NfaState CreateEquivState(List<NfaState> states) {
        NfaState newState = states.getFirst().CreateClone();

        newState.next = new NfaState(this.data);

        NfaState.InsertInOrder(newState.next.epsilonMoves, states.getFirst().next);

        for (int i = 1; i < states.size(); i++) {
            NfaState tmp2 = (states.get(i));

            if (tmp2.kind < newState.kind)
                newState.kind = tmp2.kind;

            newState.isFinal |= tmp2.isFinal;
            NfaState.InsertInOrder(newState.next.epsilonMoves, tmp2.next);
        }
        return newState;
    }

    private NfaState GetEquivalentRunTimeState() {
        for (int i = this.data.getAllStateCount(); i-- > 0; ) {
            NfaState other = this.data.getAllState(i);

            if ((this != other) && (other.stateName != -1) && (this.kindToPrint == other.kindToPrint)
                    && sameMoves(other) && ((this.next == other.next)
                    || ((this.next != null) && (other.next != null)
                    && this.next.epsilonMoves.equals(other.next.epsilonMoves)))) {
                return other;
            }
        }
        return null;
    }

    /** Whether the two states move on the same characters. */
    private boolean sameMoves(NfaState other) {
        return (this.asciiMoves[0] == other.asciiMoves[0])
                && (this.asciiMoves[1] == other.asciiMoves[1])
                && Arrays.equals(this.charMoves, other.charMoves)
                && Arrays.equals(this.rangeMoves, other.rangeMoves);
    }

    // generates code (without outputting it) and returns the name used.
    void GenerateCode() {
        if (this.stateName != -1)
            return;

        if (this.next != null) {
            this.next.GenerateCode();
            if (this.next.kind != Integer.MAX_VALUE)
                this.kindToPrint = this.next.kind;
        }

        if ((this.stateName == -1) && HasTransitions()) {
            NfaState tmp = GetEquivalentRunTimeState();

            if (tmp != null) {
                this.stateName = tmp.stateName;
                return;
            }

            this.stateName = this.data.addIndexedState(this);
            if (this.next.usefulEpsilonMoves > 0)
                this.next.GetEpsilonMovesString();
        }
    }

    static void ComputeClosures(NfaStateData data) {
        for (int i = data.getAllStateCount(); i-- > 0; ) {
            NfaState tmp = data.getAllState(i);

            if (!tmp.closureDone)
                tmp.OptimizeEpsilonMoves(true);
        }

        for (int index = 0; index < data.getAllStateCount(); index++) {
            NfaState tmp = data.getAllState(index);
            if (!tmp.closureDone)
                tmp.OptimizeEpsilonMoves(false);
        }
    }

    private void OptimizeEpsilonMoves(boolean optReqd) {
        int i;

        // First do epsilon closure
        boolean[] mark;
        do {
            mark = new boolean[this.data.getAllStateCount()];
        } while (EpsilonClosure(mark));

        for (i = this.data.getAllStateCount(); i-- > 0; ) {
            this.data.getAllState(i).closureDone = mark[this.data.getAllState(i).id];
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
                        if ((tmp2 = this.epsilonMoves.get(j)).HasTransitions()
                                && tmp1.sameMoves(tmp2)) {
                            if (equivStates == null) {
                                equivStates = new ArrayList<>();
                                equivStates.add(tmp1);
                            }

                            NfaState.InsertInOrder(equivStates, tmp2);
                            this.epsilonMoves.remove(j--);
                        }
                    }
                }

                if (equivStates != null) {
                    sometingOptimized = true;
                    var sb = new StringBuilder();
                    for (NfaState equivState : equivStates) {
                        sb.append(equivState.id).append(", ");
                    }
                    String tmp = sb.toString();

                    if ((newState = this.data.equivStatesTable.get(tmp)) == null) {
                        newState = CreateEquivState(equivStates);
                        this.data.equivStatesTable.put(tmp, newState);
                    }

                    this.epsilonMoves.remove(i--);
                    this.epsilonMoves.add(newState);
                    equivStates = null;
                    newState = null;
                }
            }

            for (i = 0; i < this.epsilonMoves.size(); i++) {
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
                        this.epsilonMoves.remove(j--);
                    }
                }

                if (newState != null) {
                    this.epsilonMoves.remove(i--);
                    this.epsilonMoves.add(newState);
                    newState = null;
                }
            }
        }

        // End Warning

        // Generate an array of states for epsilon moves (not vector)
        if (!this.epsilonMoves.isEmpty()) {
            for (i = 0; i < this.epsilonMoves.size(); i++) {
                // Since we are doing a closure, just epsilon moves are unnecessary
                if (this.epsilonMoves.get(i).HasTransitions()) {
                    this.usefulEpsilonMoves++;
                } else {
                    this.epsilonMoves.remove(i--);
                }
            }
        }
    }

    String GetEpsilonMovesString() {
        if (this.epsilonMovesString != null)
            return this.epsilonMovesString;

        int[] stateNames = new int[this.usefulEpsilonMoves];
        int cnt = 0;

        if (this.usefulEpsilonMoves > 0) {
            for (NfaState element : this.epsilonMoves) {
                if (element.HasTransitions()) {
                    if (element.stateName == -1)
                        element.GenerateCode();

                    this.data.getIndexedState(element.stateName).inNextOf++;
                    stateNames[cnt++] = element.stateName;
                }
            }
            this.epsilonMovesString = NfaState.stateSetKey(Arrays.copyOf(stateNames, cnt));
        }

        this.usefulEpsilonMoves = cnt;
        if ((this.epsilonMovesString != null)
                && (this.data.getNextStates(this.epsilonMovesString) == null)) {
            this.data.setNextStates(this.epsilonMovesString, Arrays.copyOf(stateNames, cnt));
        }

        return this.epsilonMovesString;
    }

    /**
     * The key a set of state names is known by: {@code "{ a, b, };"}, with a line break after every
     * sixteenth name. The key orders hash tables whose order reaches the generated lexer, so its
     * format must not change.
     */
    static String stateSetKey(int[] stateNames) {
        var sb = new StringBuilder("{ ");
        for (int i = 0; i < stateNames.length; i++) {
            sb.append(stateNames[i]).append(", ");
            if (((i + 1) % 16) == 0)
                sb.append("\n");
        }
        return sb.append("};").toString();
    }

    private boolean CanMoveUsingChar(char c) {
        int i;

        if (this.onlyChar == 1)
            return c == this.matchSingleChar;

        if (c < 128)
            return Bits.test(this.asciiMoves, c);

        // Just check directly if there is a move for this char
        if ((this.charMoves != null) && (this.charMoves[0] != 0)) {
            for (i = 0; i < this.charMoves.length; i++) {
                if (c == this.charMoves[i])
                    return true;
                else if ((c < this.charMoves[i]) || (this.charMoves[i] == 0))
                    break;
            }
        }

        // For ranges, iterate through the table to see if the current char
        // is in some range
        if ((this.rangeMoves != null) && (this.rangeMoves[0] != 0)) {
            for (i = 0; i < this.rangeMoves.length; i += 2) {
                if ((c >= this.rangeMoves[i]) && (c <= this.rangeMoves[i + 1]))
                    return true;
                else if ((c < this.rangeMoves[i]) || (this.rangeMoves[i] == 0))
                    break;
            }
        }

        return false;
    }

    private int MoveFrom(char c, List<NfaState> newStates) {
        if (CanMoveUsingChar(c)) {
            for (int i = this.next.epsilonMoves.size(); i-- > 0; ) {
                NfaState.InsertInOrder(newStates, this.next.epsilonMoves.get(i));
            }
            return this.kindToPrint;
        }
        return Integer.MAX_VALUE;
    }

    static int MoveFromSet(char c, List<NfaState> states, List<NfaState> newStates) {
        int tmp;
        int retVal = Integer.MAX_VALUE;

        for (int i = states.size(); i-- > 0; ) {
            if (retVal > (tmp = states.get(i).MoveFrom(c, newStates)))
                retVal = tmp;
        }
        return retVal;
    }

    static int[] GetStateSetIndicesForUse(NfaStateData data, String arrayString) {
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

    /** The single ASCII move of {@code byteNum}, or -1 when there is not exactly one. */
    public final int onlyOneAsciiMove(int byteNum) {
        long l = this.asciiMoves[byteNum];
        return (Long.bitCount(l) == 1) ? Long.numberOfTrailingZeros(l) : -1;
    }

    static boolean contains(int[] arr, int elem) {
        for (int e : arr) {
            if (e == elem)
                return true;
        }
        return false;
    }

    static boolean Intersect(NfaStateData data, String set1, String set2) {
        if ((set1 == null) || (set2 == null))
            return false;

        int[] nameSet1 = data.getNextStates(set1);
        int[] nameSet2 = data.getNextStates(set2);

        if ((nameSet1 == null) || (nameSet2 == null))
            return false;

        if (nameSet1 == nameSet2)
            return true;

        for (int i = nameSet1.length; i-- > 0; ) {
            for (int j = nameSet2.length; j-- > 0; ) {
                if (nameSet1[i] == nameSet2[j])
                    return true;
            }
        }
        return false;
    }

    public boolean selfLoop() {
        if ((this.next == null) || (this.next.epsilonMovesString == null))
            return false;
        int[] set = this.data.getNextStates(this.next.epsilonMovesString);
        return NfaState.contains(set, this.stateName);
    }
}
