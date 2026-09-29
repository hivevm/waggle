// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/NfaState.java

package org.hivevm.waggle.codegen;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.lexer.LexerPlan.Accept;
import org.hivevm.waggle.lexer.LexerPlan.Guard;
import org.hivevm.waggle.lexer.LexerPlan.Move;
import org.hivevm.waggle.lexer.LexerPlan.MoveArm;
import org.hivevm.waggle.lexer.LexerPlan.NextStates;
import org.hivevm.waggle.lexer.LexerPlan.NfaMoves;

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

    /** The indentation of the labels of a composite state of which one member moves. */
    private static final String COMPOSITE_INDENT = "               ";

    public NfaMoveEmitter(TargetSyntax syntax) {
        this.syntax = syntax;
    }

    /** Emits {@code jjMoveNfa}, the interpreter loop of the generated NFA. */
    protected void dumpMoveNfa(LinePrinter printer, LexState lex) {
        boolean debug = lex.debug();
        NfaMoves moves = lex.plan().moves();
        String noKind = "0x" + Integer.toHexString(Integer.MAX_VALUE);

        printer.println();
        this.syntax.printMoveNfaSignature(printer, lex);
        printer.indent();

        if (lex.mixed()) {
            this.syntax.printMoveNfaMixedPrologue(printer);
        }

        printMoveNfaLocals(printer, lex);

        if (debug) {
            this.syntax.printDebugStartingNfa(printer);
            this.syntax.printDebugCurrentCharacter(printer, lex.withLexState());
        }

        printKindInit(printer, noKind);
        this.syntax.printForEver(printer);
        printer.indent();
        printNextRound(printer, noKind);

        printer.println("if " + curCharBelow(64) + " {");
        printer.indent();
        DumpAsciiMoves(printer, moves.low(), 0);
        printer.outdent();

        printer.println("} else if " + curCharBelow(128) + " {");
        printer.indent();
        DumpAsciiMoves(printer, moves.high(), 1);
        printer.outdent();

        printer.println("} else {");
        printer.indent();
        DumpCharAndRangeMoves(printer, moves.other());
        printer.outdent();
        printer.println("}");

        printCommitKind(printer, noKind);

        if (debug) {
            this.syntax.printDebugCurrentlyMatched(printer);
        }

        this.syntax.printSwapStateSets(printer, lex);

        if (debug) {
            this.syntax.printDebugPossibleLongerMatches(printer);
        }

        this.syntax.printReadCharOrLeave(printer, lex);

        if (debug) {
            this.syntax.printDebugCurrentCharacter(printer, lex.withLexState());
        }
        printer.outdent();
        printer.println("}");

        if (lex.mixed()) {
            printMoveNfaMixedEpilogue(printer);
        }

        printer.outdent();
        printer.println("}");
    }

    /**
     * The body of an arm with a single ASCII move. Which of the three shapes it has was decided
     * when the move was planned (ADR-0029); this writes the one it is.
     *
     * @param labels     the labels of the arm: Java has written each one out already and lets
     *                   them fall through, Rust joins them into a single match arm here
     * @param openIndent the indentation of that joined arm
     */
    protected void DumpAsciiMove(LinePrinter printer, Move move, List<Integer> labels,
                                 String openIndent) {
        this.syntax.printCasesOpen(printer, labels, openIndent);
        printer.indent();

        switch (move.shape()) {
            case ACCEPT -> {
                this.syntax.printIfNoBlock(printer, condition(move.guard()) + raises(move.accept()));
                printer.indent();
                printKind(printer, move.accept());
                printer.outdent();
                this.syntax.printEndIf(printer);
                this.syntax.printBreak(printer, "");
            }
            case MATCH -> {
                if (!(move.guard() instanceof Guard.Always)) {
                    this.syntax.printIf(printer, negated(move.guard()));
                    printer.indent();
                    printer.println("break;");
                    printer.outdent();
                    printer.println("}");
                }
                printAccept(printer, move.accept());
                printNextStates(printer, move.next());
                this.syntax.printBreak(printer, "");
            }
            case ADVANCE -> {
                boolean guarded = !(move.guard() instanceof Guard.Always);
                if (guarded) {
                    this.syntax.printIfNoBlock(printer, condition(move.guard()));
                    printer.indent();
                }
                printNextStates(printer, move.next());
                if (guarded) {
                    printer.outdent();
                }
                this.syntax.printBreak(printer, "");
                if (guarded) {
                    this.syntax.printEndIf(printer);
                }
            }
        }

        printer.outdent();
        // Close exactly what printCasesOpen opened -- it is driven by the labels. Rust lost the
        // closing brace of every arm whose only labels came from the states merged into it.
        if (!labels.isEmpty()) {
            this.syntax.printCasesClose(printer);
        }
    }

    /** The body of an arm with a single move beyond ASCII; see {@link #DumpAsciiMove}. */
    protected void DumpNonAsciiMove(LinePrinter printer, Move move, List<Integer> labels) {
        this.syntax.printCasesOpen(printer, labels, "");

        switch (move.shape()) {
            case ACCEPT -> {
                this.syntax.printIfNoBlock(printer, condition(move.guard()) + raises(move.accept()));
                printer.indent();
                printKind(printer, move.accept());
                printer.outdent();
                this.syntax.printEndIf(printer);
                this.syntax.printBreak(printer, "");
                // This branch used to return without closing what printCasesOpen opened. Java and
                // C++ never noticed -- their case labels carry no braces -- but every Rust match
                // arm that took it was left hanging open.
            }
            case MATCH -> {
                this.syntax.printIfNoBlock(printer, negated(move.guard()));
                printer.indent();
                printer.println("break;");
                printer.outdent();
                this.syntax.printEndIf(printer);

                printAcceptNoBlock(printer, move.accept());

                printer.indent();
                printNextStates(printer, move.next());
                this.syntax.printBreak(printer, "");
                printer.outdent();
            }
            case ADVANCE -> {
                this.syntax.printIfNoBlock(printer, condition(move.guard()));
                printer.indent();
                printNextStates(printer, move.next());
                this.syntax.printBreak(printer, "");
                printer.outdent();
                this.syntax.printEndIf(printer);
            }
        }

        if (!labels.isEmpty()) {
            this.syntax.printCasesClose(printer);
        }
    }

    /** One member of a composite state, in an if-else chain over the ASCII characters. */
    protected void DumpAsciiMoveForCompositeState(LinePrinter printer, Move move) {
        boolean guarded = !(move.guard() instanceof Guard.Always);
        if (guarded) {
            this.syntax.printIfNoBlock(printer, move.elseIf() ? "else if " : "if ",
                    condition(move.guard()));
        }
        printer.indent();

        if (move.accept() != null) {
            if (guarded) {
                printer.println("{");
            }
            printAccept(printer, move.accept());
        }

        printNextStates(printer, move.next());

        printer.outdent();
        if (guarded && (move.accept() != null)) {
            printer.println("}");
        }

        if (guarded) {
            this.syntax.printEndIf(printer);
        }
    }

    /** One member of a composite state, beyond ASCII. */
    protected void DumpNonAsciiMoveForCompositeState(LinePrinter printer, Move move) {
        // Not a bare "if (...)": Rust needs the braces that printIfNoBlock and printEndIf add.
        this.syntax.printIfNoBlock(printer, condition(move.guard()));

        if (move.accept() != null) {
            printer.println("{");
            printer.indent();
            printAccept(printer, move.accept());
        }

        printNextStates(printer, move.next());

        if (move.accept() != null) {
            printer.outdent();
            printer.println("}");
        }
        this.syntax.printEndIf(printer);
    }

    /** The test a move is taken under. */
    private String condition(Guard guard) {
        return switch (guard) {
            case Guard.OneChar g -> this.syntax.charEquals(g.c());
            case Guard.Mask g -> this.syntax.bitIsSet(g.mask());
            case Guard.NonAscii g -> this.syntax.canMove(g.method());
            // A move that is taken for every character of its group is written without a test;
            // reaching this would mean the plan and the shape disagree.
            case Guard.Always g -> throw new IllegalStateException("an unguarded move has no test");
        };
    }

    /** The same test the other way round, for a move that leaves the arm when it does not hold. */
    private String negated(Guard guard) {
        return switch (guard) {
            case Guard.OneChar g -> this.syntax.charNotEquals(g.c());
            case Guard.Mask g -> this.syntax.bitIsClear(g.mask());
            case Guard.NonAscii g -> "!" + this.syntax.canMove(g.method());
            case Guard.Always g -> throw new IllegalStateException("an unguarded move has no test");
        };
    }

    /** What a move that only accepts adds to its condition to yield to a lower kind. */
    private static String raises(Accept accept) {
        return accept.raise() ? " && kind > " + accept.kind() : "";
    }

    private static void printKind(LinePrinter printer, Accept accept) {
        printer.println("kind = " + accept.kind() + ";");
    }

    /**
     * Takes the kind: outright when the move is the only one on its characters, and otherwise only
     * when it beats the kind the round has already matched.
     *
     * <p>The guarded form comes out as a block, which Java and C++ write with braces.
     */
    private void printAccept(LinePrinter printer, Accept accept) {
        if (!accept.raise()) {
            printKind(printer, accept);
            return;
        }
        this.syntax.printIf(printer, "kind > " + accept.kind());
        printer.indent();
        printKind(printer, accept);
        printer.outdent();
        printer.println("}");
    }

    /**
     * The same over a single statement, which Java and C++ write without braces. The two forms are
     * how JavaCC wrote them; they differ in the generated source, not in what it does.
     */
    private void printAcceptNoBlock(LinePrinter printer, Accept accept) {
        this.syntax.printIfNoBlock(printer, "kind > " + accept.kind());
        printer.indent();
        printKind(printer, accept);
        printer.outdent();
        this.syntax.printEndIf(printer);
    }

    /** The labels of an arm: the leading ones at {@code leadIndent}, the merged ones without. */
    private void printLabels(LinePrinter printer, MoveArm arm, String leadIndent) {
        for (int i = 0; i < arm.labels().size(); i++) {
            this.syntax.printCaseLabel(printer, (i < arm.leading()) ? leadIndent : "",
                    arm.labels().get(i));
        }
    }

    protected void DumpAsciiMoves(LinePrinter printer, List<MoveArm> arms, int byteNum) {
        DumpHeadForCase(printer, byteNum);

        for (MoveArm arm : arms) {
            switch (arm.shape()) {
                case COMPOSITE -> {
                    this.syntax.printCaseLabel(printer, "", arm.labels().getFirst());
                    this.syntax.printCasesOpen(printer, arm.labels(), "");
                    printer.indent();
                    for (Move move : arm.moves()) {
                        DumpAsciiMoveForCompositeState(printer, move);
                    }
                    this.syntax.printBreak(printer, "");
                    printer.outdent();
                    this.syntax.printCasesClose(printer);
                }
                case COMPOSITE_ONE -> {
                    printLabels(printer, arm, NfaMoveEmitter.COMPOSITE_INDENT);
                    DumpAsciiMove(printer, arm.moves().getFirst(), arm.labels(),
                            NfaMoveEmitter.COMPOSITE_INDENT);
                }
                case SINGLE -> {
                    printLabels(printer, arm, "");
                    DumpAsciiMove(printer, arm.moves().getFirst(), arm.labels(), "");
                }
            }
        }

        this.syntax.printDefaultAndEndLoop(printer, false);
    }

    protected void DumpCharAndRangeMoves(LinePrinter printer, List<MoveArm> arms) {
        DumpHeadForCase(printer, -1);

        for (MoveArm arm : arms) {
            switch (arm.shape()) {
                case COMPOSITE -> {
                    this.syntax.printCaseLabel(printer, "", arm.labels().getFirst());
                    this.syntax.printCasesOpen(printer, arm.labels(), "");
                    for (Move move : arm.moves()) {
                        DumpNonAsciiMoveForCompositeState(printer, move);
                    }
                    this.syntax.printBreak(printer, "    ");
                    this.syntax.printCasesClose(printer);
                }
                case COMPOSITE_ONE -> {
                    printLabels(printer, arm, "");
                    DumpNonAsciiMove(printer, arm.moves().getFirst(), arm.labels());
                }
                case SINGLE -> {
                    // The labels of the states merged into the arm come out one level deeper.
                    this.syntax.printCaseLabel(printer, "", arm.labels().getFirst());
                    printer.indent();
                    for (int label : arm.labels().subList(1, arm.labels().size())) {
                        this.syntax.printCaseLabel(printer, "", label);
                    }
                    DumpNonAsciiMove(printer, arm.moves().getFirst(), arm.labels());
                    printer.outdent();
                }
            }
        }

        this.syntax.printDefaultAndEndLoop(printer, true);
    }

    /** Emits the move into the states a move leads to. */
    protected void printNextStates(LinePrinter printer, NextStates next) {
        if (next == null) {
            return;
        }

        switch (next.form()) {
            case ADD -> this.syntax.printAddState(printer, next.first());
            case CHECK_ADD -> this.syntax.printCheckNAdd(printer, next.first());
            case CHECK_ADD_TWO -> this.syntax.printCheckNAddTwoStates(printer, next.first(),
                    next.second());
            case ADD_STATES -> this.syntax.printAddStates(printer, next.first(), next.second());
            case CHECK_ADD_STATES -> this.syntax.printCheckNAddStates(printer, next.first(),
                    next.second(), next.isRange());
        }
    }

    /** The position the NFA has reached. */
    protected String curPos() {
        return "curPos";
    }

    /** The loop's locals, and the start state as the only state of the first round. */
    protected void printMoveNfaLocals(LinePrinter printer, LexState lex) {
        printer.println("int startsAt = 0;");
        printer.println("jjnewStateCnt = " + lex.generatedStates() + ";");
        printer.println("int i = 1;");
        printer.println("jjstateSet[0] = startState;");
    }

    /** The kind matched in the current round, none yet. */
    protected void printKindInit(LinePrinter printer, String noKind) {
        printer.println("int kind = " + noKind + ";");
    }

    /** Starts a round; the round counter wraps before it reaches {@code noKind}. */
    protected void printNextRound(LinePrinter printer, String noKind) {
        printer.println("if (++jjround == " + noKind + ")");
        printer.println("    ReInitRounds();");
    }

    /** The test that the current character is below {@code bound}. */
    protected String curCharBelow(int bound) {
        return "(curChar < " + bound + ")";
    }

    /** Takes over the kind this round matched, and moves on by a character. */
    protected void printCommitKind(LinePrinter printer, String noKind) {
        printer.println("if (kind != " + noKind + ") {");
        printer.println("    jjmatchedKind = kind;");
        printer.println("    jjmatchedPos = curPos;");
        printer.println("    kind = " + noKind + ";");
        printer.println("}");
        printer.println("curPos++;");
    }

    /** See {@link TargetSyntax#printMoveNfaMixedPrologue(LinePrinter)}. */
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

    protected void DumpHeadForCase(LinePrinter printer, int byteNum) {
        this.syntax.printCharBits(printer, byteNum);
        this.syntax.printMatchLoopOpen(printer);
        printer.indent();
        this.syntax.printSwitchOnStateSet(printer);
        printer.indent();
    }
}
