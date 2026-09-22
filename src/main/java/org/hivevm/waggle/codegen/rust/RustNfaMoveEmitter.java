// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.rust;

import org.hivevm.source.LinePrinter;
import static org.hivevm.waggle.codegen.rust.RustDebugPrinter.printCurrentCharacter;
import static org.hivevm.waggle.codegen.rust.RustDebugPrinter.printCurrentlyMatched;
import org.hivevm.waggle.codegen.NfaMoveEmitter;
import org.hivevm.waggle.codegen.TargetSyntax;
import org.hivevm.waggle.lexer.NfaStateData;


/**
 * Rust's NFA move loop. See {@link RustStringLiteralDfaEmitter} (ADR-0017).
 */
class RustNfaMoveEmitter extends NfaMoveEmitter {

    RustNfaMoveEmitter(TargetSyntax syntax) {
        super(syntax);
    }

    @Override
    protected void dumpMoveNfa(LinePrinter printer, NfaStateData data) {
        printer.println();
        printer.println("fn jj_move_nfa" + data.getLexerStateSuffix()
                + "(&mut self, start_state: usize, mut cur_pos: usize) -> usize {");
        printer.indent();

        if (data.generatedStates() == 0) {
            printer.println("return cur_pos;");
            printer.outdent();
            printer.println("}");
            return;
        }

        if (data.isMixedState()) {
            printer.print("""
                    let str_kind = self.jjmatched_kind;
                    let str_pos = self.jjmatched_pos;
                    let seen_upto: usize = cur_pos + 1;
                    self.input_stream.backup(seen_upto);
                    let result = self.input_stream.read_char();
                    if result.is_err() {
                        panic!("Internal Error");
                    }
                    self.cur_char = u32::from(result.unwrap());
                    let mut cur_pos: usize = 0;
                    """);
        }

        printer.println("let mut starts_at: usize = 0;");
        printer.println("self.jjnew_state_cnt = " + data.generatedStates() + ";");
        printer.println("let mut i: usize = 1;");
        printer.println("self.jjstate_set[0] = start_state;");

        if (data.global.options().getDebugTokenManager()) {
            printer.println("eprintln!(\"   Starting NFA to match one of : {}\", "
                    + "self.jj_kinds_for_state_vector(self.cur_lex_state as usize, "
                    + "&self.jjstate_set, 0, 1));");
            printCurrentCharacter(printer, data.global.maxLexStates() > 1);
        }

        printer.println("let mut kind: u32 = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
        printer.println("loop {");
        printer.indent();
        printer.println("self.jjround += 1;");
        printer.println("if self.jjround == 0x" + Integer.toHexString(Integer.MAX_VALUE) + " {");
        printer.println("    self.re_init_rounds();");
        printer.println("}");
        printer.println("if self.cur_char < 64 {");

        printer.indent();
        DumpAsciiMoves(printer, data, 0);
        printer.outdent();

        printer.println("} else if self.cur_char < 128 {");

        printer.indent();
        DumpAsciiMoves(printer, data, 1);
        printer.outdent();

        printer.println("} else {");

        printer.indent();
        DumpCharAndRangeMoves(printer, data);
        printer.outdent();

        printer.println("}");
        printer.println("if kind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + " {");
        printer.println("   self.jjmatched_kind = kind;");
        printer.println("   self.jjmatched_pos = cur_pos;");
        printer.println("   kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
        printer.println("}");
        printer.println("cur_pos += 1;");

        if (data.global.options().getDebugTokenManager()) {
            printer.println("if self.jjmatched_kind != 0 && self.jjmatched_kind != 0x"
                    + Integer.toHexString(Integer.MAX_VALUE) + " {");
            printCurrentlyMatched(printer, "   ");
            printer.println("}");
        }

        printer.println("i = self.jjnew_state_cnt;");
        printer.println("self.jjnew_state_cnt = starts_at;");
        printer.println("starts_at = " + data.generatedStates() + " - self.jjnew_state_cnt;");
        printer.println("if i == starts_at {");
        if (data.isMixedState())
            printer.println("    break;");
        else
            printer.println("    return cur_pos;");
        printer.println("}");

        if (data.global.options().getDebugTokenManager()) {
            printer.println("eprintln!(\"   Possible kinds of longer matches : {}\", "
                    + "self.jj_kinds_for_state_vector(self.cur_lex_state as usize, "
                    + "&self.jjstate_set, starts_at, i));");
        }

        printer.println("let result = self.input_stream.read_char();");
        printer.println("if result.is_err() {");
        if (data.isMixedState())
            printer.println("    break;");
        else
            printer.println("    return cur_pos;");
        printer.println("}");
        printer.println("self.cur_char = u32::from(result.unwrap());");

        if (data.global.options().getDebugTokenManager()) {
            printCurrentCharacter(printer, data.global.maxLexStates() > 1);
        }
        printer.outdent();
        printer.println("}");

        if (data.isMixedState()) {
            printer.print("""
                    if self.jjmatched_pos > str_pos {
                       return cur_pos;
                    }
                    
                    let to_ret = cmp::max(cur_pos, seen_upto);
                    if cur_pos < to_ret {
                        let mut i = cmp::min(cur_pos, seen_upto);
                        while i > 0 {
                            let result = self.input_stream.read_char();
                            if result.is_err() {
                                panic!("Internal Error : Please send a bug report.");
                            }
                            self.cur_char = u32::from(result.unwrap());
                            i -= 1;
                        }
                    }
                    if self.jjmatched_pos < str_pos {
                        self.jjmatched_kind = str_kind;
                        self.jjmatched_pos = str_pos;
                    } else if self.jjmatched_pos == str_pos && self.jjmatched_kind > str_kind {
                        self.jjmatched_kind = str_kind;
                    }
                    
                    to_ret
                    """);
        }

        printer.outdent();
        printer.println("}");
    }
}
