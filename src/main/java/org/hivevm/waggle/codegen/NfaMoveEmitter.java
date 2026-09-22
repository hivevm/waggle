// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.lexer.NfaState;
import org.hivevm.waggle.lexer.NfaStateData;

import java.util.ArrayList;
import java.util.List;

/**
 * Emits the NFA move loop: {@code jjMoveNfa} and the ASCII and non-ASCII move code for single
 * and composite states.
 *
 * <p>These used to be methods of the 3064-line {@code LexerGenerator}, reachable only by
 * extending it; Rust reached its own version by overriding one of them (ADR-0017).
 */
public class NfaMoveEmitter {

    /** How the target spells what this emitter prints. Composed, not inherited (ADR-0017). */
    protected final TargetSyntax syntax;

    public NfaMoveEmitter(TargetSyntax syntax) {
        this.syntax = syntax;
    }

    /** Emits {@code jjMoveNfa}, the interpreter loop of the generated NFA. */
    protected void dumpMoveNfa(LinePrinter printer, NfaStateData data) {
        boolean debug = data.global.options().getDebugTokenManager();
        String noKind = "0x" + Integer.toHexString(Integer.MAX_VALUE);

        printer.println();
        this.syntax.printMoveNfaSignature(printer, data);
        printer.indent();

        if (data.generatedStates() == 0) {
            printer.println("return curPos;");
            printer.outdent();
            printer.println("}");
            return;
        }

        if (data.isMixedState()) {
            this.syntax.printMoveNfaMixedPrologue(printer);
        }

        printer.println("int startsAt = 0;");
        printer.println("jjnewStateCnt = " + data.generatedStates() + ";");
        printer.println("int i = 1;");
        printer.println("jjstateSet[0] = startState;");

        if (debug) {
            this.syntax.printDebugStartingNfa(printer);
            this.syntax.printDebugCurrentCharacter(printer, data);
        }

        printer.println("int kind = " + noKind + ";");
        this.syntax.printForEver(printer);
        printer.indent();
        printer.println("if (++jjround == " + noKind + ")");
        printer.println("    ReInitRounds();");

        printer.println("if (curChar < 64) {");
        printer.indent();
        DumpAsciiMoves(printer, data, 0);
        printer.outdent();

        printer.println("} else if (curChar < 128) {");
        printer.indent();
        DumpAsciiMoves(printer, data, 1);
        printer.outdent();

        printer.println("} else {");
        printer.indent();
        DumpCharAndRangeMoves(printer, data);
        printer.outdent();
        printer.println("}");

        printer.println("if (kind != " + noKind + ") {");
        printer.println("    jjmatchedKind = kind;");
        printer.println("    jjmatchedPos = curPos;");
        printer.println("    kind = " + noKind + ";");
        printer.println("}");
        printer.println("curPos++;");

        if (debug) {
            this.syntax.printDebugCurrentlyMatched(printer);
        }

        this.syntax.printSwapStateSets(printer, data);

        if (debug) {
            this.syntax.printDebugPossibleLongerMatches(printer);
        }

        this.syntax.printReadCharOrLeave(printer, data);

        if (debug) {
            this.syntax.printDebugCurrentCharacter(printer, data);
        }
        printer.outdent();
        printer.println("}");

        if (data.isMixedState()) {
            printMoveNfaMixedEpilogue(printer);
        }

        printer.outdent();
        printer.println("}");
    }

    /**
     * @param cases      labels the caller has already collected for this very body -- Java writes
     *                   each one out on the spot and lets them fall through, Rust has to join them
     *                   into a single match arm, so there may only ever be one list per body
     * @param openIndent the indentation of that joined arm
     */
    protected void DumpAsciiMove(LinePrinter printer, NfaStateData data, NfaState state, int byteNum,
                               boolean[] dumped, boolean use_state_name, List<String> cases,
                               String openIndent) {
        boolean nextIntersects = state.selfLoop() && state.isComposite;
        boolean onlyState = true;
        if (use_state_name) {
            this.syntax.printCaseLabel(printer, cases, state.stateName);
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

            if (!nextIntersects && data.intersects(element.next.epsilonMovesString,
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
                this.syntax.printCaseLabel(printer, cases, element.stateName);
            }
        }

        this.syntax.printCasesOpen(printer, cases, openIndent);

        printer.indent();

        int oneBit = state.onlyOneAsciiMove(byteNum);
        if ((state.asciiMoves[byteNum] != 0xffffffffffffffffL)
                && (((state.next == null) || (state.next.usefulEpsilonMoves == 0))
                && (state.kindToPrint != Integer.MAX_VALUE))) {
            String kindCheck = "";

            if (!onlyState) {
                kindCheck = " && kind > " + state.kindToPrint;
            }

            String condition = (oneBit != -1)
                    ? this.syntax.charEquals((64 * byteNum) + oneBit)
                    : this.syntax.bitIsSet(state.asciiMoves[byteNum]);

            this.syntax.printIfNoBlock(printer, condition + kindCheck);
            printer.indent();
            printer.println("kind = " + state.kindToPrint + ";");
            printer.outdent();
            this.syntax.printEndIf(printer);
            this.syntax.printBreak(printer, "");

            printer.outdent();
            if (!cases.isEmpty()) {
                this.syntax.printCasesClose(printer);
            }
            return;
        }

        boolean hasIf = false;
        if (state.kindToPrint != Integer.MAX_VALUE) {
            if (oneBit != -1) {
                this.syntax.printIf(printer, this.syntax.charNotEquals((64 * byteNum) + oneBit));
                printer.indent();
                printer.println("break;");
                printer.outdent();
                printer.println("}");
            } else if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
                this.syntax.printIf(printer, this.syntax.bitIsClear(state.asciiMoves[byteNum]));
                printer.indent();
                printer.println("break;");
                printer.outdent();
                printer.println("}");
            }

            if (onlyState) {
                printer.println("kind = " + state.kindToPrint + ";");
            } else {
                this.syntax.printIf(printer, this.syntax.kindIsWeakerThan(state.kindToPrint));
                printer.indent();
                printer.println("kind = " + state.kindToPrint + ";");
                printer.outdent();
                printer.println("}");
            }
        } else if (oneBit != -1) {
            this.syntax.printIfNoBlock(printer, this.syntax.charEquals((64 * byteNum) + oneBit));
            hasIf = true;
            printer.indent();
        } else if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
            this.syntax.printIfNoBlock(printer, this.syntax.bitIsSet(state.asciiMoves[byteNum]));
            hasIf = true;
            printer.indent();
        }

        printNextStates(printer, data, state, nextIntersects);

        if (hasIf) {
            printer.outdent();
        }

        this.syntax.printBreak(printer, "");

        if (hasIf) {
            this.syntax.printEndIf(printer);
        }

        printer.outdent();
        // Close exactly what printCasesOpen opened -- it is driven by the labels, not by
        // use_state_name. Rust lost the closing brace of every arm whose only labels came
        // from the states merged into it.
        if (!cases.isEmpty()) {
            this.syntax.printCasesClose(printer);
        }
    }

    protected void DumpCompositeStatesAsciiMoves(LinePrinter printer, NfaStateData data, String key, int byteNum, boolean[] dumped) {
        int i;
        int[] nameSet = data.getNextStates(key);

        if ((nameSet.length == 1) || dumped[data.compositeStateName(key)]) {
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
                    throw new IllegalStateException(
                            "Two NFA states of the same composite state claim stateForCase");
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
            var cases = new ArrayList<String>();
            if (toPrint) {
                this.syntax.printCaseLabel(printer, cases, "               ", stateForCase.stateName);
            }
            this.syntax.printCaseLabel(printer, cases, "               ", data.compositeStateName(key));
            if (!dumped[toBePrinted.stateName] && !stateBlock && (toBePrinted.inNextOf > 1)) {
                this.syntax.printCaseLabel(printer, cases, "               ", toBePrinted.stateName);
            }

            dumped[toBePrinted.stateName] = true;
            DumpAsciiMove(printer, data, toBePrinted, byteNum, dumped, false, cases,
                    "               ");
            return;
        }

        List<List<NfaState>> partition = PartitionStatesSetForAscii(data, nameSet, byteNum);

        var cases = new ArrayList<String>();
        if (toPrint) {
            this.syntax.printCaseLabel(printer, cases, stateForCase.stateName);
        }

        int keyState = data.compositeStateName(key);
        this.syntax.printCaseLabel(printer, cases, keyState);
        this.syntax.printCasesOpen(printer, cases);
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

        this.syntax.printBreak(printer, "");
        printer.outdent();
        this.syntax.printCasesClose(printer);
    }

    protected void DumpCompositeStatesNonAsciiMoves(LinePrinter printer, NfaStateData data,
                                                  String key, boolean[] dumped) {
        int[] nameSet = data.getNextStates(key);
        if ((nameSet.length == 1) || dumped[data.compositeStateName(key)]) {
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
                    throw new IllegalStateException(
                            "Two NFA states of the same composite state claim stateForCase");
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
            // "stateForCase" is one more label on the very same body. Opening a block for it turned
            // the labels that follow into orphaned cases -- the generated Java did not compile.
            var cases = new ArrayList<String>();
            if (toPrint) {
                this.syntax.printCaseLabel(printer, cases, stateForCase.stateName);
            }
            this.syntax.printCaseLabel(printer, cases, data.compositeStateName(key));
            if (!dumped[toBePrinted.stateName] && !stateBlock && (toBePrinted.inNextOf > 1)) {
                this.syntax.printCaseLabel(printer, cases, toBePrinted.stateName);
            }

            dumped[toBePrinted.stateName] = true;
            DumpNonAsciiMove(printer, data, toBePrinted, dumped, cases);
            return;
        }

        var cases = new ArrayList<String>();
        if (toPrint) {
            this.syntax.printCaseLabel(printer, cases, stateForCase.stateName);
        }

        int keyState = data.compositeStateName(key);
        this.syntax.printCaseLabel(printer, cases, keyState);
        this.syntax.printCasesOpen(printer, cases);
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

        this.syntax.printBreak(printer, "    ");
        this.syntax.printCasesClose(printer);
    }

    /** @param cases see {@link #DumpAsciiMove} -- one list per body, never two. */
    protected void DumpNonAsciiMove(LinePrinter printer, NfaStateData data, NfaState state,
                                  boolean[] dumped, List<String> cases) {
        boolean nextIntersects = state.selfLoop() && state.isComposite;

        for (NfaState element : data.getAllStates()) {
            if ((state == element) || (element.stateName == -1) || element.dummy || (state.stateName
                    == element.stateName)
                    || (element.nonAsciiMethod == -1)) {
                continue;
            }

            if (!nextIntersects && data.intersects(element.next.epsilonMovesString,
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
                this.syntax.printCaseLabel(printer, cases, element.stateName);
            }
        }

        this.syntax.printCasesOpen(printer, cases);

        if ((state.next == null) || (state.next.usefulEpsilonMoves <= 0)) {
            String kindCheck = " && kind > " + state.kindToPrint;

            this.syntax.printIfNoBlock(printer, this.syntax.canMove(state) + kindCheck);
            printer.indent();
            printer.println("kind = " + state.kindToPrint + ";");
            printer.outdent();
            this.syntax.printEndIf(printer);
            this.syntax.printBreak(printer, "");

            // This branch used to return without closing what printCasesOpen opened. Java and C++
            // never noticed -- their case labels carry no braces -- but every Rust match arm that
            // took it was left hanging open.
            if (!cases.isEmpty()) {
                this.syntax.printCasesClose(printer);
            }
            return;
        }

        if (state.kindToPrint != Integer.MAX_VALUE) {
            this.syntax.printIfNoBlock(printer, "!" + this.syntax.canMove(state));
            printer.indent();
            printer.println("break;");
            printer.outdent();
            this.syntax.printEndIf(printer);

            this.syntax.printIfNoBlock(printer, this.syntax.kindIsWeakerThan(state.kindToPrint));
            printer.indent();
            printer.println("kind = " + state.kindToPrint + ";");
            printer.outdent();
            this.syntax.printEndIf(printer);
        } else {
            this.syntax.printIfNoBlock(printer, this.syntax.canMove(state));
        }
        printer.indent();

        printNextStates(printer, data, state, nextIntersects);

        this.syntax.printBreak(printer, "");

        printer.outdent();

        if (state.kindToPrint == Integer.MAX_VALUE) {
            this.syntax.printEndIf(printer);
        }

        if (!cases.isEmpty()) {
            this.syntax.printCasesClose(printer);
        }
    }

    protected void DumpAsciiMoveForCompositeState(LinePrinter printer, NfaStateData data,
                                                NfaState state, int byteNum, boolean elseNeeded) {
        boolean nextIntersects = state.selfLoop();

        for (NfaState temp1 : data.getAllStates()) {
            if ((state == temp1) || (temp1.stateName == -1) || temp1.dummy || (state.stateName
                    == temp1.stateName)
                    || (temp1.asciiMoves[byteNum] == 0L)) {
                continue;
            }

            if (!nextIntersects && data.intersects(temp1.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
                break;
            }
        }

        boolean hasIf = false;
        if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
            int oneBit = state.onlyOneAsciiMove(byteNum);

            var cond = (elseNeeded ? "else if " : "if ");
            this.syntax.printIfNoBlock(printer, cond, (oneBit != -1)
                    ? this.syntax.charEquals((64 * byteNum) + oneBit)
                    : this.syntax.bitIsSet(state.asciiMoves[byteNum]));
            hasIf = true;
        }
        printer.indent();

        if (state.kindToPrint != Integer.MAX_VALUE) {
            if (state.asciiMoves[byteNum] != 0xffffffffffffffffL) {
                printer.println("{");
            }

            this.syntax.printIf(printer, this.syntax.kindIsWeakerThan(state.kindToPrint));
            printer.indent();
            printer.println("kind = " + state.kindToPrint + ";");
            printer.outdent();
            printer.println("}");
        }

        printNextStates(printer, data, state, nextIntersects);

        printer.outdent();
        if ((state.asciiMoves[byteNum] != 0xffffffffffffffffL) && (state.kindToPrint != Integer.MAX_VALUE)) {
            printer.println("}");
        }

        if (hasIf) {
            this.syntax.printEndIf(printer);
        }
    }

    protected void DumpNonAsciiMoveForCompositeState(LinePrinter printer, NfaStateData data,
                                                   NfaState state) {
        boolean nextIntersects = state.selfLoop();
        for (NfaState temp1 : data.getAllStates()) {
            if ((state == temp1) || (temp1.stateName == -1) || temp1.dummy || (state.stateName
                    == temp1.stateName)
                    || (temp1.nonAsciiMethod == -1)) {
                continue;
            }

            if (!nextIntersects && data.intersects(temp1.next.epsilonMovesString,
                    state.next.epsilonMovesString)) {
                nextIntersects = true;
                break;
            }
        }

        printer.println("if (" + this.syntax.canMove(state) + ")");

        if (state.kindToPrint != Integer.MAX_VALUE) {
            printer.println("{");
            printer.indent();
            this.syntax.printIf(printer, this.syntax.kindIsWeakerThan(state.kindToPrint));
            printer.indent();
            printer.println("kind = " + state.kindToPrint + ";");
            printer.outdent();
            printer.println("}");
        }

        printNextStates(printer, data, state, nextIntersects);

        if (state.kindToPrint != Integer.MAX_VALUE) {
            printer.outdent();
            printer.println("}");
        }
    }

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

            // A stateForCase without moves of its own shares this body, as in the composite and
            // non-ASCII paths. This used to print "case " + the NfaState object itself and open a
            // block that was never closed.
            var cases = new ArrayList<String>();
            if (toPrint) {
                this.syntax.printCaseLabel(printer, cases, element.stateForCase.stateName);
            }

            dumped[element.stateName] = true;
            DumpAsciiMove(printer, data, element, byteNum, dumped, true, cases, "");
        }

        this.syntax.printDefaultAndEndLoop(printer, (byteNum != 0) && (byteNum != 1));
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

            var cases = new ArrayList<String>();
            if (toPrint) {
                this.syntax.printCaseLabel(printer, cases, temp.stateForCase.stateName);
            }

            dumped[temp.stateName] = true;
            this.syntax.printCaseLabel(printer, cases, temp.stateName);
            printer.indent();
            DumpNonAsciiMove(printer, data, temp, dumped, cases);
            printer.outdent();
        }

        this.syntax.printDefaultAndEndLoop(printer, true);
    }

    protected List<List<NfaState>> PartitionStatesSetForAscii(NfaStateData data, int[] states, int byteNum) {
        var cardinalities = new int[states.length];
        var original = new ArrayList<NfaState>();
        var partition = new ArrayList<List<NfaState>>();
        NfaState tmp;
        int cnt = 0;
        for (int i = 0; i < states.length; i++) {
            tmp = data.getAllState(states[i]);

            if (tmp.asciiMoves[byteNum] != 0L) {
                int j;
                int p = Long.bitCount(tmp.asciiMoves[byteNum]);

                for (j = 0; j < i; j++) {
                    if (cardinalities[j] <= p) {
                        break;
                    }
                }

                for (int k = i; k > j; k--) {
                    cardinalities[k] = cardinalities[k - 1];
                }

                cardinalities[j] = p;
                original.add(j, tmp);
                cnt++;
            }
        }

        while (!original.isEmpty()) {
            tmp = original.getFirst();
            original.remove(tmp);

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

    /**
     * Emits the move into the next state set of "state". This was written out four times, verbatim,
     * across DumpAsciiMove, DumpAsciiMoveForCompositeState, DumpNonAsciiMove and
     * DumpNonAsciiMoveForCompositeState.
     */
    protected void printNextStates(LinePrinter printer, NfaStateData data, NfaState state,
                                 boolean nextIntersects) {
        if ((state.next == null) || (state.next.usefulEpsilonMoves <= 0)) {
            return;
        }

        int[] stateNames = data.getNextStates(state.next.epsilonMovesString);

        if (state.next.usefulEpsilonMoves == 1) {
            if (nextIntersects) {
                this.syntax.printCheckNAdd(printer, stateNames[0]);
            } else {
                this.syntax.printAddState(printer, stateNames[0]);
            }
            return;
        }

        if ((state.next.usefulEpsilonMoves == 2) && nextIntersects) {
            this.syntax.printCheckNAddTwoStates(printer, stateNames[0], stateNames[1]);
            return;
        }

        int[] indices = data.getStateSetIndices(state.next.epsilonMovesString);
        boolean isRange = (indices[0] + 1) != indices[1];

        // Whether jjCheckNAddStates is needed at all was decided in stage 4 (DfaBuilder); the
        // template reads that flag before any of this is rendered.
        if (nextIntersects) {
            this.syntax.printCheckNAddStates(printer, indices[0], indices[1], isRange);
        } else {
            this.syntax.printAddStates(printer, indices[0], indices[1]);
        }
    }

    /** See {@link #this.syntax.printMoveNfaMixedPrologue(LinePrinter)}. */
    protected void printMoveNfaMixedEpilogue(LinePrinter printer) {
        printer.print("""
                if (jjmatchedPos > strPos)
                    return curPos;

                int toRet = Math.max(curPos, seenUpto);
                if (curPos < toRet)
                    for (i = toRet - Math.min(curPos, seenUpto); i-- > 0; )
                        try {
                            curChar = input_stream.readChar();
                        } catch (java.io.IOException e) {
                            throw new Error("Internal Error : Please send a bug report.");
                        }

                if (jjmatchedPos < strPos) {
                    jjmatchedKind = strKind;
                    jjmatchedPos = strPos;
                } else if (jjmatchedPos == strPos && jjmatchedKind > strKind)
                    jjmatchedKind = strKind;

                return toRet;
                """);
    }

    protected boolean print_no_break(LinePrinter printer, NfaStateData data, NfaState state, int byteNum, boolean[] dumped) {
        if (state.inNextOf != 1) {
            throw new IllegalStateException(
                    "NFA state " + state.stateName + " is the case of more than one state");
        }

        dumped[state.stateName] = true;

        if (byteNum >= 0) {
            if (state.asciiMoves[byteNum] != 0L) {
                this.syntax.print_case(printer, "" + state.stateName);
                DumpAsciiMoveForCompositeState(printer, data, state, byteNum, false);
                printer.println("}");
                return false;
            }
        } else if (state.nonAsciiMethod != -1) {
            this.syntax.print_case(printer, "" + state.stateName);
            DumpNonAsciiMoveForCompositeState(printer, data, state);
            printer.println("}");
            return false;
        }
        return true;
    }

    protected void DumpHeadForCase(LinePrinter printer, int byteNum) {
        this.syntax.printCharBits(printer, byteNum);
        this.syntax.printMatchLoopOpen(printer);
        printer.indent();
        this.syntax.printSwitchOnStateSet(printer);
        printer.indent();
    }
}
