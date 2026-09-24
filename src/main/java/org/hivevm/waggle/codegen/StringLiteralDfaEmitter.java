// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/RStringLiteral.java

package org.hivevm.waggle.codegen;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.lexer.NfaStateData;
import org.hivevm.waggle.lexer.NfaStateData.KindInfo;

import java.util.Hashtable;


/**
 * Emits the string-literal DFA: one {@code jjMoveStringLiteralDfa} per position, plus the
 * {@code jjStopStringLiteralDfa} that hands over to the NFA.
 *
 * <p>These used to be methods of the 3064-line {@code LexerGenerator}, reachable only by
 * extending it; Rust reached its own version by overriding one of them (ADR-0017).
 */
public class StringLiteralDfaEmitter {

    /** How the target spells what this emitter prints. Composed, not inherited (ADR-0017). */
    protected final TargetSyntax syntax;

    /** Whether {@code jjStopAtPos} has been written for this rendering. */
    private boolean stopAtPosDumped;

    public StringLiteralDfaEmitter(TargetSyntax syntax) {
        this.syntax = syntax;
    }

    final boolean isStopAtPosDumped() {
        return this.stopAtPosDumped;
    }

    final void setStopAtPosDumped(boolean dumped) {
        this.stopAtPosDumped = dumped;
    }

    /**
     * The per-state code of the string-literal DFA. Java and C++ share it verbatim — every place
     * they used to differ is now one of the dialect hooks above. Rust still overrides it.
     */
    protected void dumpDfaStates(LinePrinter printer, NfaStateData data) {
        Hashtable<String, ?> tab;
        String key;
        KindInfo info;
        int maxLongsReqd = (data.getMaxStrKind() / 64) + 1;
        int i, j, k;
        boolean ifGenerated;

        for (i = 0; i < data.getMaxLen(); i++) {
            boolean startNfaNeeded = false;
            tab = data.getCharPosKind(i);
            var keys = data.getOrderedCharPosKinds(i);

            printMoveStringLiteralDfaSignature(printer, data, i, maxLongsReqd);

            if (i != 0) {
                printActiveCheck(printer, data, i, maxLongsReqd);

                this.syntax.printDebugPossibleMatches(printer, data, i);

                printEofBailout(printer, data, i, maxLongsReqd);
            }

            if ((i != 0) && data.global.getDebugTokenManager()) {
                this.syntax.printDebugCurrentCharacter(printer, data);
            }

            this.syntax.printSwitchOnChar(printer);
            printer.indent();

            for (String key2 : keys) {
                key = key2;
                info = (KindInfo) tab.get(key);
                ifGenerated = false;
                char c = key.charAt(0);

                if (data.isPlainSkip(info, i, c)) {
                    continue;
                }

                // Since we know key is a single character ...
                if (data.ignoreCase()) {
                    if (c != Character.toUpperCase(c)) {
                        this.syntax.printCharCase(printer, Character.toUpperCase(c));
                    }

                    if (c != Character.toLowerCase(c)) {
                        this.syntax.printCharCase(printer, Character.toLowerCase(c));
                    }
                }

                this.syntax.printCharCaseWithBody(printer, c);
                printer.indent();

                long matchedKind;
                if (info.hasFinalKindCnt()) {
                    for (j = 0; j < maxLongsReqd; j++) {
                        if ((matchedKind = info.finalKinds[j]) == 0L) {
                            continue;
                        }

                        for (k = 0; k < 64; k++) {
                            if ((matchedKind & (1L << k)) == 0L) {
                                continue;
                            }

                            if (ifGenerated)
                                printer.print("else if ");
                            else if (i != 0)
                                printer.print("if ");

                            ifGenerated = true;

                            if (i != 0) {
                                printer.print("((active" + j + " & " + this.syntax.toHexString(1L << k) + ") != 0L)");
                            }

                            int kindToPrint = data.kindToPrint(i, (j * 64) + k);

                            if (!data.isSubString((j * 64) + k)) {
                                int stateSetName = data.getStateSetName(i, (j * 64) + k);

                                if (stateSetName != -1) {
                                    printer.println("return jjStartNfaWithStates"
                                            + data.getLexerStateSuffix() + "(" + i
                                            + ", " + kindToPrint + ", " + stateSetName + ");");
                                } else {
                                    printer.println("return jjStopAtPos" + "(" + i + ", " + kindToPrint + ");");
                                }
                            } else if (((data.global.initMatch(data.getStateIndex()) != 0)
                                    && (data.global.initMatch(data.getStateIndex())
                                    != Integer.MAX_VALUE)) || (i
                                    != 0)) {
                                printer.println(" {");
                                printer.indent();
                                printer.println("jjmatchedKind = " + kindToPrint + ";");
                                printer.println("jjmatchedPos = " + i + ";");
                                printer.outdent();
                                printer.println("}");
                            } else {
                                printer.println("jjmatchedKind = " + kindToPrint + ";");
                            }
                        }
                    }
                }

                if (info.hasValidKindCnt()) {
                    var atLeastOne = false;

                    if (i == 0) {
                        printer.print("return jjMoveStringLiteralDfa" + (i + 1) + data.getLexerStateSuffix() + "(");
                        for (j = 0; j < (maxLongsReqd - 1); j++) {
                            if ((i + 1) <= data.getMaxLenForActive(j)) {
                                if (atLeastOne)
                                    printer.print(", ");
                                else
                                    atLeastOne = true;

                                printer.print(this.syntax.toHexString(info.validKinds[j]));
                            }
                        }

                        if ((i + 1) <= data.getMaxLenForActive(j)) {
                            if (atLeastOne)
                                printer.print(", ");

                            printer.print(this.syntax.toHexString(info.validKinds[j]));
                        }
                    } else {
                        printer.print("return jjMoveStringLiteralDfa" + (i + 1) + data.getLexerStateSuffix() + "(");

                        for (j = 0; j < (maxLongsReqd - 1); j++) {
                            if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
                                if (atLeastOne)
                                    printer.print(", ");
                                else
                                    atLeastOne = true;

                                if (info.validKinds[j] != 0L)
                                    printer.print("active" + j + ", " + this.syntax.toHexString(info.validKinds[j]));
                                else
                                    printer.print("active" + j + ", 0L");
                            }
                        }

                        if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
                            if (atLeastOne)
                                printer.print(", ");
                            if (info.validKinds[j] != 0L)
                                printer.print("active" + j + ", " + this.syntax.toHexString(info.validKinds[j]));
                            else
                                printer.print("active" + j + ", 0L");
                        }
                    }

                    printer.println(");");
                } else { // A very special case.
                    if ((i == 0) && data.isMixedState()) {

                        if (data.generatedStates() != 0) {
                            printer.println("return jjMoveNfa" + data.getLexerStateSuffix() + "(" + this.syntax.InitStateName(data) + ", 0);");
                        } else {
                            printer.println("return 1;");
                        }
                    } else if (i != 0) // No more str literals to look for
                    {
                        printer.println("break;");
                        startNfaNeeded = true;
                    }
                }

                printer.outdent();
                printer.println("}");
            }

            this.syntax.printDefaultCaseOpen(printer);
            printer.indent();

            if (data.global.getDebugTokenManager()) {
                this.syntax.printDebugNoMatchPossible(printer);
            }

            if (data.generatedStates() != 0) {
                if (i == 0) {
                    // This means no string literal is possible. Just move nfa with this guy and return.
                    printer.println("return jjMoveNfa" + data.getLexerStateSuffix() + "("
                            + this.syntax.InitStateName(data) + ", 0);");
                } else {
                    printer.println("break;");
                    startNfaNeeded = true;
                }
            } else {
                printer.println("return " + (i + 1) + ";");
            }

            printer.outdent();
            printer.println("}");

            printer.outdent();
            printer.println("}");

            if ((i != 0) && startNfaNeeded) {
                if (!data.isMixedState() && (data.generatedStates() != 0)) {
                    /*
                     * Here, a string literal is successfully matched and no more string literals are
                     * possible. So set the kind and state set upto and including this position for the
                     * matched string.
                     */

                    printer.print("return jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ");
                    for (k = 0; k < (maxLongsReqd - 1); k++) {
                        if (i <= data.getMaxLenForActive(k))
                            printer.print("active" + k + ", ");
                        else
                            printer.print("0L, ");
                    }
                    if (i <= data.getMaxLenForActive(k))
                        printer.println("active" + k + ");");
                    else
                        printer.println("0L);");
                } else if (data.generatedStates() != 0)
                    printer.println("return jjMoveNfa" + data.getLexerStateSuffix() + "(" + this.syntax.InitStateName(data) + ", " + i + ");");
                else
                    printer.println("return " + (i + 1) + ";");
            }

            printer.outdent();
            printer.println("}");
        }

        if (!data.isMixedState() && (data.generatedStates() != 0) && data.getCreateStartNfa()) {
            DumpStartWithStates(printer, data);
        }
    }

    /**
     * Emits {@code jjStopStringLiteralDfa}, which reports how far the string-literal DFA got, and
     * {@code jjStartNfa}, which hands the result over to the NFA.
     */
    protected void dumpNfaStartStatesCode(LinePrinter printer, NfaStateData data,
                                                Hashtable<String, long[]>[] statesForPos) {
        if (data.getMaxStrKind() == 0) { // there is no string literal to stop on
            return;
        }

        int maxKindsReqd = (data.getMaxStrKind() / 64) + 1;
        boolean condGenerated = false;

        printer.println();
        this.syntax.printStopStringLiteralDfaSignature(printer, data, maxKindsReqd);
        printer.indent();

        if (data.global.getDebugTokenManager()) {
            this.syntax.printDebugNoMoreStringLiteralMatches(printer);
        }

        this.syntax.printSwitchOnPos(printer);
        printer.indent();

        for (int i = 0; i < (data.getMaxLen() - 1); i++) {
            if (statesForPos[i] == null) {
                continue;
            }

            this.syntax.printPosCase(printer, i);
            printer.indent();

            for (String stateSetString : statesForPos[i].keySet()) {
                long[] actives = statesForPos[i].get(stateSetString);

                for (int j = 0; j < maxKindsReqd; j++) {
                    if (actives[j] == 0L) {
                        continue;
                    }

                    printer.print(condGenerated ? " || " : "if (");
                    condGenerated = true;
                    printer.print("(active" + j + " & " + this.syntax.toHexString(actives[j]) + ") != "
                            + this.syntax.longZero());
                }

                if (!condGenerated) {
                    continue;
                }
                printer.print(")");

                int ind = stateSetString.indexOf(", ");
                String kindStr = stateSetString.substring(0, ind);
                String afterKind = stateSetString.substring(ind + 2);
                int matchedPos = Integer.parseInt(afterKind.substring(0, afterKind.indexOf(", ")));
                boolean hasKind = !kindStr.equals(String.valueOf(Integer.MAX_VALUE));

                this.syntax.printStopDfaBodyOpen(printer, hasKind);
                printer.indent();

                if (hasKind) {
                    if (i == 0) {
                        printer.println(this.syntax.matchedKind() + " = " + kindStr + ";");

                        int initMatch = data.global.initMatch(data.getStateIndex());
                        if ((initMatch != 0) && (initMatch != Integer.MAX_VALUE)) {
                            printer.println(this.syntax.matchedPos() + " = 0;");
                        }
                    } else if (i == matchedPos) {
                        if (data.isSubStringAtPos(i)) {
                            printer.println("if (" + this.syntax.matchedPos() + " != " + i + ")  {");
                            printer.indent();
                            printer.println(this.syntax.matchedKind() + " = " + kindStr + ";");
                            printer.println(this.syntax.matchedPos() + " = " + i + ";");
                            printer.outdent();
                            printer.println("}");
                        } else {
                            printer.println(this.syntax.matchedKind() + " = " + kindStr + ";");
                            printer.println(this.syntax.matchedPos() + " = " + i + ";");
                        }
                    } else {
                        if (matchedPos > 0) {
                            printer.print("if (" + this.syntax.matchedPos() + " < " + matchedPos + ")");
                        } else {
                            printer.print("if (" + this.syntax.matchedPos() + " == 0)");
                        }
                        printer.println(" {");
                        printer.indent();
                        printer.println(this.syntax.matchedKind() + " = " + kindStr + ";");
                        printer.println(this.syntax.matchedPos() + " = " + matchedPos + ";");
                        printer.outdent();
                        printer.println("}");
                    }
                }

                String stateSet = afterKind.substring(afterKind.indexOf(", ") + 2);
                if (stateSet.equals("null;")) {
                    printer.println("return " + this.syntax.noState() + ";");
                } else {
                    printer.println("return " + data.compositeStateName(stateSet) + ";");
                }

                printer.outdent();
                this.syntax.printStopDfaBodyClose(printer, hasKind);
                condGenerated = false;
            }

            printer.println("return " + this.syntax.noState() + ";");
            printer.outdent();
            this.syntax.printPosCaseEnd(printer);
        }

        this.syntax.printPosDefault(printer);
        printer.outdent();
        printer.println("}");
        printer.outdent();
        printer.println("}");

        printer.println();
        this.syntax.printStartNfaSignature(printer, data, maxKindsReqd);

        String arguments = TargetSyntax.activeArguments(maxKindsReqd);
        if (data.isMixedState()) {
            if (data.generatedStates() != 0) {
                printer.println("    return " + this.syntax.moveNfaName(data) + "(" + this.syntax.InitStateName(data)
                        + ", pos + 1);");
            } else {
                printer.println("    return pos + 1;");
            }
        } else {
            this.syntax.printStartNfaBody(printer, data, arguments);
        }
        printer.println("}");
    }

    /**
     * The signature of jjMoveStringLiteralDfa<i>, including its "active"/"old" bit-vector parameters.
     *
     * <p>Java and C++ build a parameter list that starts empty, so each parameter after the first has
     * to prepend a comma; Rust always leads with "&mut self" and therefore overrides this wholesale.
     */
    protected void printMoveStringLiteralDfaSignature(LinePrinter printer, NfaStateData data, int i,
                                                      int maxLongsReqd) {
        boolean atLeastOne = false;
        int j;

        printer.println();
        this.syntax.printMoveStringLiteralDfaHead(printer, data, i);

        if (i != 0) {
            if (i == 1) {
                for (j = 0; j < (maxLongsReqd - 1); j++) {
                    if (i <= data.getMaxLenForActive(j)) {
                        if (atLeastOne) {
                            printer.print(", ");
                        } else {
                            atLeastOne = true;
                        }
                        printer.print(this.syntax.longType() + " active" + j);
                    }
                }

                if (i <= data.getMaxLenForActive(j)) {
                    if (atLeastOne) {
                        printer.print(", ");
                    }
                    printer.print(this.syntax.longType() + " active" + j);
                }
            } else {
                for (j = 0; j < (maxLongsReqd - 1); j++) {
                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        if (atLeastOne) {
                            printer.print(", ");
                        } else {
                            atLeastOne = true;
                        }
                        printer.print(this.syntax.longType() + " old" + j + ", " + this.syntax.longType() + " active" + j);
                    }
                }

                if (i <= (data.getMaxLenForActive(j) + 1)) {
                    if (atLeastOne) {
                        printer.print(", ");
                    }
                    printer.print(this.syntax.longType() + " old" + j + ", " + this.syntax.longType() + " active" + j);
                }
            }
        }

        printer.println(") {");
        printer.indent();
    }

    /**
     * The early exit of jjMoveStringLiteralDfa<i>: when no bit of the "active" vectors survives being
     * masked with "old", no string literal can match any more.
     *
     * <p>Java and C++ fold the masking into the test itself — "(active0 &= old0) | …" — which Rust
     * cannot express, since an assignment is not a value there. Rust therefore overrides this and
     * emits a "let" per vector first.
     */
    protected void printActiveCheck(LinePrinter printer, NfaStateData data, int i,
                                   int maxLongsReqd) {
        int j;

        if (i > 1) {
            var atLeastOne = false;
            printer.print("if ((");

            for (j = 0; j < (maxLongsReqd - 1); j++) {
                if (i <= (data.getMaxLenForActive(j) + 1)) {
                    if (atLeastOne) {
                        printer.print(" | ");
                    } else {
                        atLeastOne = true;
                    }
                    printer.print("(active" + j + " &= old" + j + ")");
                }
            }

            if (i <= (data.getMaxLenForActive(j) + 1)) {
                if (atLeastOne)
                    printer.print(" | ");
                printer.print("(active" + j + " &= old" + j + ")");
            }
            printer.println(") == 0L) {");
            printer.indent();

            if (!data.isMixedState() && (data.generatedStates() != 0)) {
                printer.print("return jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 2) + ", ");
                for (j = 0; j < (maxLongsReqd - 1); j++) {
                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        printer.print("old" + j + ", ");
                    } else {
                        printer.print("0L, ");
                    }
                }
                if (i <= (data.getMaxLenForActive(j) + 1)) {
                    printer.println("old" + j + ");");
                } else {
                    printer.println("0L);");
                }
            } else if (data.generatedStates() != 0) {
                printer.println("return jjMoveNfa" + data.getLexerStateSuffix() + "("
                        + this.syntax.InitStateName(data) + ", " + (i - 1) + ");");
            } else {
                printer.println("return " + i + ";");
            }
            printer.outdent();
            printer.println("}");
        }
    }

    /**
     * Reads the next character and, when the input is exhausted, bails out of the string-literal DFA.
     * The guard around the read differs per target (try/catch, endOfInput, Result), the body does not.
     */
    protected void printEofBailout(LinePrinter printer, NfaStateData data, int i, int maxLongsReqd) {
        int k;

        this.syntax.printReadCharGuardOpen(printer);
        printer.indent();

        if (!data.isMixedState() && (data.generatedStates() != 0)) {
            printer.print(this.syntax.stopStringLiteralDfaCall(data, i));
            for (k = 0; k < (maxLongsReqd - 1); k++) {
                if (i <= data.getMaxLenForActive(k)) {
                    printer.print("active" + k + ", ");
                } else {
                    printer.print(this.syntax.longZero() + ", ");
                }
            }

            if (i <= data.getMaxLenForActive(k))
                printer.println("active" + k + ");");
            else
                printer.println(this.syntax.longZero() + ");");

            if (data.global.getDebugTokenManager()) {
                this.syntax.printDebugCurrentlyMatched(printer);
            }
            printer.println("return " + i + ";");
        } else if (data.generatedStates() != 0)
            printer.println("return " + this.syntax.moveNfaCall(data, i - 1) + ";");
        else
            printer.println("return " + i + ";");

        printer.outdent();
        printer.println("}");
        this.syntax.printReadCharAfterGuard(printer);
    }

    /**
     * The string-literal DFA. The prologue below was written out three times, once per target; only
     * the declarations, the field names and the shape of a "return" ever differed. What is left per
     * target is the state machine itself.
     */
    protected void dumpDfaCode(LinePrinter printer, NfaStateData data) {
        if (data.getMaxLen() == 0) {
            printer.println();
            this.syntax.printMoveStringLiteralDfa0Signature(printer, data);
            printer.indent();
            if (data.generatedStates() > 0)
                printer.println("return " + this.syntax.self() + "jjMoveNfa" + data.getLexerStateSuffix() + "(" + this.syntax.InitStateName(data) + ", 0);");
            else
                printer.println("return 1;");
            printer.outdent();
            printer.println("}");
            return;
        }

        if (!this.stopAtPosDumped) {
            this.syntax.printStopAtPosSignature(printer, data);
            printer.indent();
            printer.println(this.syntax.matchedKind() + " = kind;");
            printer.println(this.syntax.matchedPos() + " = pos;");

            if (data.global.getDebugTokenManager()) {
                this.syntax.printDebugNoMoreMatches(printer);
            }

            this.syntax.printReturn(printer, "pos + 1");
            printer.outdent();
            printer.println("}");
            this.stopAtPosDumped = true;
        }

        dumpDfaStates(printer, data);
    }

    /**
     * Emits {@code jjStartNfaWithStates}: the string-literal DFA matched, but the NFA may still find
     * a longer match, so hand it the state it left off in.
     */
    protected void DumpStartWithStates(LinePrinter printer, NfaStateData data) {
        boolean debug = data.global.getDebugTokenManager();

        this.syntax.printStartNfaWithStatesSignature(printer, data);
        printer.indent();
        printer.println(this.syntax.matchedKind() + " = kind;");
        printer.println(this.syntax.matchedPos() + " = pos;");

        if (debug) {
            this.syntax.printDebugNoMoreMatches(printer);
        }

        this.syntax.printReadCharOrReturn(printer);

        if (debug) {
            this.syntax.printDebugCurrentCharacter(printer, data);
        }

        printer.println("return " + this.syntax.moveNfaName(data) + "(state, pos + 1);");
        printer.outdent();
        printer.println("}");
    }
}
