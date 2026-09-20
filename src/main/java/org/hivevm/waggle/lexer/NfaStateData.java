// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.lexer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

/**
 * The {@link NfaStateData} class.
 */
public class NfaStateData {

    public final LexerData global;

    private final NfaState initialState;
    private final int lexStateIndex;
    private final String lexStateSuffix;


    // RString
    int maxLen;
    int maxStrKind;
    boolean[] subString;
    boolean[] subStringAtPos;


    final int[] maxLenForActive;
    int[][] intermediateKinds;
    int[][] intermediateMatchedPos;


    public Hashtable<String, long[]>[] statesForPos;
    final List<Hashtable<String, KindInfo>> charPosKind;

    // NfaState
    boolean done;
    boolean[] mark;
    public boolean hasNFA;
    boolean hasMixed;
    boolean createStartNfa;

    private int idCnt;
    private int generatedStates;
    private List<NfaState> allStates;
    private final List<NfaState> indexedAllStates;

    public int dummyStateIndex;
    private final Hashtable<String, int[]> allNextStates;
    public final Hashtable<String, Integer> stateNameForComposite;
    public final Hashtable<String, int[]> compositeStateTable;
    public final Hashtable<String, String> stateBlockTable;
    public final Hashtable<String, int[]> stateSetsToFix;
    final Hashtable<String, NfaState> equivStatesTable;

    // ADR-0012: finished-model DFA lookup. Stage 4 (DfaBuilder#getDfaCode) records the composite
    // state-set name for every (position, kind) it visits, so the stage-5 generators render it
    // without recomputing — or registering — DFA structure at emit time.
    private final Hashtable<Long, Integer> stateSetForPosKind;


    NfaStateData(LexerData data, String name) {
        this.global = data;
        this.lexStateIndex = this.global.getStateIndex(name);
        this.lexStateSuffix = "_" + this.lexStateIndex;

        // RString
        this.maxLen = 0;
        this.maxStrKind = 0;
        this.subString = null;
        this.subStringAtPos = null;
        // Indexed by ordinal / 64 (see StringLiteralAnalyzer), so size it from the token count
        // instead of a fixed 100 ints (which silently overflowed past 6400 token kinds).
        this.maxLenForActive = new int[(this.global.maxOrdinal / 64) + 1];
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
        this.stateSetForPosKind = new Hashtable<>();

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

    public final List<NfaState> cloneAllStates() {
        List<NfaState> v = this.allStates;
        this.allStates = new ArrayList<>(Collections.nCopies(generatedStates(), null));
        return v;
    }

    public final NfaState getIndexedState(int index) {
        return this.indexedAllStates.get(index);
    }

    final int addIndexedState(NfaState state) {
        this.indexedAllStates.add(state);
        return this.generatedStates++;
    }

    public final int getAllStateCount() {
        return this.allStates.size();
    }

    public final NfaState getAllState(int index) {
        return this.allStates.get(index);
    }

    public final void setAllState(int index, NfaState state) {
        this.allStates.set(index, state);
    }

    public final Iterable<NfaState> getAllStates() {
        return this.allStates;
    }

    final int addAllState(NfaState state) {
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

    /**
     * The keys of {@link #getCharPosKind(int)}, ordered by their first character.
     *
     * <p>The order is a property of the finished DFA, so it is settled here rather than recomputed
     * by each back end while it emits (ADR-0012).
     */
    public final String[] getOrderedCharPosKinds(int index) {
        return NfaStateData.reArrange(this.charPosKind.get(index));
    }

    /**
     * Where the state set {@code arrayString} lives in the emitted {@code jjnextStates} table.
     *
     * <p>Registering it is stage 4's job ({@code DfaBuilder}); a back end only looks it up. It used
     * to call the registering method itself while emitting, so the back end could still grow the
     * table it was in the middle of rendering.
     */
    /** Whether the two state sets share a state. A query over the finished DFA. */
    public final boolean intersects(String set1, String set2) {
        return NfaState.Intersect(this, set1, set2);
    }

    /** The position of {@code element} in {@code set}, or -1. */
    public final int positionOf(int element, int[] set) {
        return NfaState.ElemOccurs(element, set);
    }

    public final int[] getStateSetIndices(String arrayString) {
        var indices = this.global.tableToDump.get(arrayString);
        if (indices == null) {
            throw new IllegalStateException(
                    "state set was never registered by the lexer stage: " + arrayString);
        }
        return indices;
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

    /**
     * Whether the string literal of {@code kind} is shadowed at position {@code i} by a shorter
     * literal that is complete there and declared earlier: the DFA then reports that one instead.
     */
    public final boolean isShadowedByIntermediate(int i, int kind) {
        return (this.intermediateKinds != null) && (this.intermediateKinds[kind] != null)
                && (this.intermediateKinds[kind][i] < kind)
                && (this.intermediateMatchedPos != null)
                && (this.intermediateMatchedPos[kind][i] == i);
    }

    /**
     * Whether the string literal of {@code kind} is shadowed on its first character by an earlier
     * catch-all token of this lexical state.
     */
    public final boolean isShadowedByAnyChar(int i, int kind) {
        int anyChar = this.global.canMatchAnyChar(getStateIndex());
        return (i == 0) && (anyChar >= 0) && (anyChar < kind);
    }

    /** The kind the string-literal DFA reports at position {@code i} for the literal of {@code kind}. */
    public final int kindToPrint(int i, int kind) {
        if (isShadowedByIntermediate(i, kind)) {
            return this.intermediateKinds[kind][i];
        }
        if (isShadowedByAnyChar(i, kind)) {
            return this.global.canMatchAnyChar(getStateIndex());
        }
        return kind;
    }

    /**
     * Whether the single character {@code c} at position {@code i} needs no case of its own in the
     * string-literal DFA: it is a plain SKIP — no action, no lexical state change — that the token
     * manager deals with elsewhere.
     */
    public final boolean isPlainSkip(KindInfo info, int i, char c) {
        // Both call sites used to compute "generatedStates == 0 || no ASCII move for c" through
        // helpers of opposite sense; canStartNfaUsingAscii is the positive one.
        if (!((i == 0) && (c < 128) && info.hasFinalKindCnt()
                && ((generatedStates() == 0) || !canStartNfaUsingAscii(c)))) {
            return false;
        }

        int maxLongsReqd = (this.maxStrKind / 64) + 1;
        int j;
        for (j = 0; j < maxLongsReqd; j++) {
            if (info.finalKinds[j] != 0L) {
                break;
            }
        }

        for (int k = 0; k < 64; k++) {
            int kind = (j * 64) + k;
            if (((info.finalKinds[j] & (1L << k)) != 0L) && !isSubString(kind)) {
                if (isShadowedByIntermediate(i, kind) || isShadowedByAnyChar(i, kind)) {
                    return false;
                } else if (((this.global.toSkip(kind / 64) & (1L << (kind % 64))) != 0L)
                        && ((this.global.toSpecial(kind / 64) & (1L << (kind % 64))) == 0L)
                        && (this.global.actions(kind) == null)
                        && (this.global.newLexState(kind) == null)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Records the composite state-set name computed for a {@code (position, kind)} slot during stage
     * 4 (see {@link DfaBuilder#getDfaCode}). Stored once so the generators can render it without
     * recomputing or registering DFA structure (ADR-0012).
     */
    void putStateSetName(int pos, int kind, int stateSetName) {
        this.stateSetForPosKind.put(posKindKey(pos, kind), stateSetName);
    }

    /**
     * Returns the composite state-set name recorded for {@code (pos, kind)} in stage 4, or {@code -1}
     * when none was registered.
     */
    public int getStateSetName(int pos, int kind) {
        Integer stateSetName = this.stateSetForPosKind.get(posKindKey(pos, kind));
        return (stateSetName == null) ? -1 : stateSetName;
    }

    private static long posKindKey(int pos, int kind) {
        return ((long) pos << 32) | (kind & 0xffffffffL);
    }

    /**
     * Whether the NFA can start on the ASCII character {@code c} from this state's initial state.
     * A pure query over the finished DFA model; owned by the lexer layer so stage-5 generators read
     * it instead of recomputing DFA structure (ADR-0012).
     */
    public boolean canStartNfaUsingAscii(char c) {
        if (c >= 128) {
            throw new IllegalStateException(
                    "canStartNfaUsingAscii called with a non-ASCII character: " + (int) c);
        }

        String s = getInitialState().GetEpsilonMovesString();
        if ((s == null) || s.equals("null;")) {
            return false;
        }

        for (int state : getNextStates(s)) {
            NfaState tmp = getIndexedState(state);
            if ((tmp.asciiMoves[c / 64] & (1L << (c % 64))) != 0L) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the keys of {@code tab} ordered by their first character (a stable insertion sort).
     * Stage 4 owns the order; back ends read it through {@link #getOrderedCharPosKinds(int)}
     * (ADR-0012).
     */
    static <T> String[] reArrange(Hashtable<String, T> tab) {
        String[] ret = new String[tab.size()];
        int cnt = 0;

        for (String s : tab.keySet()) {
            int i = 0, j;
            char c = s.charAt(0);

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

    int addCompositeStateSet(String stateSetString) {
        Integer stateNameToReturn;

        if ((stateNameToReturn = this.stateNameForComposite.get(stateSetString)) != null) {
            return stateNameToReturn;
        }

        int toRet = 0;
        int[] nameSet = getNextStates(stateSetString);

        if (nameSet == null) {
            throw new IllegalStateException(
                    "No next states registered for the state set: " + stateSetString);
        }

        if (nameSet.length == 1) {
            stateNameToReturn = nameSet[0];
            this.stateNameForComposite.put(stateSetString, stateNameToReturn);
            return nameSet[0];
        }

        for (int element : nameSet) {
            if (element == -1) {
                continue;
            }
            NfaState st = getIndexedState(element);
            st.isComposite = true;
            st.compositeStates = nameSet;
        }

        while ((toRet < nameSet.length) && (getIndexedState(nameSet[toRet]).inNextOf > 1)) {
            toRet++;
        }

        for (var entry : this.compositeStateTable.entrySet()) {
            String s = entry.getKey();
            if (!s.equals(stateSetString) && NfaState.Intersect(this, stateSetString, s)) {
                int[] other = entry.getValue();
                while ((toRet < nameSet.length) && (
                        (getIndexedState(nameSet[toRet]).inNextOf > 1)
                                || (NfaState.ElemOccurs(nameSet[toRet], other) >= 0))) {
                    toRet++;
                }
            }
        }

        int tmp;
        if (toRet >= nameSet.length) {
            tmp = (this.dummyStateIndex == -1) ? (this.dummyStateIndex = generatedStates())
                    : ++this.dummyStateIndex;
        } else {
            tmp = nameSet[toRet];
        }

        stateNameToReturn = tmp;
        this.stateNameForComposite.put(stateSetString, stateNameToReturn);
        this.compositeStateTable.put(stateSetString, nameSet);
        return tmp;
    }

    public static final class KindInfo {

        public final long[] validKinds;
        public final long[] finalKinds;

        private int validKindCnt = 0;
        private int finalKindCnt = 0;

        KindInfo(int maxKind) {
            this.validKinds = new long[(maxKind / 64) + 1];
            this.finalKinds = new long[(maxKind / 64) + 1];
        }

        void InsertValidKind(int kind) {
            this.validKinds[kind / 64] |= (1L << (kind % 64));
            this.validKindCnt++;
        }

        void InsertFinalKind(int kind) {
            this.finalKinds[kind / 64] |= (1L << (kind % 64));
            this.finalKindCnt++;
        }

        public boolean hasValidKindCnt() {
            return this.validKindCnt != 0;
        }

        public boolean hasFinalKindCnt() {
            return this.finalKindCnt != 0;
        }
    }
}
