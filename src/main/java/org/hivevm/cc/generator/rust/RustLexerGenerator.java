// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.rust;

import org.hivevm.cc.Encoding;
import org.hivevm.cc.Language;
import org.hivevm.cc.generator.LexerGenerator;
import org.hivevm.cc.lexer.LexerData;
import org.hivevm.cc.lexer.NfaState;
import org.hivevm.cc.lexer.NfaStateData;
import org.hivevm.cc.lexer.NfaStateData.KindInfo;
import org.hivevm.cc.model.Action;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.RStringLiteral;
import org.hivevm.cc.model.TokenKind;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.source.Context;
import org.hivevm.source.LinePrinter;
import org.hivevm.source.SourceProvider;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

/**
 * Generate lexer.
 */
class RustLexerGenerator extends LexerGenerator {

    public RustLexerGenerator() {
        super(Language.RUST);
    }

    @Override
    protected final void generate(LexerData data, Context options) {
        options.add("LOHI_BYTES_LENGTH", data.getLohiByteSize() + 1);
        options.add("LITERAL_IMAGES", RustLexerGenerator.getStrLiteralImageList(data))
                .set("LITERAL_IMAGE_NAME", s -> s);
        options.set("LITERAL_IMAGES_LENGTH",
                RustLexerGenerator.getStrLiteralImageList(data).size());
        options.set("STATES_FOR_STATE", () -> getStatesForState(data));
        options.set("KIND_FOR_STATE", () -> getKindForState(data));
        options.set("STATE_NAMES_LENGTH", data.getStateNames().size());

        RustTemplate.LEXER.render(options);
    }

    protected String self() {
        return "self.";
    }

    protected String getNonAsciiMethod(NfaState state) {
        return "_" + state.nonAsciiMethod;
    }

    protected SourceProvider getConstantsTemplate() {
        return RustTemplate.PARSER_CONSTANTS;
    }

    protected void dumpStaticVarDeclarations(LinePrinter printer, LexerData data) {
        if (data.maxLexStates() > 1) {
            printer.print("const JJNEW_LEX_STATE: [i8; " + data.maxOrdinal() + "] = [");
            printer.indent();

            for (int i = 0; i < data.maxOrdinal(); i++) {
                if ((i % 25) == 0) {
                    printer.println();
                }

                if (data.newLexState(i) == null) {
                    printer.print("-1, ");
                } else {
                    printer.print(data.getStateIndex(data.newLexState(i)) + ", ");
                }
            }
            printer.println();
            printer.outdent();
            printer.println("];");
        }

        if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
            // Bit vector for TOKEN
            printer.print("const JJTO_TOKEN: [u64; " + ((data.maxOrdinal() / 64) + 1) + "] = [");
            printer.indent();
            for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
                if ((i % 4) == 0) {
                    printer.println();
                }
                printer.print(toHexString(data.toToken(i)) + ", ");
            }
            printer.println();
            printer.outdent();
            printer.println("];");
        }

        if (data.hasSkip() || data.hasSpecial()) {
            // Bit vector for SKIP
            printer.print("const JJTO_SKIP: [u64; " + ((data.maxOrdinal() / 64) + 1) + "] = [");
            printer.indent();
            for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
                if ((i % 4) == 0) {
                    printer.println();
                }
                printer.print(toHexString(data.toSkip(i)) + ", ");
            }
            printer.println();
            printer.outdent();
            printer.println("];");
        }

        if (data.hasSpecial()) {
            // Bit vector for SPECIAL
            printer.print("const JJTO_SPECIAL: [u64; " + ((data.maxOrdinal() / 64) + 1) + "] = [");
            printer.indent();
            for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
                if ((i % 4) == 0) {
                    printer.println();
                }
                printer.print(toHexString(data.toSpecial(i)) + ", ");
            }
            printer.println();
            printer.outdent();
            printer.println("];");
        }

        if (data.hasMore()) {
            // Bit vector for MORE
            printer.print("const JJTO_MORE: [u64; " + ((data.maxOrdinal() / 64) + 1) + "] = [");
            printer.indent();
            for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
                if ((i % 4) == 0) {
                    printer.println();
                }
                printer.print(toHexString(data.toMore(i)) + ", ");
            }
            printer.println();
            printer.outdent();
            printer.println("];");
        }
    }

    protected void dumpGetNextToken(LinePrinter printer, LexerData data) {
        if (data.hasEof()) {
            printer.println("    self.token_lexical_actions(matched_token);");
        }

        printer.println("    return matched_token;");
        printer.println("}");

        if (data.hasMoreActions() || data.hasSkipActions() || data.hasTokenActions()) {
            printer.println("image = jjimage;");
            printer.println("image.setLength(0);");
            printer.println("jjimage_len = 0;");
        }

        printer.println();

        if (data.hasMore()) {
            printer.println("for (;;) {");
            printer.indent();
        }

        // this also sets up the start state of the nfa
        if (data.maxLexStates() > 1) {
            printer.println("match self.cur_lex_state {");
            printer.indent();
        }

        for (int i = 0; i < data.maxLexStates(); i++) {
            if (data.maxLexStates() > 1) {
                printer.println(i + " => {");
                printer.indent();
            }

            if (data.singlesToSkip(i).HasTransitions()) {
                // added the backup(0) to make JIT happy
                printer.println("try {");
                printer.indent();
                printer.println("input_stream.backup(0);");
                if ((data.singlesToSkip(i).asciiMoves[0] != 0L) && (
                        data.singlesToSkip(i).asciiMoves[1]
                                != 0L)) {
                    printer.println("while ((cur_char < 64" + " && (0x" + Long.toHexString(
                            data.singlesToSkip(i).asciiMoves[0])
                            + "L & (1L << cur_char)) != 0L) || \n"
                            + "          (cur_char >> 6) == 1"
                            + " && (0x"
                            + Long.toHexString(data.singlesToSkip(i).asciiMoves[1])
                            + "L & (1L << (cur_char & 0o77))) != 0L)");
                } else if (data.singlesToSkip(i).asciiMoves[1] == 0L) {
                    printer.println("while (cur_char <= " + (int) LexerGenerator.MaxChar(
                            data.singlesToSkip(i).asciiMoves[0])
                            + " && (0x" + Long.toHexString(data.singlesToSkip(i).asciiMoves[0])
                            + "L & (1L << cur_char)) != 0L)");
                } else if (data.singlesToSkip(i).asciiMoves[0] == 0L) {
                    printer.println("while (cur_char > 63 && cur_char <= "
                            + (LexerGenerator.MaxChar(data.singlesToSkip(i).asciiMoves[1]) + 64)
                            + " && (0x"
                            + Long.toHexString(data.singlesToSkip(i).asciiMoves[1])
                            + "L & (1L << (cur_char & 0o77))) != 0L)");
                }

                if (data.options().getDebugTokenManager()) {
                    printer.println(" {");
                    printer.indent();
                    printer.println("debugStream.println("
                            + (data.maxLexStates() > 1
                            ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + " : "")
                            + "\"Skipping character : \" + TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \")\");");
                }

                printer.println("cur_char = input_stream.begin_token();");
                printer.outdent();

                if (data.options().getDebugTokenManager()) {
                    printer.println("}");
                }

                printer.outdent();
                printer.println("} catch (java.io.IOException e1) { continue 'EOFLoop; }");
            }

            if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
                if (data.options().getDebugTokenManager()) {
                    printer.println("debugStream.println(\"   Matched the empty string as \" + tokenImage["
                            + data.initMatch(i) + "] + \" token.\");");
                }

                printer.println("self.jjmatched_kind = " + data.initMatch(i) + ";");
                printer.println("self.jjmatched_pos = usize::MAX;");
                printer.println("cur_pos = 0;");
            } else {
                printer.println("self.jjmatched_kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
                printer.println("self.jjmatched_pos = 0;");
            }

            if (data.options().getDebugTokenManager()) {
                printer.println("debugStream.println("
                        + (data.maxLexStates() > 1
                        ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + " : "")
                        + "\"Current character : \" + TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \") "
                        + "at line \" + input_stream.get_end_line() + \" column \" + input_stream.get_end_column());");
            }

            printer.println("cur_pos = self.jj_move_string_literal_dfa0_" + i + "();");
            if (data.canMatchAnyChar(i) != -1) {
                if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
                    printer.println("if self.jjmatched_pos < 0 || self.jjmatched_pos == 0 && self.jjmatched_kind > "
                            + data.canMatchAnyChar(i) + ") {");
                } else {
                    printer.println("if self.jjmatched_pos == 0 && self.jjmatched_kind > " + data.canMatchAnyChar(i) + " {");
                }
                printer.indent();

                if (data.options().getDebugTokenManager()) {
                    printer.println("debugStream.println(\"   Current character matched as a \" + tokenImage["
                            + data.canMatchAnyChar(i) + "] + \" token.\");");
                }
                printer.println("self.jjmatched_kind = " + data.canMatchAnyChar(i) + ";");

                if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
                    printer.println("self.jjmatched_pos = 0;");
                }

                printer.outdent();
                printer.println("}");
            }

            if (data.maxLexStates() > 1) {
                printer.outdent();
                printer.println("}");
            }
        }

        if (data.maxLexStates() > 1) {
            printer.println("_ => {}");
            printer.outdent();
            printer.println("}");
        } else if (data.maxLexStates() == 0) {
            printer.println("self.jjmatched_kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
        }

        if (data.maxLexStates() > 0) {
            printer.println("if self.jjmatched_kind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + " {");
            printer.indent();

            printer.println("if self.jjmatched_pos + 1 < cur_pos {");
            printer.indent();

            if (data.options().getDebugTokenManager()) {
                printer.println("debugStream.println(\"   Putting back \" + (cur_pos - jjmatched_pos - 1) + \" characters into the input stream.\");");
            }

            printer.println("self.input_stream.backup(cur_pos - self.jjmatched_pos - 1);");

            printer.outdent();
            printer.println("}");

            if (data.options().getDebugTokenManager()) {
                printer.println("debugStream.println("
                        + "\"****** FOUND A \" + tokenImage[jjmatched_kind] + \" MATCH "
                        + "(\" + TokenException.addEscapes(new String(input_stream.GetSuffix(jjmatched_pos + 1))) + "
                        + "\") ******\\n\");");
            }

            if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
                printer.println("if (JJTO_TOKEN[(self.jjmatched_kind >> 6) as usize]");
                printer.println("      & (1 << (self.jjmatched_kind & 0o77))) != 0 {");
                printer.indent();
            }

            printer.println("matched_token = self.jj_fill_token();");

            if (data.hasSpecial()) {
                printer.println("matched_token.specialToken = specialToken;");
            }

            if (data.hasTokenActions()) {
                printer.println("self.token_lexical_actions(matched_token);");
            }

            if (data.maxLexStates() > 1) {
                printer.println("if JJNEW_LEX_STATE[self.jjmatched_kind as usize] != -1 {");
                printer.println("   self.cur_lex_state = JJNEW_LEX_STATE[self.jjmatched_kind as usize];");
                printer.println("}");
            }

            printer.println("return matched_token;");

            if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
                printer.outdent();
                printer.println("}");

                if (data.hasSkip() || data.hasSpecial()) {
                    if (data.hasMore()) {
                        printer.print("else if (self.JJTO_SKIP[self.jjmatched_kind >> 6] & " + "(1 << (jjmatched_kind & 0o77))) != 0");
                    } else {
                        printer.print("else");
                    }

                    printer.println(" {");
                    printer.indent();

                    if (data.hasSpecial()) {
                        printer.println("if (self.jjtoSpecial[self.jjmatched_kind >> 6] & "
                                + "(1 << (self.jjmatched_kind & 0o77))) != 0 {");
                        printer.indent();

                        printer.println("matched_token = self.jj_fill_token();");

                        printer.println("if specialToken == null {");
                        printer.println("   specialToken = matched_token;");
                        printer.println("} else {");
                        printer.println("   matched_token.specialToken = specialToken;");
                        printer.println("   specialToken = (specialToken.next = matched_token);");
                        printer.println("}");

                        if (data.hasSkipActions()) {
                            printer.println("self.skip_lexical_actions(matched_token);");
                        }

                        printer.outdent();
                        printer.println("}");

                        if (data.hasSkipActions()) {
                            printer.println("} else {");
                            printer.println("   self.skip_lexical_actions(null);");
                            printer.println("}");
                        }
                    } else if (data.hasSkipActions()) {
                        printer.println("self.skip_lexical_actions(null);");
                    }

                    if (data.maxLexStates() > 1) {
                        printer.println("if JJNEW_LEX_STATE[self.jjmatched_kind as usize] != -1 {");
                        printer.println("   self.cur_lex_state = JJNEW_LEX_STATE[self.jjmatched_kind as usize];");
                        printer.println("}");
                    }

                    printer.println("continue 'EOFLoop;");
                    printer.outdent();
                    printer.println("}");
                }

                if (data.hasMore()) {
                    if (data.hasMoreActions()) {
                        printer.println("self.more_lexical_actions();");
                    } else if (data.hasSkipActions() || data.hasTokenActions()) {
                        printer.println("self.jjimage_len += self.jjmatched_pos + 1;");
                    }

                    if (data.maxLexStates() > 1) {
                        printer.println("if JJNEW_LEX_STATE[self.jjmatched_kind as usize] != -1 {");
                        printer.println("   self.cur_lex_state = JJNEW_LEX_STATE[self.jjmatched_kind as usize];");
                        printer.println("}");
                    }
                    printer.println("cur_pos = 0;");
                    printer.println("self.jjmatched_kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");

                    printer.println("try {");
                    printer.println("   cur_char = input_stream.read_char();");

                    if (data.options().getDebugTokenManager()) {
                        printer.println("   debugStream.println("
                                + (data.maxLexStates() > 1
                                ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + " : "")
                                + "\"Current character : \" + "
                                + "TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \") "
                                + "at line \" + input_stream.get_end_line() + \" column \" + input_stream.get_end_column());");
                    }
                    printer.println("continue;");
                    printer.println("} catch (java.io.IOException e1) {");
                    printer.println("}");
                }
            }

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

        if (data.hasMore()) {
            printer.outdent();
            printer.println("}");
        }
    }

    @Override
    protected void dumpSkipActions(LinePrinter printer, LexerData data) {
        Outer:
        for (int i = 0; i < data.maxOrdinal(); i++) {
            if ((data.toSkip(i / 64) & (1L << (i % 64))) == 0L) {
                continue;
            }

            for (; ; ) {
                Action act = data.actions(i);
                if (((act == null) || act.getActionTokens().isEmpty()) && !data.canLoop(
                        data.getState(i))) {
                    continue Outer;
                }

                printer.println("      case " + i + " :");

                if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
                    printer.println("         if (jjmatched_pos == -1)");
                    printer.println("         {");
                    printer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
                    printer.println("                jjemptyLineNo[" + data.getState(i)
                            + "] == input_stream.get_begin_line() &&");
                    printer.println("                jjemptyColNo[" + data.getState(i)
                            + "] == input_stream.get_begin_column())");
                    printer.println("               throw new TokenException("
                            + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                            + "at line \" + input_stream.get_begin_line() + \", "
                            + "column \" + input_stream.get_begin_column() + \".\"), TokenException.LOOP_DETECTED);");
                    printer.println("            jjemptyLineNo[" + data.getState(i)
                            + "] = input_stream.get_begin_line();");
                    printer.println("            jjemptyColNo[" + data.getState(i)
                            + "] = input_stream.get_begin_column();");
                    printer.println("            jjbeenHere[" + data.getState(i) + "] = true;");
                    printer.println("         }");
                }

                if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
                    break;
                }

                printer.print("         image.append");
                if (data.getImage(i) != null) {
                    printer.println("(JJSTR_LITERAL_IMAGES[" + i + "]);");
                    printer.println("        length_of_match = JJSTR_LITERAL_IMAGES[" + i + "].length();");
                } else {
                    printer.println("(input_stream.GetSuffix(jjimage_len + (length_of_match = jjmatched_pos + 1)));");
                }

                setup_token(act.getActionTokens().getFirst());
                reset_column();

                printActionToken(printer, act);
                printer.println();

                break;
            }

            printer.println("         break;");
        }
    }

    @Override
    protected void dumpMoreActions(LinePrinter printer, LexerData data) {
        Outer:
        for (int i = 0; i < data.maxOrdinal(); i++) {
            if ((data.toMore(i / 64) & (1L << (i % 64))) == 0L) {
                continue;
            }

            for (; ; ) {
                Action act = data.actions(i);
                if (((act == null) || act.getActionTokens().isEmpty()) && !data.canLoop(
                        data.getState(i))) {
                    continue Outer;
                }

                printer.println("      case " + i + " :");

                if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
                    printer.println("         if (jjmatched_pos == -1)");
                    printer.println("         {");
                    printer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
                    printer.println("                jjemptyLineNo[" + data.getState(i)
                            + "] == input_stream.get_begin_line() &&");
                    printer.println("                jjemptyColNo[" + data.getState(i)
                            + "] == input_stream.get_begin_column())");
                    printer.println("               throw new TokenException("
                            + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                            + "at line \" + input_stream.get_begin_line() + \", "
                            + "column \" + input_stream.get_begin_column() + \".\"), TokenException.LOOP_DETECTED);");
                    printer.println("            jjemptyLineNo[" + data.getState(i)
                            + "] = input_stream.get_begin_line();");
                    printer.println("            jjemptyColNo[" + data.getState(i)
                            + "] = input_stream.get_begin_column();");
                    printer.println("            jjbeenHere[" + data.getState(i) + "] = true;");
                    printer.println("         }");
                }

                if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
                    break;
                }

                printer.print("         image.append");

                if (data.getImage(i) != null) {
                    printer.println("(JJSTR_LITERAL_IMAGES[" + i + "]);");
                } else {
                    printer.println("(input_stream.GetSuffix(jjimage_len));");
                }

                printer.println("         jjimage_len = 0;");
                setup_token(act.getActionTokens().getFirst());
                reset_column();

                printActionToken(printer, act);
                printer.println();

                break;
            }

            printer.println("         break;");
        }
    }

    @Override
    protected void dumpTokenActions(LinePrinter printer, LexerData data) {
        Action act;
        int i;

        Outer:
        for (i = 0; i < data.maxOrdinal(); i++) {
            if ((data.toToken(i / 64) & (1L << (i % 64))) == 0L) {
                continue;
            }

            for (; ; ) {
                if ((((act = data.actions(i)) == null) || act.getActionTokens().isEmpty())
                        && !data.canLoop(
                        data.getState(i))) {
                    continue Outer;
                }

                printer.println("      case " + i + " :");

                if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
                    printer.println("         if (jjmatched_pos == -1)");
                    printer.println("         {");
                    printer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
                    printer.println("                jjemptyLineNo[" + data.getState(i)
                            + "] == input_stream.get_begin_line() &&");
                    printer.println("                jjemptyColNo[" + data.getState(i)
                            + "] == input_stream.get_begin_column())");
                    printer.println("               throw new TokenException("
                            + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                            + "at line \" + input_stream.get_begin_line() + \", "
                            + "column \" + input_stream.get_begin_column() + \".\"), TokenException.LOOP_DETECTED);");
                    printer.println("            jjemptyLineNo[" + data.getState(i)
                            + "] = input_stream.get_begin_line();");
                    printer.println("            jjemptyColNo[" + data.getState(i)
                            + "] = input_stream.get_begin_column();");
                    printer.println("            jjbeenHere[" + data.getState(i) + "] = true;");
                    printer.println("         }");
                }

                if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
                    break;
                }

                if (i == 0) {
                    printer.println("      image.setLength(0);"); // For EOF no image is there
                } else {
                    printer.print("        image.append");

                    if (data.getImage(i) != null) {
                        printer.println("(JJSTR_LITERAL_IMAGES[" + i + "]);");
                        printer.println("        length_of_match = JJSTR_LITERAL_IMAGES[" + i + "].length();");
                    } else {
                        printer.println("(input_stream.GetSuffix(jjimage_len + (length_of_match = jjmatched_pos + 1)));");
                    }
                }

                setup_token(act.getActionTokens().getFirst());
                reset_column();

                printActionToken(printer, act);
                printer.println();

                break;
            }

            printer.println("         break;");
        }
    }

    protected void dumpStateSets(LinePrinter printer, LexerData data) {
        int cnt = 0;
        int length = 0;
        for (int[] set : data.getOrderedStateSet()) {
            length += set.length;
        }

        printer.print("const JJNEXT_STATES : [usize; " + length + "] = [");
        if (!data.getOrderedStateSet().isEmpty()) {
            for (int[] set : data.getOrderedStateSet()) {
                for (int element : set) {
                    if ((cnt++ % 16) == 0) {
                        printer.print("\n   ");
                    }

                    printer.print(element + ", ");
                }
            }
        } else {
            printer.print("0");
        }

        printer.println("\n];");
    }

    private static List<String> getStrLiteralImageList(LexerData data) {
        var list = new ArrayList<String>();
        if (data.getImageCount() <= 0) {
            return list;
        }

        String image;
        int i;
        int charCnt = 0; // Set to zero in reInit() but just to be sure

        data.setImage(0, "");
        for (i = 0; i < data.getImageCount(); i++) {
            if (((image = data.getImage(i)) == null)
                    || (((data.toSkip(i / 64) & (1L << (i % 64))) == 0L) && (
                    (data.toMore(i / 64) & (1L << (i % 64))) == 0L)
                    && ((data.toToken(i / 64) & (1L << (i % 64))) == 0L))
                    || ((data.toSkip(i / 64) & (1L << (i % 64))) != 0L) || (
                    (data.toMore(i / 64) & (1L << (i % 64))) != 0L)
                    || data.canReachOnMore(data.getState(i))
                    || ((data.ignoreCase() || data.ignoreCase(i)) && (
                    !image.equals(image.toLowerCase(Locale.ENGLISH))
                            || !image.equals(image.toUpperCase(Locale.ENGLISH))))) {
                data.setImage(i, null);
                if ((charCnt += 6) > 80) {
                    charCnt = 0;
                }

                list.add(null);
                continue;
            }

            String toPrint = "";
            for (int j = 0; j < image.length(); j++) {
                if (image.charAt(j) <= 0xff) {
                    toPrint += ("0o" + Integer.toOctalString(image.charAt(j))) + ";";
                } else {
                    String hexVal = Integer.toHexString(image.charAt(j));
                    if (hexVal.length() == 3) {
                        hexVal = "0x" + hexVal + ";";
                    }
                    toPrint += ("\\u" + hexVal);
                }
            }

            if ((charCnt += toPrint.length()) >= 80) {
                charCnt = 0;
            }

            list.add(toPrint);
        }

        while (++i < data.maxOrdinal()) {
            if ((charCnt += 6) > 80) {
                charCnt = 0;
            }
            list.add(null);
        }
        return list;
    }

    private void DumpStartWithStates(LinePrinter printer, NfaStateData data) {
        printer.println("private int " + "jjStartNfaWithStates" + data.getLexerStateSuffix()
                + "(int pos, int kind, int state) {");
        printer.indent();
        printer.println("jjmatched_kind = kind;");
        printer.println("jjmatched_pos = pos;");

        if (data.global.options().getDebugTokenManager()) {
            printer.println("debugStream.println(\"   No more string literal token matches are possible.\");");
            printer.println("debugStream.println(\"   Currently matched the first \" "
                    + "+ (jjmatched_pos + 1) + \" characters as a \" + tokenImage[jjmatched_kind] + \" token.\");");
        }

        printer.println("try { cur_char = input_stream.read_char(); }");
        printer.println("catch (java.io.IOException e) { return pos + 1; }");

        if (data.global.options().getDebugTokenManager()) {
            printer.println("debugStream.println("
                    + (data.global.maxLexStates() > 1
                    ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + " : "")
                    + "\"Current character : \" + TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \") "
                    + "at line \" + input_stream.get_end_line() + \" column \" + input_stream.get_end_column());");
        }
        printer.println("return jj_move_nfa" + data.getLexerStateSuffix() + "(state, pos + 1);");
        printer.outdent();
        printer.println("}");
    }

    @Override
    protected final void DumpHeadForCase(LinePrinter printer, int byteNum) {
        if (byteNum == 0)
            printer.println("let l: u64 = 1u64 << self.cur_char;");
        else if (byteNum == 1)
            printer.println("let l: u64 = 1u64 << (self.cur_char & 0o77);");
        else {
            printer.println("let hi_byte: u32 = self.cur_char >> 8;");
            printer.println("let l1: u64 = 1u64 << (hi_byte & 0o77);");
            printer.println("let l2: u64 = 1u64 << (self.cur_char & 0o77);");
            printer.println("let i1: usize = (hi_byte >> 6) as usize;");
            printer.println("let i2: usize = ((self.cur_char & 0xff) >> 6) as usize;");
        }

        // printer.println(" MatchLoop: do");
        printer.println("let mut while_cond = true;");
        printer.println("while while_cond {");
        printer.indent();
        printer.println("i -= 1;");
        printer.println("match self.jjstate_set[i] {");
        printer.indent();
    }

    protected void dumpNonAsciiMoveMethod(LexerData data, NfaState state, LinePrinter printer) {
        for (int j = 0; j < state.loByteVec.size(); j += 2) {
            printer.println(state.loByteVec.get(j) + " => {");
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.loByteVec.get(j + 1))))
                printer.println("    return (JJBIT_VEC" + state.loByteVec.get(j + 1) + "[i2] & l2) != 0;");
            else
                printer.println("    return true;");
            printer.println("}");
        }

        printer.println("_ => {");
        for (int j = state.nonAsciiMoveIndices.length; j > 0; j -= 2) {
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.nonAsciiMoveIndices[j - 2]))) {
                printer.println("    if (JJBIT_VEC" + state.nonAsciiMoveIndices[j - 2] + "[i1] & l1) != 0 {");
            }
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.nonAsciiMoveIndices[j - 1]))) {
                printer.println("        if (JJBIT_VEC" + state.nonAsciiMoveIndices[j - 1] + "[i2] & l2) == 0 {");
                printer.println("            return false;");
                printer.println("        } else {");
            }
            printer.println("        return true;");
            printer.println("    }");
        }
        printer.println("    false");
        printer.println("}");
    }

    private String getStatesForState(LexerData data) {
        if (data.getStatesForState() == null) {
            assert (false) : "This should never be null.";
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < data.maxLexStates(); i++) {
            if (data.getStatesForState()[i] == null) {
                builder.append("{},");
                continue;
            }
            builder.append("{");
            for (int j = 0; j < data.getStatesForState()[i].length; j++) {
                int[] stateSet = data.getStatesForState()[i][j];
                if (stateSet == null) {
                    builder.append("{ ").append(j).append(" },");
                    continue;
                }
                builder.append("{ ");
                for (int element : stateSet) {
                    builder.append(element).append(",");
                }
                builder.append("},");
            }
            builder.append("},");
        }
        return builder.toString();
    }

    private String getKindForState(LexerData data) {
        if (data.getKinds() == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        boolean moreThanOne = false;
        for (int[] kind : data.getKinds()) {
            if (moreThanOne) {
                builder.append(",");
            }
            moreThanOne = true;
            if (kind == null) {
                builder.append("{}");
            } else {
                builder.append("{ ");
                for (int element : kind) {
                    builder.append(element);
                    builder.append(",");
                }
                builder.append("}");
            }
        }
        return builder.toString();
    }

    @Override
    protected final void dumpNfaStartStatesCode(LinePrinter printer, NfaStateData data,
                                                Hashtable<String, long[]>[] statesForPos) {
        if (data.getMaxStrKind() == 0) // No need to generate this function
            return;

        int i, maxKindsReqd = (data.getMaxStrKind() / 64) + 1;
        boolean condGenerated = false;
        int ind;

        printer.println();
        printer.print("fn jjStopStringLiteralDfa" + data.getLexerStateSuffix() + "(&self, pos: usize, ");
        for (i = 0; i < maxKindsReqd; i++) {
            printer.print(", active" + i + ": u64");
        }
        printer.println(") -> usize {");

        if (data.global.options().getDebugTokenManager()) {
            printer.println("      debugStream.println(\"   No more string literal token matches are possible.\");");
        }

        printer.println("   switch (pos)");
        printer.println("   {");

        for (i = 0; i < (data.getMaxLen() - 1); i++) {
            if (statesForPos[i] == null) {
                continue;
            }

            printer.println("      case " + i + ":");

            for (String stateSetString : statesForPos[i].keySet()) {
                long[] actives = statesForPos[i].get(stateSetString);

                for (int j = 0; j < maxKindsReqd; j++) {
                    if (actives[j] == 0L) {
                        continue;
                    }

                    if (condGenerated) {
                        printer.print(" || ");
                    } else {
                        printer.print("         if (");
                    }

                    condGenerated = true;

                    printer.print("(active" + j + " & " + toHexString(actives[j]) + ") != 0L");
                }

                if (condGenerated) {
                    printer.println(")");

                    String kindStr = stateSetString.substring(0,
                            ind = stateSetString.indexOf(", "));
                    String afterKind = stateSetString.substring(ind + 2);
                    int jjmatchedPos = Integer.parseInt(
                            afterKind.substring(0, afterKind.indexOf(", ")));

                    if (!kindStr.equals(String.valueOf(Integer.MAX_VALUE))) {
                        printer.println("         {");
                    }

                    if (!kindStr.equals(String.valueOf(Integer.MAX_VALUE))) {
                        if (i == 0) {
                            printer.println("            jjmatched_kind = " + kindStr + ";");

                            if (((data.global.initMatch(data.getStateIndex()) != 0)
                                    && (data.global.initMatch(data.getStateIndex())
                                    != Integer.MAX_VALUE))) {
                                printer.println("            jjmatched_pos = 0;");
                            }
                        } else if (i == jjmatchedPos) {
                            if (data.isSubStringAtPos(i)) {
                                printer.println("            if (jjmatched_pos != " + i + ")");
                                printer.println("            {");
                                printer.println("               jjmatched_kind = " + kindStr + ";");
                                printer.println("               jjmatched_pos = " + i + ";");
                                printer.println("            }");
                            } else {
                                printer.println("            jjmatched_kind = " + kindStr + ";");
                                printer.println("            jjmatched_pos = " + i + ";");
                            }
                        } else {
                            if (jjmatchedPos > 0) {
                                printer.println("            if (jjmatched_pos < " + jjmatchedPos + ")");
                            } else {
                                printer.println("            if (jjmatched_pos == 0)");
                            }
                            printer.println("            {");
                            printer.println("               jjmatched_kind = " + kindStr + ";");
                            printer.println("               jjmatched_pos = " + jjmatchedPos + ";");
                            printer.println("            }");
                        }
                    }

                    kindStr = stateSetString.substring(0, ind = stateSetString.indexOf(", "));
                    afterKind = stateSetString.substring(ind + 2);
                    stateSetString = afterKind.substring(afterKind.indexOf(", ") + 2);

                    if (stateSetString.equals("null;")) {
                        printer.println("            return -1;");
                    } else {
                        printer.println("            return " + getCompositeStateSet(data, stateSetString) + ";");
                    }

                    if (!kindStr.equals(String.valueOf(Integer.MAX_VALUE))) {
                        printer.println("         }");
                    }
                    condGenerated = false;
                }
            }

            printer.println("         return -1;");
        }

        printer.println("      default:");
        printer.println("         return -1;");
        printer.println("   }");
        printer.println("}");

        printer.println();
        printer.print("private final int jjStartNfa" + data.getLexerStateSuffix() + "(int pos");
        for (i = 0; i < maxKindsReqd; i++) {
            printer.print(",  long active" + i);
        }

        printer.println(") {");

        if (data.isMixedState()) {
            if (data.generatedStates() != 0) {
                printer.println("   return jj_move_nfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", pos + 1);");
            } else {
                printer.println("   return pos + 1;");
            }

            printer.println("}");
            return;
        }

        printer.println("   return jj_move_nfa" + data.getLexerStateSuffix() + "(" + "jjStopStringLiteralDfa"
                + data.getLexerStateSuffix() + "(pos, ");
        for (i = 0; i < (maxKindsReqd - 1); i++) {
            printer.print("active" + i + ", ");
        }
        printer.print("active" + i + ")");
        printer.println(", pos + 1);");
        printer.println("}");
    }

    @Override
    protected final void dumpDfaCode(LinePrinter printer, NfaStateData data) {
        Hashtable<String, ?> tab;
        String key;
        KindInfo info;
        int maxLongsReqd = (data.getMaxStrKind() / 64) + 1;
        int i, j, k;
        boolean ifGenerated;

        if (data.getMaxLen() == 0) {
            printer.println();
            printer.println("fn jj_move_string_literal_dfa0" + data.getLexerStateSuffix() + "(&self) -> usize {");
            printer.indent();
            if (data.generatedStates() > 0)
                printer.println("return " + self() + "jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", 0);");
            else
                printer.println("return 1;");
            printer.outdent();
            printer.println("}");
            return;
        }

        if (!data.global.isBoilerPlateDumped()) {
            printer.println("fn jj_stop_at_pos(&mut self, pos: usize, kind: u32) -> usize {");
            printer.indent();
            printer.println("self.jjmatched_kind = kind;");
            printer.println("self.jjmatched_pos = pos;");

            if (data.global.options().getDebugTokenManager()) {
                printer.println("debugStream.println(\"No more string literal token matches are possible.\");");
                printer.println("debugStream.println(\"Currently matched the first \" + (jjmatched_pos + 1) + " + "\" characters as a \" + tokenImage[jjmatched_kind] + \" token.\");");
            }

            printer.println("pos + 1");
            printer.outdent();
            printer.println("}");
            data.global.setBoilerPlateDumped(true);
        }

        for (i = 0; i < data.getMaxLen(); i++) {
            boolean startNfaNeeded = false;
            tab = data.getCharPosKind(i);
            var keys = LexerGenerator.re_arrange(tab);

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

            if (i != 0) {
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
                                "(" + InitStateName(data) + ", " + (i - 1) + ");");
                    } else {
                        printer.println("return " + i + ";");
                    }
                    printer.outdent();
                    printer.println("}");
                }

                if (data.global.options().getDebugTokenManager()) {
                    printer.println("if self.jjmatched_kind != 0 && vjjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + " {");
                    printer.println("   debugStream.println(\"   Currently matched the first \" + "
                            + "(self.jjmatched_pos + 1) + \" characters as a \" + self.tokenImage[self.jjmatched_kind as usize] + \" token.\");");
                    printer.println("   debugStream.println(\"   Possible string literal matches : { \"");

                    for (int vecs = 0; vecs < ((data.getMaxStrKind() / 64) + 1); vecs++) {
                        if (i <= data.getMaxLenForActive(vecs)) {
                            printer.println(" +");
                            printer.print("         self.jjKindsForBitVector(" + vecs + ", ");
                            printer.print("active" + vecs + ") ");
                        }
                    }

                    printer.println(" + \" } \");");
                    printer.println("}");
                }

                printer.println("let result = self.input_stream.read_char();");
                printer.println("if result.is_err() {");
                printer.indent();

                if (!data.isMixedState() && (data.generatedStates() != 0)) {
                    printer.print("self.jjStopStringLiteralDfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ");
                    for (k = 0; k < (maxLongsReqd - 1); k++) {
                        if (i <= data.getMaxLenForActive(k)) {
                            printer.print("active" + k + ", ");
                        } else {
                            printer.print("0, ");
                        }
                    }

                    if (i <= data.getMaxLenForActive(k))
                        printer.println("active" + k + ");");
                    else
                        printer.println("0);");

                    if (data.global.options().getDebugTokenManager()) {
                        printer.println("if self.jjmatched_kind != 0 && self.jjmatched_kind != 0x" + Integer.toHexString(Integer.MAX_VALUE));
                        printer.println("   debugStream.println(\"   Currently matched the first \" + " + "(jjmatched_pos + 1) + \" characters as a \" + tokenImage[jjmatched_kind] + \" token.\");");
                    }
                    printer.println("return " + i + ";");
                } else if (data.generatedStates() != 0)
                    printer.println("return self.jj_move_nfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", " + (i - 1) + ");");
                else
                    printer.println("return " + i + ";");

                printer.outdent();
                printer.println("}");
                printer.println("self.cur_char = u32::from(result.unwrap());");
                printer.println();
            }

            if ((i != 0) && data.global.options().getDebugTokenManager()) {
                printer.println("debugStream.println("
                        + (data.global.maxLexStates() > 1
                        ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + "
                        : "")
                        + "\"Current character : \" + TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \") "
                        + "at line \" + input_stream.get_end_line() + \" column \" + input_stream.get_end_column());");
            }

            printer.println("match self.cur_char {");
            printer.indent();

            CaseLoop:
            for (String key2 : keys) {
                key = key2;
                info = (KindInfo) tab.get(key);
                ifGenerated = false;
                char c = key.charAt(0);

                if ((i == 0) && (c < 128) && info.hasFinalKindCnt()
                        && ((data.generatedStates() == 0) || CanStartNfaUsingAscii(data, c))) {
                    for (j = 0; j < maxLongsReqd; j++) {
                        if (info.finalKinds[j] != 0L) {
                            break;
                        }
                    }

                    int kind;
                    for (k = 0; k < 64; k++) {
                        if (((info.finalKinds[j] & (1L << k)) != 0L) && !data.isSubString(kind = ((j * 64) + k))) {
                            if (((data.getIntermediateKinds() != null) && (
                                    data.getIntermediateKinds()[((j * 64) + k)] != null)
                                    && (data.getIntermediateKinds()[((j * 64) + k)][i] < ((j * 64) + k))
                                    && (data.getIntermediateMatchedPos() != null)
                                    && (data.getIntermediateMatchedPos()[((j * 64) + k)][i] == i))
                                    || ((data.global.canMatchAnyChar(data.getStateIndex()) >= 0)
                                    && (data.global.canMatchAnyChar(data.getStateIndex()) < ((j * 64)
                                    + k)))) {
                                break;
                            } else if (((data.global.toSkip(kind / 64) & (1L << (kind % 64))) != 0L)
                                    && ((data.global.toSpecial(kind / 64) & (1L << (kind % 64))) == 0L)
                                    && (data.global.actions(kind) == null) && (
                                    data.global.newLexState(kind)
                                            == null)) {
                                continue CaseLoop;
                            }
                        }
                    }
                }

                // Since we know key is a single character ...
                if (data.ignoreCase()) {
                    if (c != Character.toUpperCase(c)) {
                        printer.println(((int) Character.toUpperCase(c)) + " =>");
                    }

                    if (c != Character.toLowerCase(c)) {
                        printer.println(((int) Character.toUpperCase(c)) + " =>");
                    }
                }

                printer.println((int) c + " => {");
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

                            int kindToPrint;
                            if (i != 0) {
                                printer.print("(active" + j + " & 0x" + Long.toHexString(1L << k) + ") != 0");
                            }

                            if ((data.getIntermediateKinds() != null) && (
                                    data.getIntermediateKinds()[((j * 64) + k)] != null)
                                    && (data.getIntermediateKinds()[((j * 64) + k)][i] < ((j * 64) + k))
                                    && (data.getIntermediateMatchedPos() != null)
                                    && (data.getIntermediateMatchedPos()[((j * 64) + k)][i] == i)) {
                                show_warning_intermediate(data, i, j, k);
                                kindToPrint = data.getIntermediateKinds()[((j * 64) + k)][i];
                            } else if ((i == 0) && (data.global.canMatchAnyChar(data.getStateIndex())
                                    >= 0)
                                    && (data.global.canMatchAnyChar(data.getStateIndex()) < ((j * 64)
                                    + k))) {
                                show_warning_match(data, i, j, k);
                                kindToPrint = data.global.canMatchAnyChar(data.getStateIndex());
                            } else {
                                kindToPrint = (j * 64) + k;
                            }

                            if (!data.isSubString((j * 64) + k)) {
                                int stateSetName = GetStateSetForKind(data, i, (j * 64) + k);

                                if (stateSetName != -1) {
                                    printer.println("return self.jjStartNfaWithStates"
                                            + data.getLexerStateSuffix() + "(" + i
                                            + ", " + kindToPrint + ", " + stateSetName + ");");
                                } else {
                                    printer.println("return self.jj_stop_at_pos" + "(" + i + ", " + kindToPrint + ");");
                                }
                                printer.println("}");
                            } else if (((data.global.initMatch(data.getStateIndex()) != 0)
                                    && (data.global.initMatch(data.getStateIndex())
                                    != Integer.MAX_VALUE)) || (i
                                    != 0)) {
                                printer.println(" {");
                                printer.println("    self.jjmatched_kind = " + kindToPrint + ";");
                                printer.println("    self.jjmatched_pos = " + i + ";");
                                printer.println("}");
                            } else {
                                printer.println("self.jjmatched_kind = " + kindToPrint + ";");
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

                                printer.print(toHexString(info.validKinds[j]));
                            }
                        }

                        if ((i + 1) <= data.getMaxLenForActive(j)) {
                            if (atLeastOne)
                                printer.print(", ");

                            printer.print(toHexString(info.validKinds[j]));
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
                            printer.println("return self.jj_move_nfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", 0);");
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

            printer.println("_ => {");
            printer.indent();

            if (data.global.options().getDebugTokenManager()) {
                printer.println("debugStream.println(\"No string literal matches possible.\");");
            }

            if (data.generatedStates() != 0) {
                if (i == 0) {
                    // This means no string literal is possible. Just move nfa with this guy and return.
                    printer.println("return self.jj_move_nfa" + data.getLexerStateSuffix() + "("
                            + InitStateName(data) + ", 0);");
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
                    printer.println("self.jj_move_nfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", " + i + ")");
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

    @Override
    protected final void dumpMoveNfa(LinePrinter printer, NfaStateData data) {
        printer.println();
        printer.println("fn jj_move_nfa" + data.getLexerStateSuffix()
                + "(&mut self, start_state: usize, cur_pos: usize) -> usize {");
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
            printer.println("debugStream.println(\"   Starting NFA to match one of : \" + "
                    + "jjKindsForStateVector(self.cur_lex_state, jjstate_set, 0, 1));");
            printer.println("debugStream.println("
                    + (data.global.maxLexStates() > 1
                    ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + " : "")
                    + "\"Current character : \" + TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \") "
                    + "at line \" + input_stream.get_end_line() + \" column \" + input_stream.get_end_column());");
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
            printer.println("if self.jjmatched_kind != 0 && self.jjmatched_kind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + " {");
            printer.println("   debugStream.println("
                    + "\"   Currently matched the first \" + (jjmatched_pos + 1) + \" characters as"
                    + " a \" + tokenImage[jjmatched_kind] + \" token.\");");
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
            printer.println("      debugStream.println(\"   Possible kinds of longer matches : \" + "
                    + "jjKindsForStateVector(self.cur_lex_state, jjstate_set, starts_at, i));");
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
            printer.println("debugStream.println("
                    + (data.global.maxLexStates() > 1 ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + " : "")
                    + "\"Current character : \" + TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \") "
                    + "at line \" + input_stream.get_end_line() + \" column \" + input_stream.get_end_column());");
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

    protected void getRegExp(LinePrinter printer, int i, List<RExpression> expressions, boolean isImage) {
        if (i == 0)
            printer.print("\"<EOF>\",");
        else {
            var re = expressions.get(i - 1);
            if (re instanceof RStringLiteral rl) {
                printer.print("\"\\\"" + Encoding.escape(Encoding.escape(rl.getImage())) + "\\\"\"");
            } else if (!re.getLabel().isEmpty()) {
                printer.print("\"<" + re.getLabel() + ">\"");
            } else if (re.getTokenKind() == TokenKind.TOKEN) {
                JavaCCErrors.warning(re, "Consider giving this non-string token a label for better error reporting.");
            } else {
                printer.print("\"<token of kind " + re.getOrdinal() + ">\"");
            }
            printer.print(",");
        }
    }
}
