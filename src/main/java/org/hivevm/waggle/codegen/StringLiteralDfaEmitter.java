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
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


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

    /**
     * The per-state code of the string-literal DFA. All three targets share it — every place they
     * differ is a dialect hook of {@link TargetSyntax} or one of the small hooks below.
     */
    protected void dumpDfaStates(LinePrinter printer, NfaStateData data) {
        KindInfo info;
        int maxLongsReqd = (data.getMaxStrKind() / 64) + 1;
        int i, j, k;
        boolean ifGenerated;

        for (i = 0; i < data.getMaxLen(); i++) {
            boolean startNfaNeeded = false;

            printMoveStringLiteralDfaSignature(printer, data, i, maxLongsReqd);

            if (i != 0) {
                printActiveCheck(printer, data, i, maxLongsReqd);

                this.syntax.printDebugPossibleMatches(printer, data, i);

                printEofBailout(printer, data, i, maxLongsReqd);
            }

            if ((i != 0) && data.global.getDebugTokenManager()) {
                this.syntax.printDebugCurrentCharacter(printer, data.global);
            }

            this.syntax.printSwitchOnChar(printer);
            printer.indent();

            for (var entry : data.getCharPosKind(i).entrySet()) {
                info = entry.getValue();
                ifGenerated = false;
                char c = entry.getKey();

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

                            printFinalKindGuardOpen(printer, ifGenerated, i, j, k);
                            ifGenerated = true;

                            int kindToPrint = data.kindToPrint(i, (j * 64) + k);

                            if (!data.isSubString((j * 64) + k)) {
                                int stateSetName = data.getStateSetName(i, (j * 64) + k);

                                if (stateSetName != -1) {
                                    printer.println("return " + startNfaWithStatesName(data) + "(" + i
                                            + ", " + kindToPrint + ", " + stateSetName + ");");
                                } else {
                                    printer.println("return " + stopAtPosName() + "(" + i + ", " + kindToPrint + ");");
                                }
                            } else if (((data.global.initMatch(data.getStateIndex()) != 0)
                                    && (data.global.initMatch(data.getStateIndex())
                                    != Integer.MAX_VALUE)) || (i
                                    != 0)) {
                                printMatchedKindAndPos(printer, kindToPrint, i);
                            } else {
                                printer.println(this.syntax.matchedKind() + " = " + kindToPrint + ";");
                            }

                            printFinalKindGuardClose(printer, i);
                        }
                    }
                }

                if (info.hasValidKindCnt()) {
                    // The vectors of the next position: the valid kinds, preceded by the vector
                    // they are masked against from the second position on.
                    var args = new StringJoiner(", ");
                    for (int v : StringLiteralDfaEmitter.parameterVectors(data, i + 1, maxLongsReqd)) {
                        var validKinds = (info.validKinds[v] != 0L)
                                ? this.syntax.toHexString(info.validKinds[v]) : this.syntax.longZero();
                        args.add((i == 0) ? this.syntax.toHexString(info.validKinds[v]) : "active" + v + ", " + validKinds);
                    }
                    printer.println("return " + moveStringLiteralDfaName(data, i + 1) + "(" + args + ");");
                } else { // A very special case.
                    if ((i == 0) && data.isMixedState()) {

                        if (data.generatedStates() != 0) {
                            printer.println("return " + moveNfaCall(data, "0") + ";");
                        } else {
                            printer.println("return 1;");
                        }
                    } else if (i != 0) // No more str literals to look for
                    {
                        this.syntax.printBreak(printer, "");
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
                    printer.println("return " + moveNfaCall(data, "0") + ";");
                } else {
                    this.syntax.printBreak(printer, "");
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

                    var args = new StringJoiner(", ");
                    for (k = 0; k < maxLongsReqd; k++) {
                        args.add((i <= data.getMaxLenForActive(k)) ? "active" + k : this.syntax.longZero());
                    }
                    this.syntax.printReturn(printer, startNfaName(data) + "(" + (i - 1) + ", " + args + ")");
                } else if (data.generatedStates() != 0)
                    this.syntax.printReturn(printer, moveNfaCall(data, String.valueOf(i)));
                else
                    printReturnPosition(printer, i + 1);
            }

            printMoveStringLiteralDfaEnd(printer);
        }

        if (!data.isMixedState() && (data.generatedStates() != 0) && data.getCreateStartNfa()) {
            DumpStartWithStates(printer, data);
        }
    }

    /** The call that hands control to the NFA in its initial state at {@code position}. */
    private String moveNfaCall(NfaStateData data, String position) {
        return this.syntax.moveNfaName(data) + "(" + this.syntax.InitStateName(data) + ", " + position
                + ")";
    }

    /** The name {@code jjStartNfa<state>} is called by. */
    protected String startNfaName(NfaStateData data) {
        return "jjStartNfa" + data.getLexerStateSuffix();
    }

    /** The name {@code jjStartNfaWithStates<state>} is called by. */
    protected String startNfaWithStatesName(NfaStateData data) {
        return "jjStartNfaWithStates" + data.getLexerStateSuffix();
    }

    /** The name {@code jjStopAtPos} is called by. */
    protected String stopAtPosName() {
        return "jjStopAtPos";
    }

    /** The name {@code jjMoveStringLiteralDfa<i><state>} is called by. */
    protected String moveStringLiteralDfaName(NfaStateData data, int i) {
        return "jjMoveStringLiteralDfa" + i + data.getLexerStateSuffix();
    }

    /**
     * Opens the test for one final kind of a literal. From the second position on it is guarded by
     * the kind's bit in its active vector; Java and C++ write the guard as a braceless {@code if}
     * on the line of what it guards.
     */
    protected void printFinalKindGuardOpen(LinePrinter printer, boolean elseIf, int i, int j, int k) {
        if (elseIf)
            printer.print("else if ");
        else if (i != 0)
            printer.print("if ");

        if (i != 0) {
            printer.print("((active" + j + " & " + this.syntax.toHexString(1L << k) + ") != 0L)");
        }
    }

    /** Closes what {@link #printFinalKindGuardOpen} opened; a braceless guard needs nothing. */
    protected void printFinalKindGuardClose(LinePrinter printer, int i) {
    }

    /** Records a literal matched at position {@code i} that a longer one may still extend. */
    protected void printMatchedKindAndPos(LinePrinter printer, int kind, int i) {
        printer.println(" {");
        printer.indent();
        printer.println(this.syntax.matchedKind() + " = " + kind + ";");
        printer.println(this.syntax.matchedPos() + " = " + i + ";");
        printer.outdent();
        printer.println("}");
    }

    /** Returns the position the DFA stopped at when there is no NFA to hand over to. */
    protected void printReturnPosition(LinePrinter printer, int pos) {
        printer.println("return " + pos + ";");
    }

    /** Closes one {@code jjMoveStringLiteralDfa<i>}. */
    protected void printMoveStringLiteralDfaEnd(LinePrinter printer) {
        printer.outdent();
        printer.println("}");
    }

    /**
     * Emits {@code jjStopStringLiteralDfa}, which reports how far the string-literal DFA got, and
     * {@code jjStartNfa}, which hands the result over to the NFA.
     */
    protected void dumpNfaStartStatesCode(LinePrinter printer, NfaStateData data) {
        Hashtable<String, long[]>[] statesForPos = data.statesForPos;
        if (data.getMaxStrKind() == 0) { // there is no string literal to stop on
            return;
        }

        int maxKindsReqd = (data.getMaxStrKind() / 64) + 1;

        printer.println();
        this.syntax.printPosAndActivesSignature(printer, data,
                "jjStopStringLiteralDfa" + data.getLexerStateSuffix(), maxKindsReqd);
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

                // Every key has at least one active kind, so the condition is never empty.
                boolean condGenerated = false;
                for (int j = 0; j < maxKindsReqd; j++) {
                    if (actives[j] == 0L) {
                        continue;
                    }

                    printer.print(condGenerated ? " || " : "if (");
                    condGenerated = true;
                    printer.print("(active" + j + " & " + this.syntax.toHexString(actives[j]) + ") != "
                            + this.syntax.longZero());
                }

                printer.print(")");

                var stop = data.stopKey(stateSetString);
                int stopKind = stop.kind();
                int matchedPos = stop.matchedPos();
                boolean hasKind = stopKind != Integer.MAX_VALUE;

                this.syntax.printStopDfaBodyOpen(printer, hasKind);
                printer.indent();

                if (hasKind) {
                    if (i == 0) {
                        printer.println(this.syntax.matchedKind() + " = " + stopKind + ";");

                        int initMatch = data.global.initMatch(data.getStateIndex());
                        if ((initMatch != 0) && (initMatch != Integer.MAX_VALUE)) {
                            printer.println(this.syntax.matchedPos() + " = 0;");
                        }
                    } else if (i == matchedPos) {
                        if (data.isSubStringAtPos(i)) {
                            printer.println("if (" + this.syntax.matchedPos() + " != " + i + ")  {");
                            printer.indent();
                            printer.println(this.syntax.matchedKind() + " = " + stopKind + ";");
                            printer.println(this.syntax.matchedPos() + " = " + i + ";");
                            printer.outdent();
                            printer.println("}");
                        } else {
                            printer.println(this.syntax.matchedKind() + " = " + stopKind + ";");
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
                        printer.println(this.syntax.matchedKind() + " = " + stopKind + ";");
                        printer.println(this.syntax.matchedPos() + " = " + matchedPos + ";");
                        printer.outdent();
                        printer.println("}");
                    }
                }

                String stateSet = stop.stateSet();
                if (stateSet.equals("null;")) {
                    printer.println("return " + this.syntax.noState() + ";");
                } else {
                    printer.println("return " + data.compositeStateName(stateSet) + ";");
                }

                printer.outdent();
                this.syntax.printStopDfaBodyClose(printer, hasKind);
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
        this.syntax.printPosAndActivesSignature(printer, data,
                "jjStartNfa" + data.getLexerStateSuffix(), maxKindsReqd);

        String arguments = activeArguments(maxKindsReqd);
        if (data.isMixedState()) {
            if (data.generatedStates() != 0) {
                printer.println("    return " + moveNfaCall(data, "pos + 1") + ";");
            } else {
                printer.println("    return pos + 1;");
            }
        } else {
            this.syntax.printStartNfaBody(printer, data, arguments);
        }
        printer.println("}");
    }

    /** {@code active0, active1, …} — the arguments {@code jjStartNfa} passes on. */
    private static String activeArguments(int maxKindsReqd) {
        return IntStream.range(0, maxKindsReqd).mapToObj(i -> "active" + i)
                .collect(Collectors.joining(", "));
    }

    /**
     * The signature of jjMoveStringLiteralDfa<i>, including its "active"/"old" bit-vector parameters.
     *
     * <p>Java and C++ build a parameter list that starts empty, so each parameter after the first has
     * to prepend a comma; Rust always leads with "&mut self" and therefore overrides this wholesale.
     */
    protected void printMoveStringLiteralDfaSignature(LinePrinter printer, NfaStateData data, int i,
                                                      int maxLongsReqd) {
        printer.println();
        this.syntax.printMoveStringLiteralDfaHead(printer, data, i);
        printer.print(StringLiteralDfaEmitter.parameterList(data, i, maxLongsReqd, this.syntax.longType()));
        printer.println(") {");
        printer.indent();
    }

    /**
     * The vectors {@code jjMoveStringLiteralDfa<i>} takes: those that still hold a literal long
     * enough to reach position {@code i}. The first position takes none.
     */
    public static int[] parameterVectors(NfaStateData data, int i, int maxLongsReqd) {
        return IntStream.range(0, maxLongsReqd)
                .filter(j -> (i == 1) ? (i <= data.getMaxLenForActive(j))
                        : (i > 1) && (i <= (data.getMaxLenForActive(j) + 1)))
                .toArray();
    }

    /**
     * The parameters of {@code jjMoveStringLiteralDfa<i>} in C-like syntax, for its definition
     * and, in C++, for its declaration in the header.
     */
    public static String parameterList(NfaStateData data, int i, int maxLongsReqd, String longType) {
        var params = new StringJoiner(", ");
        for (int j : StringLiteralDfaEmitter.parameterVectors(data, i, maxLongsReqd)) {
            params.add((i == 1) ? longType + " active" + j
                    : longType + " old" + j + ", " + longType + " active" + j);
        }
        return params.toString();
    }

    /**
     * The early exit of jjMoveStringLiteralDfa<i>: when no bit of the "active" vectors survives being
     * masked with "old", no string literal can match any more.
     *
     * <p>Java and C++ fold the masking into the test itself — "(active0 &= old0) | …" — which Rust
     * cannot express, since an assignment is not a value there. Rust therefore overrides this and
     * emits a "let" per vector first.
     */
    protected final void printActiveCheck(LinePrinter printer, NfaStateData data, int i,
                                          int maxLongsReqd) {
        if (i > 1) {
            printActiveTest(printer, StringLiteralDfaEmitter.parameterVectors(data, i, maxLongsReqd));
            printer.indent();

            if (!data.isMixedState() && (data.generatedStates() != 0)) {
                var args = new StringJoiner(", ");
                for (int j = 0; j < maxLongsReqd; j++) {
                    args.add((i <= (data.getMaxLenForActive(j) + 1)) ? "old" + j : this.syntax.longZero());
                }
                printer.println("return " + startNfaName(data) + "(" + (i - 2) + ", " + args + ");");
            } else if (data.generatedStates() != 0) {
                printer.println("return " + moveNfaCall(data, String.valueOf(i - 1)) + ";");
            } else {
                printer.println("return " + i + ";");
            }
            printer.outdent();
            printer.println("}");
        }
    }

    /**
     * Opens the block taken when none of the literals still active at this position survives:
     * each vector is masked against the one of the previous position.
     */
    protected void printActiveTest(LinePrinter printer, int[] vectors) {
        var masked = new StringJoiner(" | ");
        for (int j : vectors) {
            masked.add("(active" + j + " &= old" + j + ")");
        }
        printer.println("if ((" + masked + ") == 0L) {");
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
            printer.print(this.syntax.stopStringLiteralDfaName(data) + "(" + (i - 1) + ", ");
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
            printer.println("return " + moveNfaCall(data, String.valueOf(i - 1)) + ";");
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
                printer.println("return " + moveNfaCall(data, "0") + ";");
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
            this.syntax.printDebugCurrentCharacter(printer, data.global);
        }

        printer.println("return " + this.syntax.moveNfaName(data) + "(state, pos + 1);");
        printer.outdent();
        printer.println("}");
    }
}
