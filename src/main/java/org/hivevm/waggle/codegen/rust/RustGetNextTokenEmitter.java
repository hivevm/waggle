// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/LexGen.java, org/javacc/parser/LexGenCPP.java

package org.hivevm.waggle.codegen.rust;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.codegen.GetNextTokenEmitter;
import org.hivevm.waggle.codegen.LexerGenerator;
import org.hivevm.waggle.codegen.TargetSyntax;
import org.hivevm.waggle.lexer.LexerData;

/**
 * How Rust spells {@code getNextToken} (ADR-0017).
 */
class RustGetNextTokenEmitter extends GetNextTokenEmitter {

    RustGetNextTokenEmitter(TargetSyntax syntax, LexerGenerator tokens) {
        super(syntax, tokens);
    }

    @Override
    protected void printSkipSingles(LinePrinter printer, LexerData data, int state) {
        // the backup(0) is there to make the JIT happy
        printer.println("self.input_stream.backup(0);");

        long lower = data.singlesToSkip(state).asciiMoves[0];
        long upper = data.singlesToSkip(state).asciiMoves[1];
        String condition;
        if ((lower != 0L) && (upper != 0L)) {
            condition = "(self.cur_char < 64 && (0x" + Long.toHexString(lower)
                    + "u64 & (1u64 << self.cur_char)) != 0) || ((self.cur_char >> 6) == 1 && (0x"
                    + Long.toHexString(upper) + "u64 & (1u64 << (self.cur_char & 0o77))) != 0)";
        } else if (upper == 0L) {
            condition = "self.cur_char <= " + (int) TargetSyntax.MaxChar(lower) + " && (0x"
                    + Long.toHexString(lower) + "u64 & (1u64 << self.cur_char)) != 0";
        } else {
            condition = "self.cur_char > 63 && self.cur_char <= "
                    + (TargetSyntax.MaxChar(upper) + 64) + " && (0x" + Long.toHexString(upper)
                    + "u64 & (1u64 << (self.cur_char & 0o77))) != 0";
        }

        printer.println("while " + condition + " {");
        printer.indent();

        if (data.getDebugTokenManager()) {
            printDebugSkippingCharacter(printer, data);
        }

        // begin_token yields a Result; running out of input ends the token loop.
        printer.println("match self.input_stream.begin_token() {");
        printer.println("    Ok(c) => self.cur_char = u32::from(c),");
        printer.println("    Err(_) => continue 'EOFLoop,");
        printer.println("}");

        printer.outdent();
        printer.println("}");
    }

    @Override
    protected void printInitialMatch(LinePrinter printer, LexerData data, int state) {
        if (hasInitialMatch(data, state)) {
            if (data.getDebugTokenManager()) {
                printer.println("eprintln!(\"   Matched the empty string as {} token.\", "
                        + "TOKEN_IMAGE[" + data.initMatch(state) + "]);");
            }
            printer.println("self.jjmatched_kind = " + data.initMatch(state) + ";");
            printer.println("self.jjmatched_pos = usize::MAX;");
            printer.println("cur_pos = 0;");
        } else {
            printer.println("self.jjmatched_kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
            printer.println("self.jjmatched_pos = 0;");
        }
    }

    @Override
    protected void printCanMatchAnyChar(LinePrinter printer, LexerData data, int state) {
        int kind = data.canMatchAnyChar(state);
        if (hasInitialMatch(data, state)) {
            printer.println("if self.jjmatched_pos == usize::MAX || (self.jjmatched_pos == 0"
                    + " && self.jjmatched_kind > " + kind + ") {");
        } else {
            printer.println("if self.jjmatched_pos == 0 && self.jjmatched_kind > " + kind + " {");
        }
        printer.indent();

        if (data.getDebugTokenManager()) {
            printer.println("eprintln!(\"   Current character matched as a {} token.\", "
                    + "TOKEN_IMAGE[" + kind + "]);");
        }
        printer.println("self.jjmatched_kind = " + kind + ";");

        if (hasInitialMatch(data, state)) {
            printer.println("self.jjmatched_pos = 0;");
        }

        printer.outdent();
        printer.println("}");
    }

    @Override
    protected void printBackupBlock(LinePrinter printer, LexerData data) {
        printer.println("if self.jjmatched_pos + 1 < cur_pos {");
        printer.indent();

        if (data.getDebugTokenManager()) {
            printer.println("eprintln!(\"   Putting back {} characters into the input stream.\", "
                    + "cur_pos - self.jjmatched_pos - 1);");
        }

        printer.println("self.input_stream.backup(cur_pos - self.jjmatched_pos - 1);");
        printer.outdent();
        printer.println("}");
    }

    @Override
    protected void printTokenBranch(LinePrinter printer, LexerData data) {
        printer.println("matched_token = self.jj_fill_token();");

        if (data.hasSpecial()) {
            printer.println("matched_token.special = special_token.take();");
        }
        if (data.hasTokenActions()) {
            printer.println("self.token_lexical_actions(&mut matched_token);");
        }
        if (data.maxLexStates() > 1) {
            printNewLexState(printer);
        }
        printer.println("return matched_token;");
    }

    @Override
    protected void printSkipBranch(LinePrinter printer, LexerData data) {
        if (data.hasMore()) {
            printer.print("else if " + RustLexerGenerator.bitVectorTest("JJTO_SKIP"));
        } else {
            printer.print("else");
        }

        printer.println(" {");
        printer.indent();

        if (data.hasSpecial()) {
            printer.println("if " + RustLexerGenerator.bitVectorTest("JJTO_SPECIAL") + " {");
            printer.indent();

            // Link the new special token behind the previous one: it points back at its
            // predecessor, and the predecessor's "next" holds it. Both ends are shared, so the
            // chain is Rc<RefCell<Token>> rather than the raw references Java gets away with.
            printer.println("let token = Rc::new(RefCell::new(self.jj_fill_token()));");
            printer.println("if let Some(previous) = special_token.take() {");
            printer.println("    token.borrow_mut().special = Some(Rc::clone(&previous));");
            printer.println("    previous.borrow_mut().next = Some(Rc::clone(&token));");
            printer.println("}");
            printer.println("special_token = Some(Rc::clone(&token));");

            if (data.hasSkipActions()) {
                printer.println("self.skip_lexical_actions(Some(&token.borrow()));");
            }

            printer.outdent();

            if (data.hasSkipActions()) {
                printer.println("} else {");
                printer.println("    self.skip_lexical_actions(None);");
                printer.println("}");
            } else {
                printer.println("}");
            }
        } else if (data.hasSkipActions()) {
            printer.println("self.skip_lexical_actions(None);");
        }

        if (data.maxLexStates() > 1) {
            printNewLexState(printer);
        }

        printer.println("continue 'EOFLoop;");
        printer.outdent();
        printer.println("}");
    }

    @Override
    protected void printMoreBranch(LinePrinter printer, LexerData data) {
        if (data.hasMoreActions()) {
            printer.println("self.more_lexical_actions();");
        } else if (data.hasSkipActions() || data.hasTokenActions()) {
            printer.println("self.jjimage_len += self.jjmatched_pos + 1;");
        }

        if (data.maxLexStates() > 1) {
            printNewLexState(printer);
        }
        printer.println("cur_pos = 0;");
        printer.println("self.jjmatched_kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");

        printer.println("match self.input_stream.read_char() {");
        printer.indent();
        printer.println("Ok(c) => {");
        printer.println("    self.cur_char = u32::from(c);");
        printer.println("    continue;");
        printer.println("}");
        printer.println("Err(_) => {}");
        printer.outdent();
        printer.println("}");
    }

    @Override
    protected void printLexicalErrorEpilogue(LinePrinter printer) {
        printer.outdent();
        printer.println("}");
        printer.println("""
                let mut error_line = self.input_stream.get_end_line();
                let mut error_column = self.input_stream.get_end_column();
                let mut error_after = String::new();
                let mut eof_seen = false;
                let result = self.input_stream.read_char();
                if result.is_ok() {
                    self.input_stream.backup(1);
                } else {
                    eof_seen = true;
                    if cur_pos <= 1 {
                        error_after = String::new();
                    } else {
                        error_after = self.input_stream.get_image();
                    }
                    if self.cur_char == '\\n'.try_into().unwrap()
                        || self.cur_char == '\\r'.try_into().unwrap()
                    {
                        error_line += 1;
                        error_column = 0;
                    } else {
                        error_column += 1;
                    }
                }
                if !eof_seen {
                    self.input_stream.backup(1);
                    if cur_pos <= 1 {
                        error_after = String::new();
                    } else {
                        error_after = self.input_stream.get_image();
                    }
                }
                return Token::empty();
                """);
    }

    /** Rust needs braces around the body of an {@code if}. */
    protected void printNewLexState(LinePrinter printer) {
        printer.println("if JJNEW_LEX_STATE[self.jjmatched_kind as usize] != -1 {");
        printer.println("   self.cur_lex_state = JJNEW_LEX_STATE[self.jjmatched_kind as usize];");
        printer.println("}");
    }

    /** The trace the skip loop writes for every character it throws away. */
    protected void printDebugSkippingCharacter(LinePrinter printer, LexerData data) {
        var prefix = (data.maxLexStates() > 1)
                ? "<{}>Skipping character : {}({})\", LEX_STATE_NAMES[self.cur_lex_state as usize], "
                : "Skipping character : {}({})\", ";
        printer.println("eprintln!(\"" + prefix
                + "char::from_u32(self.cur_char).unwrap_or('\\u{fffd}'), self.cur_char);");
    }
}
