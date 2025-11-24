// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import org.hivevm.cc.Encoding;
import org.hivevm.cc.lexer.NfaState;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.RStringLiteral;
import org.hivevm.cc.parser.Token;
import org.hivevm.source.SourceWriter;

/**
 * The {@link LexerGenerator} class.
 */
public abstract class LexerGenerator extends CodeGenerator<LexerData> {

    protected static final String LOHI_BYTES        = "LOHI_BYTES";
    protected static final String STATES            = "STATES";
    protected static final String NON_ASCII_TABLE   = "NON_ASCII_TABLE";
    protected static final String DEFAULT_LEX_STATE = "DEFAULT_LEX_STATE";
    protected static final String MAX_LEX_STATES    = "MAX_LEX_STATES";
    protected static final String STATE_NAMES       = "STATE_NAMES";
    protected static final String STATE_COUNT       = "STATE_COUNT";
    protected static final String KEEP_LINE_COL     = "KEEP_LINE_COOL";

    protected static final String HAS_LOOP    = "HAS_LOOP";
    protected static final String HAS_SKIP    = "HAS_SKIP";
    protected static final String HAS_MORE    = "HAS_MORE";
    protected static final String HAS_SPECIAL = "HAS_SPECIAL";

    protected static final String HAS_MOPRE_ACTIONS = "HAS_MORE_ACTIONS";
    protected static final String HAS_SKIP_ACTIONS  = "HAS_SKIP_ACTIONS";
    protected static final String HAS_TOKEN_ACTIONS = "HAS_TOKEN_ACTIONS";
    protected static final String HAS_EMPTY_MATCH   = "HAS_EMPTY_MATCH";

    protected static final String STATE_SET_SIZE = "STATE_SET_SIZE";

    protected static final String DUAL_NEED  = "CHECK_NADD_STATES_DUAL_NEEDED";
    protected static final String UNARY_NEED = "CHECK_NADD_STATES_UNARY_NEEDED";

    protected String self() {
        return "";
    }

    // --------------------------------------- RString

    protected static String[] ReArrange(Hashtable<String, ?> tab) {
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

    protected final int GetStateSetForKind(NfaStateData data, int pos, int kind) {
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
                return AddCompositeStateSet(data, s);
            }
        }
        return -1;
    }

    protected final boolean CanStartNfaUsingAscii(NfaStateData data, char c) {
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

    private void ReArrange(NfaStateData data) {
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

    private void FixStateSets(NfaStateData data) {
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

    protected final void dumpNfaAndDfa(NfaStateData stateData, SourceWriter writer) {
        if (stateData.hasNFA && !stateData.isMixedState())
            dumpNfaStartStatesCode(writer, stateData, stateData.statesForPos);
        dumpDfaCode(writer, stateData);
        if (stateData.hasNFA) {
            prepareNfaStates(stateData);
            dumpMoveNfa(writer, stateData);
        }
    }

    protected abstract void dumpNfaStartStatesCode(SourceWriter writer, NfaStateData stateData,
                                                   Hashtable<String, long[]>[] statesForPos);

    protected abstract void dumpDfaCode(SourceWriter writer, NfaStateData stateData);

    protected abstract void dumpMoveNfa(SourceWriter writer, NfaStateData stateData);

    protected final int stateNameForComposite(NfaStateData data, String stateSetString) {
        return data.stateNameForComposite.get(stateSetString);
    }

    private void prepareNfaStates(NfaStateData data) {
        int i;
        int[] kindsForStates = null;

        if (data.global.getKinds() == null) {
            data.global.kinds = new int[data.global.maxLexStates()][];
            data.global.statesForState = new int[data.global.maxLexStates()][][];
        }

        ReArrange(data);

        for (i = 0; i < data.getAllStateCount(); i++) {
            NfaState temp = data.getAllState(i);

            if ((temp.lexState != data.getStateIndex()) || !temp.HasTransitions() || temp.dummy || (
                    temp.stateName == -1)) {
                continue;
            }

            if (kindsForStates == null) {
                kindsForStates = new int[data.generatedStates()];
                data.global.getStatesForState()[data.getStateIndex()] =
                        new int[Math.max(data.generatedStates(), data.dummyStateIndex + 1)][];
            }

            kindsForStates[temp.stateName] = temp.lookingFor;
            data.global.getStatesForState()[data.getStateIndex()][temp.stateName] = temp.compositeStates;
        }

        Enumeration<String> e = data.stateNameForComposite.keys();

        while (e.hasMoreElements()) {
            String s = e.nextElement();
            int state = data.stateNameForComposite.get(s);

            if (state >= data.generatedStates()) {
                data.global.getStatesForState()[data.getStateIndex()][state] = data.getNextStates(s);
            }
        }

        if (!data.stateSetsToFix.isEmpty()) {
            FixStateSets(data);
        }

        data.global.kinds[data.getStateIndex()] = kindsForStates;
    }

    protected final Vector<List<NfaState>> PartitionStatesSetForAscii(NfaStateData data, int[] states,
                                                              int byteNum) {
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

        while (!original.isEmpty()) {
            tmp = original.getFirst();
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

    protected final int InitStateName(NfaStateData data) {
        if (data.getInitialState().usefulEpsilonMoves == 0) {
            return -1;
        }
        String s = data.getInitialState().GetEpsilonMovesString();
        return stateNameForComposite(data, s);
    }


    protected final int getCompositeStateSet(NfaStateData data, String stateSetString) {
        Integer stateNameToReturn = data.stateNameForComposite.get(stateSetString);

        if (stateNameToReturn != null) {
            return stateNameToReturn;
        }

        int[] nameSet = data.getNextStates(stateSetString);

        if (nameSet.length == 1) {
            return nameSet[0];
        }

        int toRet = 0;
        while ((toRet < nameSet.length) && ((data.getIndexedState(nameSet[toRet]).inNextOf > 1))) {
            toRet++;
        }

        for (String s : data.compositeStateTable.keySet()) {
            if (!s.equals(stateSetString) && NfaState.Intersect(data, stateSetString, s)) {
                int[] other = data.compositeStateTable.get(s);
                while ((toRet < nameSet.length) && (((data.getIndexedState(nameSet[toRet]).inNextOf > 1))
                        || (NfaState.ElemOccurs(nameSet[toRet], other) >= 0))) {
                    toRet++;
                }
            }
        }
        return nameSet[toRet];
    }

    private int AddCompositeStateSet(NfaStateData data, String stateSetString) {
        Integer stateNameToReturn;

        if ((stateNameToReturn = data.stateNameForComposite.get(stateSetString)) != null) {
            return stateNameToReturn;
        }

        int toRet = 0;
        int[] nameSet = data.getNextStates(stateSetString);

        if (false) {
            data.stateBlockTable.put(stateSetString, stateSetString);
        }

        if (nameSet == null) {
            throw new Error("JavaCC Bug: Please file a bug at: https://github.com/javacc/javacc/issues");
        }

        if (nameSet.length == 1) {
            stateNameToReturn = nameSet[0];
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

        while ((toRet < nameSet.length) && ((data.getIndexedState(nameSet[toRet]).inNextOf > 1))) {
            toRet++;
        }

        Enumeration<String> e = data.compositeStateTable.keys();
        String s;
        while (e.hasMoreElements()) {
            s = e.nextElement();
            if (!s.equals(stateSetString) && NfaState.Intersect(data, stateSetString, s)) {
                int[] other = data.compositeStateTable.get(s);

                while ((toRet < nameSet.length) && (((data.getIndexedState(nameSet[toRet]).inNextOf > 1))
                        || (NfaState.ElemOccurs(nameSet[toRet], other) >= 0))) {
                    toRet++;
                }
            }
        }

        int tmp;

        if (toRet >= nameSet.length) {
            if (data.dummyStateIndex == -1) {
                tmp = data.dummyStateIndex = data.generatedStates();
            }
            else {
                tmp = ++data.dummyStateIndex;
            }
        }
        else {
            tmp = nameSet[toRet];
        }

        stateNameToReturn = tmp;
        data.stateNameForComposite.put(stateSetString, stateNameToReturn);
        data.compositeStateTable.put(stateSetString, nameSet);
        return tmp;
    }


    protected final void genToken(SourceWriter writer, Token t) {
        writer.append(getStringToPrint(t));
    }


    protected final String GetLabel(LexerData data, int kind) {
        RExpression re = data.rexprs[kind];
        if (re instanceof RStringLiteral) {
            return " \"" + Encoding.escape(((RStringLiteral) re).getImage()) + "\"";
        }
        else if (!re.getLabel().isEmpty()) {
            return " <" + re.getLabel() + ">";
        }
        else {
            return " <token of kind " + kind + ">";
        }
    }


    private String PrintNoBreak(SourceWriter writer, NfaStateData data, NfaState state, int byteNum,
                                boolean[] dumped) {
        if (state.inNextOf != 1) {
            throw new Error("JavaCC Bug: Please send mail to sankar@cs.stanford.edu");
        }

        dumped[state.stateName] = true;

        if (byteNum >= 0) {
            if (state.asciiMoves[byteNum] != 0L) {
                writer.append("               case " + state.stateName + ":").new_line();
                DumpAsciiMoveForCompositeState(writer, data, state, byteNum, false);
                return "";
            }
        }
        else if (state.nonAsciiMethod != -1) {
            writer.append("               case " + state.stateName + ":").new_line();
            DumpNonAsciiMoveForCompositeState(writer, data, state);
            return "";
        }

        return ("               case " + state.stateName + ":\n");
    }

    protected final void DumpNullStrLiterals(SourceWriter writer, NfaStateData data) {
        writer.append("{").new_line();
        if (data.generatedStates() > 0)
            writer.append("   return " + self() + "jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", 0);").new_line();
        else
            writer.append("   return 1;").new_line();
        writer.append("}").new_line();
    }

    private void DumpCompositeStatesNonAsciiMoves(SourceWriter writer, NfaStateData data, String key,
                                                  boolean[] dumped) {
        int i;
        int[] nameSet = data.getNextStates(key);

        if ((nameSet.length == 1) || dumped[stateNameForComposite(data, key)]) {
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
                }
                else {
                    toBePrinted = tmp;
                }
            }
            else {
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
                writer.append("                  break;").new_line();
            }

            return;
        }

        if (neededStates == 1) {
            if (!toPrint.equals("")) {
                writer.append(toPrint);
            }

            writer.append("               case " + stateNameForComposite(data, key) + ":").new_line();

            if (!dumped[toBePrinted.stateName] && !stateBlock && (toBePrinted.inNextOf > 1)) {
                writer.append("               case " + toBePrinted.stateName + ":").new_line();
            }

            dumped[toBePrinted.stateName] = true;
            DumpNonAsciiMove(writer, data, toBePrinted, dumped);
            return;
        }

        if (!toPrint.equals("")) {
            writer.append(toPrint);
        }

        int keyState = stateNameForComposite(data, key);
        writer.append("               case " + keyState + ":").new_line();
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

        writer.append("                  break;").new_line();
    }

    private void DumpAsciiMove(SourceWriter writer, NfaStateData data, NfaState state, int byteNum,
                               boolean[] dumped) {
        boolean nextIntersects = state.selfLoop() && state.isComposite;
        boolean onlyState = true;

        for (NfaState element : data.getAllStates()) {

            if ((state == element) || (element.stateName == -1) || element.dummy || (state.stateName
                    == element.stateName)
                    || (element.asciiMoves[byteNum] == 0L)) {
                continue;
            }

            if (onlyState && ((state.asciiMoves[byteNum] & element.asciiMoves[byteNum]) != 0L)) {
                onlyState = false;
            }

            if (!nextIntersects && NfaState.Intersect(data, element.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
            }

            if (!dumped[element.stateName] && !element.isComposite && (state.asciiMoves[byteNum]
                    == element.asciiMoves[byteNum])
                    && (state.kindToPrint == element.kindToPrint)
                    && ((state.next.epsilonMovesString == element.next.epsilonMovesString)
                    || ((state.next.epsilonMovesString != null) && (element.next.epsilonMovesString != null)
                    && state.next.epsilonMovesString.equals(element.next.epsilonMovesString)))) {
                dumped[element.stateName] = true;
                writer.append("               case " + element.stateName + ":").new_line();
            }
        }

        int oneBit = NfaState.OnlyOneBitSet(state.asciiMoves[byteNum]);
        if ((state.asciiMoves[byteNum] != 0xffffffffffffffffL)
                && (((state.next == null) || (state.next.usefulEpsilonMoves == 0))
                && (state.kindToPrint != Integer.MAX_VALUE))) {
            String kindCheck = "";

            if (!onlyState) {
                kindCheck = " && kind > " + state.kindToPrint;
            }

            if (oneBit != -1) {
                writer.append("                  if (curChar == " + ((64 * byteNum) + oneBit) + kindCheck + ")").new_line();
            }
            else {
                writer.append("                  if ((" + toHexString(state.asciiMoves[byteNum])
                    + " & l) != 0L" + kindCheck + ")").new_line();
            }

            writer.append("                     kind = " + state.kindToPrint + ";").new_line();

            writer.append("                  break;").new_line();

            return;
        }

        String prefix = "";
        if (state.kindToPrint != Integer.MAX_VALUE) {

            if (oneBit != -1) {
                writer.append("                  if (curChar != " + ((64 * byteNum) + oneBit) + ")").new_line();
                writer.append("                     break;").new_line();
            }
            else if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
                writer.append("                  if ((" + toHexString(state.asciiMoves[byteNum]) + " & l) == 0L)").new_line();
                writer.append("                     break;").new_line();
            }

            if (onlyState) {
                writer.append("                  kind = " + state.kindToPrint + ";").new_line();
            }
            else {
                writer.append("                  if (kind > " + state.kindToPrint + ")").new_line();
                writer.append("                     kind = " + state.kindToPrint + ";").new_line();
            }
        }
        else if (oneBit != -1) {
            writer.append("                  if (curChar == " + ((64 * byteNum) + oneBit) + ")").new_line();
            prefix = "   ";
        }
        else if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
            writer.append("                  if ((" + toHexString(state.asciiMoves[byteNum]) + " & l) != 0L)").new_line();
            prefix = "   ";
        }

        if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
            int[] stateNames = data.getNextStates(state.next.epsilonMovesString);
            if (state.next.usefulEpsilonMoves == 1) {
                int name = stateNames[0];
                if (nextIntersects) {
                    writer.append(prefix + "                  { jjCheckNAdd(" + name + "); }").new_line();
                }
                else {
                    writer.append(prefix + "                  jjstateSet[jjnewStateCnt++] = " + name + ";").new_line();
                }
            }
            else if ((state.next.usefulEpsilonMoves == 2) && nextIntersects) {
                writer.append(
                        prefix + "                  { jjCheckNAddTwoStates(" + stateNames[0] + ", "
                                + stateNames[1] + "); }").new_line();
            }
            else {
                int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
                boolean notTwo = ((indices[0] + 1) != indices[1]);

                if (nextIntersects) {
                    writer.append(prefix + "                  { jjCheckNAddStates(" + indices[0]);
                    if (notTwo) {
                        data.global.jjCheckNAddStatesDualNeeded = true;
                        writer.append(", " + indices[1]);
                    }
                    else {
                        data.global.jjCheckNAddStatesUnaryNeeded = true;
                    }
                    writer.append("); }").new_line();
                }
                else {
                    writer.append(
                            prefix + "                  { jjAddStates(" + indices[0] + ", " + indices[1]
                                    + "); }").new_line();
                }
            }
        }

        writer.append("                  break;").new_line();
    }


    private void DumpAsciiMoveForCompositeState(SourceWriter writer, NfaStateData data, NfaState state,
                                                int byteNum,
                                                boolean elseNeeded) {
        boolean nextIntersects = state.selfLoop();

        for (NfaState temp1 : data.getAllStates()) {
            if ((state == temp1) || (temp1.stateName == -1) || temp1.dummy || (state.stateName
                    == temp1.stateName)
                    || (temp1.asciiMoves[byteNum] == 0L)) {
                continue;
            }

            if (!nextIntersects && NfaState.Intersect(data, temp1.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
                break;
            }
        }

        // System.out.println(stateName + " \'s nextIntersects : " + nextIntersects);
        String prefix = "";
        if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
            int oneBit = NfaState.OnlyOneBitSet(state.asciiMoves[byteNum]);

            if (oneBit != -1) {
                writer.append("                  "
                    + (elseNeeded ? "else " : "") + "if (curChar == " + ((64 * byteNum) + oneBit) + ")")
                    .new_line();
            }
            else {
                writer.append("                  " + (elseNeeded ? "else " : "")
                    + "if ((" + toHexString(state.asciiMoves[byteNum]) + " & l) != 0L)")
                    .new_line();
            }
            prefix = "   ";
        }

        if (state.kindToPrint != Integer.MAX_VALUE) {
            if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
                writer.append("                  {").new_line();
            }

            writer.append(prefix + "                  if (kind > " + state.kindToPrint + ")").new_line();
            writer.append(prefix + "                     kind = " + state.kindToPrint + ";").new_line();
        }

        if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
            int[] stateNames = data.getNextStates(state.next.epsilonMovesString);
            if (state.next.usefulEpsilonMoves == 1) {
                int name = stateNames[0];

                if (nextIntersects) {
                    writer.append(prefix + "                  { jjCheckNAdd(" + name + "); }").new_line();
                }
                else {
                    writer.append(prefix + "                  jjstateSet[jjnewStateCnt++] = " + name + ";").new_line();
                }
            }
            else if ((state.next.usefulEpsilonMoves == 2) && nextIntersects) {
                writer.append(
                        prefix + "                  { jjCheckNAddTwoStates(" + stateNames[0] + ", "
                                + stateNames[1] + "); }")
                    .new_line();
            }
            else {
                int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
                boolean notTwo = ((indices[0] + 1) != indices[1]);

                if (nextIntersects) {
                    writer.append(prefix + "                  { jjCheckNAddStates(" + indices[0]);
                    if (notTwo) {
                        data.global.jjCheckNAddStatesDualNeeded = true;
                        writer.append(", " + indices[1]);
                    }
                    else {
                        data.global.jjCheckNAddStatesUnaryNeeded = true;
                    }
                    writer.append("); }").new_line();
                }
                else {
                    writer.append(
                            prefix + "                  { jjAddStates(" + indices[0] + ", " + indices[1]
                                    + "); }").new_line();
                }
            }
        }

        if ((state.asciiMoves[byteNum] != 0xffffffffffffffffL) && (state.kindToPrint
                != Integer.MAX_VALUE)) {
            writer.append("                  }").new_line();
        }
    }

    private void DumpNonAsciiMoveForCompositeState(SourceWriter writer, NfaStateData data,
                                                   NfaState state) {
        boolean nextIntersects = state.selfLoop();
        for (NfaState temp1 : data.getAllStates()) {
            if ((state == temp1) || (temp1.stateName == -1) || temp1.dummy || (state.stateName
                    == temp1.stateName)
                    || (temp1.nonAsciiMethod == -1)) {
                continue;
            }

            if (!nextIntersects && NfaState.Intersect(data, temp1.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
                break;
            }
        }

        writer.append(
                "                  if (jjCanMove_" + state.nonAsciiMethod + "(hiByte, i1, i2, l1, l2))")
            .new_line();

        if (state.kindToPrint != Integer.MAX_VALUE) {
            writer.append("                  {").new_line();
            writer.append("                     if (kind > " + state.kindToPrint + ")").new_line();
            writer.append("                        kind = " + state.kindToPrint + ";").new_line();
        }

        if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
            int[] stateNames = data.getNextStates(state.next.epsilonMovesString);
            if (state.next.usefulEpsilonMoves == 1) {
                int name = stateNames[0];
                if (nextIntersects) {
                    writer.append("                     { jjCheckNAdd(" + name + "); }").new_line();
                }
                else {
                    writer.append("                     jjstateSet[jjnewStateCnt++] = " + name + ";").new_line();
                }
            }
            else if ((state.next.usefulEpsilonMoves == 2) && nextIntersects) {
                writer.append(
                        "                     { jjCheckNAddTwoStates(" + stateNames[0] + ", " + stateNames[1]
                                + "); }")
                    .new_line();
            }
            else {
                int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
                boolean notTwo = ((indices[0] + 1) != indices[1]);

                if (nextIntersects) {
                    writer.append("                     { jjCheckNAddStates(" + indices[0]);
                    if (notTwo) {
                        data.global.jjCheckNAddStatesDualNeeded = true;
                        writer.append(", " + indices[1]);
                    }
                    else {
                        data.global.jjCheckNAddStatesUnaryNeeded = true;
                    }
                    writer.append("); }").new_line();
                }
                else {
                    writer.append(
                            "                     { jjAddStates(" + indices[0] + ", " + indices[1] + "); }")
                        .new_line();
                }
            }
        }

        if (state.kindToPrint != Integer.MAX_VALUE) {
            writer.append("                  }").new_line();
        }
    }

    private void DumpNonAsciiMove(SourceWriter writer, NfaStateData data, NfaState state,
                                  boolean[] dumped) {
        boolean nextIntersects = state.selfLoop() && state.isComposite;

        for (NfaState element : data.getAllStates()) {
            if ((state == element) || (element.stateName == -1) || element.dummy || (state.stateName
                    == element.stateName)
                    || (element.nonAsciiMethod == -1)) {
                continue;
            }

            if (!nextIntersects && NfaState.Intersect(data, element.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
            }

            if (!dumped[element.stateName] && !element.isComposite && (state.nonAsciiMethod
                    == element.nonAsciiMethod)
                    && (state.kindToPrint == element.kindToPrint)
                    && ((state.next.epsilonMovesString == element.next.epsilonMovesString)
                    || ((state.next.epsilonMovesString != null) && (element.next.epsilonMovesString != null)
                    && state.next.epsilonMovesString.equals(element.next.epsilonMovesString)))) {
                dumped[element.stateName] = true;
                writer.append("               case " + element.stateName + ":").new_line();
            }
        }

        if ((state.next == null) || (state.next.usefulEpsilonMoves <= 0)) {
            String kindCheck = " && kind > " + state.kindToPrint;

            writer.append("                  if (jjCanMove_" + state.nonAsciiMethod + "(hiByte, i1, i2, l1, l2)"
                            + kindCheck + ")")
                .new_line();
            writer.append("                     kind = " + state.kindToPrint + ";").new_line();
            writer.append("                  break;").new_line();
            return;
        }

        String prefix = "   ";
        if (state.kindToPrint != Integer.MAX_VALUE) {
            writer.append("                  if (!jjCanMove_" + state.nonAsciiMethod + "(hiByte, i1, i2, l1, l2))").new_line();
            writer.append("                     break;").new_line();

            writer.append("                  if (kind > " + state.kindToPrint + ")").new_line();
            writer.append("                     kind = " + state.kindToPrint + ";").new_line();
            prefix = "";
        }
        else {
            writer.append("                  if (jjCanMove_" + state.nonAsciiMethod + "(hiByte, i1, i2, l1, l2))").new_line();
        }

        if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
            int[] stateNames = data.getNextStates(state.next.epsilonMovesString);
            if (state.next.usefulEpsilonMoves == 1) {
                int name = stateNames[0];
                if (nextIntersects) {
                    writer.append(prefix + "                  { jjCheckNAdd(" + name + "); }").new_line();
                }
                else {
                    writer.append(prefix + "                  jjstateSet[jjnewStateCnt++] = " + name + ";").new_line();
                }
            }
            else if ((state.next.usefulEpsilonMoves == 2) && nextIntersects) {
                writer.append(
                        prefix + "                  { jjCheckNAddTwoStates(" + stateNames[0] + ", "
                                + stateNames[1] + "); }").new_line();
            }
            else {
                int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
                boolean notTwo = ((indices[0] + 1) != indices[1]);

                if (nextIntersects) {
                    writer.append(prefix + "                  { jjCheckNAddStates(" + indices[0]);
                    if (notTwo) {
                        data.global.jjCheckNAddStatesDualNeeded = true;
                        writer.append(", " + indices[1]);
                    }
                    else {
                        data.global.jjCheckNAddStatesUnaryNeeded = true;
                    }
                    writer.append("); }").new_line();
                }
                else {
                    writer.append(prefix
                        + "                  { jjAddStates(" + indices[0] + ", " + indices[1] + "); }").new_line();
                }
            }
        }

        writer.append("                  break;").new_line();
    }


    private void DumpCompositeStatesAsciiMoves(SourceWriter writer, NfaStateData data, String key,
                                               int byteNum,
                                               boolean[] dumped) {
        int i;
        int[] nameSet = data.getNextStates(key);

        if ((nameSet.length == 1) || dumped[stateNameForComposite(data, key)]) {
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
                }
                else {
                    toBePrinted = tmp;
                }
            }
            else {
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
                writer.append("                  break;").new_line();
            }
            return;
        }

        if (neededStates == 1) {
            if (!toPrint.equals("")) {
                writer.append(toPrint);
            }

            writer.append("               case " + stateNameForComposite(data, key) + ":").new_line();

            if (!dumped[toBePrinted.stateName] && !stateBlock && (toBePrinted.inNextOf > 1)) {
                writer.append("               case " + toBePrinted.stateName + ":").new_line();
            }

            dumped[toBePrinted.stateName] = true;
            DumpAsciiMove(writer, data, toBePrinted, byteNum, dumped);
            return;
        }

        List<List<NfaState>> partition = PartitionStatesSetForAscii(data, nameSet, byteNum);

        if (!toPrint.equals("")) {
            writer.append(toPrint);
        }

        int keyState = stateNameForComposite(data, key);
        writer.append("               case " + keyState + ":").new_line();
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

        writer.append("                  break;").new_line();
    }

    protected abstract void DumpHeadForCase(SourceWriter writer, int byteNum);

    protected void DumpAsciiMoves(SourceWriter writer, NfaStateData data, int byteNum) {
        boolean[] dumped = new boolean[Math.max(data.generatedStates(), data.dummyStateIndex + 1)];
        Enumeration<String> e = data.compositeStateTable.keys();

        DumpHeadForCase(writer, byteNum);

        while (e.hasMoreElements()) {
            DumpCompositeStatesAsciiMoves(writer, data, e.nextElement(), byteNum, dumped);
        }

        for (NfaState element : data.getAllStates()) {

            if (dumped[element.stateName] || (element.lexState != data.getStateIndex())
                    || !element.HasTransitions() || element.dummy
                    || (element.stateName == -1)) {
                continue;
            }

            String toPrint = "";

            if (element.stateForCase != null) {
                if ((element.inNextOf == 1) || dumped[element.stateForCase.stateName]) {
                    continue;
                }

                toPrint = PrintNoBreak(writer, data, element.stateForCase, byteNum, dumped);

                if (element.asciiMoves[byteNum] == 0L) {
                    if (toPrint.equals("")) {
                        writer.append("                  break;").new_line();
                    }

                    continue;
                }
            }

            if (element.asciiMoves[byteNum] == 0L) {
                continue;
            }

            if (!toPrint.equals("")) {
                writer.append(toPrint);
            }

            dumped[element.stateName] = true;
            writer.append("               case " + element.stateName + ":").new_line();
            DumpAsciiMove(writer, data, element, byteNum, dumped);
        }

        if ((byteNum != 0) && (byteNum != 1))
            writer.append("               default : if (i1 == 0 || l1 == 0 || i2 == 0 ||  l2 == 0) break; else break;").new_line();
        else
            writer.append("               default : break;").new_line();

        writer.append("            }").new_line();
        writer.append("         } while(i != startsAt);").new_line();
    }


    protected void DumpCharAndRangeMoves(SourceWriter writer, NfaStateData data) {
        boolean[] dumped = new boolean[Math.max(data.generatedStates(), data.dummyStateIndex + 1)];
        Enumeration<String> e = data.compositeStateTable.keys();
        int i;

        DumpHeadForCase(writer, -1);

        while (e.hasMoreElements()) {
            DumpCompositeStatesNonAsciiMoves(writer, data, e.nextElement(), dumped);
        }

        for (i = 0; i < data.getAllStateCount(); i++) {
            NfaState temp = data.getAllState(i);

            if ((temp.stateName == -1) || dumped[temp.stateName]
                || (temp.lexState != data.getStateIndex()) || !temp.HasTransitions() || temp.dummy) {
                continue;
            }

            String toPrint = "";

            if (temp.stateForCase != null) {
                if ((temp.inNextOf == 1) || dumped[temp.stateForCase.stateName])
                    continue;

                toPrint = PrintNoBreak(writer, data, temp.stateForCase, -1, dumped);

                if (temp.nonAsciiMethod == -1) {
                    if (toPrint.equals(""))
                        writer.append("                  break;").new_line();
                    continue;
                }
            }

            if (temp.nonAsciiMethod == -1)
                continue;

            if (!toPrint.equals(""))
                writer.append(toPrint);

            dumped[temp.stateName] = true;
            // System.out.println("case : " + temp.stateName);
            writer.append("               case " + temp.stateName + ":").new_line();
            DumpNonAsciiMove(writer, data, temp, dumped);
        }

        writer.append("               default : if (i1 == 0 || l1 == 0 || i2 == 0 ||  l2 == 0) break; else break;").new_line();
        writer.append("            }").new_line();
        writer.append("         } while(i != startsAt);").new_line();
    }

    protected String toHexString(long value) {
        return "0x" + Long.toHexString(value) + "L";
    }

    // Assumes l != 0L
    protected static char MaxChar(long l) {
        for (int i = 64; i-- > 0; ) {
            if ((l & (1L << i)) != 0L) {
                return (char) i;
            }
        }
        return 0xffff;
    }

    protected String getLohiBytes(LexerData data, int i) {
        return String.join(", ", toHexString(data.lohiByte.get(i)[0]),
                toHexString(data.lohiByte.get(i)[1]),
                toHexString(data.lohiByte.get(i)[2]), toHexString(data.lohiByte.get(i)[3]));
    }
}
