// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/RStringLiteral.java

package org.hivevm.waggle.codegen.rust;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.codegen.StringLiteralDfaEmitter;
import org.hivevm.waggle.codegen.TargetSyntax;
import org.hivevm.waggle.codegen.LexState;
import org.hivevm.waggle.lexer.LexerPlan.DfaPos;

import java.util.List;
import java.util.StringJoiner;


/**
 * Rust's string-literal DFA: the shared one, with Rust's signatures, its "let" per active vector,
 * block guards and snake-case calls.
 */
class RustStringLiteralDfaEmitter extends StringLiteralDfaEmitter {

    RustStringLiteralDfaEmitter(TargetSyntax syntax) {
        super(syntax);
    }

    /** Rust always leads with "&mut self", so every parameter prepends its own comma. */
    @Override
    protected void printMoveStringLiteralDfaSignature(LinePrinter printer, LexState lex,
                                                      DfaPos pos) {
        printer.print("fn jj_move_string_literal_dfa" + pos.pos() + lex.suffix()
                + "(&mut self");
        for (int j : pos.params()) {
            printer.print((pos.pos() == 1) ? ", active" + j + ": u64"
                    : ", old" + j + ": u64, active_old" + j + ": u64");
        }

        printer.println(") -> usize {");
        printer.indent();
    }

    /** Rust needs a "let" per vector: an assignment is not an expression. */
    @Override
    protected void printActiveTest(LinePrinter printer, List<Integer> vectors) {
        // One statement per vector. The " | " between them was copied from Java's expression
        // form, and gave "| let ..." as soon as a state had more than 128 literal kinds.
        var active = new StringJoiner(" | ");
        for (int j : vectors) {
            printer.println("let active" + j + " = active_old" + j + " & old" + j + ";");
            active.add("active" + j);
        }
        printer.println("if (" + active + ") == 0 {");
    }

    @Override
    protected String startNfaName(LexState lex) {
        return "self." + super.startNfaName(lex);
    }

    @Override
    protected String startNfaWithStatesName(LexState lex) {
        return "self." + super.startNfaWithStatesName(lex);
    }

    @Override
    protected String stopAtPosName() {
        return "self.jj_stop_at_pos";
    }

    @Override
    protected String moveStringLiteralDfaName(LexState lex, int i) {
        return "self.jj_move_string_literal_dfa" + i + lex.suffix();
    }

    /**
     * Rust has no braceless "if": the guard has to open a block, and only then may one be closed
     * again. Closing it unconditionally left a stray brace whenever there was no guard (i == 0).
     */
    @Override
    protected void printFinalKindGuardOpen(LinePrinter printer, boolean elseIf, int i, int word,
                                           long bit) {
        if (i != 0) {
            printer.println((elseIf ? "else if " : "if ") + "(active" + word + " & "
                    + this.syntax.toHexString(bit) + ") != 0 {");
            printer.indent();
        }
    }

    @Override
    protected void printFinalKindGuardClose(LinePrinter printer, int i) {
        if (i != 0) {
            printer.outdent();
            printer.println("}");
        }
    }

    /** The guard, if any, is already a block. */
    @Override
    protected void printMatchedKindAndPos(LinePrinter printer, int kind, int i) {
        printer.println(this.syntax.matchedKind() + " = " + kind + ";");
        printer.println(this.syntax.matchedPos() + " = " + i + ";");
    }

    @Override
    protected void printReturnPosition(LinePrinter printer, int pos) {
        printer.println("return " + pos);
    }

    /** The signature above leaves out the blank line before a function, so it follows it. */
    @Override
    protected void printMoveStringLiteralDfaEnd(LinePrinter printer) {
        super.printMoveStringLiteralDfaEnd(printer);
        printer.println();
    }
}
