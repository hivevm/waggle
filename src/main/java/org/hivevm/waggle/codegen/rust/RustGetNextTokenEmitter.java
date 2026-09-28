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
        long lower = data.singlesToSkip(state).asciiMoves[0];
        long upper = data.singlesToSkip(state).asciiMoves[1];
        String condition;
        if ((lower != 0L) && (upper != 0L)) {
            condition = "(self.cur_char < 64 && (0x" + Long.toHexString(lower)
                    + "u64 & (1u64 << self.cur_char)) != 0) || ((self.cur_char >> 6) == 1 && (0x"
                    + Long.toHexString(upper) + "u64 & (1u64 << (self.cur_char & 0o77))) != 0)";
        } else if (upper == 0L) {
            condition = "self.cur_char <= " + (int) MaxChar(lower) + " && (0x"
                    + Long.toHexString(lower) + "u64 & (1u64 << self.cur_char)) != 0";
        } else {
            condition = "self.cur_char > 63 && self.cur_char <= "
                    + (MaxChar(upper) + 64) + " && (0x" + Long.toHexString(upper)
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
    protected void printDebugEmptyStringMatched(LinePrinter printer, int kind) {
        printer.println("eprintln!(\"   Matched the empty string as {} token.\", "
                + "TOKEN_IMAGE[" + kind + "]);");
    }

    @Override
    protected void printDebugCurrentCharacterMatched(LinePrinter printer, int kind) {
        printer.println("eprintln!(\"   Current character matched as a {} token.\", "
                + "TOKEN_IMAGE[" + kind + "]);");
    }

    @Override
    protected void printDebugPuttingBack(LinePrinter printer) {
        printer.println("eprintln!(\"   Putting back {} characters into the input stream.\", "
                + charsPastMatch() + ");");
    }

    @Override
    protected String curPos() {
        return "cur_pos";
    }

    /** The position is unsigned: one before 0 wraps around. */
    @Override
    protected String noMatchPos() {
        return "usize::MAX";
    }

    @Override
    protected String isNoMatchPos() {
        return this.syntax.matchedPos() + " == usize::MAX";
    }

    @Override
    protected String matchLength() {
        return this.syntax.matchedPos() + ".wrapping_add(1)";
    }

    @Override
    protected String charsPastMatch() {
        return curPos() + " - " + matchLength();
    }

    @Override
    protected void printTokenBranch(LinePrinter printer, LexerData data) {
        printer.println("matched_token = self.jj_fill_token();");

        if (data.hasSpecial()) {
            printer.println("matched_token.special = std::mem::take(&mut special_tokens);");
        }
        if (data.hasTokenActions()) {
            printer.println("self.token_lexical_actions(&mut matched_token)?;");
        }
        if (data.maxLexStates() > 1) {
            printNewLexState(printer);
        }
        printer.println("return Ok(matched_token);");
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

            // The next token owns the special tokens before it (ADR-0030); Java chains them.
            printer.println("let token = self.jj_fill_token();");
            if (data.hasSkipActions()) {
                printer.println("self.skip_lexical_actions(Some(&token))?;");
            }
            printer.println("special_tokens.push(token);");

            printer.outdent();

            if (data.hasSkipActions()) {
                printer.println("} else {");
                printer.println("    self.skip_lexical_actions(None)?;");
                printer.println("}");
            } else {
                printer.println("}");
            }
        } else if (data.hasSkipActions()) {
            printer.println("self.skip_lexical_actions(None)?;");
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
            printer.println("self.more_lexical_actions()?;");
        } else if (data.hasSkipActions() || data.hasTokenActions()) {
            printer.println("self.jjimage_len += self.jjmatched_pos.wrapping_add(1);");
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
    protected void printLexicalErrorEpilogue(LinePrinter printer, LexerData data) {
        printer.outdent();
        printer.println("}");
        printer.println("""
                let mut error_line = self.input_stream.get_end_line();
                let mut error_column = self.input_stream.get_end_column();
                let eof_seen = self.input_stream.read_char().is_err();
                if eof_seen {
                    if self.cur_char == '\\n' as u32 || self.cur_char == '\\r' as u32 {
                        error_line += 1;
                        error_column = 0;
                    } else {
                        error_column += 1;
                    }
                } else {
                    // Back over the character just read and the one that failed, as Java does.
                    self.input_stream.backup(1);
                    self.input_stream.backup(1);
                }
                let error_after = if cur_pos <= 1 {
                    String::new()
                } else {
                    self.input_stream.get_image()
                };
                // A value, not a panic (ADR-0030); it used to be Token::empty(), which is <EOF>,
                // so the rest of the input was silently dropped. The message is the Java lexer's.
                let encountered = if eof_seen {
                    String::from("<EOF> ")
                } else {
                    let c = char::from_u32(self.cur_char).unwrap_or(char::REPLACEMENT_CHARACTER);
                    format!("\\"{}\\" ({}), ", add_escapes(&c.to_string()), self.cur_char)
                };
                return Err(LexicalError {
                    line: error_line,
                    column: error_column,
                    message: format!(
                        "Lexical error at line {}, column {}.  Encountered: {}after : \\"{}\\"",
                        error_line, error_column, encountered, add_escapes(&error_after)
                    ),
                });
                """);
    }

    /** Rust needs braces around the body of an {@code if}. */
    @Override
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
