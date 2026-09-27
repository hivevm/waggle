// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/RStringLiteral.java, org/javacc/parser/NfaState.java

package org.hivevm.waggle.lexer;

import java.util.BitSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The {@link NfaStateData} class.
 */
public class NfaStateData {

    public final LexerData global;

    private final NfaState initialState;
    private final int lexStateIndex;
    private final String lexStateSuffix;

    int maxLen;
    int maxStrKind;
    boolean[] subString;
    boolean[] subStringAtPos;

    final int[] maxLenForActive;
    int[][] intermediateKinds;
    int[][] intermediateMatchedPos;

    /**
     * Deliberately a {@link Hashtable}: {@code StringLiteralDfaEmitter} walks this table's
     * {@code keySet()} and writes one {@code jjStopStringLiteralDfa} branch per key, so the table's
     * iteration order is the order of those branches in the generated token manager. A
     * {@code LinkedHashMap} here reorders those branches, which is a change to the emitted parser -
     * and to the checked-in bootstrap parser (ADR-0009) - not a cleanup.
     */
    public Hashtable<String, long[]>[] statesForPos;
    /**
     * Per position, the string literals that continue with a character there, keyed by that
     * character. Sorted by it, since the string-literal DFA emits one case per key in this order.
     */
    final List<TreeMap<Character, KindInfo>> charPosKind;

    /**
     * Package-private, not public: the same value was reachable both as this field and through
     * {@link #hasNFA()}, and the two back ends picked different ones. Stage 4 writes it, back ends
     * read it through the method (ADR-0012).
     */
    boolean hasNFA;
    boolean hasMixed;
    boolean createStartNfa;

    private int idCnt;
    private int generatedStates;
    private List<NfaState> allStates;
    private final List<NfaState> indexedAllStates;

    private int dummyStateIndex = -1;
    private final Map<String, int[]> allNextStates;
    final Map<String, Integer> stateNameForComposite;
    public final Map<String, int[]> compositeStateTable;
    final Map<String, NfaState> equivStatesTable;

    // ADR-0012: finished-model DFA lookup. Stage 4 (DfaBuilder#getDfaCode) records the composite
    // state-set name for every (position, kind) it visits, so the stage-5 generators render it
    // without recomputing — or registering — DFA structure at emit time.
    private final Map<Long, Integer> stateSetForPosKind;

    NfaStateData(LexerData data, String name) {
        this.global = data;
        this.lexStateIndex = this.global.getStateIndex(name);
        this.lexStateSuffix = "_" + this.lexStateIndex;

        // Indexed by ordinal / 64 (see StringLiteralAnalyzer), so size it from the token count
        // instead of a fixed 100 ints (which silently overflowed past 6400 token kinds).
        this.maxLenForActive = new int[(this.global.maxOrdinal / 64) + 1];
        this.charPosKind = new ArrayList<>();

        this.allStates = new ArrayList<>();
        this.indexedAllStates = new ArrayList<>();

        this.allNextStates = new LinkedHashMap<>();
        this.stateNameForComposite = new LinkedHashMap<>();
        this.compositeStateTable = new LinkedHashMap<>();
        this.equivStatesTable = new LinkedHashMap<>();
        this.stateSetForPosKind = new LinkedHashMap<>();

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

    /**
     * Makes the states of this lexical state its indexed states, each in the slot of its state
     * name. The others - dummies, and states without transitions that never got a name - are of no
     * further use.
     */
    final void reindexByStateName() {
        this.allStates = new ArrayList<>(this.indexedAllStates);
    }

    /**
     * How many state names there are: the generated states and the names given to composite state
     * sets that no member state can stand for.
     */
    public final int stateNameCount() {
        return Math.max(generatedStates(), this.dummyStateIndex + 1);
    }

    final NfaState getIndexedState(int index) {
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

    final void setNextStates(String name, int[] states) {
        this.allNextStates.put(name, states);
    }

    public final SortedMap<Character, KindInfo> getCharPosKind(int index) {
        return this.charPosKind.get(index);
    }

    /** Whether the two state sets share a state. A query over the finished DFA. */
    public final boolean intersects(String set1, String set2) {
        return NfaState.Intersect(this, set1, set2);
    }

    /**
     * Where the state set {@code arrayString} lives in the emitted {@code jjnextStates} table.
     *
     * <p>Registering it is stage 4's job ({@code DfaBuilder}); a back end only looks it up. It used
     * to call the registering method itself while emitting, so the back end could still grow the
     * table it was in the middle of rendering.
     */
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
    final boolean isShadowedByIntermediate(int i, int kind) {
        return (this.intermediateKinds != null) && (this.intermediateKinds[kind] != null)
                && (this.intermediateKinds[kind][i] < kind)
                && (this.intermediateMatchedPos != null)
                && (this.intermediateMatchedPos[kind][i] == i);
    }

    /**
     * Whether the string literal of {@code kind} is shadowed on its first character by an earlier
     * catch-all token of this lexical state.
     */
    final boolean isShadowedByAnyChar(int i, int kind) {
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
        return plainSkipKind(info, i, c) >= 0;
    }

    /** The kind of the plain SKIP {@link #isPlainSkip} finds, or -1 if there is none. */
    final int plainSkipKind(KindInfo info, int i, char c) {
        if (!((i == 0) && (c < 128) && info.hasFinalKindCnt()
                && ((generatedStates() == 0) || !canStartNfaUsingAscii(c)))) {
            return -1;
        }

        // Only the kinds in the first word that has one are looked at, as JavaCC did.
        int[] kinds = info.finalKindsAscending();
        for (int kind : kinds) {
            if ((kind / 64) != (kinds[0] / 64)) {
                break;
            }
            if (!isSubString(kind)) {
                if (isShadowedByIntermediate(i, kind) || isShadowedByAnyChar(i, kind)) {
                    return -1;
                } else if (this.global.isSkip(kind) && !this.global.isSpecial(kind)
                        && (this.global.actions(kind) == null)
                        && (this.global.newLexState(kind) == null)) {
                    return kind;
                }
            }
        }
        return -1;
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
    boolean canStartNfaUsingAscii(char c) {
        if (c >= 128) {
            throw new IllegalStateException(
                    "canStartNfaUsingAscii called with a non-ASCII character: " + (int) c);
        }

        String s = getInitialState().epsilonMovesString;
        if ((s == null) || s.equals("null;")) {
            return false;
        }

        for (int state : getNextStates(s)) {
            NfaState tmp = getIndexedState(state);
            if (Bits.test(tmp.asciiMoves, c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * An array of per-position state-set tables. Generic array creation needs the unchecked cast,
     * so it is made once here instead of raw at the call site.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Hashtable<String, long[]>[] newStatesForPos(int length) {
        return new Hashtable[length];
    }

    /**
     * The state name stage 4 gave this composite state set. A query: unlike
     * {@link #addCompositeStateSet(String)} it registers nothing, which is what a back end needs
     * while it renders (ADR-0012). Every set a back end asks for was registered in stage 4.
     */
    public final int compositeStateName(String stateSetString) {
        Integer stateName = this.stateNameForComposite.get(stateSetString);
        if (stateName == null) {
            throw new IllegalStateException(
                    "The state set was not registered in stage 4: " + stateSetString);
        }
        return stateName;
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
                                || NfaState.contains(other, nameSet[toRet]))) {
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

        KindInfo(int maxKind) {
            this.validKinds = new long[(maxKind / 64) + 1];
            this.finalKinds = new long[(maxKind / 64) + 1];
        }

        void InsertValidKind(int kind) {
            Bits.set(this.validKinds, kind);
        }

        void InsertFinalKind(int kind) {
            Bits.set(this.finalKinds, kind);
        }

        public boolean hasValidKindCnt() {
            return Arrays.stream(this.validKinds).anyMatch(word -> word != 0L);
        }

        public boolean hasFinalKindCnt() {
            return Arrays.stream(this.finalKinds).anyMatch(word -> word != 0L);
        }

        /** The final kinds, ascending. */
        int[] finalKindsAscending() {
            return BitSet.valueOf(this.finalKinds).stream().toArray();
        }
    }

    /**
     * Splits the states of a composite state into groups whose ASCII moves on {@code byteNum} are
     * disjoint, largest move sets first. Each group becomes one if-else chain in the move code.
     */
    public final List<List<NfaState>> asciiPartition(int[] states, int byteNum) {
        // Of equally large move sets, the later state comes first.
        List<NfaState> original = new ArrayList<>(Arrays.stream(states).mapToObj(this::getAllState)
                .filter(state -> state.asciiMoves[byteNum] != 0L).toList().reversed());
        original.sort(Comparator.comparingInt(
                (NfaState state) -> Long.bitCount(state.asciiMoves[byteNum])).reversed());

        List<List<NfaState>> partition = new ArrayList<>();
        while (!original.isEmpty()) {
            NfaState tmp = original.removeFirst();
            long bitVec = tmp.asciiMoves[byteNum];
            List<NfaState> subSet = new ArrayList<>();
            subSet.add(tmp);

            for (int j = 0; j < original.size(); j++) {
                NfaState tmp1 = original.get(j);

                if ((tmp1.asciiMoves[byteNum] & bitVec) == 0L) {
                    bitVec |= tmp1.asciiMoves[byteNum];
                    subSet.add(tmp1);
                    original.remove(j--);
                }
            }

            partition.add(subSet);
        }

        return partition;
    }

    /** What a key of {@link #statesForPos} holds. */
    public final StopKey stopKey(String key) {
        return StopKey.parse(key);
    }

    /**
     * A key of {@link #statesForPos}: the kind the string-literal DFA has matched when it stops, the
     * position it matched it at, and the state set the NFA resumes in ({@code "null;"} for none).
     */
    public record StopKey(int kind, int matchedPos, String stateSet) {

        String key() {
            return this.kind + ", " + this.matchedPos + ", " + this.stateSet;
        }

        static StopKey parse(String key) {
            String[] parts = key.split(", ", 3);
            return new StopKey(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), parts[2]);
        }
    }
}
