// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.rust;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

import org.hivevm.cc.Encoding;
import org.hivevm.cc.Language;
import org.hivevm.cc.generator.LexerData;
import org.hivevm.cc.generator.LexerGenerator;
import org.hivevm.cc.generator.LexerGeneratorRust;
import org.hivevm.cc.generator.NfaStateData;
import org.hivevm.cc.generator.NfaStateData.KindInfo;
import org.hivevm.cc.lexer.NfaState;
import org.hivevm.cc.model.Action;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.RStringLiteral;
import org.hivevm.cc.model.TokenProduction;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.Token;
import org.hivevm.source.SourceWriter;
import org.hivevm.source.Template;

/**
 * Generate lexer.
 */
class RustLexerGenerator extends LexerGeneratorRust {

    @Override
    public final void generate(LexerData data) {
        if (JavaCCErrors.hasError()) {
            return;
        }

        var options = Template.newContext(data.options());
        options.add("LOHI_BYTES_LENGTH", data.getLohiByteSize() + 1);
        options.add(LexerGenerator.LOHI_BYTES, data.getLohiByte())
            .set("LOHI_BYTES_INDEX", i -> i)
            .set("LOHI_BYTES_BYTES", i -> getLohiBytes(data, i));
        options.add(LexerGenerator.STATES, data.getStateNames())
            .set("STATE_NAME", s -> s)
            .set("NFA_AND_DFA", (n, w) -> dumpNfaAndDfa(data.getStateData(n), w));
        options.add(LexerGenerator.NON_ASCII_TABLE, data.getNonAsciiTableForMethod())
                .set("NON_ASCII_TABLE_NAME", s -> "_" + s.nonAsciiMethod)
                .set("NON_ASCII_TABLE_MOVE", (s, w) -> DumpNonAsciiMoveMethod(s, data, w));

        options.add("LITERAL_IMAGES", RustLexerGenerator.getStrLiteralImageList(data))
            .set("LITERAL_IMAGE_NAME", s -> s);
        options.set("LITERAL_IMAGES_LENGTH", RustLexerGenerator.getStrLiteralImageList(data).size());
        options.set("STATES_FOR_STATE", () -> getStatesForState(data));
        options.set("KIND_FOR_STATE", () -> getKindForState(data));

        options.set("DUMP_SKIP_ACTIONS", p -> DumpSkipActions(p, data));
        options.set("DUMP_MORE_ACTIONS", p -> DumpMoreActions(p, data));
        options.set("DUMP_TOKEN_ACTIONS", p -> DumpTokenActions(p, data));
        options.set("DUMP_STATES_SET", p -> DumpStateSets(p, data));
        options.set("DUMP_GET_NEXT_TOKEN", p -> DumpGetNextToken(p, data));
        options.set("DUMP_STATIC_VAR_DECLARATION", p -> DumpStaticVarDeclarations(p, data));

        options.set(LexerGenerator.DEFAULT_LEX_STATE, data.defaultLexState());
        options.set(LexerGenerator.MAX_LEX_STATES, data.maxLexStates());
        options.set(LexerGenerator.STATE_NAMES, data.getStateNames());
        options.set("STATE_NAMES_LENGTH", data.getStateNames().size());
        options.set(LexerGenerator.KEEP_LINE_COL, data.keepLineCol());

        options.set(LexerGenerator.HAS_SKIP, data.hasSkip());
        options.set(LexerGenerator.HAS_MORE, data.hasMore());
        options.set(LexerGenerator.HAS_LOOP, data.hasLoop());
        options.set(LexerGenerator.HAS_SPECIAL, data.hasSpecial());

        options.set(LexerGenerator.HAS_MOPRE_ACTIONS, data.hasMoreActions());
        options.set(LexerGenerator.HAS_SKIP_ACTIONS, data.hasSkipActions());
        options.set(LexerGenerator.HAS_TOKEN_ACTIONS, data.hasTokenActions());
        options.set(LexerGenerator.HAS_EMPTY_MATCH, data.hasEmptyMatch());

        options.set(LexerGenerator.STATE_SET_SIZE, data.stateSetSize());
        options.set(LexerGenerator.STATE_SET_SIZE + "2", data.stateSetSize() * 2);
        options.set(LexerGenerator.DUAL_NEED, data.jjCheckNAddStatesDualNeeded());
        options.set(LexerGenerator.UNARY_NEED, data.jjCheckNAddStatesUnaryNeeded());
        options.set(LexerGenerator.STATE_COUNT, data.getStateCount());

        RustSources.LEXER.render(options);

        generateConstants(data);
    }

    protected String self() {
        return "self.";
    }

    protected final void generateConstants(LexerData data) {
        var expressions = new ArrayList<RExpression>();
        for (var tp : data.getTokenProductions()) {
            for (var res : tp.getRespecs()) {
                expressions.add(res.rexp);
            }
        }

        var options = Template.newContext(data.options());
        options.add("STATES", data.getStateCount())
            .set("STATE_INDEX", i -> i)
            .set("STATE_NAME", data::getStateName);
        options.add("TOKENS", data.getOrderedsTokens())
            .set("TOKEN_ORDINAL", RExpression::getOrdinal)
            .set("TOKEN_LABEL", RExpression::getLabel);
        options.add("PRODUCTIONS_COUNT", expressions.size() + 1);
        options.add("PRODUCTIONS", expressions)
            .set("PRODUCTION_LABEL", re -> {
                var builder = new StringBuilder();
                if (re instanceof RStringLiteral) {
                    builder.append("\"\\\"")
                        .append(Encoding.escape(Encoding.escape(((RStringLiteral) re).getImage())))
                        .append("\\\"\"");
                }
                else if (!re.getLabel().isEmpty()) {
                    builder.append("\"<").append(re.getLabel()).append(">\"");
                }
                else if (re.getTpContext().getKind() == TokenProduction.Kind.TOKEN) {
                    JavaCCErrors.warning(re, "Consider giving this non-string token a label for better error reporting.");
                }
                else {
                    builder.append("\"<token of kind ").append(re.getOrdinal()).append(">\"");
                }

                builder.append(",");
                return builder.toString();
        });

        RustSources.PARSER_CONSTANTS.render(options);
    }

    @Override
    protected final Language getLanguage() {
        return Language.RUST;
    }

    private void DumpStaticVarDeclarations(SourceWriter writer, LexerData data) {
        if (data.maxLexStates() > 1) {
            writer.new_line();
            writer.append("const JJNEW_LEX_STATE : [i8; " + data.maxOrdinal() + "] = [");

            for (int i = 0; i < data.maxOrdinal(); i++) {
                if ((i % 25) == 0) {
                    writer.append("\n   ");
                }

                if (data.newLexState(i) == null) {
                    writer.append("-1, ");
                }
                else {
                    writer.append(data.getStateIndex(data.newLexState(i)) + ", ");
                }
            }
            writer.append("\n];").new_line();
        }

        if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
            // Bit vector for TOKEN
            writer.append("const JJTO_TOKEN : [u64; " + ((data.maxOrdinal() / 64) + 1) + "] = [");
            for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
                if ((i % 4) == 0) {
                    writer.append("\n   ");
                }
                writer.append(toHexString(data.toToken(i)) + ", ");
            }
            writer.append("\n];").new_line();
        }

        if (data.hasSkip() || data.hasSpecial()) {
            // Bit vector for SKIP
            writer.append("const JJTO_SKIP : [u64; " + ((data.maxOrdinal() / 64) + 1) + "] = [");
            for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
                if ((i % 4) == 0) {
                    writer.append("\n   ");
                }
                writer.append(toHexString(data.toSkip(i)) + ", ");
            }
            writer.append("\n];").new_line();
        }

        if (data.hasSpecial()) {
            // Bit vector for SPECIAL
            writer.append("static final long[] jjtoSpecial = {");
            for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
                if ((i % 4) == 0) {
                    writer.append("\n   ");
                }
                writer.append(toHexString(data.toSpecial(i)) + ", ");
            }
            writer.append("\n};").new_line();
        }

        if (data.hasMore()) {
            // Bit vector for MORE
            writer.append("static final long[] jjtoMore = {");
            for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
                if ((i % 4) == 0) {
                    writer.append("\n   ");
                }
                writer.append(toHexString(data.toMore(i)) + ", ");
            }
            writer.append("\n};").new_line();
        }
    }

    private void DumpGetNextToken(SourceWriter writer, LexerData data) {
        if ((data.getNextStateForEof() != null) || (data.getActionForEof() != null)) {
            writer.append("self.token_lexical_actions(matched_token);").new_line();
        }

        writer.append("    return matched_token;").new_line();
        writer.append("}").new_line();

        if (data.hasMoreActions() || data.hasSkipActions() || data.hasTokenActions()) {
            writer.append("   image = jjimage;").new_line();
            writer.append("   image.setLength(0);").new_line();
            writer.append("   jjimage_len = 0;").new_line();
        }

        writer.new_line();

        String prefix = "";
        if (data.hasMore()) {
            writer.append("   for (;;)").new_line();
            writer.append("   {").new_line();
            prefix = "  ";
        }

        String endSwitch = "";
        String caseStr = "";
        // this also sets up the start state of the nfa
        if (data.maxLexStates() > 1) {
            writer.append(prefix + "match self.cur_lex_state {").new_line();
            endSwitch = prefix + "   }";
            caseStr = prefix + " => {";
            prefix += "    ";
        }

        prefix += "   ";
        for (int i = 0; i < data.maxLexStates(); i++) {
            if (data.maxLexStates() > 1)
                writer.append(i + caseStr).new_line();

            if (data.singlesToSkip(i).HasTransitions()) {
                // added the backup(0) to make JIT happy
                writer.append("try { input_stream.backup(0);").new_line();
                if ((data.singlesToSkip(i).asciiMoves[0] != 0L) && (data.singlesToSkip(i).asciiMoves[1]
                        != 0L)) {
                    writer.append(
                            prefix + "   while ((cur_char < 64" + " && (0x" + Long.toHexString(
                                    data.singlesToSkip(i).asciiMoves[0])
                                    + "L & (1L << cur_char)) != 0L) || \n" + prefix + "          (cur_char >> 6) == 1"
                                    + " && (0x"
                                    + Long.toHexString(data.singlesToSkip(i).asciiMoves[1])
                                    + "L & (1L << (cur_char & 0o77))) != 0L)")
                        .new_line();
                }
                else if (data.singlesToSkip(i).asciiMoves[1] == 0L) {
                    writer.append(
                            prefix + "   while (cur_char <= " + (int) LexerGenerator.MaxChar(
                                    data.singlesToSkip(i).asciiMoves[0])
                                    + " && (0x" + Long.toHexString(data.singlesToSkip(i).asciiMoves[0])
                                    + "L & (1L << cur_char)) != 0L)")
                        .new_line();
                }
                else if (data.singlesToSkip(i).asciiMoves[0] == 0L) {
                    writer.append(prefix + "   while (cur_char > 63 && cur_char <= "
                            + (LexerGenerator.MaxChar(data.singlesToSkip(i).asciiMoves[1]) + 64) + " && (0x"
                            + Long.toHexString(data.singlesToSkip(i).asciiMoves[1])
                            + "L & (1L << (cur_char & 0o77))) != 0L)")
                        .new_line();
                }

                if (data.options().getDebugTokenManager()) {
                    writer.append(prefix + "{").new_line();
                    writer.append("      debugStream.println("
                            + (data.maxLexStates() > 1 ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + " : "")
                            + "\"Skipping character : \" + TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \")\");")
                        .new_line();
                }
                writer.append(prefix + "      cur_char = input_stream.begin_token();")
                    .new_line();

                if (data.options().getDebugTokenManager()) {
                    writer.append(prefix + "}").new_line();
                }

                writer.append(prefix + "}").new_line();
                writer.append(prefix + "catch (java.io.IOException e1) { continue 'EOFLoop; }")
                    .new_line();
            }

            if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
                if (data.options().getDebugTokenManager()) {
                    writer.append(
                            "      debugStream.println(\"   Matched the empty string as \" + tokenImage["
                                    + data.initMatch(i) + "] + \" token.\");")
                        .new_line();
                }

                writer.append(prefix + "self.jjmatched_kind = " + data.initMatch(i) + ";").new_line();
                writer.append(prefix + "self.jjmatched_pos = usize::MAX;").new_line();
                writer.append(prefix + "cur_pos = 0;").new_line();
            }
            else {
                writer.append(
                        prefix + "self.jjmatched_kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";")
                    .new_line();
                writer.append(prefix + "self.jjmatched_pos = 0;")
                    .new_line();
            }

            if (data.options().getDebugTokenManager()) {
                writer.append("      debugStream.println("
                        + (data.maxLexStates() > 1 ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + " : "")
                        + "\"Current character : \" + TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \") "
                        + "at line \" + input_stream.get_end_line() + \" column \" + input_stream.get_end_column());")
                    .new_line();
            }

            writer.append(prefix + "cur_pos = self.jj_move_string_literal_dfa0_" + i + "();")
                .new_line();
            if (data.canMatchAnyChar(i) != -1) {
                if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
                    writer.append(prefix + "if self.jjmatched_pos < 0 || self.jjmatched_pos == 0 && self.jjmatched_kind > "
                            + data.canMatchAnyChar(i) + ") {")
                        .new_line();
                }
                else {
                    writer.append(
                            prefix + "if self.jjmatched_pos == 0 && self.jjmatched_kind > " + data.canMatchAnyChar(i) + " {")
                        .new_line();
                }

                if (data.options().getDebugTokenManager()) {
                    writer.append("           debugStream.println(\"   Current character matched as a \" + tokenImage["
                                    + data.canMatchAnyChar(i) + "] + \" token.\");")
                        .new_line();
                }
                writer.append(prefix + "   self.jjmatched_kind = " + data.canMatchAnyChar(i) + ";").new_line();

                if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
                    writer.append(prefix + "   self.jjmatched_pos = 0;").new_line();
                }

                writer.append(prefix + "}").new_line();
            }

            if (data.maxLexStates() > 1) {
                writer.append("}").new_line();
            }
        }

        if (data.maxLexStates() > 1) {
            writer.append("   _ => {}").new_line();
            writer.append(endSwitch).new_line();
        }
        else if (data.maxLexStates() == 0) {
            writer.append("       self.jjmatched_kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";").new_line();
        }

        if (data.maxLexStates() > 1) {
            prefix = "  ";
        }
        else {
            prefix = "";
        }

        if (data.maxLexStates() > 0) {
            writer.append(prefix + "   if self.jjmatched_kind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + " {").new_line();
            writer.append(prefix + "      if self.jjmatched_pos + 1 < cur_pos {").new_line();

            if (data.options().getDebugTokenManager()) {
                writer.append(prefix + "         debugStream.println("
                        + "\"   Putting back \" + (cur_pos - jjmatched_pos - 1) + \" characters into the input stream.\");")
                    .new_line();
            }

            writer.append(prefix + "         self.input_stream.backup(cur_pos - self.jjmatched_pos - 1);").new_line();
            writer.append(prefix + "      }").new_line();

            if (data.options().getDebugTokenManager()) {
                writer.append("    debugStream.println("
                        + "\"****** FOUND A \" + tokenImage[jjmatched_kind] + \" MATCH "
                        + "(\" + TokenException.addEscapes(new String(input_stream.GetSuffix(jjmatched_pos + 1))) + "
                        + "\") ******\\n\");").new_line();
            }

            if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
                writer.append(prefix + "  if (JJTO_TOKEN[(self.jjmatched_kind >> 6) as usize]").new_line();
                writer.append(prefix + "      & (1 << (self.jjmatched_kind & 0o77)))").new_line();
                writer.append(prefix + "      != 0").new_line();
                writer.append(prefix + "      {").new_line();
            }

            writer.append(prefix + "         matched_token = self.jj_fill_token();").new_line();

            if (data.hasSpecial()) {
                writer.append(prefix + "         matched_token.specialToken = specialToken;").new_line();
            }

            if (data.hasTokenActions()) {
                writer.append(prefix + "         self.token_lexical_actions(matched_token);").new_line();
            }

            if (data.maxLexStates() > 1) {
                writer.append("       if JJNEW_LEX_STATE[self.jjmatched_kind as usize] != -1 {").new_line();
                writer.append(prefix + "       self.cur_lex_state = JJNEW_LEX_STATE[self.jjmatched_kind as usize];")
                    .new_line();
                writer.append("       }").new_line();
            }

            writer.append(prefix + "         return matched_token;").new_line();

            if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
                writer.append(prefix + "      }").new_line();

                if (data.hasSkip() || data.hasSpecial()) {
                    if (data.hasMore()) {
                        writer.append(
                                prefix + "      else if (self.JJTO_SKIP[self.jjmatched_kind >> 6] & "
                                        + "(1 << (jjmatched_kind & 0o77))) != 0")
                            .new_line();
                    }
                    else {
                        writer.append(prefix + "      else").new_line();
                    }

                    writer.append(prefix + "      {").new_line();

                    if (data.hasSpecial()) {
                        writer.append(
                                prefix + "         if (self.jjtoSpecial[self.jjmatched_kind >> 6] & "
                                        + "(1 << (self.jjmatched_kind & 0o77))) != 0 {")
                            .new_line();

                        writer.append(prefix + "            matched_token = self.jj_fill_token();").new_line();
                        writer.append(prefix + "         }").new_line();

                        writer.append(prefix + "            if specialToken == null {").new_line();
                        writer.append(prefix + "               specialToken = matched_token;").new_line();
                        writer.append(prefix + "            } else {").new_line();
                        writer.append(prefix + "               matched_token.specialToken = specialToken;").new_line();
                        writer.append(prefix + "               specialToken = (specialToken.next = matched_token);").new_line();
                        writer.append(prefix + "            }").new_line();

                        if (data.hasSkipActions()) {
                            writer.append(prefix + "            self.skip_lexical_actions(matched_token);").new_line();
                        }

                        writer.append(prefix + "         }").new_line();

                        if (data.hasSkipActions()) {
                            writer.append(prefix + "         } else {").new_line();
                            writer.append(prefix + "            self.skip_lexical_actions(null);").new_line();
                            writer.append(prefix + "         }").new_line();
                        }
                    }
                    else if (data.hasSkipActions()) {
                        writer.append(prefix + "         self.skip_lexical_actions(null);").new_line();
                    }

                    if (data.maxLexStates() > 1) {
                        writer.append("         if JJNEW_LEX_STATE[self.jjmatched_kind as usize] != -1 {").new_line();
                        writer.append(prefix + "         self.cur_lex_state = JJNEW_LEX_STATE[self.jjmatched_kind as usize];").new_line();
                        writer.append("         }").new_line();
                    }

                    writer.append(prefix + "         continue 'EOFLoop;").new_line();
                    writer.append(prefix + "      }").new_line();
                }

                if (data.hasMore()) {
                    if (data.hasMoreActions()) {
                        writer.append(prefix + "      self.more_lexical_actions();").new_line();
                    }
                    else if (data.hasSkipActions() || data.hasTokenActions()) {
                        writer.append(prefix + "      self.jjimage_len += self.jjmatched_pos + 1;").new_line();
                    }

                    if (data.maxLexStates() > 1) {
                        writer.append("      if JJNEW_LEX_STATE[self.jjmatched_kind as usize] != -1 7").new_line();
                        writer.append(prefix + "      self.cur_lex_state = JJNEW_LEX_STATE[self.jjmatched_kind as usize];").new_line();
                        writer.append("      }").new_line();
                    }
                    writer.append(prefix + "      cur_pos = 0;").new_line();
                    writer.append(
                            prefix + "      self.jjmatched_kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";")
                        .new_line();

                    writer.append(prefix + "      try {").new_line();
                    writer.append(prefix + "         cur_char = input_stream.read_char();").new_line();

                    if (data.options().getDebugTokenManager()) {
                        writer.append("   debugStream.println("
                                + (data.maxLexStates() > 1 ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + " : "")
                                + "\"Current character : \" + "
                                + "TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \") "
                                + "at line \" + input_stream.get_end_line() + \" column \" + input_stream.get_end_column());")
                            .new_line();
                    }
                    writer.append(prefix + "         continue;").new_line();
                    writer.append(prefix + "      }").new_line();
                    writer.append(prefix + "      catch (java.io.IOException e1) { }").new_line();
                }
            }

            writer.append(prefix + "   }").new_line();
            writer.append("""
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
            // panic!(
            //     "Lexical error at line {}, column {} with error: {}",
            //     error_line, error_column, error_after
            // );
            return Token::empty();""").new_line();
        }

        if (data.hasMore()) {
            writer.append(prefix + " }").new_line();
        }
    }

    private void DumpSkipActions(SourceWriter writer, LexerData data) {
        Outer:
        for (int i = 0; i < data.maxOrdinal(); i++) {
            if ((data.toSkip(i / 64) & (1L << (i % 64))) == 0L) {
                continue;
            }

            for (; ; ) {
                Action act = data.actions(i);
                if (((act == null) || act.getActionTokens().isEmpty()) && !data.canLoop(data.getState(i))) {
                    continue Outer;
                }

                writer.append("      case " + i + " :").new_line();

                if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
                    writer.append("         if (jjmatched_pos == -1)").new_line();
                    writer.append("         {").new_line();
                    writer.append("            if (jjbeenHere[" + data.getState(i) + "] &&").new_line();
                    writer.append("                jjemptyLineNo[" + data.getState(i)
                            + "] == input_stream.get_begin_line() &&").new_line();
                    writer.append("                jjemptyColNo[" + data.getState(i)
                            + "] == input_stream.get_begin_column())").new_line();
                    writer.append("               throw new TokenException("
                            + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                            + "at line \" + input_stream.get_begin_line() + \", "
                            + "column \" + input_stream.get_begin_column() + \".\"), TokenException.LOOP_DETECTED);")
                        .new_line();
                    writer.append(
                            "            jjemptyLineNo[" + data.getState(i) + "] = input_stream.get_begin_line();")
                        .new_line();
                    writer.append("            jjemptyColNo[" + data.getState(i)
                            + "] = input_stream.get_begin_column();").new_line();
                    writer.append("            jjbeenHere[" + data.getState(i) + "] = true;").new_line();
                    writer.append("         }").new_line();
                }

                if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
                    break;
                }

                writer.append("         image.append");
                if (data.getImage(i) != null) {
                    writer.append("(JJSTR_LITERAL_IMAGES[" + i + "]);").new_line();
                    writer.append("        length_of_match = JJSTR_LITERAL_IMAGES[" + i + "].length();").new_line();
                }
                else {
                    writer.append(
                            "(input_stream.GetSuffix(jjimage_len + (length_of_match = jjmatched_pos + 1)));")
                        .new_line();
                }

                genTokenSetup(act.getActionTokens().getFirst());
                resetColumn();

                for (Token element : act.getActionTokens()) {
                    genToken(writer, element);
                }
                writer.new_line();

                break;
            }

            writer.append("         break;").new_line();
        }
    }

    private void DumpMoreActions(SourceWriter writer, LexerData data) {
        Outer:
        for (int i = 0; i < data.maxOrdinal(); i++) {
            if ((data.toMore(i / 64) & (1L << (i % 64))) == 0L) {
                continue;
            }

            for (; ; ) {
                Action act = data.actions(i);
                if (((act == null) || act.getActionTokens().isEmpty()) && !data.canLoop(data.getState(i))) {
                    continue Outer;
                }

                writer.append("      case " + i + " :").new_line();

                if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
                    writer.append("         if (jjmatched_pos == -1)").new_line();
                    writer.append("         {").new_line();
                    writer.append("            if (jjbeenHere[" + data.getState(i) + "] &&").new_line();
                    writer.append("                jjemptyLineNo[" + data.getState(i)
                            + "] == input_stream.get_begin_line() &&").new_line();
                    writer.append("                jjemptyColNo[" + data.getState(i)
                            + "] == input_stream.get_begin_column())").new_line();
                    writer.append("               throw new TokenException("
                            + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                            + "at line \" + input_stream.get_begin_line() + \", "
                            + "column \" + input_stream.get_begin_column() + \".\"), TokenException.LOOP_DETECTED);")
                        .new_line();
                    writer.append(
                            "            jjemptyLineNo[" + data.getState(i) + "] = input_stream.get_begin_line();")
                        .new_line();
                    writer.append("            jjemptyColNo[" + data.getState(i)
                            + "] = input_stream.get_begin_column();").new_line();
                    writer.append("            jjbeenHere[" + data.getState(i) + "] = true;").new_line();
                    writer.append("         }").new_line();
                }

                if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
                    break;
                }

                writer.append("         image.append");

                if (data.getImage(i) != null) {
                    writer.append("(JJSTR_LITERAL_IMAGES[" + i + "]);").new_line();
                }
                else {
                    writer.append("(input_stream.GetSuffix(jjimage_len));").new_line();
                }

                writer.append("         jjimage_len = 0;").new_line();
                genTokenSetup(act.getActionTokens().getFirst());
                resetColumn();

                for (Token element : act.getActionTokens()) {
                    genToken(writer, element);
                }
                writer.new_line();

                break;
            }

            writer.append("         break;").new_line();
        }
    }

    private void DumpTokenActions(SourceWriter writer, LexerData data) {
        Action act;
        int i;

        Outer:
        for (i = 0; i < data.maxOrdinal(); i++) {
            if ((data.toToken(i / 64) & (1L << (i % 64))) == 0L) {
                continue;
            }

            for (; ; ) {
                if ((((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) && !data.canLoop(
                        data.getState(i))) {
                    continue Outer;
                }

                writer.append("      case " + i + " :").new_line();

                if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
                    writer.append("         if (jjmatched_pos == -1)").new_line();
                    writer.append("         {").new_line();
                    writer.append("            if (jjbeenHere[" + data.getState(i) + "] &&").new_line();
                    writer.append("                jjemptyLineNo[" + data.getState(i)
                            + "] == input_stream.get_begin_line() &&").new_line();
                    writer.append("                jjemptyColNo[" + data.getState(i)
                            + "] == input_stream.get_begin_column())").new_line();
                    writer.append("               throw new TokenException("
                            + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                            + "at line \" + input_stream.get_begin_line() + \", "
                            + "column \" + input_stream.get_begin_column() + \".\"), TokenException.LOOP_DETECTED);")
                        .new_line();
                    writer.append(
                            "            jjemptyLineNo[" + data.getState(i) + "] = input_stream.get_begin_line();")
                        .new_line();
                    writer.append("            jjemptyColNo[" + data.getState(i)
                            + "] = input_stream.get_begin_column();").new_line();
                    writer.append("            jjbeenHere[" + data.getState(i) + "] = true;").new_line();
                    writer.append("         }").new_line();
                }

                if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
                    break;
                }

                if (i == 0) {
                    writer.append("      image.setLength(0);").new_line(); // For EOF no image is there
                }
                else {
                    writer.append("        image.append");

                    if (data.getImage(i) != null) {
                        writer.append("(JJSTR_LITERAL_IMAGES[" + i + "]);").new_line();
                        writer.append("        length_of_match = JJSTR_LITERAL_IMAGES[" + i + "].length();").new_line();
                    }
                    else {
                        writer.append("(input_stream.GetSuffix(jjimage_len + (length_of_match = jjmatched_pos + 1)));").new_line();
                    }
                }

                genTokenSetup(act.getActionTokens().getFirst());
                resetColumn();

                for (Token element : act.getActionTokens()) {
                    genToken(writer, element);
                }
                writer.new_line();

                break;
            }

            writer.append("         break;").new_line();
        }
    }

    private void DumpStateSets(SourceWriter writer, LexerData data) {
        int cnt = 0;
        int length = 0;
        for (int[] set : data.getOrderedStateSet()) {
            for (int element : set) {
                length++;
            }
        }

        writer.append("const JJNEXT_STATES : [usize; " + length + "] = [");
        if (!data.getOrderedStateSet().isEmpty()) {
            for (int[] set : data.getOrderedStateSet()) {
                for (int element : set) {
                    if ((cnt++ % 16) == 0) {
                        writer.append("\n   ");
                    }

                    writer.append(element + ", ");
                }
            }
        }
        else {
            writer.append("0");
        }

        writer.append("\n];").new_line();
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
                }
                else {
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
            list.add(null);;
        }
        return list;
    }

    private void DumpStartWithStates(SourceWriter writer, NfaStateData data) {
        writer.append("private int " + "jjStartNfaWithStates" + data.getLexerStateSuffix()
                + "(int pos, int kind, int state)").new_line();
        writer.append("{").new_line();
        writer.append("   jjmatched_kind = kind;").new_line();
        writer.append("   jjmatched_pos = pos;").new_line();

        if (data.global.options().getDebugTokenManager()) {
            writer.append("   debugStream.println(\"   No more string literal token matches are possible.\");")
                .new_line();
            writer.append("   debugStream.println(\"   Currently matched the first \" "
                    + "+ (jjmatched_pos + 1) + \" characters as a \" + tokenImage[jjmatched_kind] + \" token.\");")
                .new_line();
        }

        writer.append("   try { cur_char = input_stream.read_char(); }").new_line();
        writer.append("   catch(java.io.IOException e) { return pos + 1; }").new_line();

        if (data.global.options().getDebugTokenManager()) {
            writer.append("   debugStream.println("
                    + (data.global.maxLexStates() > 1 ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + " : "")
                    + "\"Current character : \" + TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \") "
                    + "at line \" + input_stream.get_end_line() + \" column \" + input_stream.get_end_column());")
                .new_line();
        }
        writer.append("   return jj_move_nfa" + data.getLexerStateSuffix() + "(state, pos + 1);").new_line();
        writer.append("}").new_line();
    }

    @Override
    protected final void DumpHeadForCase(SourceWriter writer, int byteNum) {
        if (byteNum == 0)
            writer.append("         let l: u64 = 1u64 << self.cur_char;").new_line();
        else if (byteNum == 1)
            writer.append("         let l: u64 = 1u64 << (self.cur_char & 0o77);").new_line();
        else {
            writer.append("         let hi_byte: u32 = self.cur_char >> 8;").new_line();
            writer.append("         let l1: u64 = 1u64 << (hi_byte & 0o77);").new_line();
            writer.append("         let l2: u64 = 1u64 << (self.cur_char & 0o77);").new_line();
            writer.append("         let i1: usize = (hi_byte >> 6) as usize;").new_line();
            writer.append("         let i2: usize = ((self.cur_char & 0xff) >> 6) as usize;").new_line();
        }

        // writer.append(" MatchLoop: do").new_line();
        writer.append("         let mut while_cond = true;").new_line();
        writer.append("         while while_cond {").new_line();
        writer.append("            i -= 1;").new_line();
        writer.append("            match self.jjstate_set[i] {").new_line();
    }

    private void DumpNonAsciiMoveMethod(NfaState state, LexerData data, SourceWriter writer) {
        for (int j = 0; j < state.loByteVec.size(); j += 2) {
            writer.append(state.loByteVec.get(j) + " => {").new_line();
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.loByteVec.get(j + 1))))
                writer.append("    return (JJBIT_VEC" + state.loByteVec.get(j + 1) + "[i2] & l2) != 0;").new_line();
            else
                writer.append("    return true;").new_line();
            writer.append("}").new_line();
        }

        writer.append("_ => {").new_line();
        for (int j = state.nonAsciiMoveIndices.length; j > 0; j -= 2) {
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.nonAsciiMoveIndices[j - 2])))
                writer.append("    if (JJBIT_VEC" + state.nonAsciiMoveIndices[j - 2] + "[i1] & l1) != 0 {").new_line();
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.nonAsciiMoveIndices[j - 1]))) {
                writer.append("        if (JJBIT_VEC" + state.nonAsciiMoveIndices[j - 1] + "[i2] & l2) == 0 {").new_line();
                writer.append("            return false;").new_line();
                writer.append("        } else {").new_line();
            }
            writer.append("        return true;").new_line();
            writer.append("    }").new_line();
        }
        writer.append("    false").new_line();
        writer.append("}").new_line();
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
            }
            else {
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
    protected final void dumpNfaStartStatesCode(SourceWriter writer, NfaStateData data,
                                                Hashtable<String, long[]>[] statesForPos) {
        if (data.getMaxStrKind() == 0) // No need to generate this function
            return;

        int i, maxKindsReqd = (data.getMaxStrKind() / 64) + 1;
        boolean condGenerated = false;
        int ind;

        StringBuilder params = new StringBuilder();
        for (i = 0; i < (maxKindsReqd - 1); i++) {
            params.append("active").append(i).append(": u64, ");
        }
        params.append("active").append(i).append(": u64)");

        writer.append("fn jjStopStringLiteralDfa" + data.getLexerStateSuffix() + "(&self, pos: usize, " + params+ " -> usize {").new_line();

        if (data.global.options().getDebugTokenManager())
            writer.append("      debugStream.println(\"   No more string literal token matches are possible.\");").new_line();

        writer.append("   switch (pos)").new_line();
        writer.append("   {").new_line();

        for (i = 0; i < (data.getMaxLen() - 1); i++) {
            if (statesForPos[i] == null) {
                continue;
            }

            writer.append("      case " + i + ":").new_line();

            Enumeration<String> e = statesForPos[i].keys();
            while (e.hasMoreElements()) {
                String stateSetString = e.nextElement();
                long[] actives = statesForPos[i].get(stateSetString);

                for (int j = 0; j < maxKindsReqd; j++) {
                    if (actives[j] == 0L) {
                        continue;
                    }

                    if (condGenerated) {
                        writer.append(" || ");
                    }
                    else {
                        writer.append("        if (");
                    }

                    condGenerated = true;

                    writer.append("(active" + j + " & " + toHexString(actives[j]) + ") != 0L");
                }

                if (condGenerated) {
                    writer.append(")").new_line();

                    String kindStr = stateSetString.substring(0, ind = stateSetString.indexOf(", "));
                    String afterKind = stateSetString.substring(ind + 2);
                    int jjmatchedPos = Integer.parseInt(afterKind.substring(0, afterKind.indexOf(", ")));

                    if (!kindStr.equals(String.valueOf(Integer.MAX_VALUE))) {
                        writer.append("         {").new_line();
                    }

                    if (!kindStr.equals(String.valueOf(Integer.MAX_VALUE))) {
                        if (i == 0) {
                            writer.append("            jjmatched_kind = " + kindStr + ";").new_line();

                            if (((data.global.initMatch(data.getStateIndex()) != 0)
                                    && (data.global.initMatch(data.getStateIndex()) != Integer.MAX_VALUE))) {
                                writer.append("            jjmatched_pos = 0;").new_line();
                            }
                        }
                        else if (i == jjmatchedPos) {
                            if (data.isSubStringAtPos(i)) {
                                writer.append("            if (jjmatched_pos != " + i + ")").new_line();
                                writer.append("            {").new_line();
                                writer.append("               jjmatched_kind = " + kindStr + ";").new_line();
                                writer.append("               jjmatched_pos = " + i + ";").new_line();
                                writer.append("            }").new_line();
                            }
                            else {
                                writer.append("            jjmatched_kind = " + kindStr + ";").new_line();
                                writer.append("            jjmatched_pos = " + i + ";").new_line();
                            }
                        }
                        else {
                            if (jjmatchedPos > 0) {
                                writer.append("            if (jjmatched_pos < " + jjmatchedPos + ")").new_line();
                            }
                            else {
                                writer.append("            if (jjmatched_pos == 0)").new_line();
                            }
                            writer.append("            {").new_line();
                            writer.append("               jjmatched_kind = " + kindStr + ";").new_line();
                            writer.append("               jjmatched_pos = " + jjmatchedPos + ";").new_line();
                            writer.append("            }").new_line();
                        }
                    }

                    kindStr = stateSetString.substring(0, ind = stateSetString.indexOf(", "));
                    afterKind = stateSetString.substring(ind + 2);
                    stateSetString = afterKind.substring(afterKind.indexOf(", ") + 2);

                    if (stateSetString.equals("null;")) {
                        writer.append("            return -1;").new_line();
                    }
                    else {
                        writer.append("            return " + getCompositeStateSet(data, stateSetString) + ";").new_line();
                    }

                    if (!kindStr.equals(String.valueOf(Integer.MAX_VALUE))) {
                        writer.append("         }").new_line();
                    }
                    condGenerated = false;
                }
            }

            writer.append("         return -1;").new_line();
        }

        writer.append("      default :").new_line();
        writer.append("         return -1;").new_line();
        writer.append("   }").new_line();
        writer.append("}").new_line();

        params.setLength(0);
        params.append("(int pos, ");
        for (i = 0; i < (maxKindsReqd - 1); i++) {
            params.append("long active").append(i).append(", ");
        }
        params.append("long active").append(i).append(")");

        writer.append("private final int jjStartNfa" + data.getLexerStateSuffix() + params);
        writer.append("{").new_line();

        if (data.isMixedState()) {
            if (data.generatedStates() != 0) {
                writer.append(
                        "   return jj_move_nfa" + data.getLexerStateSuffix() + "(" + InitStateName(data)
                                + ", pos + 1);").new_line();
            }
            else {
                writer.append("   return pos + 1;").new_line();
            }

            writer.append("}").new_line();
            return;
        }

        writer.append("   return jj_move_nfa" + data.getLexerStateSuffix() + "(" + "jjStopStringLiteralDfa"
                + data.getLexerStateSuffix() + "(pos, ");
        for (i = 0; i < (maxKindsReqd - 1); i++) {
            writer.append("active" + i + ", ");
        }
        writer.append("active" + i + ")");
        writer.append(", pos + 1);").new_line();
        writer.append("}").new_line();
    }

    @Override
    protected final void dumpDfaCode(SourceWriter writer, NfaStateData data) {
        Hashtable<String, ?> tab;
        String key;
        KindInfo info;
        int maxLongsReqd = (data.getMaxStrKind() / 64) + 1;
        int i, j, k;
        boolean ifGenerated;

        if (data.getMaxLen() == 0) {
            writer.append("fn jj_move_string_literal_dfa0" + data.getLexerStateSuffix() + "(&self) -> usize ").new_line();
            DumpNullStrLiterals(writer, data);
            return;
        }

        if (!data.global.boilerPlateDumped) {
            writer.append("fn jj_stop_at_pos(&mut self, pos: usize, kind: u32) -> usize {").new_line();
            writer.append("    self.jjmatched_kind = kind;").new_line();
            writer.append("    self.jjmatched_pos = pos;").new_line();

            if (data.global.options().getDebugTokenManager()) {
                writer.append("    debugStream.println(\"   No more string literal token matches are possible.\");").new_line();
                writer.append("    debugStream.println(\"   Currently matched the first \" + (jjmatched_pos + 1) + "
                                + "\" characters as a \" + tokenImage[jjmatched_kind] + \" token.\");").new_line();
            }

            writer.append("    pos + 1").new_line();
            writer.append("}").new_line();
            data.global.boilerPlateDumped = true;
        }

        for (i = 0; i < data.getMaxLen(); i++) {
            boolean startNfaNeeded = false;
            tab = data.getCharPosKind(i);
            String[] keys = LexerGenerator.ReArrange(tab);

            StringBuilder params = new StringBuilder();
            params.append("(&mut self");
            if (i != 0) {
                if (i == 1) {
                    for (j = 0; j < (maxLongsReqd - 1); j++) {
                        if (i <= data.getMaxLenForActive(j)) {
                            params.append(", active").append(j).append(": u64");
                        }
                    }

                    if (i <= data.getMaxLenForActive(j)) {
                        params.append(", active").append(j).append(": u64");
                    }
                }
                else {
                    for (j = 0; j < (maxLongsReqd - 1); j++) {
                        if (i <= (data.getMaxLenForActive(j) + 1)) {
                            params.append(", old").append(j).append(": u64, ")
                                .append("active_old").append(j).append(": u64");
                        }
                    }

                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        params.append(", old").append(j).append(": u64, ")
                            .append("active_old").append(j).append(": u64");
                    }
                }
            }
            params.append(")");

            writer.append("fn jj_move_string_literal_dfa" + i + data.getLexerStateSuffix() + params + " -> usize {").new_line();
            if (i != 0) {
                if (i > 1) {
                    var atLeastOne = false;
                    for (j = 0; j < (maxLongsReqd - 1); j++) {
                        if (i <= (data.getMaxLenForActive(j) + 1)) {
                            if (atLeastOne)
                                writer.append(" | ");
                            else
                                atLeastOne = true;
                            writer.append("    let active" + j + " = active_old" + j + " & old" + j + ";").new_line();
                        }
                    }

                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        writer.append("    let active" + j + " = active_old" + j + " & old" + j + ";").new_line();
                    }

                    atLeastOne = false;
                    writer.append("   if (");

                    for (j = 0; j < (maxLongsReqd - 1); j++) {
                        if (i <= (data.getMaxLenForActive(j) + 1)) {
                            if (atLeastOne)
                                writer.append(" | ");
                            else
                                atLeastOne = true;
                            writer.append("active" + j);
                        }
                    }

                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        if (atLeastOne)
                            writer.append(" | ");
                        writer.append("active" + j);
                    }

                    writer.append(") == 0 {").new_line();
                    if (!data.isMixedState() && (data.generatedStates() != 0)) {
                        writer.append("        return self.jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 2) + ", ");
                        for (j = 0; j < (maxLongsReqd - 1); j++) {
                            if (i <= (data.getMaxLenForActive(j) + 1))
                                writer.append("old" + j + ", ");
                            else
                                writer.append("0, ");
                        }
                        if (i <= (data.getMaxLenForActive(j) + 1))
                            writer.append("old" + j + ");").new_line();
                        else
                            writer.append("0);").new_line();
                    }
                    else if (data.generatedStates() != 0)
                        writer.append("        return self.jj_move_nfa" + data.getLexerStateSuffix() +
                            "(" + InitStateName(data) + ", " + (i - 1) + ");").new_line();
                    else
                        writer.append("        return " + i + ";").new_line();
                    writer.append("   }");
                }

                if (data.global.options().getDebugTokenManager()) {
                    writer.append("   if self.jjmatched_kind != 0 && vjjmatchedKind != 0x" + Integer.toHexString(
                                    Integer.MAX_VALUE) + " {").new_line();
                    writer.append("      debugStream.println(\"   Currently matched the first \" + "
                            + "(self.jjmatched_pos + 1) + \" characters as a \" + self.tokenImage[self.jjmatched_kind as usize] + \" token.\");")
                        .new_line();
                    writer.append("   debugStream.println(\"   Possible string literal matches : { \"").new_line();

                    for (int vecs = 0; vecs < ((data.getMaxStrKind() / 64) + 1); vecs++) {
                        if (i <= data.getMaxLenForActive(vecs)) {
                            writer.append(" +").new_line();
                            writer.append("         self.jjKindsForBitVector(" + vecs + ", ");
                            writer.append("active" + vecs + ") ");
                        }
                    }

                    writer.append(" + \" } \");").new_line();
                    writer.append("   }").new_line();
                }
                writer.new_line();

                writer.append("   let result = self.input_stream.read_char();").new_line();
                writer.append("   if result.is_err() {").new_line();

                if (!data.isMixedState() && (data.generatedStates() != 0)) {
                    writer.append("      self.jjStopStringLiteralDfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ");
                    for (k = 0; k < (maxLongsReqd - 1); k++) {
                        if (i <= data.getMaxLenForActive(k)) {
                            writer.append("active" + k + ", ");
                        }
                        else {
                            writer.append("0, ");
                        }
                    }

                    if (i <= data.getMaxLenForActive(k))
                        writer.append("active" + k + ");").new_line();
                    else
                        writer.append("0);").new_line();

                    if (data.global.options().getDebugTokenManager()) {
                        writer.append("      if self.jjmatched_kind != 0 && self.jjmatched_kind != 0x" +
                            Integer.toHexString(Integer.MAX_VALUE))
                            .new_line();
                        writer.append("         debugStream.println(\"   Currently matched the first \" + "
                                + "(jjmatched_pos + 1) + \" characters as a \" + tokenImage[jjmatched_kind] + \" token.\");")
                            .new_line();
                    }
                    writer.append("      return " + i + ";").new_line();
                }
                else if (data.generatedStates() != 0)
                    writer.append("     return self.jj_move_nfa" + data.getLexerStateSuffix() +
                                "(" + InitStateName(data) + ", " + (i - 1) + ");").new_line();
                else
                    writer.append("      return " + i + ";").new_line();

                writer.append("   }").new_line();
                writer.append("   self.cur_char = u32::from(result.unwrap());").new_line();
                writer.new_line();
            }

            if ((i != 0) && data.global.options().getDebugTokenManager()) {
                writer.append("   debugStream.println("
                        + (data.global.maxLexStates() > 1 ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + "
                        : "")
                        + "\"Current character : \" + TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \") "
                        + "at line \" + input_stream.get_end_line() + \" column \" + input_stream.get_end_column());")
                    .new_line();
            }

            writer.append("    match self.cur_char {").new_line();

            CaseLoop:
            for (String key2 : keys) {
                key = key2;
                info = (KindInfo) tab.get(key);
                ifGenerated = false;
                char c = key.charAt(0);

                if ((i == 0) && (c < 128) && info.hasFinalKindCnt()
                        && ((data.generatedStates() == 0) || !CanStartNfaUsingAscii(data, c))) {
                    for (j = 0; j < maxLongsReqd; j++) {
                        if (info.finalKinds[j] != 0L) {
                            break;
                        }
                    }

                    int kind;
                    for (k = 0; k < 64; k++) {
                        if (((info.finalKinds[j] & (1L << k)) != 0L) && !data.isSubString(
                                kind = ((j * 64) + k))) {
                            if (((data.getIntermediateKinds() != null) && (
                                    data.getIntermediateKinds()[((j * 64) + k)] != null)
                                    && (data.getIntermediateKinds()[((j * 64) + k)][i] < ((j * 64) + k))
                                    && (data.getIntermediateMatchedPos() != null)
                                    && (data.getIntermediateMatchedPos()[((j * 64) + k)][i] == i))
                                    || ((data.global.canMatchAnyChar(data.getStateIndex()) >= 0)
                                    && (data.global.canMatchAnyChar(data.getStateIndex()) < ((j * 64) + k)))) {
                                break;
                            }
                            else if (((data.global.toSkip(kind / 64) & (1L << (kind % 64))) != 0L)
                                    && ((data.global.toSpecial(kind / 64) & (1L << (kind % 64))) == 0L)
                                    && (data.global.actions(kind) == null) && (data.global.newLexState(kind)
                                    == null)) {
                                continue CaseLoop;
                            }
                        }
                    }
                }

                // Since we know key is a single character ...
                if (data.ignoreCase()) {
                    if (c != Character.toUpperCase(c)) {
                        writer.append("        " + (int) Character.toUpperCase(c) + " => {").new_line();
                    }

                    if (c != Character.toLowerCase(c)) {
                        writer.append("      "   + (int) Character.toLowerCase(c) + " => {").new_line();
                    }
                }

                writer.append("        " + (int) c + " => {").new_line();

                long matchedKind;
                String prefix = (i == 0) ? "        " : "            ";

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
                                writer.append("        else if ");
                            else if (i != 0)
                                writer.append("        if ");

                            ifGenerated = true;

                            int kindToPrint;
                            if (i != 0) {
                                writer.append("(active" + j + " & 0x" + Long.toHexString(1L << k) + ") != 0").new_line();
                            }

                            if ((data.getIntermediateKinds() != null) && (
                                    data.getIntermediateKinds()[((j * 64) + k)] != null)
                                    && (data.getIntermediateKinds()[((j * 64) + k)][i] < ((j * 64) + k))
                                    && (data.getIntermediateMatchedPos() != null)
                                    && (data.getIntermediateMatchedPos()[((j * 64) + k)][i] == i)) {
                                JavaCCErrors.warning(" \"" + Encoding.escape(data.global.getImage((j * 64) + k))
                                        + "\" cannot be matched as a string literal token " + "at line "
                                        + GetLine(data.global, (j * 64) + k) + ", column " + GetColumn(data.global,
                                        (j * 64) + k)
                                        + ". It will be matched as " + GetLabel(data.global,
                                        data.getIntermediateKinds()[((j * 64) + k)][i])
                                        + ".");
                                kindToPrint = data.getIntermediateKinds()[((j * 64) + k)][i];
                            }
                            else if ((i == 0) && (data.global.canMatchAnyChar(data.getStateIndex()) >= 0)
                                    && (data.global.canMatchAnyChar(data.getStateIndex()) < ((j * 64) + k))) {
                                JavaCCErrors.warning(" \"" + Encoding.escape(data.global.getImage((j * 64) + k))
                                        + "\" cannot be matched as a string literal token " + "at line "
                                        + GetLine(data.global, (j * 64) + k) + ", column " + GetColumn(data.global,
                                        (j * 64) + k)
                                        + ". It will be matched as "
                                        + GetLabel(data.global, data.global.canMatchAnyChar(data.getStateIndex()))
                                        + ".");
                                kindToPrint = data.global.canMatchAnyChar(data.getStateIndex());
                            }
                            else {
                                kindToPrint = (j * 64) + k;
                            }

                            if (!data.isSubString(((j * 64) + k))) {
                                int stateSetName = GetStateSetForKind(data, i, (j * 64) + k);

                                if (stateSetName != -1) {
                                    writer.append(
                                            prefix + "return self.jjStartNfaWithStates" + data.getLexerStateSuffix() + "(" + i
                                                    + ", " + kindToPrint + ", " + stateSetName + ");")
                                        .new_line();
                                }
                                else {
                                    writer.append(prefix + "return self.jj_stop_at_pos" + "(" + i + ", " + kindToPrint + ");").new_line();
                                }
                                writer.append("        }").new_line();
                            }
                            else if (((data.global.initMatch(data.getStateIndex()) != 0)
                                    && (data.global.initMatch(data.getStateIndex()) != Integer.MAX_VALUE)) || (i
                                    != 0)) {
                                writer.append("           {").new_line();
                                writer.append(prefix + "self.jjmatched_kind = " + kindToPrint + ";").new_line();
                                writer.append(prefix + "self.jjmatched_pos = " + i + ";").new_line();
                                writer.append("           }").new_line();
                            }
                            else {
                                writer.append(prefix + "self.jjmatched_kind = " + kindToPrint + ";").new_line();
                            }
                        }
                    }
                }

                if (info.hasValidKindCnt()) {
                    var atLeastOne = false;

                    if (i == 0) {
                        writer.append("          return self.jj_move_string_literal_dfa" + (i + 1) + data.getLexerStateSuffix() + "(");
                        for (j = 0; j < (maxLongsReqd - 1); j++) {
                            if ((i + 1) <= data.getMaxLenForActive(j)) {
                                if (atLeastOne)
                                    writer.append(", ");
                                else
                                    atLeastOne = true;

                                writer.append("0x" + Long.toHexString(info.validKinds[j]));
                            }
                        }

                        if ((i + 1) <= data.getMaxLenForActive(j)) {
                            if (atLeastOne)
                                writer.append(", ");

                            writer.append("0x" + Long.toHexString(info.validKinds[j]));
                        }
                        writer.append(");").new_line();
                        writer.append("      }").new_line();
                    }
                    else {
                        writer.append("         return self.jj_move_string_literal_dfa" + (i + 1) + data.getLexerStateSuffix() + "(");

                        for (j = 0; j < (maxLongsReqd - 1); j++) {
                            if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
                                if (atLeastOne)
                                    writer.append(", ");
                                else
                                    atLeastOne = true;

                                if (info.validKinds[j] != 0L)
                                    writer.append("active" + j + ", 0x" + Long.toHexString(info.validKinds[j]));
                                else
                                    writer.append("active" + j + ", 0");
                            }
                        }

                        if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
                            if (atLeastOne)
                                writer.append(", ");

                            if (info.validKinds[j] != 0L)
                                writer.append("active" + j + ", 0x" + Long.toHexString(info.validKinds[j]));
                            else
                                writer.append("active" + j + ", 0");
                        }

                        writer.append(");").new_line();
                        writer.append("      }").new_line();
                    }
                }
                else {// A very special case.
                    if ((i == 0) && data.isMixedState()) {

                        if (data.generatedStates() != 0) {
                            writer.append("         return self.jj_move_nfa" + data.getLexerStateSuffix() + "("
                                    + InitStateName(data) + ", 0);").new_line();
                        }
                        else {
                            writer.append("         return 1;").new_line();
                        }
                    }
                    else if (i != 0) // No more str literals to look for
                    {
                        startNfaNeeded = true;
                    }
                    writer.append("      }").new_line();
                }
            }

            writer.append("      _ => {").new_line();

            if (data.global.options().getDebugTokenManager()) {
                writer.append("      debugStream.println(\"   No string literal matches possible.\");").new_line();
            }

            if (data.generatedStates() != 0) {
                if (i == 0) {
                    // This means no string literal is possible. Just move nfa with this guy and return.
                    writer.append("         return self.jj_move_nfa" + data.getLexerStateSuffix() + "(" + InitStateName(
                                            data) + ", 0);").new_line();
                }
                else
                    startNfaNeeded = true;
            }
            else {
                writer.append("            return " + (i + 1) + ";").new_line();
            }
            writer.append("        }").new_line();
            writer.append("    }").new_line();

            if ((i != 0) && startNfaNeeded) {
                if (!data.isMixedState() && (data.generatedStates() != 0)) {
                    /*
                     * Here, a string literal is successfully matched and no more string literals are
                     * possible. So set the kind and state set upto and including this position for the
                     * matched string.
                     */

                    writer.append("   self.jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ");
                    for (k = 0; k < (maxLongsReqd - 1); k++) {
                        if (i <= data.getMaxLenForActive(k))
                            writer.append("active" + k + ", ");
                        else
                            writer.append("0, ");
                    }
                    if (i <= data.getMaxLenForActive(k))
                        writer.append("active" + k + ")").new_line();
                    else
                        writer.append("0)").new_line();
                }
                else if (data.generatedStates() != 0)
                    writer.append("    self.jj_move_nfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", "  + i + ")").new_line();
                else
                    writer.append("    return " + (i + 1)).new_line();
            }

            writer.append("}").new_line();
            writer.new_line();
        }

        if (!data.isMixedState() && (data.generatedStates() != 0) && data.getCreateStartNfa()) {
            DumpStartWithStates(writer, data);
        }
    }

    @Override
    protected final void dumpMoveNfa(SourceWriter writer, NfaStateData data) {
        writer.append("fn jj_move_nfa" + data.getLexerStateSuffix() + "(&mut self, start_state: usize, cur_pos: usize) -> usize {").new_line();
        if (data.generatedStates() == 0) {
            writer.append("   return cur_pos;").new_line();
            writer.append("}").new_line();
            return;
        }

        if (data.isMixedState()) {
            writer.append("""
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
            writer.new_line();
        }

        writer.append("   let mut starts_at: usize = 0;").new_line();
        writer.append("   self.jjnew_state_cnt = " + data.generatedStates() + ";").new_line();
        writer.append("   let mut i: usize = 1;").new_line();
        writer.append("   self.jjstate_set[0] = start_state;").new_line();

        if (data.global.options().getDebugTokenManager()) {
            writer.append("      debugStream.println(\"   Starting NFA to match one of : \" + "
                    + "jjKindsForStateVector(self.cur_lex_state, jjstate_set, 0, 1));").new_line();
            writer.append("      debugStream.println("
                    + (data.global.maxLexStates() > 1 ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + " : "")
                    + "\"Current character : \" + TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \") "
                    + "at line \" + input_stream.get_end_line() + \" column \" + input_stream.get_end_column());")
                .new_line();
        }

        writer.append("   let mut kind: u32 = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";").new_line();
        writer.append("   loop {").new_line();
        writer.append("      self.jjround += 1;").new_line();
        writer.append("      if self.jjround == 0x" + Integer.toHexString(Integer.MAX_VALUE) + " {").new_line();
        writer.append("         self.re_init_rounds();").new_line();
        writer.append("      }").new_line();

        writer.append("      if self.cur_char < 64 {").new_line();
        DumpAsciiMoves(writer, data, 0);

        writer.append("      }").new_line();

        writer.append("      else if self.cur_char < 128 {").new_line();
        DumpAsciiMoves(writer, data, 1);
        writer.append("      }").new_line();

        writer.append("      else {").new_line();
        DumpCharAndRangeMoves(writer, data);
        writer.append("      }").new_line();
        writer.append("      if kind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + " {").new_line();
        writer.append("         self.jjmatched_kind = kind;").new_line();
        writer.append("         self.jjmatched_pos = cur_pos;").new_line();
        writer.append("         kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";").new_line();
        writer.append("      }").new_line();
        writer.append("      cur_pos += 1;").new_line();

        if (data.global.options().getDebugTokenManager()) {
            writer.append("      if self.jjmatched_kind != 0 && self.jjmatched_kind != 0x"
                + Integer.toHexString(Integer.MAX_VALUE) + " {")
                .new_line();
            writer.append("         debugStream.println("
                    + "\"   Currently matched the first \" + (jjmatched_pos + 1) + \" characters as"
                    + " a \" + tokenImage[jjmatched_kind] + \" token.\");")
                .new_line();
            writer.append("      }").new_line();
        }

        writer.append("      i = self.jjnew_state_cnt;").new_line();
        writer.append("      self.jjnew_state_cnt = starts_at;").new_line();
        writer.append("      starts_at = " + data.generatedStates() + " - self.jjnew_state_cnt;").new_line();
        writer.append("      if i == starts_at {").new_line();
        if (data.isMixedState())
            writer.append("         break;").new_line();
        else
            writer.append("         return cur_pos;").new_line();
        writer.append("      }").new_line();

        if (data.global.options().getDebugTokenManager()) {
            writer.append("      debugStream.println(\"   Possible kinds of longer matches : \" + "
                    + "jjKindsForStateVector(self.cur_lex_state, jjstate_set, starts_at, i));")
                .new_line();
        }

        writer.append("      let result = self.input_stream.read_char();").new_line();
        writer.append("      if result.is_err() {").new_line();
        if (data.isMixedState())
            writer.append("         break;").new_line();
        else
            writer.append("         return cur_pos;").new_line();
        writer.append("      }").new_line();
        writer.append("      self.cur_char = u32::from(result.unwrap());").new_line();

        if (data.global.options().getDebugTokenManager()) {
            writer.append("      debugStream.println("
                    + (data.global.maxLexStates() > 1 ? "\"<\" + LEX_STATE_NAMES[self.cur_lex_state] + \">\" + " : "")
                    + "\"Current character : \" + TokenException.addEscapes(String.valueOf(cur_char)) + \" (\" + (int)cur_char + \") "
                    + "at line \" + input_stream.get_end_line() + \" column \" + input_stream.get_end_column());")
                .new_line();
        }
        writer.append("   }").new_line();

        if (data.isMixedState()) {
            writer.append("""
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
   }
   else if self.jjmatched_pos == str_pos && self.jjmatched_kind > str_kind {
      self.jjmatched_kind = str_kind;
   }

   to_ret
""").new_line();
        }
        writer.append("}").new_line();
    }
}
