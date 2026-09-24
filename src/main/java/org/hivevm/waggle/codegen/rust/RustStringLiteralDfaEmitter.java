// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/RStringLiteral.java

package org.hivevm.waggle.codegen.rust;

import org.hivevm.source.LinePrinter;
import static org.hivevm.waggle.codegen.rust.RustDebugPrinter.printCurrentCharacter;
import org.hivevm.waggle.codegen.StringLiteralDfaEmitter;
import org.hivevm.waggle.codegen.TargetSyntax;
import org.hivevm.waggle.lexer.NfaStateData;
import org.hivevm.waggle.lexer.NfaStateData.KindInfo;

import java.util.Hashtable;


/**
 * Rust's string-literal DFA. It differs from the shared one by more than dialect, so it is an
 * emitter of its own rather than 117 overridden hooks on a shared base (ADR-0017).
 */
class RustStringLiteralDfaEmitter extends StringLiteralDfaEmitter {

    RustStringLiteralDfaEmitter(TargetSyntax syntax) {
        super(syntax);
    }

    /** Rust always leads with "&mut self", so every parameter prepends its own comma. */
    @Override
    protected void printMoveStringLiteralDfaSignature(LinePrinter printer, NfaStateData data, int i,
                                                      int maxLongsReqd) {
        int j;

        printer.print("fn jj_move_string_literal_dfa" + i + data.getLexerStateSuffix() + "(&mut self");

        if (i != 0) {
            if (i == 1) {
                for (j = 0; j < (maxLongsReqd - 1); j++) {
                    if (i <= data.getMaxLenForActive(j)) {
                        printer.print(", active" + j + ": u64");
                    }
                }

                if (i <= data.getMaxLenForActive(j)) {
                    printer.print(", active" + j + ": u64");
                }
            } else {
                for (j = 0; j < (maxLongsReqd - 1); j++) {
                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        printer.print(", old" + j + ": u64, active_old" + j + ": u64");
                    }
                }

                if (i <= (data.getMaxLenForActive(j) + 1)) {
                    printer.print(", old" + j + ": u64, active_old" + j + ": u64");
                }
            }
        }

        printer.println(") -> usize {");
        printer.indent();
    }

    /** Rust needs a "let" per vector: an assignment is not an expression. */
    @Override
    protected void printActiveCheck(LinePrinter printer, NfaStateData data, int i,
                                   int maxLongsReqd) {
        int j;

        if (i > 1) {
            var atLeastOne = false;
            for (j = 0; j < (maxLongsReqd - 1); j++) {
                if (i <= (data.getMaxLenForActive(j) + 1)) {
                    if (atLeastOne)
                        printer.print(" | ");
                    else
                        atLeastOne = true;
                    printer.println("let active" + j + " = active_old" + j + " & old" + j + ";");
                }
            }

            if (i <= (data.getMaxLenForActive(j) + 1)) {
                printer.println("let active" + j + " = active_old" + j + " & old" + j + ";");
            }

            atLeastOne = false;
            printer.print("if (");

            for (j = 0; j < (maxLongsReqd - 1); j++) {
                if (i <= (data.getMaxLenForActive(j) + 1)) {
                    if (atLeastOne) {
                        printer.print(" | ");
                    } else {
                        atLeastOne = true;
                    }
                    printer.print("active" + j);
                }
            }

            if (i <= (data.getMaxLenForActive(j) + 1)) {
                if (atLeastOne)
                    printer.print(" | ");
                printer.print("active" + j);
            }
            printer.println(") == 0 {");
            printer.indent();

            if (!data.isMixedState() && (data.generatedStates() != 0)) {
                printer.print("return self.jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 2) + ", ");
                for (j = 0; j < (maxLongsReqd - 1); j++) {
                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        printer.print("old" + j + ", ");
                    } else {
                        printer.print("0, ");
                    }
                }
                if (i <= (data.getMaxLenForActive(j) + 1)) {
                    printer.println("old" + j + ");");
                } else {
                    printer.println("0);");
                }
            } else if (data.generatedStates() != 0) {
                printer.println("return self.jj_move_nfa" + data.getLexerStateSuffix() +
                        "(" + this.syntax.InitStateName(data) + ", " + (i - 1) + ");");
            } else {
                printer.println("return " + i + ";");
            }
            printer.outdent();
            printer.println("}");
        }
    }

    @Override
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
                printCurrentCharacter(printer, data.global.maxLexStates() > 1);
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

                            // Rust has no braceless "if": the guard has to open a block, and only
                            // then may one be closed again. Emitting the "}" unconditionally left a
                            // stray brace whenever there was no guard at all (i == 0).
                            boolean guarded = (i != 0);
                            if (guarded) {
                                printer.println((ifGenerated ? "else if " : "if ") + "(active" + j
                                        + " & 0x" + Long.toHexString(1L << k) + ") != 0 {");
                                printer.indent();
                            }
                            ifGenerated = true;

                            int kindToPrint = data.kindToPrint(i, (j * 64) + k);

                            if (!data.isSubString((j * 64) + k)) {
                                int stateSetName = data.getStateSetName(i, (j * 64) + k);

                                if (stateSetName != -1) {
                                    printer.println("return self.jjStartNfaWithStates"
                                            + data.getLexerStateSuffix() + "(" + i
                                            + ", " + kindToPrint + ", " + stateSetName + ");");
                                } else {
                                    printer.println("return self.jj_stop_at_pos(" + i + ", "
                                            + kindToPrint + ");");
                                }
                            } else if (((data.global.initMatch(data.getStateIndex()) != 0)
                                    && (data.global.initMatch(data.getStateIndex())
                                    != Integer.MAX_VALUE)) || (i != 0)) {
                                printer.println("self.jjmatched_kind = " + kindToPrint + ";");
                                printer.println("self.jjmatched_pos = " + i + ";");
                            } else {
                                printer.println("self.jjmatched_kind = " + kindToPrint + ";");
                            }

                            if (guarded) {
                                printer.outdent();
                                printer.println("}");
                            }
                        }
                    }
                }

                if (info.hasValidKindCnt()) {
                    var atLeastOne = false;

                    if (i == 0) {
                        printer.print("return self.jj_move_string_literal_dfa" + (i + 1) + data.getLexerStateSuffix() + "(");
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
                        printer.print("return self.jj_move_string_literal_dfa" + (i + 1) + data.getLexerStateSuffix() + "(");

                        for (j = 0; j < (maxLongsReqd - 1); j++) {
                            if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
                                if (atLeastOne)
                                    printer.print(", ");
                                else
                                    atLeastOne = true;

                                if (info.validKinds[j] != 0L)
                                    printer.print("active" + j + ", 0x" + Long.toHexString(info.validKinds[j]));
                                else
                                    printer.print("active" + j + ", 0");
                            }
                        }

                        if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
                            if (atLeastOne)
                                printer.print(", ");
                            if (info.validKinds[j] != 0L)
                                printer.print("active" + j + ", 0x" + Long.toHexString(info.validKinds[j]));
                            else
                                printer.print("active" + j + ", 0");
                        }
                    }

                    printer.println(");");
                } else { // A very special case.
                    if ((i == 0) && data.isMixedState()) {

                        if (data.generatedStates() != 0) {
                            printer.println("return self.jj_move_nfa" + data.getLexerStateSuffix() + "(" + this.syntax.InitStateName(data) + ", 0);");
                        } else {
                            printer.println("return 1;");
                        }
                    } else if (i != 0) // No more str literals to look for
                    {
                        startNfaNeeded = true;
                    }
                }

                printer.outdent();
                printer.println("}");
            }

            this.syntax.printDefaultCaseOpen(printer);
            printer.indent();

            if (data.global.getDebugTokenManager()) {
                printer.println("eprintln!(\"No string literal matches possible.\");");
            }

            if (data.generatedStates() != 0) {
                if (i == 0) {
                    // This means no string literal is possible. Just move nfa with this guy and return.
                    printer.println("return self.jj_move_nfa" + data.getLexerStateSuffix() + "("
                            + this.syntax.InitStateName(data) + ", 0);");
                } else {
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

                    printer.print("self.jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ");
                    for (k = 0; k < (maxLongsReqd - 1); k++) {
                        if (i <= data.getMaxLenForActive(k))
                            printer.print("active" + k + ", ");
                        else
                            printer.print("0, ");
                    }
                    if (i <= data.getMaxLenForActive(k))
                        printer.println("active" + k + ")");
                    else
                        printer.println("0)");
                } else if (data.generatedStates() != 0)
                    printer.println("self.jj_move_nfa" + data.getLexerStateSuffix() + "(" + this.syntax.InitStateName(data) + ", " + i + ")");
                else
                    printer.println("return " + (i + 1));
            }

            printer.outdent();
            printer.println("}");
            printer.println();
        }

        if (!data.isMixedState() && (data.generatedStates() != 0) && data.getCreateStartNfa()) {
            DumpStartWithStates(printer, data);
        }
    }
}
