// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;

import org.hivevm.cc.Encoding;
import org.hivevm.cc.Language;
import org.hivevm.cc.generator.LexerData;
import org.hivevm.cc.generator.LexerGenerator;
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
class JavaLexerGenerator extends LexerGenerator {

    @Override
    public final void generate(LexerData data) {
        if (JavaCCErrors.hasError()) {
            return;
        }

        var options = Template.newContext(data.options());
        options.add(LexerGenerator.LOHI_BYTES, data.getLohiByte())
            .set("LOHI_BYTES_INDEX", i -> i)
            .set("LOHI_BYTES_VALUE", i -> getLohiBytes(data, i));
        options.add(LexerGenerator.STATES, data.getStateNames())
            .set("STATES_NFA_AND_DFA", (n, w) -> dumpNfaAndDfa(data.getStateData(n), w));
        options.add(LexerGenerator.NON_ASCII_TABLE, data.getNonAsciiTableForMethod())
            .set("NON_ASCII_TABLE_METHOD", s -> s.nonAsciiMethod)
            .set("NON_ASCII_TABLE_MOVE", (s, w) -> DumpNonAsciiMoveMethod(s, data, w));

        options.set("LITERAL_IMAGES", () -> JavaLexerGenerator.getStrLiteralImages(data));
        options.set("STATES_FOR_STATE", () -> getStatesForState(data));
        options.set("KIND_FOR_STATE", () -> getKindForState(data));

        options.set("DUMP_SKIP_ACTIONS", p -> DumpSkipActions(p, data));
        options.set("DUMP_MORE_ACTIONS", p -> DumpMoreActions(p, data));
        options.set("DUMP_TOKEN_ACTIONS", p -> DumpTokenActions(p, data));
        options.set("DUMP_STATE_SETS", p -> DumpStateSets(p, data));
        options.set("DUMP_GET_NEXT_TOKEN", p -> DumpGetNextToken(p, data));
        options.set("DUMP_STATIC_VAR_DECLARATIONS", p -> DumpStaticVarDeclarations(p, data));

        options.set(LexerGenerator.DEFAULT_LEX_STATE, data.defaultLexState());
        options.set(LexerGenerator.MAX_LEX_STATES, data.maxLexStates());
        options.add(LexerGenerator.STATE_NAMES, data.getStateNames())
            .set("STATE_NAMES_VALUE", i -> i);
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
        options.set(LexerGenerator.DUAL_NEED, data.jjCheckNAddStatesDualNeeded());
        options.set(LexerGenerator.UNARY_NEED, data.jjCheckNAddStatesUnaryNeeded());
        options.set(LexerGenerator.STATE_COUNT, data.getStateCount());

        JavaSources.LEXER.render(options);

        generateConstants(data);
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
            .set("STATES_INDEX", i -> i)
            .set("STATES_NAME", data::getStateName);
        options.add("TOKENS", data.getOrderedsTokens())
            .set("TOKENS_ORDINAL", RExpression::getOrdinal)
            .set("TOKENS_LABEL", RExpression::getLabel);
        options.add("PRODUCTIONS", expressions)
            .set("PRODUCTIONS_LABEL", re -> {
            StringBuilder buffer = new StringBuilder();

            if (re instanceof RStringLiteral) {
                buffer.append("\"\\\"")
                        .append(Encoding.escape(Encoding.escape(((RStringLiteral) re).getImage())))
                        .append("\\\"\"");
            }
            else if (!re.getLabel().equals("")) {
                buffer.append("\"<").append(re.getLabel()).append(">\"");
            }
            else if (re.getTpContext().getKind() == TokenProduction.Kind.TOKEN) {
                JavaCCErrors.warning(re, "Consider giving this non-string token a label for better error reporting.");
            }
            else {
                buffer.append("\"<token of kind ").append(re.getOrdinal()).append(">\"");
            }

            if (expressions.indexOf(re) < (expressions.size() - 1)) {
                buffer.append(",");
            }
            return buffer.toString();
        });

        JavaSources.PARSER_CONSTANTS.render(options, data.getParserName());
    }

    @Override
    protected final Language getLanguage() {
        return Language.JAVA;
    }

    private void DumpStaticVarDeclarations(SourceWriter writer, LexerData data) {
        if (data.maxLexStates() > 1) {
            writer.new_line();
            writer.append("/** Lex State array. */").new_line();
            writer.append("public static final int[] jjnewLexState = {");

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
            writer.append("\n};").new_line();
        }

        if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
            // Bit vector for TOKEN
            writer.append("static final long[] jjtoToken = {");
            for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
                if ((i % 4) == 0) {
                    writer.append("\n   ");
                }
                writer.append(toHexString(data.toToken(i)) + ", ");
            }
            writer.append("\n};").new_line();
        }

        if (data.hasSkip() || data.hasSpecial()) {
            // Bit vector for SKIP
            writer.append("static final long[] jjtoSkip = {");
            for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
                if ((i % 4) == 0) {
                    writer.append("\n   ");
                }
                writer.append(toHexString(data.toSkip(i)) + ", ");
            }
            writer.append("\n};").new_line();
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
            writer.append("      TokenLexicalActions(matchedToken);").new_line();
        }

        writer.append("      return matchedToken;").new_line();
        writer.append("   }").new_line();

        if (data.hasMoreActions() || data.hasSkipActions() || data.hasTokenActions()) {
            writer.append("   image = jjimage;").new_line();
            writer.append("   image.setLength(0);").new_line();
            writer.append("   jjimageLen = 0;").new_line();
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
            writer.append(prefix + "   switch(curLexState)").new_line();
            writer.append(prefix + "   {").new_line();
            endSwitch = prefix + "   }";
            caseStr = prefix + "     case ";
            prefix += "    ";
        }

        prefix += "   ";
        for (int i = 0; i < data.maxLexStates(); i++) {
            if (data.maxLexStates() > 1) {
                writer.append(caseStr + i + ":").new_line();
            }

            if (data.singlesToSkip(i).HasTransitions()) {
                // added the backup(0) to make JIT happy
                writer.append(prefix + "try { input_stream.backup(0);").new_line();
                if ((data.singlesToSkip(i).asciiMoves[0] != 0L) && (data.singlesToSkip(i).asciiMoves[1]
                        != 0L)) {
                    writer.append(
                            prefix + "   while ((curChar < 64" + " && (0x" + Long.toHexString(
                                    data.singlesToSkip(i).asciiMoves[0])
                                    + "L & (1L << curChar)) != 0L) || \n" + prefix + "          (curChar >> 6) == 1"
                                    + " && (0x"
                                    + Long.toHexString(data.singlesToSkip(i).asciiMoves[1])
                                    + "L & (1L << (curChar & 077))) != 0L)")
                        .new_line();
                }
                else if (data.singlesToSkip(i).asciiMoves[1] == 0L) {
                    writer.append(
                            prefix + "   while (curChar <= " + (int) LexerGenerator.MaxChar(
                                    data.singlesToSkip(i).asciiMoves[0])
                                    + " && (0x" + Long.toHexString(data.singlesToSkip(i).asciiMoves[0])
                                    + "L & (1L << curChar)) != 0L)")
                        .new_line();
                }
                else if (data.singlesToSkip(i).asciiMoves[0] == 0L) {
                    writer.append(prefix + "   while (curChar > 63 && curChar <= "
                            + (LexerGenerator.MaxChar(data.singlesToSkip(i).asciiMoves[1]) + 64) + " && (0x"
                            + Long.toHexString(data.singlesToSkip(i).asciiMoves[1])
                            + "L & (1L << (curChar & 077))) != 0L)")
                        .new_line();
                }

                if (data.options().getDebugTokenManager()) {
                    writer.append(prefix + "{").new_line();
                    writer.append("      debugStream.println("
                            + (data.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
                            + "\"Skipping character : \" + TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \")\");")
                        .new_line();
                }
                writer.append(prefix + "      curChar = input_stream.BeginToken();").new_line();

                if (data.options().getDebugTokenManager()) {
                    writer.append(prefix + "}").new_line();
                }

                writer.append(prefix + "}").new_line();
                writer.append(prefix + "catch (java.io.IOException e1) { continue EOFLoop; }").new_line();
            }

            if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
                if (data.options().getDebugTokenManager()) {
                    writer.append(
                            "      debugStream.println(\"   Matched the empty string as \" + tokenImage["
                                    + data.initMatch(i) + "] + \" token.\");").new_line();
                }

                writer.append(prefix + "jjmatchedKind = " + data.initMatch(i) + ";").new_line();
                writer.append(prefix + "jjmatchedPos = -1;").new_line();
                writer.append(prefix + "curPos = 0;").new_line();
            }
            else {
                writer.append(prefix + "jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";").new_line();
                writer.append(prefix + "jjmatchedPos = 0;").new_line();
            }

            if (data.options().getDebugTokenManager()) {
                writer.append("      debugStream.println("
                        + (data.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
                        + "\"Current character : \" + TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \") "
                        + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());").new_line();
            }

            writer.append(prefix + "curPos = jjMoveStringLiteralDfa0_" + i + "();").new_line();
            if (data.canMatchAnyChar(i) != -1) {
                if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
                    writer.append(prefix + "if (jjmatchedPos < 0 || (jjmatchedPos == 0 && jjmatchedKind > "
                            + data.canMatchAnyChar(i) + "))").new_line();
                }
                else {
                    writer.append(prefix + "if (jjmatchedPos == 0 && jjmatchedKind > " + data.canMatchAnyChar(i) + ")").new_line();
                }
                writer.append(prefix + "{").new_line();

                if (data.options().getDebugTokenManager()) {
                    writer.append("           debugStream.println(\"   Current character matched as a \" + tokenImage["
                                    + data.canMatchAnyChar(i) + "] + \" token.\");").new_line();
                }
                writer.append(prefix + "   jjmatchedKind = " + data.canMatchAnyChar(i) + ";").new_line();

                if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
                    writer.append(prefix + "   jjmatchedPos = 0;").new_line();
                }

                writer.append(prefix + "}").new_line();
            }

            if (data.maxLexStates() > 1) {
                writer.append(prefix + "break;").new_line();
            }
        }

        if (data.maxLexStates() > 1) {
            writer.append(endSwitch).new_line();
        }
        else if (data.maxLexStates() == 0) {
            writer.append("       jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";").new_line();
        }

        if (data.maxLexStates() > 1) {
            prefix = "  ";
        }
        else {
            prefix = "";
        }

        if (data.maxLexStates() > 0) {
            writer.append(prefix + "   if (jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")").new_line();
            writer.append(prefix + "   {").new_line();
            writer.append(prefix + "      if (jjmatchedPos + 1 < curPos)").new_line();

            if (data.options().getDebugTokenManager()) {
                writer.append(prefix + "      {").new_line();
                writer.append(prefix + "         debugStream.println("
                        + "\"   Putting back \" + (curPos - jjmatchedPos - 1) + \" characters into the input stream.\");")
                    .new_line();
            }

            writer.append(prefix + "         input_stream.backup(curPos - jjmatchedPos - 1);").new_line();

            if (data.options().getDebugTokenManager()) {
                writer.append(prefix + "      }").new_line();
            }

            if (data.options().getDebugTokenManager()) {
                writer.append("    debugStream.println("
                        + "\"****** FOUND A \" + tokenImage[jjmatchedKind] + \" MATCH "
                        + "(\" + TokenException.addEscapes(new String(input_stream.GetSuffix(jjmatchedPos + 1))) + "
                        + "\") ******\\n\");").new_line();
            }

            if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
                writer.append(prefix + "      if ((jjtoToken[jjmatchedKind >> 6] & "
                                + "(1L << (jjmatchedKind & 077))) != 0L)").new_line();
                writer.append(prefix + "      {").new_line();
            }

            writer.append(prefix + "         matchedToken = jjFillToken();").new_line();

            if (data.hasSpecial()) {
                writer.append(prefix + "         matchedToken.specialToken = specialToken;").new_line();
            }

            if (data.hasTokenActions()) {
                writer.append(prefix + "         TokenLexicalActions(matchedToken);").new_line();
            }

            if (data.maxLexStates() > 1) {
                writer.append("       if (jjnewLexState[jjmatchedKind] != -1)").new_line();
                writer.append(prefix + "       curLexState = jjnewLexState[jjmatchedKind];").new_line();
            }

            writer.append(prefix + "         return matchedToken;").new_line();

            if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
                writer.append(prefix + "      }").new_line();

                if (data.hasSkip() || data.hasSpecial()) {
                    if (data.hasMore()) {
                        writer.append(
                                prefix + "      else if ((jjtoSkip[jjmatchedKind >> 6] & "
                                        + "(1L << (jjmatchedKind & 077))) != 0L)").new_line();
                    }
                    else {
                        writer.append(prefix + "      else").new_line();
                    }

                    writer.append(prefix + "      {").new_line();

                    if (data.hasSpecial()) {
                        writer.append(
                                prefix + "         if ((jjtoSpecial[jjmatchedKind >> 6] & "
                                        + "(1L << (jjmatchedKind & 077))) != 0L)").new_line();
                        writer.append(prefix + "         {").new_line();

                        writer.append(prefix + "            matchedToken = jjFillToken();").new_line();

                        writer.append(prefix + "            if (specialToken == null)").new_line();
                        writer.append(prefix + "               specialToken = matchedToken;").new_line();
                        writer.append(prefix + "            else").new_line();
                        writer.append(prefix + "            {").new_line();
                        writer.append(prefix + "               matchedToken.specialToken = specialToken;").new_line();
                        writer.append(prefix + "               specialToken = (specialToken.next = matchedToken);").new_line();
                        writer.append(prefix + "            }").new_line();

                        if (data.hasSkipActions()) {
                            writer.append(prefix + "            SkipLexicalActions(matchedToken);").new_line();
                        }

                        writer.append(prefix + "         }").new_line();

                        if (data.hasSkipActions()) {
                            writer.append(prefix + "         else").new_line();
                            writer.append(prefix + "            SkipLexicalActions(null);").new_line();
                        }
                    }
                    else if (data.hasSkipActions()) {
                        writer.append(prefix + "         SkipLexicalActions(null);").new_line();
                    }

                    if (data.maxLexStates() > 1) {
                        writer.append("         if (jjnewLexState[jjmatchedKind] != -1)").new_line();
                        writer.append(prefix + "         curLexState = jjnewLexState[jjmatchedKind];").new_line();
                    }

                    writer.append(prefix + "         continue EOFLoop;").new_line();
                    writer.append(prefix + "      }").new_line();
                }

                if (data.hasMore()) {
                    if (data.hasMoreActions()) {
                        writer.append(prefix + "      MoreLexicalActions();").new_line();
                    }
                    else if (data.hasSkipActions() || data.hasTokenActions()) {
                        writer.append(prefix + "      jjimageLen += jjmatchedPos + 1;").new_line();
                    }

                    if (data.maxLexStates() > 1) {
                        writer.append("      if (jjnewLexState[jjmatchedKind] != -1)").new_line();
                        writer.append(prefix + "      curLexState = jjnewLexState[jjmatchedKind];").new_line();
                    }
                    writer.append(prefix + "      curPos = 0;").new_line();
                    writer.append(
                            prefix + "      jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";")
                        .new_line();

                    writer.append(prefix + "      try {").new_line();
                    writer.append(prefix + "         curChar = input_stream.readChar();").new_line();

                    if (data.options().getDebugTokenManager()) {
                        writer.append("   debugStream.println("
                                + (data.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
                                + "\"Current character : \" + "
                                + "TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \") "
                                + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());")
                            .new_line();
                    }
                    writer.append(prefix + "         continue;").new_line();
                    writer.append(prefix + "      }").new_line();
                    writer.append(prefix + "      catch (java.io.IOException e1) { }").new_line();
                }
            }

            writer.append(prefix + "   }").new_line();
            writer.append(prefix + "   int error_line = input_stream.getEndLine();").new_line();
            writer.append(prefix + "   int error_column = input_stream.getEndColumn();").new_line();
            writer.append(prefix + "   String error_after = null;").new_line();
            writer.append(prefix + "   boolean EOFSeen = false;").new_line();
            writer.append(prefix + "   try { input_stream.readChar(); input_stream.backup(1); }").new_line();
            writer.append(prefix + "   catch (java.io.IOException e1) {").new_line();
            writer.append(prefix + "      EOFSeen = true;").new_line();
            writer.append(prefix + "      error_after = curPos <= 1 ? \"\" : input_stream.GetImage();").new_line();
            writer.append(prefix + "      if (curChar == '\\n' || curChar == '\\r') {").new_line();
            writer.append(prefix + "         error_line++;").new_line();
            writer.append(prefix + "         error_column = 0;").new_line();
            writer.append(prefix + "      }").new_line();
            writer.append(prefix + "      else").new_line();
            writer.append(prefix + "         error_column++;").new_line();
            writer.append(prefix + "   }").new_line();
            writer.append(prefix + "   if (!EOFSeen) {").new_line();
            writer.append(prefix + "      input_stream.backup(1);").new_line();
            writer.append(prefix + "      error_after = curPos <= 1 ? \"\" : input_stream.GetImage();").new_line();
            writer.append(prefix + "   }").new_line();
            writer.append(prefix + "   throw new TokenException("
                    + "EOFSeen, curLexState, error_line, error_column, error_after, curChar, TokenException.LEXICAL_ERROR);")
                .new_line();
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
                    writer.append("         if (jjmatchedPos == -1)").new_line();
                    writer.append("         {").new_line();
                    writer.append("            if (jjbeenHere[" + data.getState(i) + "] &&").new_line();
                    writer.append("                jjemptyLineNo[" + data.getState(i) + "] == input_stream.getBeginLine() &&").new_line();
                    writer.append("                jjemptyColNo[" + data.getState(i) + "] == input_stream.getBeginColumn())").new_line();
                    writer.append("               throw new TokenException("
                            + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                            + "at line \" + input_stream.getBeginLine() + \", "
                            + "column \" + input_stream.getBeginColumn() + \".\"), TokenException.LOOP_DETECTED);").new_line();
                    writer.append("            jjemptyLineNo[" + data.getState(i) + "] = input_stream.getBeginLine();").new_line();
                    writer.append("            jjemptyColNo[" + data.getState(i) + "] = input_stream.getBeginColumn();").new_line();
                    writer.append("            jjbeenHere[" + data.getState(i) + "] = true;").new_line();
                    writer.append("         }").new_line();
                }

                if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
                    break;
                }

                writer.append("         image.append");
                if (data.getImage(i) != null) {
                    writer.append("(jjstrLiteralImages[" + i + "]);").new_line();
                    writer.append("        lengthOfMatch = jjstrLiteralImages[" + i + "].length();").new_line();
                }
                else {
                    writer.append("(input_stream.GetSuffix(jjimageLen + (lengthOfMatch = jjmatchedPos + 1)));").new_line();
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
                    writer.append("         if (jjmatchedPos == -1)").new_line();
                    writer.append("         {").new_line();
                    writer.append("            if (jjbeenHere[" + data.getState(i) + "] &&").new_line();
                    writer.append("                jjemptyLineNo[" + data.getState(i) + "] == input_stream.getBeginLine() &&").new_line();
                    writer.append("                jjemptyColNo[" + data.getState(i) + "] == input_stream.getBeginColumn())").new_line();
                    writer.append("               throw new TokenException("
                            + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                            + "at line \" + input_stream.getBeginLine() + \", "
                            + "column \" + input_stream.getBeginColumn() + \".\"), TokenException.LOOP_DETECTED);").new_line();
                    writer.append("            jjemptyLineNo[" + data.getState(i) + "] = input_stream.getBeginLine();").new_line();
                    writer.append("            jjemptyColNo[" + data.getState(i) + "] = input_stream.getBeginColumn();").new_line();
                    writer.append("            jjbeenHere[" + data.getState(i) + "] = true;").new_line();
                    writer.append("         }").new_line();
                }

                if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
                    break;
                }

                writer.append("         image.append");

                if (data.getImage(i) != null) {
                    writer.append("(jjstrLiteralImages[" + i + "]);").new_line();
                }
                else {
                    writer.append("(input_stream.GetSuffix(jjimageLen));").new_line();
                }

                writer.append("         jjimageLen = 0;").new_line();
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
                    writer.append("         if (jjmatchedPos == -1)").new_line();
                    writer.append("         {").new_line();
                    writer.append("            if (jjbeenHere[" + data.getState(i) + "] &&").new_line();
                    writer.append("                jjemptyLineNo[" + data.getState(i)
                            + "] == input_stream.getBeginLine() &&").new_line();
                    writer.append("                jjemptyColNo[" + data.getState(i) + "] == input_stream.getBeginColumn())").new_line();
                    writer.append("               throw new TokenException("
                            + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                            + "at line \" + input_stream.getBeginLine() + \", "
                            + "column \" + input_stream.getBeginColumn() + \".\"), TokenException.LOOP_DETECTED);").new_line();
                    writer.append("            jjemptyLineNo[" + data.getState(i) + "] = input_stream.getBeginLine();").new_line();
                    writer.append("            jjemptyColNo[" + data.getState(i) + "] = input_stream.getBeginColumn();").new_line();
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
                        writer.append("(jjstrLiteralImages[" + i + "]);").new_line();
                        writer.append("        lengthOfMatch = jjstrLiteralImages[" + i + "].length();").new_line();
                    }
                    else {
                        writer.append("(input_stream.GetSuffix(jjimageLen + (lengthOfMatch = jjmatchedPos + 1)));").new_line();
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

        writer.append("static final int[] jjnextStates = {");
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

        writer.append("\n};").new_line();
    }

    private static String getStrLiteralImages(LexerData data) {
        if (data.getImageCount() <= 0) {
            return "";
        }

        String image;
        int i;
        int charCnt = 0; // Set to zero in reInit() but just to be sure

        data.setImage(0, "");

        var buffer = new StringWriter();
        var writer = SourceWriter.create(buffer);
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
                    writer.new_line();
                    charCnt = 0;
                }

                writer.append("null, ");
                continue;
            }

            String toPrint = "\"";
            for (int j = 0; j < image.length(); j++) {
                if (image.charAt(j) <= 0xff) {
                    toPrint += ("\\" + Integer.toOctalString(image.charAt(j)));
                }
                else {
                    String hexVal = Integer.toHexString(image.charAt(j));
                    if (hexVal.length() == 3) {
                        hexVal = "0" + hexVal;
                    }
                    toPrint += ("\\u" + hexVal);
                }
            }

            toPrint += ("\", ");

            if ((charCnt += toPrint.length()) >= 80) {
                writer.new_line();
                charCnt = 0;
            }

            writer.append(toPrint);
        }

        while (++i < data.maxOrdinal()) {
            if ((charCnt += 6) > 80) {
                writer.new_line();
                charCnt = 0;
            }

            writer.append("null, ");
        }
        buffer.flush();
        return buffer.toString();
    }

    private void DumpStartWithStates(SourceWriter writer, NfaStateData data) {
        writer.append("private int " + "jjStartNfaWithStates" + data.getLexerStateSuffix() + "(int pos, int kind, int state)").new_line();
        writer.append("{").new_line();
        writer.append("   jjmatchedKind = kind;").new_line();
        writer.append("   jjmatchedPos = pos;").new_line();

        if (data.global.options().getDebugTokenManager()) {
            writer.append("   debugStream.println(\"   No more string literal token matches are possible.\");").new_line();
            writer.append("   debugStream.println(\"   Currently matched the first \" + (jjmatchedPos + 1) + \" characters as a \" + tokenImage[jjmatchedKind] + \" token.\");").new_line();
        }

        writer.append("   try { curChar = input_stream.readChar(); }").new_line();
        writer.append("   catch(java.io.IOException e) { return pos + 1; }").new_line();

        if (data.global.options().getDebugTokenManager()) {
            writer.append("   debugStream.println("
                    + (data.global.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
                    + "\"Current character : \" + TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \") "
                    + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());").new_line();
        }
        writer.append("   return jjMoveNfa" + data.getLexerStateSuffix() + "(state, pos + 1);").new_line();
        writer.append("}").new_line();
    }

    @Override
    protected final void DumpHeadForCase(SourceWriter writer, int byteNum) {
        if (byteNum == 0) {
            writer.append("         long l = 1L << curChar;").new_line();
        }
        else if (byteNum == 1) {
            writer.append("         long l = 1L << (curChar & 077);").new_line();
        }
        else {
            writer.append("         int hiByte = (curChar >> 8);").new_line();
            writer.append("         int i1 = hiByte >> 6;").new_line();
            writer.append("         long l1 = 1L << (hiByte & 077);").new_line();
            writer.append("         int i2 = (curChar & 0xff) >> 6;").new_line();
            writer.append("         long l2 = 1L << (curChar & 077);").new_line();
        }

        // writer.append(" MatchLoop: do").new_line();
        writer.append("         do").new_line();
        writer.append("         {").new_line();

        writer.append("            switch(jjstateSet[--i])").new_line();
        writer.append("            {").new_line();
    }

    private void DumpNonAsciiMoveMethod(NfaState state, LexerData data, SourceWriter writer) {
        for (int j = 0; j < state.loByteVec.size(); j += 2) {
            writer.append("      case " + state.loByteVec.get(j) + ":").new_line();
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.loByteVec.get(j + 1)))) {
                writer.append("         return ((jjbitVec" + state.loByteVec.get(j + 1) + "[i2] & l2) != 0L);").new_line();
            }
            else {
                writer.append("            return true;").new_line();
            }
        }

        writer.append("      default :").new_line();

        for (int j = state.nonAsciiMoveIndices.length; j > 0; j -= 2) {
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.nonAsciiMoveIndices[j - 2]))) {
                writer.append("         if ((jjbitVec" + state.nonAsciiMoveIndices[j - 2] + "[i1] & l1) != 0L)").new_line();
            }
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.nonAsciiMoveIndices[j - 1]))) {
                writer.append("            if ((jjbitVec" + state.nonAsciiMoveIndices[j - 1] + "[i2] & l2) == 0L)").new_line();
                writer.append("               return false;").new_line();
                writer.append("            else").new_line();
            }
            writer.append("            return true;").new_line();
        }
    }

    private String getStatesForState(LexerData data) {
        if (data.getStatesForState() == null) {
            assert (false) : "This should never be null.";
            return "null";
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
        return String.format("{%s}", builder);
    }

    private String getKindForState(LexerData data) {
        if (data.getKinds() == null) {
            return "null";
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
        return String.format("{%s}", builder);
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
            params.append("long active").append(i).append(", ");
        }
        params.append("long active").append(i).append(")");

        writer.append(
                "private final int jjStopStringLiteralDfa" + data.getLexerStateSuffix() + "(int pos, "
                        + params);
        writer.append("{").new_line();

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
                        writer.append("         if (");
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
                            writer.append("            jjmatchedKind = " + kindStr + ";").new_line();

                            if (((data.global.initMatch(data.getStateIndex()) != 0)
                                    && (data.global.initMatch(data.getStateIndex()) != Integer.MAX_VALUE))) {
                                writer.append("            jjmatchedPos = 0;").new_line();
                            }
                        }
                        else if (i == jjmatchedPos) {
                            if (data.isSubStringAtPos(i)) {
                                writer.append("            if (jjmatchedPos != " + i + ")").new_line();
                                writer.append("            {").new_line();
                                writer.append("               jjmatchedKind = " + kindStr + ";").new_line();
                                writer.append("               jjmatchedPos = " + i + ";").new_line();
                                writer.append("            }").new_line();
                            }
                            else {
                                writer.append("            jjmatchedKind = " + kindStr + ";").new_line();
                                writer.append("            jjmatchedPos = " + i + ";").new_line();
                            }
                        }
                        else {
                            if (jjmatchedPos > 0) {
                                writer.append("            if (jjmatchedPos < " + jjmatchedPos + ")").new_line();
                            }
                            else {
                                writer.append("            if (jjmatchedPos == 0)").new_line();
                            }
                            writer.append("            {").new_line();
                            writer.append("               jjmatchedKind = " + kindStr + ";").new_line();
                            writer.append("               jjmatchedPos = " + jjmatchedPos + ";").new_line();
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
                writer.append("   return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", pos + 1);").new_line();
            }
            else {
                writer.append("   return pos + 1;").new_line();
            }

            writer.append("}").new_line();
            return;
        }

        writer.append("   return jjMoveNfa" + data.getLexerStateSuffix() + "(" + "jjStopStringLiteralDfa"
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
            writer.append("private int " + "jjMoveStringLiteralDfa0" + data.getLexerStateSuffix() + "()").new_line();
            DumpNullStrLiterals(writer, data);
            return;
        }

        if (!data.global.boilerPlateDumped) {
            writer.append("private int " + "jjStopAtPos(int pos, int kind)").new_line();
            writer.append("{").new_line();
            writer.append("   jjmatchedKind = kind;").new_line();
            writer.append("   jjmatchedPos = pos;").new_line();

            if (data.global.options().getDebugTokenManager()) {
                writer.append("   debugStream.println(\"   No more string literal token matches are possible.\");").new_line();
                writer.append("   debugStream.println(\"   Currently matched the first \" + (jjmatchedPos + 1) + " + "\" characters as a \" + tokenImage[jjmatchedKind] + \" token.\");").new_line();
            }

            writer.append("   return pos + 1;").new_line();
            writer.append("}").new_line();
            data.global.boilerPlateDumped = true;
        }

        for (i = 0; i < data.getMaxLen(); i++) {
            boolean atLeastOne = false;
            boolean startNfaNeeded = false;
            tab = data.getCharPosKind(i);
            String[] keys = LexerGenerator.ReArrange(tab);

            StringBuilder params = new StringBuilder();
            params.append("(");
            if (i != 0) {
                if (i == 1) {
                    for (j = 0; j < (maxLongsReqd - 1); j++) {
                        if (i <= data.getMaxLenForActive(j)) {
                            if (atLeastOne) {
                                params.append(", ");
                            }
                            else {
                                atLeastOne = true;
                            }
                            params.append("long active").append(j);
                        }
                    }

                    if (i <= data.getMaxLenForActive(j)) {
                        if (atLeastOne) {
                            params.append(", ");
                        }
                        params.append("long active").append(j);
                    }
                }
                else {
                    for (j = 0; j < (maxLongsReqd - 1); j++) {
                        if (i <= (data.getMaxLenForActive(j) + 1)) {
                            if (atLeastOne) {
                                params.append(", ");
                            }
                            else {
                                atLeastOne = true;
                            }
                            params.append("long old").append(j).append(", ").append("long active").append(j);
                        }
                    }

                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        if (atLeastOne) {
                            params.append(", ");
                        }
                        params.append("long old").append(j).append(", ").append("long active").append(j);
                    }
                }
            }
            params.append(")");
            writer.append(
                    "private int " + "jjMoveStringLiteralDfa" + i + data.getLexerStateSuffix() + params);
            writer.append("{").new_line();

            if (i != 0) {
                if (i > 1) {
                    atLeastOne = false;
                    writer.append("   if ((");

                    for (j = 0; j < (maxLongsReqd - 1); j++) {
                        if (i <= (data.getMaxLenForActive(j) + 1)) {
                            if (atLeastOne) {
                                writer.append(" | ");
                            }
                            else {
                                atLeastOne = true;
                            }
                            writer.append("(active" + j + " &= old" + j + ")");
                        }
                    }

                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        if (atLeastOne)
                            writer.append(" | ");
                        writer.append("(active" + j + " &= old" + j + ")");
                    }

                    writer.append(") == 0L)").new_line();
                    if (!data.isMixedState() && (data.generatedStates() != 0)) {
                        writer.append(
                                "      return jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 2) + ", ");
                        for (j = 0; j < (maxLongsReqd - 1); j++) {
                            if (i <= (data.getMaxLenForActive(j) + 1)) {
                                writer.append("old" + j + ", ");
                            }
                            else {
                                writer.append("0L, ");
                            }
                        }
                        if (i <= (data.getMaxLenForActive(j) + 1)) {
                            writer.append("old" + j + ");").new_line();
                        }
                        else {
                            writer.append("0L);").new_line();
                        }
                    }
                    else if (data.generatedStates() != 0) {
                        writer.append("      return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", " + (i - 1) + ");").new_line();
                    }
                    else {
                        writer.append("      return " + i + ";").new_line();
                    }
                }

                if ((i != 0) && data.global.options().getDebugTokenManager()) {
                    writer.append("   if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")").new_line();
                    writer.append("      debugStream.println(\"   Currently matched the first \" + " + "(jjmatchedPos + 1) + \" characters as a \" + tokenImage[jjmatchedKind] + \" token.\");").new_line();
                    writer.append("   debugStream.println(\"   Possible string literal matches : { \"").new_line();

                    for (int vecs = 0; vecs < ((data.getMaxStrKind() / 64) + 1); vecs++) {
                        if (i <= data.getMaxLenForActive(vecs)) {
                            writer.append(" +").new_line();
                            writer.append("         jjKindsForBitVector(" + vecs + ", ");
                            writer.append("active" + vecs + ") ");
                        }
                    }

                    writer.append(" + \" } \");").new_line();
                }

                writer.append("   try { curChar = input_stream.readChar(); }").new_line();
                writer.append("   catch(java.io.IOException e) {").new_line();

                if (!data.isMixedState() && (data.generatedStates() != 0)) {
                    writer.append(
                            "      jjStopStringLiteralDfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ");
                    for (k = 0; k < (maxLongsReqd - 1); k++) {
                        if (i <= data.getMaxLenForActive(k)) {
                            writer.append("active" + k + ", ");
                        }
                        else {
                            writer.append("0L, ");
                        }
                    }

                    if (i <= data.getMaxLenForActive(k))
                        writer.append("active" + k + ");").new_line();
                    else
                        writer.append("0L);").new_line();

                    if ((i != 0) && data.global.options().getDebugTokenManager()) {
                        writer.append("      if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")").new_line();
                        writer.append("         debugStream.println(\"   Currently matched the first \" + " + "(jjmatchedPos + 1) + \" characters as a \" + tokenImage[jjmatchedKind] + \" token.\");").new_line();
                    }
                    writer.append("      return " + i + ";").new_line();
                }
                else if (data.generatedStates() != 0)
                    writer.append("   return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", " + (i - 1) + ");").new_line();
                else
                    writer.append("      return " + i + ";").new_line();

                writer.append("   }").new_line();
            }

            if ((i != 0) && data.global.options().getDebugTokenManager()) {
                writer.append("   debugStream.println("
                        + (data.global.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + "
                        : "")
                        + "\"Current character : \" + TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \") "
                        + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());").new_line();
            }

            writer.append("   switch(curChar)").new_line();
            writer.append("   {").new_line();

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
                        writer.append("      case " + (int) Character.toUpperCase(c) + ":").new_line();
                    }

                    if (c != Character.toLowerCase(c)) {
                        writer.append("      case " + (int) Character.toLowerCase(c) + ":").new_line();
                    }
                }

                writer.append("      case " + (int) c + ":").new_line();

                long matchedKind;
                String prefix = (i == 0) ? "         " : "            ";

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
                                writer.append("         else if ");
                            else if (i != 0)
                                writer.append("         if ");

                            ifGenerated = true;

                            int kindToPrint;
                            if (i != 0) {
                                writer.append("((active" + j + " & 0x" + Long.toHexString(1L << k) + "L) != 0L)").new_line();
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
                                            prefix + "return jjStartNfaWithStates" + data.getLexerStateSuffix() + "(" + i
                                                    + ", "
                                                    + kindToPrint + ", " + stateSetName + ");").new_line();
                                }
                                else {
                                    writer.append(prefix + "return jjStopAtPos" + "(" + i + ", " + kindToPrint + ");").new_line();
                                }
                            }
                            else if (((data.global.initMatch(data.getStateIndex()) != 0)
                                    && (data.global.initMatch(data.getStateIndex()) != Integer.MAX_VALUE)) || (i
                                    != 0)) {
                                writer.append("         {").new_line();
                                writer.append(prefix + "jjmatchedKind = " + kindToPrint + ";").new_line();
                                writer.append(prefix + "jjmatchedPos = " + i + ";").new_line();
                                writer.append("         }").new_line();
                            }
                            else {
                                writer.append(prefix + "jjmatchedKind = " + kindToPrint + ";").new_line();
                            }
                        }
                    }
                }

                if (info.hasValidKindCnt()) {
                    atLeastOne = false;

                    if (i == 0) {
                        writer.append("         return ");

                        writer.append("jjMoveStringLiteralDfa" + (i + 1) + data.getLexerStateSuffix() + "(");
                        for (j = 0; j < (maxLongsReqd - 1); j++) {
                            if ((i + 1) <= data.getMaxLenForActive(j)) {
                                if (atLeastOne)
                                    writer.append(", ");
                                else
                                    atLeastOne = true;

                                writer.append("0x" + Long.toHexString(info.validKinds[j]) + "L");
                            }
                        }

                        if ((i + 1) <= data.getMaxLenForActive(j)) {
                            if (atLeastOne)
                                writer.append(", ");

                            writer.append("0x" + Long.toHexString(info.validKinds[j]) + "L");
                        }
                        writer.append(");").new_line();
                    }
                    else {
                        writer.append("         return ");

                        writer.append("jjMoveStringLiteralDfa" + (i + 1) + data.getLexerStateSuffix() + "(");

                        for (j = 0; j < (maxLongsReqd - 1); j++) {
                            if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
                                if (atLeastOne)
                                    writer.append(", ");
                                else
                                    atLeastOne = true;

                                if (info.validKinds[j] != 0L)
                                    writer.append("active" + j + ", 0x" + Long.toHexString(info.validKinds[j]) + "L");
                                else
                                    writer.append("active" + j + ", 0L");
                            }
                        }

                        if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
                            if (atLeastOne)
                                writer.append(", ");
                            if (info.validKinds[j] != 0L)
                                writer.append("active" + j + ", 0x" + Long.toHexString(info.validKinds[j]) + "L");
                            else
                                writer.append("active" + j + ", 0L");
                        }

                        writer.append(");").new_line();
                    }
                }
                else // A very special case.
                    if ((i == 0) && data.isMixedState()) {

                        if (data.generatedStates() != 0) {
                            writer.append("         return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", 0);").new_line();
                        }
                        else {
                            writer.append("         return 1;").new_line();
                        }
                    }
                    else if (i != 0) // No more str literals to look for
                    {
                        writer.append("         break;").new_line();
                        startNfaNeeded = true;
                    }
            }

            writer.append("      default :").new_line();

            if (data.global.options().getDebugTokenManager()) {
                writer.append("      debugStream.println(\"   No string literal matches possible.\");").new_line();
            }

            if (data.generatedStates() != 0) {
                if (i == 0) {
                    // This means no string literal is possible. Just move nfa with this guy and return.
                    writer.append("         return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", 0);").new_line();
                }
                else {
                    writer.append("         break;").new_line();
                    startNfaNeeded = true;
                }
            }
            else {
                writer.append("         return " + (i + 1) + ";").new_line();
            }

            writer.append("   }").new_line();

            if ((i != 0) && startNfaNeeded) {
                if (!data.isMixedState() && (data.generatedStates() != 0)) {
                    /*
                     * Here, a string literal is successfully matched and no more string literals are
                     * possible. So set the kind and state set upto and including this position for the
                     * matched string.
                     */

                    writer.append("   return jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ");
                    for (k = 0; k < (maxLongsReqd - 1); k++) {
                        if (i <= data.getMaxLenForActive(k))
                            writer.append("active" + k + ", ");
                        else
                            writer.append("0L, ");
                    }
                    if (i <= data.getMaxLenForActive(k))
                        writer.append("active" + k + ");").new_line();
                    else
                        writer.append("0L);").new_line();
                }
                else if (data.generatedStates() != 0)
                    writer.append("   return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", " + i + ");").new_line();
                else
                    writer.append("   return " + (i + 1) + ";").new_line();
            }

            writer.append("}").new_line();
        }

        if (!data.isMixedState() && (data.generatedStates() != 0) && data.getCreateStartNfa()) {
            DumpStartWithStates(writer, data);
        }
    }

    @Override
    protected final void dumpMoveNfa(SourceWriter writer, NfaStateData data) {
        writer.append("private int " + "jjMoveNfa" + data.getLexerStateSuffix() + "(int startState, int curPos)").new_line();
        writer.append("{").new_line();
        if (data.generatedStates() == 0) {
            writer.append("   return curPos;").new_line();
            writer.append("}").new_line();
            return;
        }

        if (data.isMixedState()) {
            writer.append("   int strKind = jjmatchedKind;").new_line();
            writer.append("   int strPos = jjmatchedPos;").new_line();
            writer.append("   int seenUpto;").new_line();
            writer.append("   input_stream.backup(seenUpto = curPos + 1);").new_line();
            writer.append("   try { curChar = input_stream.readChar(); }").new_line();
            writer.append("   catch(java.io.IOException e) { throw new Error(\"Internal Error\"); }").new_line();
            writer.append("   curPos = 0;").new_line();
        }

        writer.append("   int startsAt = 0;").new_line();
        writer.append("   jjnewStateCnt = " + data.generatedStates() + ";").new_line();
        writer.append("   int i = 1;").new_line();
        writer.append("   jjstateSet[0] = startState;").new_line();

        if (data.global.options().getDebugTokenManager()) {
            writer.append("      debugStream.println(\"   Starting NFA to match one of : \" + " + "jjKindsForStateVector(curLexState, jjstateSet, 0, 1));").new_line();
        }

        if (data.global.options().getDebugTokenManager()) {
            writer.append("      debugStream.println("
                    + (data.global.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
                    + "\"Current character : \" + TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \") "
                    + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());").new_line();
        }

        writer.append("   int kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";").new_line();
        writer.append("   for (;;)").new_line();
        writer.append("   {").new_line();
        writer.append("      if (++jjround == 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")").new_line();
        writer.append("         ReInitRounds();").new_line();
        writer.append("      if (curChar < 64)").new_line();
        writer.append("      {").new_line();

        DumpAsciiMoves(writer, data, 0);

        writer.append("      }").new_line();

        writer.append("      else if (curChar < 128)").new_line();
        writer.append("      {").new_line();
        DumpAsciiMoves(writer, data, 1);
        writer.append("      }").new_line();

        writer.append("      else").new_line();
        writer.append("      {").new_line();
        DumpCharAndRangeMoves(writer, data);
        writer.append("      }").new_line();
        writer.append("      if (kind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")").new_line();
        writer.append("      {").new_line();
        writer.append("         jjmatchedKind = kind;").new_line();
        writer.append("         jjmatchedPos = curPos;").new_line();
        writer.append("         kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";").new_line();
        writer.append("      }").new_line();
        writer.append("      ++curPos;").new_line();

        if (data.global.options().getDebugTokenManager()) {
            writer.append("      if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")").new_line();
            writer.append("         debugStream.println("
                    + "\"   Currently matched the first \" + (jjmatchedPos + 1) + \" characters as"
                    + " a \" + tokenImage[jjmatchedKind] + \" token.\");").new_line();
        }

        writer.append("      if ((i = jjnewStateCnt) == (startsAt = " + data.generatedStates() + " - (jjnewStateCnt = startsAt)))").new_line();
        if (data.isMixedState())
            writer.append("         break;").new_line();
        else
            writer.append("         return curPos;").new_line();

        if (data.global.options().getDebugTokenManager()) {
            writer.append("      debugStream.println(\"   Possible kinds of longer matches : \" + " + "jjKindsForStateVector(curLexState, jjstateSet, startsAt, i));").new_line();
        }

        writer.append("      try { curChar = input_stream.readChar(); }").new_line();
        if (data.isMixedState())
            writer.append("      catch(java.io.IOException e) { break; }").new_line();
        else
            writer.append("      catch(java.io.IOException e) { return curPos; }").new_line();

        if (data.global.options().getDebugTokenManager()) {
            writer.append("      debugStream.println("
                    + (data.global.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
                    + "\"Current character : \" + TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \") "
                    + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());").new_line();
        }
        writer.append("   }").new_line();

        if (data.isMixedState()) {
            writer.append("   if (jjmatchedPos > strPos)").new_line();
            writer.append("      return curPos;").new_line();
            writer.new_line();
            writer.append("   int toRet = Math.max(curPos, seenUpto);").new_line();
            writer.new_line();
            writer.append("   if (curPos < toRet)").new_line();
            writer.append("      for (i = toRet - Math.min(curPos, seenUpto); i-- > 0; )").new_line();
            writer.append("         try { curChar = input_stream.readChar(); }").new_line();
            writer.append("         catch(java.io.IOException e) { " + "throw new Error(\"Internal Error : Please send a bug report.\"); }").new_line();
            writer.new_line();
            writer.append("   if (jjmatchedPos < strPos)").new_line();
            writer.append("   {").new_line();
            writer.append("      jjmatchedKind = strKind;").new_line();
            writer.append("      jjmatchedPos = strPos;").new_line();
            writer.append("   }").new_line();
            writer.append("   else if (jjmatchedPos == strPos && jjmatchedKind > strKind)").new_line();
            writer.append("      jjmatchedKind = strKind;").new_line();
            writer.new_line();
            writer.append("   return toRet;").new_line();
        }
        writer.append("}").new_line();
    }
}
