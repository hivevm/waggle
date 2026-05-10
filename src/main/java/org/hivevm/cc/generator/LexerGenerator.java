// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.Encoding;
import org.hivevm.cc.Language;
import org.hivevm.cc.lexer.LexerData;
import org.hivevm.cc.lexer.NfaState;
import org.hivevm.cc.lexer.NfaStateData;
import org.hivevm.cc.model.Action;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.RStringLiteral;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.Token;
import org.hivevm.source.Context;
import org.hivevm.source.LinePrinter;
import org.hivevm.source.SourceProvider;
import org.hivevm.source.Template;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

/**
 * The {@link LexerGenerator} class.
 */
public abstract class LexerGenerator extends CodeGenerator<LexerData> {

    protected static final String LOHI_BYTES = "LOHI_BYTES";
    protected static final String NON_ASCII_TABLE = "NON_ASCII_TABLE";

    private static final String HAS_LOOP = "HAS_LOOP";
    private static final String HAS_SKIP = "HAS_SKIP";
    private static final String HAS_MORE = "HAS_MORE";
    private static final String HAS_SPECIAL = "HAS_SPECIAL";

    private static final String HAS_MOPRE_ACTIONS = "HAS_MORE_ACTIONS";
    private static final String HAS_SKIP_ACTIONS = "HAS_SKIP_ACTIONS";
    private static final String HAS_TOKEN_ACTIONS = "HAS_TOKEN_ACTIONS";
    private static final String HAS_EMPTY_MATCH = "HAS_EMPTY_MATCH";

    private static final String DEFAULT_LEX_STATE = "DEFAULT_LEX_STATE";
    private static final String MAX_LEX_STATES = "MAX_LEX_STATES";
    private static final String STATE_NAMES = "STATE_NAMES";
    private static final String KEEP_LINE_COL = "KEEP_LINE_COOL";
    private static final String STATE_COUNT = "STATE_COUNT";
    private static final String STATE_SET_SIZE = "STATE_SET_SIZE";
    private static final String DUAL_NEED = "CHECK_NADD_STATES_DUAL_NEEDED";
    private static final String UNARY_NEED = "CHECK_NADD_STATES_UNARY_NEEDED";

    protected String self() {
        return "";
    }

    @Deprecated
    private final boolean __IS_RUST__;

    protected LexerGenerator(Language language) {
        super(language);
        __IS_RUST__ = language == Language.RUST;
    }

    @Override
    public final void generate(LexerData data) {
        var options = Template.newContext(data.options());
        options.add(LexerGenerator.LOHI_BYTES, data.getLohiByte())
                .set("LOHI_BYTES_INDEX", i -> i)
                .set("LOHI_BYTES_VALUE", i -> getLohiBytes(data, i));
        options.add(LexerGenerator.NON_ASCII_TABLE, data.getNonAsciiTableForMethod())
                .set("NON_ASCII_TABLE_NAME", this::getNonAsciiMethod)
                .set("NON_ASCII_TABLE_METHOD", (s, w) -> dumpNonAsciiMoveMethod(data, s, w));

        options.set(LexerGenerator.HAS_SKIP, data.hasSkip());
        options.set(LexerGenerator.HAS_MORE, data.hasMore());
        options.set(LexerGenerator.HAS_LOOP, data.hasLoop());
        options.set(LexerGenerator.HAS_SPECIAL, data.hasSpecial());

        options.set(LexerGenerator.HAS_MOPRE_ACTIONS, data.hasMoreActions());
        options.set(LexerGenerator.HAS_SKIP_ACTIONS, data.hasSkipActions());
        options.set(LexerGenerator.HAS_TOKEN_ACTIONS, data.hasTokenActions());
        options.set(LexerGenerator.HAS_EMPTY_MATCH, data.hasEmptyMatch());

        options.set(LexerGenerator.DEFAULT_LEX_STATE, data.defaultLexState());
        options.add(LexerGenerator.MAX_LEX_STATES, data.maxLexStates())
                .set(LexerGenerator.MAX_LEX_STATES + "_INDEX", i -> i);
        options.add(LexerGenerator.STATE_NAMES, data.getStateNames())
                .set(LexerGenerator.STATE_NAMES + "_VALUE", i -> i);
        options.set(LexerGenerator.STATE_COUNT, data.getStateCount());
        options.set(LexerGenerator.STATE_SET_SIZE, data.stateSetSize());
        options.set(LexerGenerator.STATE_SET_SIZE + "_2", data.stateSetSize() * 2);
        options.set(LexerGenerator.KEEP_LINE_COL, data.keepLineCol());
        options.set(LexerGenerator.DUAL_NEED, data.jjCheckNAddStatesDualNeeded());
        options.set(LexerGenerator.UNARY_NEED, data.jjCheckNAddStatesUnaryNeeded());

        options.set("DUMP_SKIP_ACTIONS", p -> dumpSkipActions(p, data));
        options.set("DUMP_MORE_ACTIONS", p -> dumpMoreActions(p, data));
        options.set("DUMP_TOKEN_ACTIONS", p -> dumpTokenActions(p, data));

        options.set("DUMP_STATE_SETS", p -> dumpStateSets(p, data));
        options.set("DUMP_GET_NEXT_TOKEN", p -> dumpGetNextToken(p, data));
        options.set("DUMP_STATIC_VAR_DECLARATIONS", p -> dumpStaticVarDeclarations(p, data));
        options.set("DUMP_NFA_AND_DFA", w ->
                data.getStateNames().forEach(name -> dump_nfa_and_dfa(data.getStateData(name), w))
        );

        generate(data, options);

        // Generate Constants
        options = Template.newContext(data.options());
        options.add("STATES", data.getStateCount())
                .set("STATES_INDEX", i -> i)
                .set("STATES_NAME", data::getStateName);
        options.add("TOKENS", data.getOrderedsTokens())
                .set("TOKENS_ORDINAL", RExpression::getOrdinal)
                .set("TOKENS_LABEL", RExpression::getLabel);

        var expressions = new ArrayList<RExpression>();
        for (var production : data.getTokenProductions()) {
            for (var spec : production.getRespecs()) {
                expressions.add(spec.rexp);
            }
        }
        options.add("REXPRESSION_COUNT", expressions.size() + 1)
                .set("REXPRESSION_INDEX", i -> i)
                .set("REXPRESSION_LABEL", (i, w) -> getRegExp(w, i, expressions, false))
                .set("REXPRESSION_IMAGE", (i, w) -> getRegExp(w, i, expressions, true));

        getConstantsTemplate().render(options, options.getParserName());
    }

    protected abstract SourceProvider getConstantsTemplate();

    protected abstract void generate(LexerData data, Context context);

    protected String getNonAsciiMethod(NfaState state) {
        return "" + state.nonAsciiMethod;
    }

    protected abstract void dumpNonAsciiMoveMethod(LexerData data, NfaState state, LinePrinter printer);

    protected abstract void dumpSkipActions(LinePrinter printer, LexerData data);

    protected abstract void dumpMoreActions(LinePrinter printer, LexerData data);

    protected abstract void dumpTokenActions(LinePrinter printer, LexerData data);

    protected abstract void dumpStateSets(LinePrinter printer, LexerData data);

    protected abstract void dumpGetNextToken(LinePrinter printer, LexerData data);

    protected abstract void dumpStaticVarDeclarations(LinePrinter printer, LexerData data);

    protected abstract void getRegExp(LinePrinter printer, int i, List<RExpression> expressions, boolean isImage);
// --------------------------------------- RString

    public static <T> String[] re_arrange(Hashtable<String, T> tab) {
        var ret = new String[tab.size()];
        int cnt = 0;

        for (var s : tab.keySet()) {
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

    private int GetLine(LexerData data, int kind) {
        return data.getRegExp(kind).getLine();
    }

    private int GetColumn(LexerData data, int kind) {
        return data.getRegExp(kind).getColumn();
    }

    protected final void show_warning_intermediate(NfaStateData data, int i, int j, int k) {
        JavaCCErrors.warning(
                " \"" + Encoding.escape(data.global.getImage((j * 64) + k))
                        + "\" cannot be matched as a string literal token  at line "
                        + GetLine(data.global, (j * 64) + k) + ", column "
                        + GetColumn(data.global, (j * 64) + k)
                        + ". It will be matched as " + GetLabel(data.global,
                        data.getIntermediateKinds()[((j * 64) + k)][i]) + ".");
    }

    protected final void show_warning_match(NfaStateData data, int i, int j, int k) {
        JavaCCErrors.warning(
                " \"" + Encoding.escape(data.global.getImage((j * 64) + k))
                        + "\" cannot be matched as a string literal token  at line "
                        + GetLine(data.global, (j * 64) + k) + ", column "
                        + GetColumn(data.global, (j * 64) + k)
                        + ". It will be matched as " + GetLabel(data.global,
                        data.global.canMatchAnyChar(data.getStateIndex())) + ".");
    }

    protected final int GetStateSetForKind(NfaStateData data, int pos, int kind) {
        if (data.isMixedState() || (data.generatedStates() == 0)) {
            return -1;
        }

        var allStateSets = data.statesForPos[pos];
        if (allStateSets == null) {
            return -1;
        }

        for (var s : allStateSets.keySet()) {
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

        var move = data.getInitialState().GetEpsilonMovesString();
        if ((move == null) || move.equals("null;")) {
            return true;
        }

        int[] states = data.getNextStates(move);
        for (int state : states) {
            NfaState tmp = data.getIndexedState(state);
            if ((tmp.asciiMoves[c / 64] & (1L << (c % 64))) != 0L) {
                return false;
            }
        }
        return true;
    }

    // ////////////////////////// NFaState

    private void re_arrange(NfaStateData data) {
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
        int[] tmp = new int[data.generatedStates()];
        int i;

        for (String s : data.stateSetsToFix.keySet()) {
            int[] toFix = data.stateSetsToFix.get(s);
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

    private void dump_nfa_and_dfa(NfaStateData stateData, LinePrinter printer) {
        if (stateData.hasNFA && !stateData.isMixedState())
            dumpNfaStartStatesCode(printer, stateData, stateData.statesForPos);
        dumpDfaCode(printer, stateData);
        if (stateData.hasNFA) {
            prepareNfaStates(stateData);
            dumpMoveNfa(printer, stateData);
        }
    }

    protected abstract void dumpNfaStartStatesCode(LinePrinter printer, NfaStateData stateData,
                                                   Hashtable<String, long[]>[] statesForPos);

    protected abstract void dumpDfaCode(LinePrinter printer, NfaStateData stateData);

    protected abstract void dumpMoveNfa(LinePrinter printer, NfaStateData stateData);

    protected final int stateNameForComposite(NfaStateData data, String stateSetString) {
        return data.stateNameForComposite.get(stateSetString);
    }

    private void prepareNfaStates(NfaStateData data) {
        int i;
        int[] kindsForStates = null;

        if (data.global.getKinds() == null) {
            data.global.init();
        }

        re_arrange(data);

        for (i = 0; i < data.getAllStateCount(); i++) {
            var temp = data.getAllState(i);
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

        for (var s : data.stateNameForComposite.keySet()) {
            int state = data.stateNameForComposite.get(s);
            if (state >= data.generatedStates()) {
                data.global.getStatesForState()[data.getStateIndex()][state] = data.getNextStates(s);
            }
        }

        if (!data.stateSetsToFix.isEmpty()) {
            FixStateSets(data);
        }

        data.global.setKinds(data.getStateIndex(), kindsForStates);
    }

    protected final Vector<List<NfaState>> PartitionStatesSetForAscii(NfaStateData data, int[] states, int byteNum) {
        var cardinalities = new int[states.length];
        var original = new Vector<NfaState>();
        var partition = new Vector<List<NfaState>>();
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

        for (var s : data.compositeStateTable.keySet()) {
            if (!s.equals(stateSetString) && NfaState.Intersect(data, stateSetString, s)) {
                int[] other = data.compositeStateTable.get(s);
                while ((toRet < nameSet.length) && (
                        ((data.getIndexedState(nameSet[toRet]).inNextOf > 1))
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

        if (nameSet == null) {
            throw new Error(
                    "JavaCC Bug: Please file a bug at: https://github.com/javacc/javacc/issues");
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

        for (String s : data.compositeStateTable.keySet()) {
            if (!s.equals(stateSetString) && NfaState.Intersect(data, stateSetString, s)) {
                int[] other = data.compositeStateTable.get(s);

                while ((toRet < nameSet.length) && (
                        ((data.getIndexedState(nameSet[toRet]).inNextOf > 1))
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

        stateNameToReturn = tmp;
        data.stateNameForComposite.put(stateSetString, stateNameToReturn);
        data.compositeStateTable.put(stateSetString, nameSet);
        return tmp;
    }

    protected final void printActionToken(LinePrinter printer, Action action) {
        for (Token token : action.getActionTokens()) {
            printToken(token, printer);
        }
    }

    protected final String GetLabel(LexerData data, int kind) {
        RExpression re = data.getRegExp(kind);
        if (re instanceof RStringLiteral) {
            return " \"" + Encoding.escape(((RStringLiteral) re).getImage()) + "\"";
        } else if (!re.getLabel().isEmpty()) {
            return " <" + re.getLabel() + ">";
        } else {
            return " <token of kind " + kind + ">";
        }
    }

    protected final void print_case(LinePrinter printer, String case_value) {
        if (__IS_RUST__)
            printer.println(case_value + " => {");
        else
            printer.println("case " + case_value + ": {");
    }

    private boolean print_no_break(LinePrinter printer, NfaStateData data, NfaState state, int byteNum, boolean[] dumped) {
        if (state.inNextOf != 1) {
            throw new Error("HiveVM Bug");
        }

        dumped[state.stateName] = true;

        if (byteNum >= 0) {
            if (state.asciiMoves[byteNum] != 0L) {
                print_case(printer, "" + state.stateName);
                DumpAsciiMoveForCompositeState(printer, data, state, byteNum, false);
                printer.println("}");
                return false;
            }
        } else if (state.nonAsciiMethod != -1) {
            print_case(printer, "" + state.stateName);
            DumpNonAsciiMoveForCompositeState(printer, data, state);
            printer.println("}");
            return false;
        }
        return true;
    }

    private void DumpCompositeStatesNonAsciiMoves(LinePrinter printer, NfaStateData data,
                                                  String key, boolean[] dumped) {
        int[] nameSet = data.getNextStates(key);
        if ((nameSet.length == 1) || dumped[stateNameForComposite(data, key)]) {
            return;
        }

        NfaState toBePrinted = null;
        int neededStates = 0;
        NfaState tmp;
        NfaState stateForCase = null;
        boolean stateBlock = (data.stateBlockTable.get(key) != null);

        for (int j : nameSet) {
            tmp = data.getAllState(j);
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
                    throw new Error("HiveVM Bug");
                }
                stateForCase = tmp.stateForCase;
            }
        }

        var toPrint = false;
        if (stateForCase != null) {
            toPrint = print_no_break(printer, data, stateForCase, -1, dumped);
        }

        if (neededStates == 0) {
            if ((stateForCase != null) && !toPrint) {
                printer.println("    break;");
            }

            return;
        }

        if (neededStates == 1) {
            if (toPrint) {
                print_case(printer, "" + stateForCase.stateName);
            }

            var cases = new ArrayList<String>();
            if (__IS_RUST__) {
                cases.add("" + stateNameForComposite(data, key));
                if (!dumped[toBePrinted.stateName] && !stateBlock && (toBePrinted.inNextOf > 1))
                    cases.add("" + toBePrinted.stateName);
                printer.println(String.join(" | ", cases) + " => {");
            } else {
                printer.println("case " + stateNameForComposite(data, key) + ":");

                if (!dumped[toBePrinted.stateName] && !stateBlock && (toBePrinted.inNextOf > 1)) {
                    printer.println("case " + toBePrinted.stateName + ":");
                }
            }

            dumped[toBePrinted.stateName] = true;
            DumpNonAsciiMove(printer, data, toBePrinted, dumped);
            if (__IS_RUST__)
                printer.println("}");
            return;
        }

        if (toPrint) {
            print_case(printer, "" + stateForCase.stateName);
        }

        int keyState = stateNameForComposite(data, key);
        print_case(printer, "" + keyState);
        if (keyState < data.generatedStates()) {
            dumped[keyState] = true;
        }

        for (int j : nameSet) {
            tmp = data.getAllState(j);

            if (tmp.nonAsciiMethod != -1) {
                if (stateBlock) {
                    dumped[tmp.stateName] = true;
                }
                DumpNonAsciiMoveForCompositeState(printer, data, tmp);
            }
        }

        if (!__IS_RUST__)
            printer.println("    break;");
        printer.println("}");
    }

    private void DumpAsciiMove(LinePrinter printer, NfaStateData data, NfaState state, int byteNum, boolean[] dumped, boolean use_state_name) {
        boolean nextIntersects = state.selfLoop() && state.isComposite;
        boolean onlyState = true;
        var cases = new ArrayList<String>();
        if (use_state_name) {
            if (__IS_RUST__)
                cases.add("" + state.stateName);
            else
                printer.println("case " + state.stateName + ":");
        }

        for (NfaState element : data.getAllStates()) {
            if ((state == element) || (element.stateName == -1)
                    || element.dummy || (state.stateName == element.stateName)
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
                if (__IS_RUST__)
                    cases.add("" + element.stateName);
                else
                    printer.println("case " + element.stateName + ":");
            }
        }

        if (__IS_RUST__ && !cases.isEmpty())
            printer.println(String.join(" | ", cases) + " => {");

        printer.indent();

        int oneBit = NfaState.OnlyOneBitSet(state.asciiMoves[byteNum]);
        if ((state.asciiMoves[byteNum] != 0xffffffffffffffffL)
                && (((state.next == null) || (state.next.usefulEpsilonMoves == 0))
                && (state.kindToPrint != Integer.MAX_VALUE))) {
            String kindCheck = "";

            if (!onlyState) {
                kindCheck = " && kind > " + state.kindToPrint;
            }

            if (__IS_RUST__) {
                if (oneBit != -1) {
                    printer.println("if self.cur_char == " + ((64 * byteNum) + oneBit) + kindCheck + " {");
                } else {
                    printer.println("if (" + toHexString(state.asciiMoves[byteNum]) + " & l) != 0" + kindCheck + " {");
                }

                printer.indent();
                printer.println("kind = " + state.kindToPrint + ";");
                printer.outdent();
                printer.println("}");
            } else {
                if (oneBit != -1) {
                    printer.println("if (curChar == " + ((64 * byteNum) + oneBit) + kindCheck + ")");
                } else {
                    printer.println("if ((" + toHexString(state.asciiMoves[byteNum]) + " & l) != 0L" + kindCheck + ")");
                }

                printer.indent();
                printer.println("kind = " + state.kindToPrint + ";");
                printer.outdent();
                printer.println("break;");
            }

            printer.outdent();
            if (__IS_RUST__ && use_state_name)
                printer.println("}");
            return;
        }

        boolean hasIf = false;
        if (state.kindToPrint != Integer.MAX_VALUE) {
            if (oneBit != -1) {
                if (__IS_RUST__)
                    printer.println("if self.cur_char != " + ((64 * byteNum) + oneBit) + " {");
                else
                    printer.println("if (curChar != " + ((64 * byteNum) + oneBit) + ") {");
                printer.indent();
                printer.println("break;");
                printer.outdent();
                printer.println("}");
            } else if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
                if (__IS_RUST__)
                    printer.println("if (" + toHexString(state.asciiMoves[byteNum]) + " & l) == 0 {");
                else
                    printer.println("if ((" + toHexString(state.asciiMoves[byteNum]) + " & l) == 0L) {");
                printer.indent();
                printer.println("break;");
                printer.outdent();
                printer.println("}");
            }

            if (onlyState) {
                printer.println("kind = " + state.kindToPrint + ";");
            } else {
                if (__IS_RUST__)
                    printer.println("if kind > " + state.kindToPrint + " {");
                else
                    printer.println("if (kind > " + state.kindToPrint + ") {");
                printer.indent();
                printer.println("kind = " + state.kindToPrint + ";");
                printer.outdent();
                printer.println("}");
            }
        } else if (oneBit != -1) {
            if (__IS_RUST__)
                printer.println("if self.cur_char == " + ((64 * byteNum) + oneBit) + " {");
            else
                printer.println("if (curChar == " + ((64 * byteNum) + oneBit) + ")");
            hasIf = true;
            printer.indent();
        } else if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
            if (__IS_RUST__)
                printer.println("if (" + toHexString(state.asciiMoves[byteNum]) + " & l) != 0 {");
            else
                printer.println("if ((" + toHexString(state.asciiMoves[byteNum]) + " & l) != 0L)");
            hasIf = true;
            printer.indent();
        }

        if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
            int[] stateNames = data.getNextStates(state.next.epsilonMovesString);
            if (state.next.usefulEpsilonMoves == 1) {
                int name = stateNames[0];
                if (__IS_RUST__) {
                    if (nextIntersects) {
                        printer.println("self.jj_check_n_add(" + name + ");");
                    } else {
                        printer.println("self.jjstate_set[self.jjnew_state_cnt] = " + name + ";");
                        printer.println("self.jjnew_state_cnt += 1;");
                    }
                } else {
                    if (nextIntersects) {
                        printer.println("jjCheckNAdd(" + name + ");");
                    } else {
                        printer.println("jjstateSet[jjnewStateCnt++] = " + name + ";");
                    }
                }
            } else if ((state.next.usefulEpsilonMoves == 2) && nextIntersects) {
                if (__IS_RUST__)
                    printer.println("self.jj_check_n_add_two_states(" + stateNames[0] + ", " + stateNames[1] + ");");
                else
                    printer.println("jjCheckNAddTwoStates(" + stateNames[0] + ", " + stateNames[1] + ");");
            } else {
                int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
                boolean notTwo = ((indices[0] + 1) != indices[1]);

                if (nextIntersects) {
                    if (__IS_RUST__)
                        printer.print("self.jj_check_n_add_states(" + indices[0]);
                    else
                        printer.print("jjCheckNAddStates(" + indices[0]);
                    data.global.setCheckNAddStates(notTwo);
                    if (notTwo) {
                        printer.print(", " + indices[1]);
                    }
                    printer.println(");");
                } else {
                    if (__IS_RUST__)
                        printer.println("self.jj_add_states(" + indices[0] + ", " + indices[1] + ");");
                    else
                        printer.println("jjAddStates(" + indices[0] + ", " + indices[1] + ");");
                }
            }
        }

        if (hasIf) {
            printer.outdent();
        }

        if (!__IS_RUST__)
            printer.println("break;");

        if (hasIf && __IS_RUST__)
            printer.println("}");

        printer.outdent();
        if (__IS_RUST__ && use_state_name)
            printer.println("}");
    }


    private void DumpAsciiMoveForCompositeState(LinePrinter printer, NfaStateData data,
                                                NfaState state, int byteNum, boolean elseNeeded) {
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
        boolean hasIf = false;
        if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
            int oneBit = NfaState.OnlyOneBitSet(state.asciiMoves[byteNum]);

            var cond = (elseNeeded ? "else if " : "if ");
            if (__IS_RUST__) {
                if (oneBit != -1) {
                    printer.println(cond + "self.cur_char == " + ((64 * byteNum) + oneBit) + " {");
                } else {
                    printer.println(cond + "(" + toHexString(state.asciiMoves[byteNum]) + " & l) != 0 {");
                }
            } else {
                if (oneBit != -1) {
                    printer.println(cond + "(curChar == " + ((64 * byteNum) + oneBit) + ")");
                } else {
                    printer.println(cond + "((" + toHexString(state.asciiMoves[byteNum]) + " & l) != 0L)");
                }
            }
            hasIf = true;
        }
        printer.indent();

        if (state.kindToPrint != Integer.MAX_VALUE) {
            if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
                printer.println("{");
            }

            if (__IS_RUST__)
                printer.println("if kind > " + state.kindToPrint + " {");
            else
                printer.println("if (kind > " + state.kindToPrint + ") {");
            printer.indent();
            printer.println("kind = " + state.kindToPrint + ";");
            printer.outdent();
            printer.println("}");
        }

        if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
            int[] stateNames = data.getNextStates(state.next.epsilonMovesString);
            if (state.next.usefulEpsilonMoves == 1) {
                int name = stateNames[0];

                if (nextIntersects) {
                    if (__IS_RUST__)
                        printer.println("self.jj_check_n_add(" + name + ");");
                    else
                        printer.println("jjCheckNAdd(" + name + ");");
                } else {
                    if (__IS_RUST__) {
                        printer.println("self.jjstate_set[self.jjnew_state_cnt] = " + name + ";");
                        printer.println("self.jjnew_state_cnt += 1;");
                    } else
                        printer.println("jjstateSet[jjnewStateCnt++] = " + name + ";");
                }
            } else if ((state.next.usefulEpsilonMoves == 2) && nextIntersects) {
                if (__IS_RUST__)
                    printer.println("self.jj_check_n_add_two_states(" + stateNames[0] + ", " + stateNames[1] + ");");
                else
                    printer.println("jjCheckNAddTwoStates(" + stateNames[0] + ", " + stateNames[1] + ");");
            } else {
                int[] indices = NfaState.GetStateSetIndicesForUse(data, state.next.epsilonMovesString);
                boolean notTwo = ((indices[0] + 1) != indices[1]);

                if (nextIntersects) {
                    if (__IS_RUST__)
                        printer.print("self.jj_check_n_add_states(" + indices[0]);
                    else
                        printer.print("jjCheckNAddStates(" + indices[0]);
                    data.global.setCheckNAddStates(notTwo);
                    if (notTwo) {
                        printer.print(", " + indices[1]);
                    }
                    printer.println(");");
                } else {
                    if (__IS_RUST__)
                        printer.println("self.jj_add_states(" + indices[0] + ", " + indices[1] + ");");
                    else
                        printer.println("jjAddStates(" + indices[0] + ", " + indices[1] + ");");
                }
            }
        }

        printer.outdent();
        if ((state.asciiMoves[byteNum] != 0xffffffffffffffffL) && (state.kindToPrint != Integer.MAX_VALUE)) {
            printer.println("}");
        }

        if (__IS_RUST__ && hasIf)
            printer.println("}");
    }

    private void DumpNonAsciiMoveForCompositeState(LinePrinter printer, NfaStateData data,
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

        if (__IS_RUST__)
            printer.println("if (jj_can_move_" + state.nonAsciiMethod + "(hi_byte, i1, i2, l1, l2))");
        else
            printer.println("if (jjCanMove_" + state.nonAsciiMethod + "(hiByte, i1, i2, l1, l2))");

        if (state.kindToPrint != Integer.MAX_VALUE) {
            printer.println("{");
            printer.indent();
            if (__IS_RUST__)
                printer.println("if kind > " + state.kindToPrint + "{");
            else
                printer.println("if (kind > " + state.kindToPrint + ") {");
            printer.indent();
            printer.println("kind = " + state.kindToPrint + ";");
            printer.outdent();
            printer.println("}");
        }

        if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
            int[] stateNames = data.getNextStates(state.next.epsilonMovesString);
            if (state.next.usefulEpsilonMoves == 1) {
                int name = stateNames[0];
                if (nextIntersects) {
                    if (__IS_RUST__)
                        printer.println("self.jj_check_n_add(" + name + ");");
                    else
                        printer.println("jjCheckNAdd(" + name + ");");
                } else {
                    if (__IS_RUST__) {
                        printer.println("self.jjstate_set[self.jjnew_state_cnt] = " + name + ";");
                        printer.println("self.jjnew_state_cnt += 1;");
                    } else
                        printer.println("jjstateSet[jjnewStateCnt++] = " + name + ";");
                }
            } else if ((state.next.usefulEpsilonMoves == 2) && nextIntersects) {
                if (__IS_RUST__)
                    printer.println("self.jj_check_n_add_two_states(" + stateNames[0] + ", " + stateNames[1] + ");");
                else
                    printer.println("jjCheckNAddTwoStates(" + stateNames[0] + ", " + stateNames[1] + ");");
            } else {
                int[] indices = NfaState.GetStateSetIndicesForUse(data,
                        state.next.epsilonMovesString);
                boolean notTwo = ((indices[0] + 1) != indices[1]);

                if (nextIntersects) {
                    if (__IS_RUST__)
                        printer.print("self.jj_check_n_add_states(" + indices[0]);
                    else
                        printer.print("jjCheckNAddStates(" + indices[0]);
                    data.global.setCheckNAddStates(notTwo);
                    if (notTwo) {
                        printer.print(", " + indices[1]);
                    }
                    printer.println(");");
                } else {
                    if (__IS_RUST__)
                        printer.println("self.jj_add_states(" + indices[0] + ", " + indices[1] + ");");
                    else
                        printer.println("jjAddStates(" + indices[0] + ", " + indices[1] + ");");
                }
            }
        }

        if (state.kindToPrint != Integer.MAX_VALUE) {
            printer.outdent();
            printer.println("}");
        }
    }

    private void DumpNonAsciiMove(LinePrinter printer, NfaStateData data, NfaState state, boolean[] dumped) {
        boolean nextIntersects = state.selfLoop() && state.isComposite;

        var cases = new ArrayList<String>();
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

            if (!dumped[element.stateName] && !element.isComposite && (state.nonAsciiMethod == element.nonAsciiMethod)
                    && (state.kindToPrint == element.kindToPrint)
                    && ((state.next.epsilonMovesString == element.next.epsilonMovesString)
                    || ((state.next.epsilonMovesString != null) && (element.next.epsilonMovesString
                    != null)
                    && state.next.epsilonMovesString.equals(element.next.epsilonMovesString)))) {
                dumped[element.stateName] = true;
                if (__IS_RUST__)
                    cases.add("" + element.stateName);
                else
                    printer.println("case " + element.stateName + ":");
            }
        }

        if (__IS_RUST__ && !cases.isEmpty())
            printer.println(String.join(" | ", cases) + " => {");

        if ((state.next == null) || (state.next.usefulEpsilonMoves <= 0)) {
            String kindCheck = " && kind > " + state.kindToPrint;

            if (__IS_RUST__) {
                printer.println("if jj_can_move_" + state.nonAsciiMethod + "(hi_byte, i1, i2, l1, l2)" + kindCheck + " {");
                printer.indent();
                printer.println("kind = " + state.kindToPrint + ";");
                printer.outdent();
                printer.println("}");
            } else {
                printer.println("if (jjCanMove_" + state.nonAsciiMethod + "(hiByte, i1, i2, l1, l2)" + kindCheck + ")");
                printer.indent();
                printer.println("kind = " + state.kindToPrint + ";");
                printer.outdent();
                printer.println("break;");
            }
            return;
        }

        if (__IS_RUST__) {
            if (state.kindToPrint != Integer.MAX_VALUE) {
                printer.println("if !jj_can_move_" + state.nonAsciiMethod + "(hi_byte, i1, i2, l1, l2) {");
                printer.indent();
                printer.println("break;");
                printer.outdent();
                printer.println("}");

                printer.println("if kind > " + state.kindToPrint + " {");
                printer.indent();
                printer.println("kind = " + state.kindToPrint + ";");
                printer.outdent();
                printer.println("}");
            } else {
                printer.println("if jj_can_move_" + state.nonAsciiMethod + "(hi_byte, i1, i2, l1, l2) {");
            }
        } else {
            if (state.kindToPrint != Integer.MAX_VALUE) {
                printer.println("if (!jjCanMove_" + state.nonAsciiMethod + "(hiByte, i1, i2, l1, l2))");
                printer.indent();
                printer.println("break;");
                printer.outdent();

                printer.println("if (kind > " + state.kindToPrint + ")");
                printer.indent();
                printer.println("kind = " + state.kindToPrint + ";");
                printer.outdent();
            } else {
                printer.println("if (jjCanMove_" + state.nonAsciiMethod + "(hiByte, i1, i2, l1, l2))");
            }
        }
        printer.indent();

        if ((state.next != null) && (state.next.usefulEpsilonMoves > 0)) {
            int[] stateNames = data.getNextStates(state.next.epsilonMovesString);
            if (state.next.usefulEpsilonMoves == 1) {
                int name = stateNames[0];
                if (nextIntersects) {
                    if (__IS_RUST__)
                        printer.println("self.jj_check_n_add(" + name + ");");
                    else
                        printer.println("jjCheckNAdd(" + name + ");");
                } else {
                    if (__IS_RUST__) {
                        printer.println("self.jjstate_set[self.jjnew_state_cnt] = " + name + ";");
                        printer.println("self.jjnew_state_cnt += 1;");
                    } else
                        printer.println("jjstateSet[jjnewStateCnt++] = " + name + ";");
                }
            } else if ((state.next.usefulEpsilonMoves == 2) && nextIntersects) {
                if (__IS_RUST__)
                    printer.println("self.jj_check_n_add_two_states(" + stateNames[0] + ", " + stateNames[1] + ");");
                else
                    printer.println("jjCheckNAddTwoStates(" + stateNames[0] + ", " + stateNames[1] + ");");
            } else {
                int[] indices = NfaState.GetStateSetIndicesForUse(data,
                        state.next.epsilonMovesString);
                boolean notTwo = ((indices[0] + 1) != indices[1]);

                if (nextIntersects) {
                    if (__IS_RUST__)
                        printer.print("self.jj_check_n_add_states(" + indices[0]);
                    else
                        printer.print("jjCheckNAddStates(" + indices[0]);
                    data.global.setCheckNAddStates(notTwo);
                    if (notTwo) {
                        printer.print(", " + indices[1]);
                    }
                    printer.println(");");
                } else {
                    if (__IS_RUST__)
                        printer.println("self.jj_add_states(" + indices[0] + ", " + indices[1] + ");");
                    else
                        printer.println("jjAddStates(" + indices[0] + ", " + indices[1] + ");");
                }
            }
        }


        if (!__IS_RUST__)
            printer.println("break;");

        printer.outdent();

        if (__IS_RUST__ && state.kindToPrint == Integer.MAX_VALUE) {
                printer.println("}");
        }
    }

    private void DumpCompositeStatesAsciiMoves(LinePrinter printer, NfaStateData data, String key, int byteNum, boolean[] dumped) {
        int i;
        int[] nameSet = data.getNextStates(key);

        if ((nameSet.length == 1) || dumped[stateNameForComposite(data, key)]) {
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

        var toPrint = false;
        if (stateForCase != null) {
            toPrint = print_no_break(printer, data, stateForCase, byteNum, dumped);
        }

        if (neededStates == 0) {
            if ((stateForCase != null) && !toPrint) {
                printer.println("                  break;");
            }
            return;
        }

        if (neededStates == 1) {
            if (toPrint) {
                print_case(printer, "" + stateForCase.stateName);
            }

            if (__IS_RUST__) {
                var cases = new ArrayList<String>();
                cases.add("" + stateNameForComposite(data, key));
                if (!dumped[toBePrinted.stateName] && !stateBlock && (toBePrinted.inNextOf > 1))
                    cases.add("" + toBePrinted.stateName);
                printer.println("               " + String.join(" | ", cases) + " => {");
            } else {
                printer.println("               case " + stateNameForComposite(data, key) + ":");

                if (!dumped[toBePrinted.stateName] && !stateBlock && (toBePrinted.inNextOf > 1)) {
                    printer.println("               case " + toBePrinted.stateName + ":");
                }
            }

            dumped[toBePrinted.stateName] = true;
            DumpAsciiMove(printer, data, toBePrinted, byteNum, dumped, false);
            return;
        }

        List<List<NfaState>> partition = PartitionStatesSetForAscii(data, nameSet, byteNum);

        if (toPrint) {
            print_case(printer, "" + stateForCase.stateName);
        }

        int keyState = stateNameForComposite(data, key);
        print_case(printer, "" + keyState);
        printer.indent();
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
                DumpAsciiMoveForCompositeState(printer, data, tmp, byteNum, j != 0);
            }
        }

        if (!__IS_RUST__)
            printer.println("break;");
        printer.outdent();
        printer.println("}");
    }

    protected abstract void DumpHeadForCase(LinePrinter printer, int byteNum);

    protected void DumpAsciiMoves(LinePrinter printer, NfaStateData data, int byteNum) {
        boolean[] dumped = new boolean[Math.max(data.generatedStates(), data.dummyStateIndex + 1)];

        DumpHeadForCase(printer, byteNum);

        for (String s : data.compositeStateTable.keySet()) {
            DumpCompositeStatesAsciiMoves(printer, data, s, byteNum, dumped);
        }

        for (var element : data.getAllStates()) {
            if (dumped[element.stateName] || (element.lexState != data.getStateIndex())
                    || !element.HasTransitions() || element.dummy
                    || (element.stateName == -1)) {
                continue;
            }

            var toPrint = false;
            if (element.stateForCase != null) {
                if ((element.inNextOf == 1) || dumped[element.stateForCase.stateName]) {
                    continue;
                }

                toPrint = print_no_break(printer, data, element.stateForCase, byteNum, dumped);

                if (element.asciiMoves[byteNum] == 0L) {
                    if (!toPrint) {
                        printer.println("                  break;");
                    }
                    continue;
                }
            }

            if (element.asciiMoves[byteNum] == 0L) {
                continue;
            }

            if (toPrint) {
                print_case(printer, "" + element.stateForCase);
                printer.indent();
            }

            dumped[element.stateName] = true;
            DumpAsciiMove(printer, data, element, byteNum, dumped, true);
        }

        if (__IS_RUST__) {
            printer.println("_ => {");
            printer.indent();
            if ((byteNum != 0) && (byteNum != 1)) {
                printer.println("break;");
            }
            printer.outdent();
            printer.println("}");

            printer.outdent();
            printer.println("}");
            printer.println("while_cond = i != starts_at;");
            printer.outdent();
            printer.println("}");
        } else {
            printer.println("default: {");
            printer.indent();
            if ((byteNum != 0) && (byteNum != 1))
                printer.println("break;");
            else
                printer.println("break;");
            printer.outdent();
            printer.println("}");

            printer.outdent();
            printer.println("}");
            printer.outdent();
            printer.println("} while (i != startsAt);");
        }
    }

    protected void DumpCharAndRangeMoves(LinePrinter printer, NfaStateData data) {
        boolean[] dumped = new boolean[Math.max(data.generatedStates(), data.dummyStateIndex + 1)];

        DumpHeadForCase(printer, -1);

        for (String s : data.compositeStateTable.keySet()) {
            DumpCompositeStatesNonAsciiMoves(printer, data, s, dumped);
        }

        for (var i = 0; i < data.getAllStateCount(); i++) {
            var temp = data.getAllState(i);
            if ((temp.stateName == -1) || dumped[temp.stateName]
                    || (temp.lexState != data.getStateIndex())
                    || !temp.HasTransitions() || temp.dummy) {
                continue;
            }

            var toPrint = false;
            if (temp.stateForCase != null) {
                if ((temp.inNextOf == 1) || dumped[temp.stateForCase.stateName])
                    continue;

                toPrint = print_no_break(printer, data, temp.stateForCase, -1, dumped);

                if (temp.nonAsciiMethod == -1) {
                    if (!toPrint)
                        printer.println("break;");
                    continue;
                }
            }

            if (temp.nonAsciiMethod == -1)
                continue;

            if (toPrint) {
                print_case(printer, "" + temp.stateForCase.stateName);
                printer.indent();
            }

            dumped[temp.stateName] = true;
            // System.out.println("case : " + temp.stateName);
            print_case(printer, "" + temp.stateName);
            printer.indent();
            DumpNonAsciiMove(printer, data, temp, dumped);
            printer.outdent();
            printer.println("}");
        }

        if (__IS_RUST__) {
            printer.println("_ => {");
            printer.indent();
            printer.println("break;");
            printer.outdent();
            printer.println("}");

            printer.outdent();
            printer.println("}");
            printer.println("while_cond = i != starts_at;");
            printer.outdent();
            printer.println("}");
        } else {
            printer.println("default: {");
            printer.indent();
            printer.println("break;");
            printer.outdent();
            printer.println("}");

            printer.outdent();
            printer.println("}");
            printer.outdent();
            printer.println("} while (i != startsAt);");
        }
    }

    protected String toHexString(long value) {
        if (__IS_RUST__)
            return "0x" + Long.toHexString(value);
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

    private String getLohiBytes(LexerData data, int i) {
        return String.join(", ",
                toHexString(data.getLohiByte(i, 0)),
                toHexString(data.getLohiByte(i, 1)),
                toHexString(data.getLohiByte(i, 2)),
                toHexString(data.getLohiByte(i, 3)));
    }
}
