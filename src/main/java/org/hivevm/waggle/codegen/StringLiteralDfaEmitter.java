// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/RStringLiteral.java

package org.hivevm.waggle.codegen;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.lexer.LexerPlan.CharCase;
import org.hivevm.waggle.lexer.LexerPlan.DfaPos;
import org.hivevm.waggle.lexer.LexerPlan.Exit;
import org.hivevm.waggle.lexer.LexerPlan.Final;
import org.hivevm.waggle.lexer.LexerPlan.Handoff;
import org.hivevm.waggle.lexer.LexerPlan.LexStatePlan;
import org.hivevm.waggle.lexer.LexerPlan.StopCase;
import org.hivevm.waggle.lexer.LexerPlan.StopMatch;

import java.util.List;
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

    public StringLiteralDfaEmitter(TargetSyntax syntax) {
        this.syntax = syntax;
    }

    /**
     * The per-state code of the string-literal DFA. All three targets share it — every place they
     * differ is a dialect hook of {@link TargetSyntax} or one of the small hooks below.
     */
    protected void dumpDfaStates(LinePrinter printer, LexState lex) {
        boolean debug = lex.debug();
        LexStatePlan state = lex.plan();

        for (DfaPos pos : state.positions()) {
            int i = pos.pos();

            printMoveStringLiteralDfaSignature(printer, lex, pos);

            if (i != 0) {
                printActiveCheck(printer, lex, pos);

                if (debug) {
                    this.syntax.printDebugPossibleMatches(printer, pos);
                }

                printEofBailout(printer, lex, pos);
            }

            if ((i != 0) && debug) {
                this.syntax.printDebugCurrentCharacter(printer, lex.withLexState());
            }

            this.syntax.printSwitchOnChar(printer);
            printer.indent();

            for (CharCase charCase : pos.cases()) {
                for (int label : charCase.labels()) {
                    this.syntax.printCharCase(printer, label);
                }

                this.syntax.printCharCaseWithBody(printer, charCase.c());
                printer.indent();

                boolean ifGenerated = false;
                for (Final kind : charCase.finals()) {
                    printFinalKindGuardOpen(printer, ifGenerated, i, kind.word(), kind.bit());
                    ifGenerated = true;

                    switch (kind.action()) {
                        case START_NFA_WITH_STATES -> printer.println("return "
                                + startNfaWithStatesName(lex) + "(" + i + ", " + kind.kind() + ", "
                                + kind.stateSet() + ");");
                        case STOP_AT_POS -> printer.println("return " + stopAtPosName() + "(" + i
                                + ", " + kind.kind() + ");");
                        case KIND_AND_POS -> printMatchedKindAndPos(printer, kind.kind(), i);
                        case KIND -> printer.println(this.syntax.matchedKind() + " = " + kind.kind()
                                + ";");
                    }

                    printFinalKindGuardClose(printer, i);
                }

                if (charCase.next() == Exit.CALL) {
                    // The vectors of the next position: the valid kinds, preceded by the vector
                    // they are masked against from the second position on.
                    var args = new StringJoiner(", ");
                    for (var mask : charCase.masks()) {
                        var validKinds = (mask.mask() != 0L)
                                ? this.syntax.toHexString(mask.mask()) : this.syntax.longZero();
                        args.add((i == 0) ? this.syntax.toHexString(mask.mask())
                                : "active" + mask.word() + ", " + validKinds);
                    }
                    printer.println("return " + moveStringLiteralDfaName(lex, i + 1) + "(" + args + ");");
                } else {
                    printExit(printer, lex, charCase.next(), i);
                }

                printer.outdent();
                printer.println("}");
            }

            this.syntax.printDefaultCaseOpen(printer);
            printer.indent();

            if (debug) {
                this.syntax.printDebugNoMatchPossible(printer);
            }

            printExit(printer, lex, pos.otherwise(), i);

            printer.outdent();
            printer.println("}");

            printer.outdent();
            printer.println("}");

            if (pos.tail()) {
                /*
                 * Here, a string literal is successfully matched and no more string literals are
                 * possible. So set the kind and state set upto and including this position for the
                 * matched string.
                 */
                switch (state.handoff()) {
                    case START_NFA -> this.syntax.printReturn(printer, startNfaName(lex) + "("
                            + (i - 1) + ", " + liveVectors(pos.active()) + ")");
                    case MOVE_NFA -> this.syntax.printReturn(printer,
                            moveNfaCall(lex, String.valueOf(i)));
                    case NONE -> printReturnPosition(printer, i + 1);
                }
            }

            printMoveStringLiteralDfaEnd(printer);
        }

        if (state.startNfaWithStates()) {
            DumpStartWithStates(printer, lex);
        }
    }

    /** How a case of position {@code i} ends, when it does not go on with the next position. */
    private void printExit(LinePrinter printer, LexState lex, Exit exit, int i) {
        switch (exit) {
            case NONE, CALL -> {
            }
            case MOVE_NFA -> printer.println("return " + moveNfaCall(lex, String.valueOf(i)) + ";");
            case BREAK -> this.syntax.printBreak(printer, "");
            case RETURN -> printer.println("return " + (i + 1) + ";");
        }
    }

    /** The active vectors, or zero for those that hold no literal any more. */
    private String liveVectors(List<Boolean> live) {
        var args = new StringJoiner(", ");
        for (int k = 0; k < live.size(); k++) {
            args.add(live.get(k) ? "active" + k : this.syntax.longZero());
        }
        return args.toString();
    }

    /** The call that hands control to the NFA in its initial state at {@code position}. */
    private String moveNfaCall(LexState lex, String position) {
        return this.syntax.moveNfaName(lex) + "(" + lex.plan().initState() + ", " + position + ")";
    }

    /** The name {@code jjStartNfa<state>} is called by. */
    protected String startNfaName(LexState lex) {
        return "jjStartNfa" + lex.suffix();
    }

    /** The name {@code jjStartNfaWithStates<state>} is called by. */
    protected String startNfaWithStatesName(LexState lex) {
        return "jjStartNfaWithStates" + lex.suffix();
    }

    /** The name {@code jjStopAtPos} is called by. */
    protected String stopAtPosName() {
        return "jjStopAtPos";
    }

    /** The name {@code jjMoveStringLiteralDfa<i><state>} is called by. */
    protected String moveStringLiteralDfaName(LexState lex, int i) {
        return "jjMoveStringLiteralDfa" + i + lex.suffix();
    }

    /**
     * Opens the test for one final kind of a literal. From the second position on it is guarded by
     * the kind's bit in its active vector; Java and C++ write the guard as a braceless {@code if}
     * on the line of what it guards.
     */
    protected void printFinalKindGuardOpen(LinePrinter printer, boolean elseIf, int i, int word,
                                           long bit) {
        if (elseIf)
            printer.print("else if ");
        else if (i != 0)
            printer.print("if ");

        if (i != 0) {
            printer.print("((active" + word + " & " + this.syntax.toHexString(bit) + ") != 0L)");
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
    protected void dumpNfaStartStatesCode(LinePrinter printer, LexState lex) {
        LexStatePlan state = lex.plan();
        if (state.stopDfa() == null) { // there is no string literal to stop on
            return;
        }

        int maxKindsReqd = state.words();

        printer.println();
        this.syntax.printPosAndActivesSignature(printer, lex,
                "jjStopStringLiteralDfa" + lex.suffix(), maxKindsReqd);
        printer.indent();

        if (lex.debug()) {
            this.syntax.printDebugNoMoreStringLiteralMatches(printer);
        }

        this.syntax.printSwitchOnPos(printer);
        printer.indent();

        for (var pos : state.stopDfa()) {
            this.syntax.printPosCase(printer, pos.pos());
            printer.indent();

            for (var stop : pos.cases()) {
                boolean first = true;
                for (var guard : stop.guard()) {
                    printer.print(first ? "if (" : " || ");
                    first = false;
                    printer.print("(active" + guard.word() + " & "
                            + this.syntax.toHexString(guard.mask()) + ") != "
                            + this.syntax.longZero());
                }

                printer.print(")");

                boolean hasKind = stop.match() != StopMatch.NONE;
                this.syntax.printStopDfaBodyOpen(printer, hasKind);
                printer.indent();
                printStopMatch(printer, pos.pos(), stop);

                if (stop.resume() == -1) {
                    printer.println("return " + this.syntax.noState() + ";");
                } else {
                    printer.println("return " + stop.resume() + ";");
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
        this.syntax.printPosAndActivesSignature(printer, lex,
                "jjStartNfa" + lex.suffix(), maxKindsReqd);
        this.syntax.printStartNfaBody(printer, lex, activeArguments(maxKindsReqd));
        printer.println("}");
    }

    /** Records the literal a case of {@code jjStopStringLiteralDfa} found matched at {@code i}. */
    private void printStopMatch(LinePrinter printer, int i, StopCase stop) {
        String kind = this.syntax.matchedKind() + " = " + stop.kind() + ";";
        switch (stop.match()) {
            case NONE -> {
            }
            case FIRST -> printer.println(kind);
            case FIRST_AFTER_EMPTY -> {
                printer.println(kind);
                printer.println(this.syntax.matchedPos() + " = 0;");
            }
            case HERE_UNLESS_MATCHED -> {
                printer.println("if (" + this.syntax.matchedPos() + " != " + i + ")  {");
                printer.indent();
                printer.println(kind);
                printer.println(this.syntax.matchedPos() + " = " + i + ";");
                printer.outdent();
                printer.println("}");
            }
            case HERE -> {
                printer.println(kind);
                printer.println(this.syntax.matchedPos() + " = " + i + ";");
            }
            case EARLIER, EARLIER_AT_FIRST -> {
                if (stop.match() == StopMatch.EARLIER) {
                    printer.print("if (" + this.syntax.matchedPos() + " < " + stop.matchedPos() + ")");
                } else {
                    printer.print("if (" + this.syntax.matchedPos() + " == 0)");
                }
                printer.println(" {");
                printer.indent();
                printer.println(kind);
                printer.println(this.syntax.matchedPos() + " = " + stop.matchedPos() + ";");
                printer.outdent();
                printer.println("}");
            }
        }
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
    protected void printMoveStringLiteralDfaSignature(LinePrinter printer, LexState lex,
                                                      DfaPos pos) {
        printer.println();
        this.syntax.printMoveStringLiteralDfaHead(printer, lex, pos.pos());
        printer.print(StringLiteralDfaEmitter.parameterList(pos, this.syntax.longType()));
        printer.println(") {");
        printer.indent();
    }

    /**
     * The parameters of {@code jjMoveStringLiteralDfa<i>} in C-like syntax, for its definition
     * and, in C++, for its declaration in the header.
     */
    public static String parameterList(DfaPos pos, String longType) {
        var params = new StringJoiner(", ");
        for (int j : pos.params()) {
            params.add((pos.pos() == 1) ? longType + " active" + j
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
    protected final void printActiveCheck(LinePrinter printer, LexState lex, DfaPos pos) {
        int i = pos.pos();
        if (i > 1) {
            printActiveTest(printer, pos.params());
            printer.indent();

            switch (lex.plan().handoff()) {
                case START_NFA -> {
                    var args = new StringJoiner(", ");
                    for (int j = 0; j < pos.old().size(); j++) {
                        args.add(pos.old().get(j) ? "old" + j : this.syntax.longZero());
                    }
                    printer.println("return " + startNfaName(lex) + "(" + (i - 2) + ", " + args + ");");
                }
                case MOVE_NFA -> printer.println("return " + moveNfaCall(lex, String.valueOf(i - 1)) + ";");
                case NONE -> printer.println("return " + i + ";");
            }
            printer.outdent();
            printer.println("}");
        }
    }

    /**
     * Opens the block taken when none of the literals still active at this position survives:
     * each vector is masked against the one of the previous position.
     */
    protected void printActiveTest(LinePrinter printer, List<Integer> vectors) {
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
    protected void printEofBailout(LinePrinter printer, LexState lex, DfaPos pos) {
        int i = pos.pos();

        this.syntax.printReadCharGuardOpen(printer);
        printer.indent();

        switch (lex.plan().handoff()) {
            case START_NFA -> {
                printer.println(this.syntax.stopStringLiteralDfaName(lex) + "(" + (i - 1) + ", "
                        + liveVectors(pos.active()) + ");");
                if (lex.debug()) {
                    this.syntax.printDebugCurrentlyMatched(printer);
                }
                printer.println("return " + i + ";");
            }
            case MOVE_NFA -> printer.println("return " + moveNfaCall(lex, String.valueOf(i - 1)) + ";");
            case NONE -> printer.println("return " + i + ";");
        }

        printer.outdent();
        printer.println("}");
        this.syntax.printReadCharAfterGuard(printer);
    }

    /**
     * The string-literal DFA. The prologue below was written out three times, once per target; only
     * the declarations, the field names and the shape of a "return" ever differed. What is left per
     * target is the state machine itself.
     */
    protected void dumpDfaCode(LinePrinter printer, LexState lex) {
        if (lex.plan().positions().isEmpty()) {
            printer.println();
            this.syntax.printMoveStringLiteralDfa0Signature(printer, lex);
            printer.indent();
            if (lex.plan().handoff() != Handoff.NONE)
                printer.println("return " + moveNfaCall(lex, "0") + ";");
            else
                printer.println("return 1;");
            printer.outdent();
            printer.println("}");
            return;
        }

        if (lex.plan().stopAtPos()) {
            this.syntax.printStopAtPosSignature(printer, lex);
            printer.indent();
            printer.println(this.syntax.matchedKind() + " = kind;");
            printer.println(this.syntax.matchedPos() + " = pos;");

            if (lex.debug()) {
                this.syntax.printDebugNoMoreMatches(printer);
            }

            this.syntax.printReturn(printer, "pos + 1");
            printer.outdent();
            printer.println("}");
        }

        dumpDfaStates(printer, lex);
    }

    /**
     * Emits {@code jjStartNfaWithStates}: the string-literal DFA matched, but the NFA may still find
     * a longer match, so hand it the state it left off in.
     */
    protected void DumpStartWithStates(LinePrinter printer, LexState lex) {
        boolean debug = lex.debug();

        this.syntax.printStartNfaWithStatesSignature(printer, lex);
        printer.indent();
        printer.println(this.syntax.matchedKind() + " = kind;");
        printer.println(this.syntax.matchedPos() + " = pos;");

        if (debug) {
            this.syntax.printDebugNoMoreMatches(printer);
        }

        this.syntax.printReadCharOrReturn(printer);

        if (debug) {
            this.syntax.printDebugCurrentCharacter(printer, lex.withLexState());
        }

        printer.println("return " + this.syntax.moveNfaName(lex) + "(state, pos + 1);");
        printer.outdent();
        printer.println("}");
    }
}
