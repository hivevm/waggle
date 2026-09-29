// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/NfaState.java, org/javacc/parser/LexGen.java

package org.hivevm.waggle.codegen.rust;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.codegen.NfaMoveEmitter;
import org.hivevm.waggle.codegen.TargetSyntax;
import org.hivevm.waggle.codegen.LexState;


/**
 * Rust's NFA move loop: the shared one, with Rust's locals and statements.
 */
class RustNfaMoveEmitter extends NfaMoveEmitter {

    RustNfaMoveEmitter(TargetSyntax syntax) {
        super(syntax);
    }

    @Override
    protected String curPos() {
        return "cur_pos";
    }

    @Override
    protected void printMoveNfaLocals(LinePrinter printer, LexState lex) {
        printer.println("let mut starts_at: usize = 0;");
        printer.println("self.jjnew_state_cnt = " + lex.generatedStates() + ";");
        printer.println("let mut i: usize = 1;");
        printer.println("self.jjstate_set[0] = start_state;");
    }

    @Override
    protected void printKindInit(LinePrinter printer, String noKind) {
        printer.println("let mut kind: u32 = " + noKind + ";");
    }

    @Override
    protected void printNextRound(LinePrinter printer, String noKind) {
        printer.println("self.jjround += 1;");
        printer.println("if self.jjround == " + noKind + " {");
        printer.println("    self.re_init_rounds();");
        printer.println("}");
    }

    @Override
    protected String curCharBelow(int bound) {
        return "self.cur_char < " + bound;
    }

    @Override
    protected void printCommitKind(LinePrinter printer, String noKind) {
        printer.println("if kind != " + noKind + " {");
        printer.println("   self.jjmatched_kind = kind;");
        printer.println("   self.jjmatched_pos = cur_pos;");
        printer.println("   kind = " + noKind + ";");
        printer.println("}");
        printer.println("cur_pos += 1;");
    }

    @Override
    protected void printMoveNfaMixedEpilogue(LinePrinter printer) {
        printer.print("""
                if self.jjmatched_pos > str_pos {
                   return cur_pos;
                }
                
                let to_ret = cmp::max(cur_pos, seen_upto);
                if cur_pos < to_ret {
                    let mut i = to_ret - cmp::min(cur_pos, seen_upto); // as in Java and C++
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
}
