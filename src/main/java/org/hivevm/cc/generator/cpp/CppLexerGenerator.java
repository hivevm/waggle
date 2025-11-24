// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
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
import org.hivevm.cc.parser.RegExprSpec;
import org.hivevm.cc.parser.Token;
import org.hivevm.source.SourceWriter;
import org.hivevm.source.Template;

/**
 * Generate lexer.
 */
class CppLexerGenerator extends LexerGenerator {

    @Override
    public final void generate(LexerData data) {
        if (JavaCCErrors.hasError()) {
            return;
        }

        var options = Template.newContext(data.options());
        options.add(LexerGenerator.LOHI_BYTES, data.getLohiByte())
            .set("LOHI_BYTES_INDEX", i -> i)
            .set("LOHI_BYTES_BYTES", i -> getLohiBytes(data, i));
        options.add(LexerGenerator.STATES, data.getStateNames())
            .set("STATES_HEAD", (i, w) -> dumpNfaAndDfaHeader(data.getStateData(i), w))
            .set("STATES_BODY", (i, w) -> dumpNfaAndDfa(data.getStateData(i), w));
        options.add(LexerGenerator.NON_ASCII_TABLE, data.getNonAsciiTableForMethod())
            .set("NON_ASCII_TABLE_OFFSET", i -> i.nonAsciiMethod)
            .set("NON_ASCII_TABLE_METHOD", (k, w) -> DumpNonAsciiMoveMethod(data, k, w));
        options.add("STATE_NAMES_AS_CHARS", data.getStateCount())
            .set("STATE_NAMES_AS_CHARS_INDEX", i -> i)
            .set("STATE_NAMES_AS_CHARS_CHARS", (i, w) -> CppLexerGenerator.getTextAsChars(data.getStateName(i), w));


        options.set("DUMP_STR_LITERAL_IMAGES", p -> DumpStrLiteralImages(p, data));
        options.set("DUMP_STATE_SETS", p -> DumpStateSets(p, data));
        options.set("DUMP_STATES_FOR_STATE_CPP", p -> DumpStatesForStateCPP(p, data));
        options.set("DUMP_STATES_FOR_KIND", p -> DumpStatesForKind(p, data));
        options.set("DUMP_STATIC_VAR_DECLARATIONS", p -> DumpStaticVarDeclarations(p, data));
        options.set("DUMP_GET_NEXT_TOKEN", p -> DumpGetNextToken(p, data));
        options.set("DUMP_SKIP_ACTIONS", p -> DumpSkipActions(p, data));
        options.set("DUMP_MORE_ACTIONS", p -> DumpMoreActions(p, data));
        options.set("DUMP_TOKEN_ACTIONS", p -> DumpTokenActions(p, data));

        options.set(LexerGenerator.DEFAULT_LEX_STATE, data.defaultLexState());
        options.add(LexerGenerator.MAX_LEX_STATES, data.maxLexStates())
            .set("MAX_LEX_STATES_INDEX", i -> i);
        options.set(LexerGenerator.STATE_NAMES, data.getStateNames());
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

        CppSources.LEXER.render(options, data.getParserName());

        data.boilerPlateDumped = false;
        CppSources.LEXER_H.render(options, data.getParserName());

        generateConstants(data);
    }

    protected final void generateConstants(LexerData data) {
        List<RExpression> expressions = new ArrayList<>();
        for (TokenProduction tp : data.getTokenProductions()) {
            for (RegExprSpec res : tp.getRespecs()) {
                expressions.add(res.rexp);
            }
        }

        var options = Template.newContext(data.options());
        options.add("TOKENS", data.getOrderedsTokens())
            .set("TOKENS_ORDINAL", RExpression::getOrdinal)
            .set("TOKENS_LABEL", RExpression::getLabel);
        options.add("STATES", data.getStateCount())
            .set("STATES_ORDINAL", i -> i)
            .set("STATES_NAME", data::getStateName);
        options.add("REGEXPS", expressions.size() + 1)
            .set("REGEXPS_ORDINAL", i -> i)
            .set("REGEXPS_LABEL", (i, w) -> CppLexerGenerator.getRegExp(w, false, i, expressions))
            .set("REGEXPS_IMAGE", (i, w) -> CppLexerGenerator.getRegExp(w, true, i, expressions));

        CppSources.PARSER_CONSTANTS.render(options, data.getParserName());
    }

    @Override
    protected final Language getLanguage() {
        return Language.CPP;
    }

    @Override
    protected String toHexString(long value) {
        return "0x" + Long.toHexString(value) + "ULL";
    }

    private void DumpStaticVarDeclarations(SourceWriter writer, LexerData data) {
        if (data.maxLexStates() > 1) {
            writer.new_line();
            writer.append("/** Lex State array. */").new_line();
            writer.append("static const int jjnewLexState[] = {");

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
            writer.append("static const unsigned long long jjtoToken[] = {");
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
            writer.append("static const unsigned long long jjtoSkip[] = {");
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
            writer.append("static const unsigned long long jjtoSpecial[] = {");
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
            writer.append("static const unsigned long long jjtoMore[] = {");
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
        writer.append("   curChar = reader->beginToken();").new_line();

        if (data.hasMoreActions() || data.hasSkipActions() || data.hasTokenActions()) {
            writer.append("   image = jjimage;").new_line();
            writer.append("   image.clear();").new_line();
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
            if (data.maxLexStates() > 1)
                writer.append(caseStr + i + ":").new_line();

            if (data.singlesToSkip(i).HasTransitions()) {
                // added the backup(0) to make JIT happy
                writer.append(prefix + "{ reader->backup(0);").new_line();
                if ((data.singlesToSkip(i).asciiMoves[0] != 0L) && (data.singlesToSkip(i).asciiMoves[1]
                        != 0L)) {
                    writer.append(
                            prefix + "   while ((curChar < 64" + " && (" + Long.toHexString(
                                    data.singlesToSkip(i).asciiMoves[0])
                                    + " & (1L << curChar)) != 0L) || \n" + prefix + "          (curChar >> 6) == 1"
                                    + " && ("
                                    + toHexString(data.singlesToSkip(i).asciiMoves[1])
                                    + " & (1L << (curChar & 077))) != 0L)").new_line();
                }
                else if (data.singlesToSkip(i).asciiMoves[1] == 0L) {
                    writer.append(
                            prefix + "   while (curChar <= " + (int) LexerGenerator.MaxChar(
                                    data.singlesToSkip(i).asciiMoves[0])
                                    + " && (" + toHexString(data.singlesToSkip(i).asciiMoves[0])
                                    + " & (1L << curChar)) != 0L)").new_line();
                }
                else if (data.singlesToSkip(i).asciiMoves[0] == 0L) {
                    writer.append(prefix + "   while (curChar > 63 && curChar <= "
                            + (LexerGenerator.MaxChar(data.singlesToSkip(i).asciiMoves[1]) + 64) + " && ("
                            + toHexString(data.singlesToSkip(i).asciiMoves[1])
                            + " & (1L << (curChar & 077))) != 0L)").new_line();
                }

                writer.append(prefix + "{").new_line();
                if (data.options().getDebugTokenManager()) {
                    if (data.maxLexStates() > 1) {
                        writer.append("      fprintf(debugStream, \"<%s>\" , addUnicodeEscapes(lexStateNames[curLexState]).c_str());").new_line();
                    }

                    writer.append("      fprintf(debugStream, \"Skipping character : %c(%d)\\n\", curChar, (int)curChar);").new_line();
                }

                writer.append(prefix + "if (reader->endOfInput()) { goto EOFLoop; }").new_line();
                writer.append(prefix + "curChar = reader->beginToken();").new_line();
                writer.append(prefix + "}").new_line();
                writer.append(prefix + "}").new_line();
            }

            if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
                if (data.options().getDebugTokenManager()) {
                    writer.append("      fprintf(debugStream, \"   Matched the empty string as %s token.\\n\", addUnicodeEscapes(tokenImage[" + data.initMatch(i) + "]).c_str());").new_line();
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
                writer.append("   fprintf(debugStream, "
                        + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
                        + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
                        + "reader->getEndLine(), reader->getEndColumn());").new_line();
            }

            writer.append(prefix + "curPos = jjMoveStringLiteralDfa0_" + i + "();").new_line();
            if (data.canMatchAnyChar(i) != -1) {
                if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
                    writer.append(prefix + "if (jjmatchedPos < 0 || (jjmatchedPos == 0 && jjmatchedKind > "  + data.canMatchAnyChar(i) + "))").new_line();
                }
                else {
                    writer.append(prefix + "if (jjmatchedPos == 0 && jjmatchedKind > " + data.canMatchAnyChar(i) + ")").new_line();
                }
                writer.append(prefix + "{").new_line();

                if (data.options().getDebugTokenManager()) {
                    writer.append("           fprintf(debugStream, \"   Current character matched as a %s token.\\n\", addUnicodeEscapes(tokenImage[" + data.canMatchAnyChar(i) + "]).c_str());").new_line();
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
                writer.append(prefix + "         fprintf(debugStream, " + "\"   Putting back %d characters into the input stream.\\n\", (curPos - jjmatchedPos - 1));").new_line();
            }

            writer.append(prefix + "         reader->backup(curPos - jjmatchedPos - 1);").new_line();

            if (data.options().getDebugTokenManager()) {
                writer.append(prefix + "      }").new_line();
            }

            if (data.options().getDebugTokenManager()) {
                writer.append("    fprintf(debugStream, \"****** FOUND A %d(%s) MATCH (%s) ******\\n\", jjmatchedKind, addUnicodeEscapes(tokenImage[jjmatchedKind]).c_str(), addUnicodeEscapes(reader->GetSuffix(jjmatchedPos + 1)).c_str());").new_line();
            }

            if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
                writer.append(prefix + "      if ((jjtoToken[jjmatchedKind >> 6] & " + "(1ULL << (jjmatchedKind & 077))) != 0L)").new_line();
                writer.append(prefix + "      {").new_line();
            }

            writer.append(prefix + "         matchedToken = jjFillToken();").new_line();

            if (data.hasSpecial()) {
                writer.append(prefix + "         matchedToken->specialToken = specialToken;").new_line();
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
                        writer.append(prefix + "      else if ((jjtoSkip[jjmatchedKind >> 6] & (1L << (jjmatchedKind & 077))) != 0L)").new_line();
                    }
                    else {
                        writer.append(prefix + "      else").new_line();
                    }

                    writer.append(prefix + "      {").new_line();

                    if (data.hasSpecial()) {
                        writer.append(prefix + "         if ((jjtoSpecial[jjmatchedKind >> 6] & " + "(1ULL << (jjmatchedKind & 077))) != 0L)").new_line();
                        writer.append(prefix + "         {").new_line();

                        writer.append(prefix + "            matchedToken = jjFillToken();").new_line();

                        writer.append(prefix + "            if (specialToken == nullptr)").new_line();
                        writer.append(prefix + "               specialToken = matchedToken;").new_line();
                        writer.append(prefix + "            else").new_line();
                        writer.append(prefix + "            {").new_line();
                        writer.append(prefix + "               matchedToken->specialToken = specialToken;").new_line();
                        writer.append(prefix + "               specialToken = (specialToken->next = matchedToken);").new_line();
                        writer.append(prefix + "            }").new_line();

                        if (data.hasSkipActions()) {
                            writer.append(prefix + "            SkipLexicalActions(matchedToken);").new_line();
                        }

                        writer.append(prefix + "         }").new_line();

                        if (data.hasSkipActions()) {
                            writer.append(prefix + "         else").new_line();
                            writer.append(prefix + "            SkipLexicalActions(nullptr);").new_line();
                        }
                    }
                    else if (data.hasSkipActions()) {
                        writer.append(prefix + "         SkipLexicalActions(nullptr);").new_line();
                    }

                    if (data.maxLexStates() > 1) {
                        writer.append("         if (jjnewLexState[jjmatchedKind] != -1)").new_line();
                        writer.append(prefix + "         curLexState = jjnewLexState[jjmatchedKind];").new_line();
                    }

                    writer.append(prefix + "         goto EOFLoop;").new_line();
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
                    writer.append(prefix + "      jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";").new_line();

                    writer.append(prefix + "   if (!reader->endOfInput()) {").new_line();
                    writer.append(prefix + "         curChar = reader->read(); // UTF8: Support Unicode").new_line();

                    if (data.options().getDebugTokenManager()) {
                        writer.append("   fprintf(debugStream, "
                                + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
                                + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
                                + "reader->getEndLine(), reader->getEndColumn());").new_line();
                    }
                    writer.append(prefix + "   continue;").new_line();
                    writer.append(prefix + " }").new_line();
                }
            }
        }
    }

    private void DumpSkipActions(SourceWriter writer, LexerData data) {
        writer.append(
                "\nvoid " + data.getParserName() + "TokenManager::SkipLexicalActions(Token *matchedToken)");

        writer.append("{").new_line();
        writer.append("   switch(jjmatchedKind)").new_line();
        writer.append("   {").new_line();

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

                writer.append("      case " + i + " : {").new_line();

                if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
                    writer.append("         if (jjmatchedPos == -1)").new_line();
                    writer.append("         {").new_line();
                    writer.append("            if (jjbeenHere[" + data.getState(i) + "] &&").new_line();
                    writer.append("                jjemptyLineNo[" + data.getState(i) + "] == reader->getBeginLine() &&").new_line();
                    writer.append("                jjemptyColNo[" + data.getState(i) + "] == reader->getBeginColumn())").new_line();
                    writer.append("               errorHandler->lexicalError(JJString(\"(\"Error: Bailing out of infinite loop caused by repeated empty string matches \" + \"at line \" + reader->getBeginLine() + \", \" + \"column \" + reader->getBeginColumn() + \".\")), this);").new_line();
                    writer.append("            jjemptyLineNo[" + data.getState(i) + "] = reader->getBeginLine();").new_line();
                    writer.append("            jjemptyColNo[" + data.getState(i) + "] = reader->getBeginColumn();").new_line();
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
                    writer.append("(reader->GetSuffix(jjimageLen + (lengthOfMatch = jjmatchedPos + 1)));").new_line();
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
            writer.append("       }").new_line();
        }

        writer.append("      default :").new_line();
        writer.append("         break;").new_line();
        writer.append("   }").new_line();
        writer.append("}").new_line();
    }

    private void DumpMoreActions(SourceWriter writer, LexerData data) {
        writer.append("\nvoid " + data.getParserName() + "TokenManager::MoreLexicalActions()");
        writer.append("{").new_line();
        writer.append("   jjimageLen += (lengthOfMatch = jjmatchedPos + 1);").new_line();
        writer.append("   switch(jjmatchedKind)").new_line();
        writer.append("   {").new_line();

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

                writer.append("      case " + i + " : {").new_line();

                if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
                    writer.append("         if (jjmatchedPos == -1)").new_line();
                    writer.append("         {").new_line();
                    writer.append("            if (jjbeenHere[" + data.getState(i) + "] &&").new_line();
                    writer.append("                jjemptyLineNo[" + data.getState(i) + "] == reader->getBeginLine() &&").new_line();
                    writer.append("                jjemptyColNo[" + data.getState(i) + "] == reader->getBeginColumn())").new_line();
                    writer.append("               errorHandler->lexicalError(JJString(\"(\"Error: Bailing out of infinite loop caused by repeated empty string matches \" + \"at line \" + reader->getBeginLine() + \", \" + \"column \" + reader->getBeginColumn() + \".\")), this);").new_line();
                    writer.append("            jjemptyLineNo[" + data.getState(i) + "] = reader->getBeginLine();").new_line();
                    writer.append("            jjemptyColNo[" + data.getState(i) + "] = reader->getBeginColumn();").new_line();
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
                    writer.append("(reader->GetSuffix(jjimageLen));").new_line();
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
            writer.append("       }").new_line();
        }

        writer.append("      default :").new_line();
        writer.append("         break;").new_line();

        writer.append("   }").new_line();
        writer.append("}").new_line();
    }

    private void DumpTokenActions(SourceWriter writer, LexerData data) {
        Action act;
        int i;
        writer.append("\nvoid " + data.getParserName()
                + "TokenManager::TokenLexicalActions(Token *matchedToken)");
        writer.append("{").new_line();
        writer.append("   switch(jjmatchedKind)").new_line();
        writer.append("   {").new_line();

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

                writer.append("      case " + i + " : {").new_line();

                if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
                    writer.append("         if (jjmatchedPos == -1)").new_line();
                    writer.append("         {").new_line();
                    writer.append("            if (jjbeenHere[" + data.getState(i) + "] &&").new_line();
                    writer.append("                jjemptyLineNo[" + data.getState(i) + "] == reader->getBeginLine() &&").new_line();
                    writer.append("                jjemptyColNo[" + data.getState(i) + "] == reader->getBeginColumn())").new_line();
                    writer.append("               errorHandler->lexicalError(JJString(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                                    + "at line \" + reader->getBeginLine() + \", "
                                    + "column \" + reader->getBeginColumn() + \".\"), this);").new_line();
                    writer.append("            jjemptyLineNo[" + data.getState(i) + "] = reader->getBeginLine();").new_line();
                    writer.append("            jjemptyColNo[" + data.getState(i) + "] = reader->getBeginColumn();").new_line();
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
                        writer.append("(reader->GetSuffix(jjimageLen + (lengthOfMatch = jjmatchedPos + 1)));").new_line();
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
            writer.append("       }").new_line();
        }

        writer.append("      default :").new_line();
        writer.append("         break;").new_line();
        writer.append("   }").new_line();
        writer.append("}").new_line();
    }

    private void DumpStatesForKind(SourceWriter writer, LexerData data) {
        boolean moreThanOne = false;
        int cnt;

        writer.append("static const int kindForState[" + data.stateSetSize() + "][" + data.stateSetSize()
                + "] = ");

        if (data.getKinds() == null) {
            writer.append("null;").new_line();
            return;
        }
        else {
            writer.append("{").new_line();
        }

        for (int[] kind : data.getKinds()) {
            if (moreThanOne) {
                writer.append(",").new_line();
            }
            moreThanOne = true;

            if (kind == null) {
                writer.append("{}").new_line();
            }
            else {
                cnt = 0;
                writer.append("{ ");
                for (int element : kind) {
                    if ((cnt % 15) == 0) {
                        writer.append("\n  ");
                    }
                    else if (cnt > 1) {
                        writer.append(" ");
                    }

                    writer.append("" + element);
                    writer.append(", ");

                }

                writer.append("}");
            }
        }
        writer.append("\n};").new_line();
    }

    private void DumpStatesForStateCPP(SourceWriter writer, LexerData data) {
        if (data.getStatesForState() == null) {
            assert (false) : "This should never be null.";
            return;
        }

        for (int i = 0; i < data.maxLexStates(); i++) {
            if (data.getStatesForState()[i] == null) {
                continue;
            }

            for (int j = 0; j < data.getStatesForState()[i].length; j++) {
                int[] stateSet = data.getStatesForState()[i][j];

                writer.append("const int stateSet_" + i + "_" + j + "[" + data.stateSetSize() + "] = ");
                if (stateSet == null) {
                    writer.append("   { " + j + " };").new_line();
                    continue;
                }

                writer.append("   { ");

                for (int element : stateSet) {
                    writer.append(element + ", ");
                }

                writer.append("};").new_line();
            }
        }

        for (int i = 0; i < data.maxLexStates(); i++) {
            writer.append("const int *stateSet_" + i + "[] = {").new_line();
            if (data.getStatesForState()[i] == null) {
                writer.append(" NULL, ").new_line();
                writer.append("};").new_line();
                continue;
            }

            for (int j = 0; j < data.getStatesForState()[i].length; j++) {
                writer.append("stateSet_" + i + "_" + j + ",");
            }
            writer.append("};").new_line();
        }

        writer.append("const int** statesForState[] = { ");
        for (int i = 0; i < data.maxLexStates(); i++) {
            writer.append("stateSet_" + i + ", ").new_line();
        }

        writer.append("\n};").new_line();
    }

    private void DumpStateSets(SourceWriter writer, LexerData data) {
        int cnt = 0;

        writer.append("static const int jjnextStates[] = {");
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

    private void DumpStrLiteralImages(SourceWriter writer, LexerData data) {
        // For C++
        String image;
        int i;
        int charCnt = 0; // Set to zero in reInit() but just to be sure

        int literalCount = 0;

        if (data.getImageCount() <= 0) {
            writer.append("static const JJString jjstrLiteralImages[] = {};").new_line();
            return;
        }

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
                    writer.new_line();
                    charCnt = 0;
                }

                writer.append("static JJChar jjstrLiteralChars_" + literalCount++ + "[] = {0};").new_line();
                continue;
            }

            String toPrint = "static JJChar jjstrLiteralChars_" + literalCount++ + "[] = {";
            for (int j = 0; j < image.length(); j++) {
                String hexVal = Integer.toHexString(image.charAt(j));
                toPrint += "0x" + hexVal + ", ";
            }

            // Null char
            toPrint += "0};";

            if ((charCnt += toPrint.length()) >= 80) {
                writer.new_line();
                charCnt = 0;
            }

            writer.append(toPrint).new_line();
        }

        while (++i < data.maxOrdinal()) {
            if ((charCnt += 6) > 80) {
                writer.new_line();
                charCnt = 0;
            }

            writer.append("static JJChar jjstrLiteralChars_" + literalCount++ + "[] = {0};").new_line();
        }

        // Generate the array here.
        writer.append("static const JJString " + "jjstrLiteralImages[] = {").new_line();
        for (int j = 0; j < literalCount; j++) {
            writer.append("jjstrLiteralChars_" + j + ", ").new_line();
        }
        writer.append("};").new_line();
    }

    private void DumpStartWithStates(SourceWriter writer, NfaStateData data) {
        writer.append("\nint " + data.getParserName() + "TokenManager::jjStartNfaWithStates"
                + data.getLexerStateSuffix()
                + "(int pos, int kind, int state)");

        writer.append("{").new_line();
        writer.append("   jjmatchedKind = kind;").new_line();
        writer.append("   jjmatchedPos = pos;").new_line();

        if (data.global.options().getDebugTokenManager()) {
            writer.append("   fprintf(debugStream, \"   No more string literal token matches are possible.\");").new_line();
            writer.append("   fprintf(debugStream, \"   Currently matched the first %d characters as a \\\"%s\\\" token.\\n\",  (jjmatchedPos + 1),  addUnicodeEscapes(tokenImage[jjmatchedKind]).c_str());").new_line();
        }

        writer.append("   if (reader->endOfInput()) { return pos + 1; }").new_line();
        writer.append("   curChar = reader->read(); // UTF8: Support Unicode").new_line();

        if (data.global.options().getDebugTokenManager()) {
            writer.append("   fprintf(debugStream, " + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
                            + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
                            + "reader->getEndLine(), reader->getEndColumn());").new_line();
        }
        writer.append("   return jjMoveNfa" + data.getLexerStateSuffix() + "(state, pos + 1);").new_line();
        writer.append("}").new_line();
    }

    @Override
    protected final void DumpHeadForCase(SourceWriter writer, int byteNum) {
        if (byteNum == 0) {
            writer.append("         unsigned long long l = 1ULL << curChar;").new_line();
            writer.append("         (void)l;").new_line();
        }
        else if (byteNum == 1) {
            writer.append("         unsigned long long l = 1ULL << (curChar & 077);").new_line();
            writer.append("         (void)l;").new_line();
        }
        else {
            writer.append("         int hiByte = (curChar >> 8);").new_line();
            writer.append("         int i1 = hiByte >> 6;").new_line();
            writer.append("         unsigned long long l1 = 1ULL << (hiByte & 077);").new_line();
            writer.append("         int i2 = (curChar & 0xff) >> 6;").new_line();
            writer.append("         unsigned long long l2 = 1ULL << (curChar & 077);").new_line();
        }

        // writer.append(" MatchLoop: do").new_line();
        writer.append("         do").new_line();
        writer.append("         {").new_line();
        writer.append("            switch(jjstateSet[--i])").new_line();
        writer.append("            {").new_line();
    }

    private void DumpNonAsciiMoveMethod(LexerData data, NfaState state, SourceWriter writer) {
        writer.append(
                "\nbool " + data.getParserName() + "TokenManager::jjCanMove_" + state.nonAsciiMethod
                        + "(int hiByte, int i1, int i2, unsigned long long l1, unsigned long long l2)");

        writer.append("{").new_line();
        writer.append("   switch(hiByte)").new_line();
        writer.append("   {").new_line();

        for (int j = 0; j < state.loByteVec.size(); j += 2) {
            writer.append("      case " + state.loByteVec.get(j) + ":").new_line();
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.loByteVec.get(j + 1))))
                writer.append("         return ((jjbitVec" + state.loByteVec.get(j + 1) + "[i2] & l2) != 0L);").new_line();
            else
                writer.append("            return true;").new_line();
        }

        writer.append("      default :").new_line();
        for (int j = state.nonAsciiMoveIndices.length; j > 0; j -= 2) {
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.nonAsciiMoveIndices[j - 2])))
                writer.append("         if ((jjbitVec" + state.nonAsciiMoveIndices[j - 2] + "[i1] & l1) != 0L)").new_line();
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.nonAsciiMoveIndices[j - 1]))) {
                writer.append("            if ((jjbitVec" + state.nonAsciiMoveIndices[j - 1] + "[i2] & l2) == 0L)").new_line();
                writer.append("               return false;").new_line();
                writer.append("            else").new_line();
            }
            writer.append("            return true;").new_line();
        }

        writer.append("         return false;").new_line();
        writer.append("   }").new_line();
        writer.append("}").new_line();
    }

    private void dumpNfaAndDfaHeader(NfaStateData stateData, SourceWriter writer) {
        if (stateData.hasNFA() && !stateData.isMixedState() && (stateData.getMaxStrKind() > 0)) {
            int i, maxKindsReqd = (stateData.getMaxStrKind() / 64) + 1;
            StringBuilder params = new StringBuilder();
            for (i = 0; i < (maxKindsReqd - 1); i++) {
                params.append("unsigned long long active").append(i).append(", ");
            }
            params.append("unsigned long long active").append(i).append(")");
            writer.append("int jjStopStringLiteralDfa" + stateData.getLexerStateSuffix() + "(int pos, " + params  + ";").new_line();

            params.setLength(0);
            params.append("(int pos, ");
            for (i = 0; i < (maxKindsReqd - 1); i++) {
                params.append("unsigned long long active").append(i).append(", ");
            }
            params.append("unsigned long long active").append(i).append(")");
            writer.append("int jjStartNfa" + stateData.getLexerStateSuffix() + params + ";").new_line();
        }

        if (stateData.getMaxLen() == 0) {
            writer.append(" int  jjMoveStringLiteralDfa0" + stateData.getLexerStateSuffix() + "();").new_line();
        }
        else if (!stateData.global.boilerPlateDumped) {
            writer.append(" int " + " jjStopAtPos(int pos, int kind);").new_line();
            stateData.global.boilerPlateDumped = true;
        }

        DumpDfaCodeHeader(writer, stateData);
        if (!stateData.isMixedState() && (stateData.generatedStates() != 0)
                && stateData.getCreateStartNfa()) {
            writer.append("int jjStartNfaWithStates" + stateData.getLexerStateSuffix() + "int pos, int kind, int state);").new_line();
        }
        if (stateData.hasNFA()) {
            writer.append("int jjMoveNfa" + stateData.getLexerStateSuffix() + "(int startState, int curPos);").new_line();
        }
    }

    private void DumpDfaCodeHeader(SourceWriter writer, NfaStateData data) {
        if (data.getMaxLen() == 0) {
            return;
        }
        int maxLongsReqd = (data.getMaxStrKind() / 64) + 1;
        int j;
        for (int i = 0; i < data.getMaxLen(); i++) {
            boolean atLeastOne = false;
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
                            params.append("unsigned long long active").append(j);
                        }
                    }

                    if (i <= data.getMaxLenForActive(j)) {
                        if (atLeastOne) {
                            params.append(", ");
                        }
                        params.append("unsigned long long active").append(j);
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
                            params.append("unsigned long long old").append(j).append(", ")
                                    .append("unsigned long long active").append(j);
                        }
                    }

                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        if (atLeastOne) {
                            params.append(", ");
                        }
                        params.append("unsigned long long old").append(j).append(", ")
                                .append("unsigned long long active").append(j);
                    }
                }
            }
            params.append(")");
            writer.append(" int  jjMoveStringLiteralDfa" + i + data.getLexerStateSuffix() + params + ";").new_line();
        }
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
            params.append("unsigned long long active").append(i).append(", ");
        }
        params.append("unsigned long long active").append(i).append(")");

        writer.append("\nint " + data.getParserName() + "TokenManager::jjStopStringLiteralDfa"
                + data.getLexerStateSuffix()
                + "(int pos, " + params);

        writer.append("{").new_line();

        if (data.global.options().getDebugTokenManager()) {
            writer.append("      fprintf(debugStream, \"   No more string literal token matches are possible.\");").new_line();
        }

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
            params.append("unsigned long long active").append(i).append(", ");
        }
        params.append("unsigned long long active").append(i).append(")");

        writer.append(
                "\nint " + data.getParserName() + "TokenManager::jjStartNfa" + data.getLexerStateSuffix()
                        + params);
        writer.append("{").new_line();

        if (data.isMixedState()) {
            if (data.generatedStates() != 0) {
                writer.append("   return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data)  + ", pos + 1);").new_line();
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
            writer.append("\n  int " + data.getParserName() + "TokenManager::jjMoveStringLiteralDfa0"
                    + data.getLexerStateSuffix() + "()");
            DumpNullStrLiterals(writer, data);
            return;
        }

        if (!data.global.boilerPlateDumped) {
            writer.append(
                    "\n int " + data.getParserName() + "TokenManager::jjStopAtPos(int pos, int kind)");
            writer.append("{").new_line();
            writer.append("   jjmatchedKind = kind;").new_line();
            writer.append("   jjmatchedPos = pos;").new_line();

            if (data.global.options().getDebugTokenManager()) {
                writer.append("   fprintf(debugStream, \"   No more string literal token matches are possible.\");").new_line();
                writer.append("   fprintf(debugStream, \"   Currently matched the first %d characters as a \\\"%s\\\" token.\\n\",  (jjmatchedPos + 1),  addUnicodeEscapes(tokenImage[jjmatchedKind]).c_str());").new_line();
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
                            params.append("unsigned long long active").append(j);
                        }
                    }

                    if (i <= data.getMaxLenForActive(j)) {
                        if (atLeastOne) {
                            params.append(", ");
                        }
                        params.append("unsigned long long active").append(j);
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
                            params.append("unsigned long long old").append(j).append(", ")
                                    .append("unsigned long long active").append(j);
                        }
                    }

                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        if (atLeastOne) {
                            params.append(", ");
                        }
                        params.append("unsigned long long old").append(j).append(", ")
                                .append("unsigned long long active").append(j);
                    }
                }
            }
            params.append(")");
            writer.append("\n int " + data.getParserName() + "TokenManager::jjMoveStringLiteralDfa" + i
                    + data.getLexerStateSuffix() + params);
            writer.append("{").new_line();

            if (i != 0) {
                if (i > 1) {
                    atLeastOne = false;
                    writer.append("   if ((");

                    for (j = 0; j < (maxLongsReqd - 1); j++) {
                        if (i <= (data.getMaxLenForActive(j) + 1)) {
                            if (atLeastOne)
                                writer.append(" | ");
                            else
                                atLeastOne = true;
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
                        writer.append("      return jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 2) + ", ");
                        for (j = 0; j < (maxLongsReqd - 1); j++) {
                            if (i <= (data.getMaxLenForActive(j) + 1))
                                writer.append("old" + j + ", ");
                            else
                                writer.append("0L, ");
                        }
                        if (i <= (data.getMaxLenForActive(j) + 1))
                            writer.append("old" + j + ");").new_line();
                        else
                            writer.append("0L);").new_line();
                    }
                    else if (data.generatedStates() != 0)
                        writer.append("      return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", " + (i - 1) + ");").new_line();
                    else
                        writer.append("      return " + i + ";").new_line();
                }

                if ((i != 0) && data.global.options().getDebugTokenManager()) {
                    writer.append("   if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")").new_line();
                    writer.append("      fprintf(debugStream, \"   Currently matched the first %d characters as a \\\"%s\\\" token.\\n\", (jjmatchedPos + 1), addUnicodeEscapes(tokenImage[jjmatchedKind]).c_str());").new_line();
                    writer.append("   fprintf(debugStream, \"   Possible string literal matches : { \");").new_line();

                    StringBuilder fmt = new StringBuilder();
                    StringBuilder args = new StringBuilder();
                    for (int vecs = 0; vecs < ((data.getMaxStrKind() / 64) + 1); vecs++) {
                        if (i <= data.getMaxLenForActive(vecs)) {
                            if (!fmt.isEmpty()) {
                                fmt.append(", ");
                                args.append(", ");
                            }

                            fmt.append("%s");
                            args.append("         jjKindsForBitVector(").append(vecs).append(", ");
                            args.append("active").append(vecs).append(").c_str() ");
                        }
                    }

                    fmt.append("}\\n");
                    writer.append("    fprintf(debugStream, \"" + fmt + "\"," + args + ");").new_line();
                }

                writer.append("   if (reader->endOfInput()) {").new_line();

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
                        writer.append("      fprintf(debugStream, \"   Currently matched the first %d characters as a \\\"%s\\\" token.\\n\", (jjmatchedPos + 1),  addUnicodeEscapes(tokenImage[jjmatchedKind]).c_str());").new_line();
                    }
                    writer.append("      return " + i + ";").new_line();
                }
                else if (data.generatedStates() != 0)
                    writer.append("   return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", " + (i - 1) + ");").new_line();
                else
                    writer.append("      return " + i + ";").new_line();

                writer.append("   }").new_line();
            }

            if ((i != 0))
                writer.append("   curChar = reader->readChar();").new_line();

            if ((i != 0) && data.global.options().getDebugTokenManager()) {
                writer.append("   fprintf(debugStreoptions.set, "
                                + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
                                + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
                                + "reader->getEndLine(), reader->getEndColumn());").new_line();
            }

            writer.append("   switch(curChar)").new_line();
            writer.append("   {").new_line();

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
                                writer.append("((active" + j + " & " + toHexString(1L << k) + ") != 0L)").new_line();
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

                            if (!data.isSubString((j * 64) + k)) {
                                int stateSetName = GetStateSetForKind(data, i, (j * 64) + k);

                                if (stateSetName != -1) {
                                    writer.append(
                                            prefix + "return jjStartNfaWithStates" + data.getLexerStateSuffix() + "(" + i
                                                    + ", " + kindToPrint + ", " + stateSetName + ");").new_line();
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

                                writer.append(toHexString(info.validKinds[j]));
                            }
                        }

                        if ((i + 1) <= data.getMaxLenForActive(j)) {
                            if (atLeastOne)
                                writer.append(", ");

                            writer.append(toHexString(info.validKinds[j]));
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
                                    writer.append("active" + j + ", " + toHexString(info.validKinds[j]));
                                else
                                    writer.append("active" + j + ", 0L");
                            }
                        }

                        if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
                            if (atLeastOne)
                                writer.append(", ");
                            if (info.validKinds[j] != 0L)
                                writer.append("active" + j + ", " + toHexString(info.validKinds[j]));
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
                writer.append("      fprintf(debugStream, \"   No string literal matches possible.\");").new_line();
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
        writer.append(
                "\nint " + data.getParserName() + "TokenManager::jjMoveNfa" + data.getLexerStateSuffix()
                        + "(int startState, int curPos)");
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
            writer.append("   reader->backup(seenUpto = curPos + 1);").new_line();
            writer.append("   assert(!reader->endOfInput());").new_line();
            writer.append("   curChar = reader->read(); // UTF8: Support Unicode").new_line();
            writer.append("   curPos = 0;").new_line();
        }

        writer.append("   int startsAt = 0;").new_line();
        writer.append("   jjnewStateCnt = " + data.generatedStates() + ";").new_line();
        writer.append("   int i = 1;").new_line();
        writer.append("   jjstateSet[0] = startState;").new_line();

        if (data.global.options().getDebugTokenManager()) {
            writer.append("      fprintf(debugStream, \"   Starting NFA to match one of : %s\\n\", jjKindsForStateVector(curLexState, jjstateSet, 0, 1).c_str());").new_line();
        }

        if (data.global.options().getDebugTokenManager()) {
            writer.append(
                    "   fprintf(debugStream, " + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
                            + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
                            + "reader->getEndLine(), reader->getEndColumn());").new_line();
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
            writer.append("   fprintf(debugStream, \"   Currently matched the first %d characters as a \\\"%s\\\" token.\\n\",  (jjmatchedPos + 1),  addUnicodeEscapes(tokenImage[jjmatchedKind]).c_str());").new_line();
        }

        writer.append("      if ((i = jjnewStateCnt), (jjnewStateCnt = startsAt), (i == (startsAt = " + data.generatedStates() + " - startsAt)))").new_line();
        if (data.isMixedState()) {
            writer.append("         break;").new_line();
        }
        else {
            writer.append("         return curPos;").new_line();
        }

        if (data.global.options().getDebugTokenManager()) {
            writer.append("      fprintf(debugStream, \"   Possible kinds of longer matches : %s\\n\", jjKindsForStateVector(curLexState, jjstateSet, startsAt, i).c_str());").new_line();
        }

        if (data.isMixedState())
            writer.append("      if (reader->endOfInput()) { break; }").new_line();
        else
            writer.append("      if (reader->endOfInput()) { return curPos; }").new_line();
        writer.append("      curChar = reader->read(); // UTF8: Support Unicode").new_line();

        if (data.global.options().getDebugTokenManager()) {
            writer.append(
                    "   fprintf(debugStream, " + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
                            + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
                            + "reader->getEndLine(), reader->getEndColumn());").new_line();
        }
        writer.append("   }").new_line();

        if (data.isMixedState()) {
            writer.append("   if (jjmatchedPos > strPos)").new_line();
            writer.append("      return curPos;").new_line();
            writer.new_line();
            writer.append("   int toRet = MAX(curPos, seenUpto);").new_line();
            writer.new_line();
            writer.append("   if (curPos < toRet)").new_line();
            writer.append("      for (i = toRet - MIN(curPos, seenUpto); i-- > 0; )").new_line();
            writer.append("        {  assert(!reader->endOfInput());").new_line();
            writer.append("           curChar = reader->read(); } // UTF8: Support Unicode").new_line();
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

    private static void getTextAsChars(String text, SourceWriter writer) {
        List<String> chars = new ArrayList<>();
        for (int j = 0; j < text.length(); j++) {
            chars.add("0x" + Integer.toHexString(text.charAt(j)));
        }
        writer.append(String.join(", ", chars));
    }

    private static void getRegExp(SourceWriter writer, boolean isImage, int i, List<RExpression> expressions) {
        if (i == 0)
            CppLexerGenerator.printCharArray(writer, "<EOF>");
        else {
            RExpression expr = expressions.get(i - 1);
            if (expr instanceof RStringLiteral) {
                CppLexerGenerator.printCharArray(writer, isImage
                    ? ((RStringLiteral) expr).getImage()
                    : "<" + expr.getLabel() + ">"
                );
            }
            else if (expr.getLabel().isEmpty()) {
                if (expr.getTpContext().getKind() == TokenProduction.Kind.TOKEN) {
                    JavaCCErrors.warning(expr,
                        "Consider giving this non-string token a label for better error reporting.");
                }
                CppLexerGenerator.printCharArray(writer, "<token of kind " + expr.getOrdinal() + ">");
            }
            else
                CppLexerGenerator.printCharArray(writer, "<" + expr.getLabel() + ">");
        }
    }

    // Used by the CPP code generatror
    private static void printCharArray(SourceWriter writer, String s) {
        for (int i = 0; i < s.length(); i++) {
            writer.append("0x" + Integer.toHexString(s.charAt(i)) + ", ");
        }
    }
}
