// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.generator.Lexer;
import org.hivevm.cc.generator.LexerData;
import org.hivevm.cc.generator.LexerGenerator;
import org.hivevm.cc.generator.NfaStateData;
import org.hivevm.cc.lexer.NfaState;
import org.hivevm.cc.parser.Action;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.RStringLiteral.KindInfo;
import org.hivevm.cc.parser.Token;
import org.hivevm.cc.utils.Encoding;
import org.hivevm.cc.utils.TemplateOptions;
import org.hivevm.cc.utils.TemplateProvider;

import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;

/**
 * Generate lexer.
 */
class CppLexerGenerator extends LexerGenerator {

  @Override
  public final void generate(LexerData data) {
    if (JavaCCErrors.hasError()) {
      return;
    }

    TemplateOptions options = new TemplateOptions();
    options.add(HiveCC.JJPARSER_CPP_DEFINE, data.getParserName().toUpperCase());
    options.add(Lexer.LOHI_BYTES, data.getLohiByte()).set("bytes", i -> getLohiBytes(data, i));
    options.add(Lexer.STATES, data.stateNames()).set("head", (i, w) -> dumpNfaAndDfaHeader(data.getStateData(i), w))
        .set("body", (i, w) -> dumpNfaAndDfa(data.getStateData(i), w));
    options.add(Lexer.NON_ASCII_TABLE, data.getNonAsciiTableForMethod()).set("offset", i -> i.nonAsciiMethod)
        .set("method", (k, w) -> DumpNonAsciiMoveMethod(data, k, w));

    options.setWriter("DumpStrLiteralImages", w -> DumpStrLiteralImages(w, data));
    options.setWriter("DumpStateSets", w -> DumpStateSets(w, data));
    options.setWriter("DumpStatesForStateCPP", w -> DumpStatesForStateCPP(w, data));
    options.setWriter("DumpStatesForKind", w -> DumpStatesForKind(w, data));
    options.setWriter("DumpStaticVarDeclarations", w -> DumpStaticVarDeclarations(w, data));
    options.setWriter("STATE_NAMES", w -> CppLexerGenerator.getStateNames(w, data));
    options.setWriter("DumpGetNextToken", w -> DumpGetNextToken(w, data));
    options.setWriter("DumpSkipActions", w -> DumpSkipActions(w, data));
    options.setWriter("DumpMoreActions", w -> DumpMoreActions(w, data));
    options.setWriter("DumpTokenActions", w -> DumpTokenActions(w, data));

    options.set("stateNames", data.stateNames());
    options.set("defaultLexState", data.defaultLexState());
    options.set("maxOrdinal", data.maxOrdinal());
    options.set("maxLexStates", data.maxLexStates());
    options.set("keepLineCol", data.keepLineCol());
    options.set("hasEmptyMatch", data.hasEmptyMatch());
    options.set("hasSkip", data.hasSkip());
    options.set("hasMore", data.hasMore());
    options.set("hasSpecial", data.hasSpecial());
    options.set("hasLoop", data.hasLoop());
    options.set("hasMoreActions", data.hasMoreActions());
    options.set("hasSkipActions", data.hasSkipActions());
    options.set("hasTokenActions", data.hasTokenActions());
    options.set("stateSetSize", data.stateSetSize());
    options.set("lexStateName", data.getStateName(data.getStateCount() - 1));
    options.set("hasActions", data.hasMoreActions() || data.hasSkipActions() || data.hasTokenActions());
    options.set("nonAsciiTableForMethod", data.getNonAsciiTableForMethod());
    options.set("jjCheckNAddStatesDualNeeded", data.jjCheckNAddStatesDualNeeded());
    options.set("jjCheckNAddStatesUnaryNeeded", data.jjCheckNAddStatesUnaryNeeded());

    TemplateProvider provider = CppTemplate.LEXER;
    provider.render(data.options(), options, data.getParserName());

    data.boilerPlateDumped = false;

    provider = CppTemplate.LEXER_H;
    provider.render(data.options(), options, data.getParserName());
  }

  @Override
  protected String toHexString(long value) {
    return "0x" + Long.toHexString(value) + "ULL";
  }

  private void DumpStaticVarDeclarations(PrintWriter writer, LexerData data) {
    if (data.maxLexStates() > 1) {
      writer.println();
      writer.println("/** Lex State array. */");
      writer.print("static const int jjnewLexState[] = {");

      for (int i = 0; i < data.maxOrdinal(); i++) {
        if ((i % 25) == 0) {
          writer.print("\n   ");
        }

        if (data.newLexState(i) == null) {
          writer.print("-1, ");
        } else {
          writer.print(data.getStateIndex(data.newLexState(i)) + ", ");
        }
      }
      writer.println("\n};");
    }

    if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
      // Bit vector for TOKEN
      writer.print("static const unsigned long long jjtoToken[] = {");
      for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
        if ((i % 4) == 0) {
          writer.print("\n   ");
        }
        writer.print(toHexString(data.toToken(i)) + ", ");
      }
      writer.println("\n};");
    }

    if (data.hasSkip() || data.hasSpecial()) {
      // Bit vector for SKIP
      writer.print("static const unsigned long long jjtoSkip[] = {");
      for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
        if ((i % 4) == 0) {
          writer.print("\n   ");
        }
        writer.print(toHexString(data.toSkip(i)) + ", ");
      }
      writer.println("\n};");
    }

    if (data.hasSpecial()) {
      // Bit vector for SPECIAL
      writer.print("static const unsigned long long jjtoSpecial[] = {");
      for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
        if ((i % 4) == 0) {
          writer.print("\n   ");
        }
        writer.print(toHexString(data.toSpecial(i)) + ", ");
      }
      writer.println("\n};");
    }

    if (data.hasMore()) {
      // Bit vector for MORE
      writer.print("static const unsigned long long jjtoMore[] = {");
      for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
        if ((i % 4) == 0) {
          writer.print("\n   ");
        }
        writer.print(toHexString(data.toMore(i)) + ", ");
      }
      writer.println("\n};");
    }
  }

  private void DumpGetNextToken(PrintWriter writer, LexerData data) {
    int i;

    if (data.options().getDebugTokenManager()) {
      writer.println("      fprintf(debugStream, \"Returning the <EOF> token.\\n\");");
    }

    writer.println("      jjmatchedKind = 0;");
    writer.println("      jjmatchedPos = -1;");
    writer.println("      matchedToken = jjFillToken();");

    if (data.hasSpecial()) {
      writer.println("      matchedToken->specialToken = specialToken;");
    }

    if ((data.getNextStateForEof() != null) || (data.getActionForEof() != null)) {
      writer.println("      TokenLexicalActions(matchedToken);");
    }

    writer.println("      return matchedToken;");
    writer.println("   }");
    writer.println("   curChar = reader->beginToken();");

    if (data.hasMoreActions() || data.hasSkipActions() || data.hasTokenActions()) {
      writer.println("   image = jjimage;");
      writer.println("   image.clear();");
      writer.println("   jjimageLen = 0;");
    }

    writer.println("");

    String prefix = "";
    if (data.hasMore()) {
      writer.println("   for (;;)");
      writer.println("   {");
      prefix = "  ";
    }

    String endSwitch = "";
    String caseStr = "";
    // this also sets up the start state of the nfa
    if (data.maxLexStates() > 1) {
      writer.println(prefix + "   switch(curLexState)");
      writer.println(prefix + "   {");
      endSwitch = prefix + "   }";
      caseStr = prefix + "     case ";
      prefix += "    ";
    }

    prefix += "   ";
    for (i = 0; i < data.maxLexStates(); i++) {
      if (data.maxLexStates() > 1) {
        writer.println(caseStr + i + ":");
      }

      if (data.singlesToSkip(i).HasTransitions()) {
        // added the backup(0) to make JIT happy
        writer.println(prefix + "{ reader->backup(0);");
        if ((data.singlesToSkip(i).asciiMoves[0] != 0L) && (data.singlesToSkip(i).asciiMoves[1] != 0L)) {
          writer.println(
              prefix + "   while ((curChar < 64" + " && (" + Long.toHexString(data.singlesToSkip(i).asciiMoves[0])
                  + " & (1L << curChar)) != 0L) || \n" + prefix + "          (curChar >> 6) == 1" + " && ("
                  + toHexString(data.singlesToSkip(i).asciiMoves[1]) + " & (1L << (curChar & 077))) != 0L)");
        } else if (data.singlesToSkip(i).asciiMoves[1] == 0L) {
          writer.println(
              prefix + "   while (curChar <= " + (int) LexerGenerator.MaxChar(data.singlesToSkip(i).asciiMoves[0])
                  + " && (" + toHexString(data.singlesToSkip(i).asciiMoves[0]) + " & (1L << curChar)) != 0L)");
        } else if (data.singlesToSkip(i).asciiMoves[0] == 0L) {
          writer.println(prefix + "   while (curChar > 63 && curChar <= "
              + (LexerGenerator.MaxChar(data.singlesToSkip(i).asciiMoves[1]) + 64) + " && ("
              + toHexString(data.singlesToSkip(i).asciiMoves[1]) + " & (1L << (curChar & 077))) != 0L)");
        }

        writer.println(prefix + "{");
        if (data.options().getDebugTokenManager()) {
          if (data.maxLexStates() > 1) {
            writer.println(
                "      fprintf(debugStream, \"<%s>\" , addUnicodeEscapes(lexStateNames[curLexState]).c_str());");
          }

          writer.println("      fprintf(debugStream, \"Skipping character : %c(%d)\\n\", curChar, (int)curChar);");
        }

        writer.println(prefix + "if (reader->endOfInput()) { goto EOFLoop; }");
        writer.println(prefix + "curChar = reader->beginToken();");
        writer.println(prefix + "}");
        writer.println(prefix + "}");
      }

      if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
        if (data.options().getDebugTokenManager()) {
          writer.println(
              "      fprintf(debugStream, \"   Matched the empty string as %s token.\\n\", addUnicodeEscapes(tokenImage["
                  + data.initMatch(i) + "]).c_str());");
        }

        writer.println(prefix + "jjmatchedKind = " + data.initMatch(i) + ";");
        writer.println(prefix + "jjmatchedPos = -1;");
        writer.println(prefix + "curPos = 0;");
      } else {
        writer.println(prefix + "jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
        writer.println(prefix + "jjmatchedPos = 0;");
      }

      if (data.options().getDebugTokenManager()) {
        writer.println("   fprintf(debugStream, " + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
            + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
            + "reader->getEndLine(), reader->getEndColumn());");
      }

      writer.println(prefix + "curPos = jjMoveStringLiteralDfa0_" + i + "();");
      if (data.canMatchAnyChar(i) != -1) {
        if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
          writer.println(prefix + "if (jjmatchedPos < 0 || (jjmatchedPos == 0 && jjmatchedKind > "
              + data.canMatchAnyChar(i) + "))");
        } else {
          writer.println(prefix + "if (jjmatchedPos == 0 && jjmatchedKind > " + data.canMatchAnyChar(i) + ")");
        }
        writer.println(prefix + "{");

        if (data.options().getDebugTokenManager()) {
          writer.println(
              "           fprintf(debugStream, \"   Current character matched as a %s token.\\n\", addUnicodeEscapes(tokenImage["
                  + data.canMatchAnyChar(i) + "]).c_str());");
        }
        writer.println(prefix + "   jjmatchedKind = " + data.canMatchAnyChar(i) + ";");

        if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
          writer.println(prefix + "   jjmatchedPos = 0;");
        }

        writer.println(prefix + "}");
      }

      if (data.maxLexStates() > 1) {
        writer.println(prefix + "break;");
      }
    }

    if (data.maxLexStates() > 1) {
      writer.println(endSwitch);
    } else if (data.maxLexStates() == 0) {
      writer.println("       jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
    }

    if (data.maxLexStates() > 1) {
      prefix = "  ";
    } else {
      prefix = "";
    }

    if (data.maxLexStates() > 0) {
      writer.println(prefix + "   if (jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
      writer.println(prefix + "   {");
      writer.println(prefix + "      if (jjmatchedPos + 1 < curPos)");

      if (data.options().getDebugTokenManager()) {
        writer.println(prefix + "      {");
        writer.println(prefix + "         fprintf(debugStream, "
            + "\"   Putting back %d characters into the input stream.\\n\", (curPos - jjmatchedPos - 1));");
      }

      writer.println(prefix + "         reader->backup(curPos - jjmatchedPos - 1);");

      if (data.options().getDebugTokenManager()) {
        writer.println(prefix + "      }");
      }

      if (data.options().getDebugTokenManager()) {
        writer.println("    fprintf(debugStream, "
            + "\"****** FOUND A %d(%s) MATCH (%s) ******\\n\", jjmatchedKind, addUnicodeEscapes(tokenImage[jjmatchedKind]).c_str(), addUnicodeEscapes(reader->GetSuffix(jjmatchedPos + 1)).c_str());");
      }

      if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
        writer.println(
            prefix + "      if ((jjtoToken[jjmatchedKind >> 6] & " + "(1ULL << (jjmatchedKind & 077))) != 0L)");
        writer.println(prefix + "      {");
      }

      writer.println(prefix + "         matchedToken = jjFillToken();");

      if (data.hasSpecial()) {
        writer.println(prefix + "         matchedToken->specialToken = specialToken;");
      }

      if (data.hasTokenActions()) {
        writer.println(prefix + "         TokenLexicalActions(matchedToken);");
      }

      if (data.maxLexStates() > 1) {
        writer.println("       if (jjnewLexState[jjmatchedKind] != -1)");
        writer.println(prefix + "       curLexState = jjnewLexState[jjmatchedKind];");
      }

      writer.println(prefix + "         return matchedToken;");

      if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
        writer.println(prefix + "      }");

        if (data.hasSkip() || data.hasSpecial()) {
          if (data.hasMore()) {
            writer.println(
                prefix + "      else if ((jjtoSkip[jjmatchedKind >> 6] & " + "(1L << (jjmatchedKind & 077))) != 0L)");
          } else {
            writer.println(prefix + "      else");
          }

          writer.println(prefix + "      {");

          if (data.hasSpecial()) {
            writer.println(prefix + "         if ((jjtoSpecial[jjmatchedKind >> 6] & "
                + "(1ULL << (jjmatchedKind & 077))) != 0L)");
            writer.println(prefix + "         {");

            writer.println(prefix + "            matchedToken = jjFillToken();");

            writer.println(prefix + "            if (specialToken == nullptr)");
            writer.println(prefix + "               specialToken = matchedToken;");
            writer.println(prefix + "            else");
            writer.println(prefix + "            {");
            writer.println(prefix + "               matchedToken->specialToken = specialToken;");
            writer.println(prefix + "               specialToken = (specialToken->next = matchedToken);");
            writer.println(prefix + "            }");

            if (data.hasSkipActions()) {
              writer.println(prefix + "            SkipLexicalActions(matchedToken);");
            }

            writer.println(prefix + "         }");

            if (data.hasSkipActions()) {
              writer.println(prefix + "         else");
              writer.println(prefix + "            SkipLexicalActions(nullptr);");
            }
          } else if (data.hasSkipActions()) {
            writer.println(prefix + "         SkipLexicalActions(nullptr);");
          }

          if (data.maxLexStates() > 1) {
            writer.println("         if (jjnewLexState[jjmatchedKind] != -1)");
            writer.println(prefix + "         curLexState = jjnewLexState[jjmatchedKind];");
          }

          writer.println(prefix + "         goto EOFLoop;");
          writer.println(prefix + "      }");
        }

        if (data.hasMore()) {
          if (data.hasMoreActions()) {
            writer.println(prefix + "      MoreLexicalActions();");
          } else if (data.hasSkipActions() || data.hasTokenActions()) {
            writer.println(prefix + "      jjimageLen += jjmatchedPos + 1;");
          }

          if (data.maxLexStates() > 1) {
            writer.println("      if (jjnewLexState[jjmatchedKind] != -1)");
            writer.println(prefix + "      curLexState = jjnewLexState[jjmatchedKind];");
          }
          writer.println(prefix + "      curPos = 0;");
          writer.println(prefix + "      jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");

          writer.println(prefix + "   if (!reader->endOfInput()) {");
          writer.println(prefix + "         curChar = reader->read(); // TOL: Support Unicode");

          if (data.options().getDebugTokenManager()) {
            writer.println("   fprintf(debugStream, " + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
                + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
                + "reader->getEndLine(), reader->getEndColumn());");
          }
          writer.println(prefix + "   continue;");
          writer.println(prefix + " }");
        }
      }
    }
  }

  private void DumpSkipActions(PrintWriter writer, LexerData data) {
    writer.print("\nvoid " + data.getParserName() + "TokenManager::SkipLexicalActions(Token *matchedToken)");

    writer.println("{");
    writer.println("   switch(jjmatchedKind)");
    writer.println("   {");

    Outer:
    for (int i = 0; i < data.maxOrdinal(); i++) {
      if ((data.toSkip(i / 64) & (1L << (i % 64))) == 0L) {
        continue;
      }

      for (;;) {
        Action act = data.actions(i);
        if (((act == null) || act.getActionTokens().isEmpty()) && !data.canLoop(data.getState(i))) {
          continue Outer;
        }

        writer.println("      case " + i + " : {");

        if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
          writer.println("         if (jjmatchedPos == -1)");
          writer.println("         {");
          writer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
          writer.println("                jjemptyLineNo[" + data.getState(i) + "] == reader->getBeginLine() &&");
          writer.println("                jjemptyColNo[" + data.getState(i) + "] == reader->getBeginColumn())");
          writer.println(
              "               errorHandler->lexicalError(JJString(\"(\"Error: Bailing out of infinite loop caused by repeated empty string matches \" + \"at line \" + reader->getBeginLine() + \", \" + \"column \" + reader->getBeginColumn() + \".\")), this);");
          writer.println("            jjemptyLineNo[" + data.getState(i) + "] = reader->getBeginLine();");
          writer.println("            jjemptyColNo[" + data.getState(i) + "] = reader->getBeginColumn();");
          writer.println("            jjbeenHere[" + data.getState(i) + "] = true;");
          writer.println("         }");
        }

        if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
          break;
        }

        writer.print("         image.append");
        if (data.getImage(i) != null) {
          writer.println("(jjstrLiteralImages[" + i + "]);");
          writer.println("        lengthOfMatch = jjstrLiteralImages[" + i + "].length();");
        } else {
          writer.println("(reader->GetSuffix(jjimageLen + (lengthOfMatch = jjmatchedPos + 1)));");
        }

        genTokenSetup(act.getActionTokens().get(0));
        resetColumn();

        for (Token element : act.getActionTokens()) {
          genToken(writer, element);
        }
        writer.println("");

        break;
      }

      writer.println("         break;");
      writer.println("       }");
    }

    writer.println("      default :");
    writer.println("         break;");
    writer.println("   }");
    writer.println("}");
  }

  private void DumpMoreActions(PrintWriter writer, LexerData data) {
    writer.print("\nvoid " + data.getParserName() + "TokenManager::MoreLexicalActions()");
    writer.println("{");
    writer.println("   jjimageLen += (lengthOfMatch = jjmatchedPos + 1);");
    writer.println("   switch(jjmatchedKind)");
    writer.println("   {");

    Outer:
    for (int i = 0; i < data.maxOrdinal(); i++) {
      if ((data.toMore(i / 64) & (1L << (i % 64))) == 0L) {
        continue;
      }

      for (;;) {
        Action act = data.actions(i);
        if (((act == null) || act.getActionTokens().isEmpty()) && !data.canLoop(data.getState(i))) {
          continue Outer;
        }

        writer.println("      case " + i + " : {");

        if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
          writer.println("         if (jjmatchedPos == -1)");
          writer.println("         {");
          writer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
          writer.println("                jjemptyLineNo[" + data.getState(i) + "] == reader->getBeginLine() &&");
          writer.println("                jjemptyColNo[" + data.getState(i) + "] == reader->getBeginColumn())");
          writer.println(
              "               errorHandler->lexicalError(JJString(\"(\"Error: Bailing out of infinite loop caused by repeated empty string matches \" + \"at line \" + reader->getBeginLine() + \", \" + \"column \" + reader->getBeginColumn() + \".\")), this);");
          writer.println("            jjemptyLineNo[" + data.getState(i) + "] = reader->getBeginLine();");
          writer.println("            jjemptyColNo[" + data.getState(i) + "] = reader->getBeginColumn();");
          writer.println("            jjbeenHere[" + data.getState(i) + "] = true;");
          writer.println("         }");
        }

        if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
          break;
        }

        writer.print("         image.append");

        if (data.getImage(i) != null) {
          writer.println("(jjstrLiteralImages[" + i + "]);");
        } else {
          writer.println("(reader->GetSuffix(jjimageLen));");
        }

        writer.println("         jjimageLen = 0;");
        genTokenSetup(act.getActionTokens().get(0));
        resetColumn();

        for (Token element : act.getActionTokens()) {
          genToken(writer, element);
        }
        writer.println("");

        break;
      }

      writer.println("         break;");
      writer.println("       }");
    }

    writer.println("      default :");
    writer.println("         break;");

    writer.println("   }");
    writer.println("}");
  }

  private void DumpTokenActions(PrintWriter writer, LexerData data) {
    Action act;
    int i;
    writer.print("\nvoid " + data.getParserName() + "TokenManager::TokenLexicalActions(Token *matchedToken)");
    writer.println("{");
    writer.println("   switch(jjmatchedKind)");
    writer.println("   {");

    Outer:
    for (i = 0; i < data.maxOrdinal(); i++) {
      if ((data.toToken(i / 64) & (1L << (i % 64))) == 0L) {
        continue;
      }

      for (;;) {
        if ((((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) && !data.canLoop(data.getState(i))) {
          continue Outer;
        }

        writer.println("      case " + i + " : {");

        if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
          writer.println("         if (jjmatchedPos == -1)");
          writer.println("         {");
          writer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
          writer.println("                jjemptyLineNo[" + data.getState(i) + "] == reader->getBeginLine() &&");
          writer.println("                jjemptyColNo[" + data.getState(i) + "] == reader->getBeginColumn())");
          writer.println(
              "               errorHandler->lexicalError(JJString(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                  + "at line \" + reader->getBeginLine() + \", "
                  + "column \" + reader->getBeginColumn() + \".\"), this);");
          writer.println("            jjemptyLineNo[" + data.getState(i) + "] = reader->getBeginLine();");
          writer.println("            jjemptyColNo[" + data.getState(i) + "] = reader->getBeginColumn();");
          writer.println("            jjbeenHere[" + data.getState(i) + "] = true;");
          writer.println("         }");
        }

        if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
          break;
        }

        if (i == 0) {
          writer.println("      image.setLength(0);"); // For EOF no image is there
        } else {
          writer.print("        image.append");

          if (data.getImage(i) != null) {
            writer.println("(jjstrLiteralImages[" + i + "]);");
            writer.println("        lengthOfMatch = jjstrLiteralImages[" + i + "].length();");
          } else {
            writer.println("(reader->GetSuffix(jjimageLen + (lengthOfMatch = jjmatchedPos + 1)));");
          }
        }

        genTokenSetup(act.getActionTokens().get(0));
        resetColumn();

        for (Token element : act.getActionTokens()) {
          genToken(writer, element);
        }
        writer.println("");

        break;
      }

      writer.println("         break;");
      writer.println("       }");
    }

    writer.println("      default :");
    writer.println("         break;");
    writer.println("   }");
    writer.println("}");
  }

  private void DumpStatesForKind(PrintWriter writer, LexerData data) {
    boolean moreThanOne = false;
    int cnt = 0;

    writer.print("static const int kindForState[" + data.stateSetSize() + "][" + data.stateSetSize() + "] = ");

    if (data.getKinds() == null) {
      writer.println("null;");
      return;
    } else {
      writer.println("{");
    }

    for (int[] kind : data.getKinds()) {
      if (moreThanOne) {
        writer.println(",");
      }
      moreThanOne = true;

      if (kind == null) {
        writer.println("{}");
      } else {
        cnt = 0;
        writer.print("{ ");
        for (int element : kind) {
          if ((cnt % 15) == 0) {
            writer.print("\n  ");
          } else if (cnt > 1) {
            writer.print(" ");
          }

          writer.print(element);
          writer.print(", ");

        }

        writer.print("}");
      }
    }
    writer.println("\n};");
  }

  private void DumpStatesForStateCPP(PrintWriter writer, LexerData data) {
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

        writer.print("const int stateSet_" + i + "_" + j + "[" + data.stateSetSize() + "] = ");
        if (stateSet == null) {
          writer.println("   { " + j + " };");
          continue;
        }

        writer.print("   { ");

        for (int element : stateSet) {
          writer.print(element + ", ");
        }

        writer.println("};");
      }
    }

    for (int i = 0; i < data.maxLexStates(); i++) {
      writer.println("const int *stateSet_" + i + "[] = {");
      if (data.getStatesForState()[i] == null) {
        writer.println(" NULL, ");
        writer.println("};");
        continue;
      }

      for (int j = 0; j < data.getStatesForState()[i].length; j++) {
        writer.print("stateSet_" + i + "_" + j + ",");
      }
      writer.println("};");
    }

    writer.print("const int** statesForState[] = { ");
    for (int i = 0; i < data.maxLexStates(); i++) {
      writer.println("stateSet_" + i + ", ");
    }

    writer.println("\n};");
  }

  private void DumpStateSets(PrintWriter writer, LexerData data) {
    int cnt = 0;

    writer.print("static const int jjnextStates[] = {");
    if (data.getOrderedStateSet().size() > 0) {
      for (int[] set : data.getOrderedStateSet()) {
        for (int element : set) {
          if ((cnt++ % 16) == 0) {
            writer.print("\n   ");
          }

          writer.print(element + ", ");
        }
      }
    } else {
      writer.print("0");
    }

    writer.println("\n};");
  }

  private void DumpStrLiteralImages(PrintWriter writer, LexerData data) {
    // For C++
    String image;
    int i;
    int charCnt = 0; // Set to zero in reInit() but just to be sure

    int literalCount = 0;

    if (data.getImageCount() <= 0) {
      writer.println("static const JJString jjstrLiteralImages[] = {};");
      return;
    }

    data.setImage(0, "");
    for (i = 0; i < data.getImageCount(); i++) {
      if (((image = data.getImage(i)) == null)
          || (((data.toSkip(i / 64) & (1L << (i % 64))) == 0L) && ((data.toMore(i / 64) & (1L << (i % 64))) == 0L)
              && ((data.toToken(i / 64) & (1L << (i % 64))) == 0L))
          || ((data.toSkip(i / 64) & (1L << (i % 64))) != 0L) || ((data.toMore(i / 64) & (1L << (i % 64))) != 0L)
          || data.canReachOnMore(data.getState(i))
          || ((data.ignoreCase() || data.ignoreCase(i)) && (!image.equals(image.toLowerCase(Locale.ENGLISH))
              || !image.equals(image.toUpperCase(Locale.ENGLISH))))) {
        data.setImage(i, null);
        if ((charCnt += 6) > 80) {
          writer.println("");
          charCnt = 0;
        }

        writer.println("static JJChar jjstrLiteralChars_" + literalCount++ + "[] = {0};");
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
        writer.println("");
        charCnt = 0;
      }

      writer.println(toPrint);
    }

    while (++i < data.maxOrdinal()) {
      if ((charCnt += 6) > 80) {
        writer.println("");
        charCnt = 0;
      }

      writer.println("static JJChar jjstrLiteralChars_" + literalCount++ + "[] = {0};");
      continue;
    }

    // Generate the array here.
    writer.println("static const JJString " + "jjstrLiteralImages[] = {");
    for (int j = 0; j < literalCount; j++) {
      writer.println("jjstrLiteralChars_" + j + ", ");
    }
    writer.println("};");
  }

  private void DumpStartWithStates(PrintWriter writer, NfaStateData data) {
    writer.print("\nint " + data.getParserName() + "TokenManager::jjStartNfaWithStates" + data.getLexerStateSuffix()
        + "(int pos, int kind, int state)");

    writer.println("{");
    writer.println("   jjmatchedKind = kind;");
    writer.println("   jjmatchedPos = pos;");

    if (data.global.options().getDebugTokenManager()) {
      writer.println("   fprintf(debugStream, \"   No more string literal token matches are possible.\");");
      writer.println(
          "   fprintf(debugStream, \"   Currently matched the first %d characters as a \\\"%s\\\" token.\\n\",  (jjmatchedPos + 1),  addUnicodeEscapes(tokenImage[jjmatchedKind]).c_str());");
    }

    writer.println("   if (reader->endOfInput()) { return pos + 1; }");
    writer.println("   curChar = reader->read(); // TOL: Support Unicode");

    if (data.global.options().getDebugTokenManager()) {
      writer.println("   fprintf(debugStream, " + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
          + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
          + "reader->getEndLine(), reader->getEndColumn());");
    }
    writer.println("   return jjMoveNfa" + data.getLexerStateSuffix() + "(state, pos + 1);");
    writer.println("}");
  }

  @Override
  protected final void DumpHeadForCase(PrintWriter writer, int byteNum) {
    if (byteNum == 0) {
      writer.println("         unsigned long long l = 1ULL << curChar;");
      writer.println("         (void)l;");
    } else if (byteNum == 1) {
      writer.println("         unsigned long long l = 1ULL << (curChar & 077);");
      writer.println("         (void)l;");
    } else {
      writer.println("         int hiByte = (curChar >> 8);");
      writer.println("         int i1 = hiByte >> 6;");
      writer.println("         unsigned long long l1 = 1ULL << (hiByte & 077);");
      writer.println("         int i2 = (curChar & 0xff) >> 6;");
      writer.println("         unsigned long long l2 = 1ULL << (curChar & 077);");
    }

    // writer.println(" MatchLoop: do");
    writer.println("         do");
    writer.println("         {");

    writer.println("            switch(jjstateSet[--i])");
    writer.println("            {");
  }

  private final void DumpNonAsciiMoveMethod(LexerData data, NfaState state, PrintWriter writer) {
    writer.print("\nbool " + data.getParserName() + "TokenManager::jjCanMove_" + +state.nonAsciiMethod
        + "(int hiByte, int i1, int i2, unsigned long long l1, unsigned long long l2)");


    writer.println("{");
    writer.println("   switch(hiByte)");
    writer.println("   {");

    for (int j = 0; j < state.loByteVec.size(); j += 2) {
      writer.println("      case " + state.loByteVec.get(j).intValue() + ":");
      if (!NfaState.AllBitsSet(data.getAllBitVectors(state.loByteVec.get(j + 1).intValue()))) {
        writer
            .println("         return ((jjbitVec" + state.loByteVec.get(j + 1).intValue() + "[i2" + "] & l2) != 0L);");
      } else {
        writer.println("            return true;");
      }
    }

    writer.println("      default :");

    for (int j = state.nonAsciiMoveIndices.length; j > 0; j -= 2) {
      if (!NfaState.AllBitsSet(data.getAllBitVectors(state.nonAsciiMoveIndices[j - 2]))) {
        writer.println("         if ((jjbitVec" + state.nonAsciiMoveIndices[j - 2] + "[i1] & l1) != 0L)");
      }
      if (!NfaState.AllBitsSet(data.getAllBitVectors(state.nonAsciiMoveIndices[j - 1]))) {
        writer.println("            if ((jjbitVec" + state.nonAsciiMoveIndices[j - 1] + "[i2] & l2) == 0L)");
        writer.println("               return false;");
        writer.println("            else");
      }
      writer.println("            return true;");
    }

    writer.println("         return false;");
    writer.println("   }");
    writer.println("}");
  }

  private final void dumpNfaAndDfaHeader(NfaStateData stateData, PrintWriter writer) {
    if (stateData.hasNFA() && !stateData.isMixedState() && (stateData.getMaxStrKind() > 0)) {
      int i, maxKindsReqd = (stateData.getMaxStrKind() / 64) + 1;
      StringBuilder params = new StringBuilder();
      for (i = 0; i < (maxKindsReqd - 1); i++) {
        params.append("unsigned long long active" + i + ", ");
      }
      params.append("unsigned long long active" + i + ")");
      writer.println("int jjStopStringLiteralDfa" + stateData.getLexerStateSuffix() + "(int pos, " + params + ";");

      params.setLength(0);
      params.append("(int pos, ");
      for (i = 0; i < (maxKindsReqd - 1); i++) {
        params.append("unsigned long long active" + i + ", ");
      }
      params.append("unsigned long long active" + i + ")");
      writer.println("int jjStartNfa" + stateData.getLexerStateSuffix() + params + ";");
    }

    if (stateData.getMaxLen() == 0) {
      writer.println(" int  jjMoveStringLiteralDfa0" + stateData.getLexerStateSuffix() + "();");
    } else if (!stateData.global.boilerPlateDumped) {
      writer.println(" int " + " jjStopAtPos(int pos, int kind);");
      stateData.global.boilerPlateDumped = true;
    }

    DumpDfaCodeHeader(writer, stateData);
    if (!stateData.isMixedState() && (stateData.generatedStates() != 0) && stateData.getCreateStartNfa()) {
      writer.println("int jjStartNfaWithStates" + stateData.getLexerStateSuffix() + "int pos, int kind, int state);");
    }
    if (stateData.hasNFA()) {
      writer.println("int jjMoveNfa" + stateData.getLexerStateSuffix() + "(int startState, int curPos);");
    }
  }

  private final void DumpDfaCodeHeader(PrintWriter writer, NfaStateData data) {
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
              } else {
                atLeastOne = true;
              }
              params.append("unsigned long long active" + j);
            }
          }

          if (i <= data.getMaxLenForActive(j)) {
            if (atLeastOne) {
              params.append(", ");
            }
            params.append("unsigned long long active" + j);
          }
        } else {
          for (j = 0; j < (maxLongsReqd - 1); j++) {
            if (i <= (data.getMaxLenForActive(j) + 1)) {
              if (atLeastOne) {
                params.append(", ");
              } else {
                atLeastOne = true;
              }
              params.append("unsigned long long old" + j + ", " + "unsigned long long active" + j);
            }
          }

          if (i <= (data.getMaxLenForActive(j) + 1)) {
            if (atLeastOne) {
              params.append(", ");
            }
            params.append("unsigned long long old" + j + ", " + "unsigned long long active" + j);
          }
        }
      }
      params.append(")");
      writer.println(" int  jjMoveStringLiteralDfa" + i + data.getLexerStateSuffix() + params + ";");
    }
  }

  protected final void dumpNfaStartStatesCode(PrintWriter writer, NfaStateData data,
      Hashtable<String, long[]>[] statesForPos) {
    if (data.getMaxStrKind() == 0) { // No need to generate this function
      return;
    }

    int i, maxKindsReqd = (data.getMaxStrKind() / 64) + 1;
    boolean condGenerated = false;
    int ind = 0;

    StringBuilder params = new StringBuilder();
    for (i = 0; i < (maxKindsReqd - 1); i++) {
      params.append("unsigned long long active" + i + ", ");
    }
    params.append("unsigned long long active" + i + ")");

    writer.print("\nint " + data.getParserName() + "TokenManager::jjStopStringLiteralDfa" + data.getLexerStateSuffix()
        + "(int pos, " + params);

    writer.println("{");

    if (data.global.options().getDebugTokenManager()) {
      writer.println("      fprintf(debugStream, \"   No more string literal token matches are possible.\");");
    }

    writer.println("   switch (pos)");
    writer.println("   {");

    for (i = 0; i < (data.getMaxLen() - 1); i++) {
      if (statesForPos[i] == null) {
        continue;
      }

      writer.println("      case " + i + ":");

      Enumeration<String> e = statesForPos[i].keys();
      while (e.hasMoreElements()) {
        String stateSetString = e.nextElement();
        long[] actives = statesForPos[i].get(stateSetString);

        for (int j = 0; j < maxKindsReqd; j++) {
          if (actives[j] == 0L) {
            continue;
          }

          if (condGenerated) {
            writer.print(" || ");
          } else {
            writer.print("         if (");
          }

          condGenerated = true;

          writer.print("(active" + j + " & " + toHexString(actives[j]) + ") != 0L");
        }

        if (condGenerated) {
          writer.println(")");

          String kindStr = stateSetString.substring(0, ind = stateSetString.indexOf(", "));
          String afterKind = stateSetString.substring(ind + 2);
          int jjmatchedPos = Integer.parseInt(afterKind.substring(0, afterKind.indexOf(", ")));

          if (!kindStr.equals(String.valueOf(Integer.MAX_VALUE))) {
            writer.println("         {");
          }

          if (!kindStr.equals(String.valueOf(Integer.MAX_VALUE))) {
            if (i == 0) {
              writer.println("            jjmatchedKind = " + kindStr + ";");

              if (((data.global.initMatch(data.getStateIndex()) != 0)
                  && (data.global.initMatch(data.getStateIndex()) != Integer.MAX_VALUE))) {
                writer.println("            jjmatchedPos = 0;");
              }
            } else if (i == jjmatchedPos) {
              if (data.isSubStringAtPos(i)) {
                writer.println("            if (jjmatchedPos != " + i + ")");
                writer.println("            {");
                writer.println("               jjmatchedKind = " + kindStr + ";");
                writer.println("               jjmatchedPos = " + i + ";");
                writer.println("            }");
              } else {
                writer.println("            jjmatchedKind = " + kindStr + ";");
                writer.println("            jjmatchedPos = " + i + ";");
              }
            } else {
              if (jjmatchedPos > 0) {
                writer.println("            if (jjmatchedPos < " + jjmatchedPos + ")");
              } else {
                writer.println("            if (jjmatchedPos == 0)");
              }
              writer.println("            {");
              writer.println("               jjmatchedKind = " + kindStr + ";");
              writer.println("               jjmatchedPos = " + jjmatchedPos + ";");
              writer.println("            }");
            }
          }

          kindStr = stateSetString.substring(0, ind = stateSetString.indexOf(", "));
          afterKind = stateSetString.substring(ind + 2);
          stateSetString = afterKind.substring(afterKind.indexOf(", ") + 2);

          if (stateSetString.equals("null;")) {
            writer.println("            return -1;");
          } else {
            writer.println("            return " + getCompositeStateSet(data, stateSetString, true) + ";");
          }

          if (!kindStr.equals(String.valueOf(Integer.MAX_VALUE))) {
            writer.println("         }");
          }
          condGenerated = false;
        }
      }

      writer.println("         return -1;");
    }

    writer.println("      default :");
    writer.println("         return -1;");
    writer.println("   }");
    writer.println("}");

    params.setLength(0);
    params.append("(int pos, ");
    for (i = 0; i < (maxKindsReqd - 1); i++) {
      params.append("unsigned long long active" + i + ", ");
    }
    params.append("unsigned long long active" + i + ")");

    writer.print("\nint " + data.getParserName() + "TokenManager::jjStartNfa" + data.getLexerStateSuffix() + params);
    writer.println("{");

    if (data.isMixedState()) {
      if (data.generatedStates() != 0) {
        writer.println("   return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", pos + 1);");
      } else {
        writer.println("   return pos + 1;");
      }

      writer.println("}");
      return;
    }

    writer.print("   return jjMoveNfa" + data.getLexerStateSuffix() + "(" + "jjStopStringLiteralDfa"
        + data.getLexerStateSuffix() + "(pos, ");
    for (i = 0; i < (maxKindsReqd - 1); i++) {
      writer.print("active" + i + ", ");
    }
    writer.print("active" + i + ")");
    writer.println(", pos + 1);");
    writer.println("}");
  }

  protected final void dumpDfaCode(PrintWriter writer, NfaStateData data) {
    Hashtable<String, ?> tab;
    String key;
    KindInfo info;
    int maxLongsReqd = (data.getMaxStrKind() / 64) + 1;
    int i, j, k;
    boolean ifGenerated;

    if (data.getMaxLen() == 0) {
      writer.print("\n  int " + data.getParserName() + "TokenManager::jjMoveStringLiteralDfa0"
          + data.getLexerStateSuffix() + "()");
      DumpNullStrLiterals(writer, data);
      return;
    }

    if (!data.global.boilerPlateDumped) {
      writer.print("\n int " + data.getParserName() + "TokenManager::jjStopAtPos(int pos, int kind)");
      writer.println("{");
      writer.println("   jjmatchedKind = kind;");
      writer.println("   jjmatchedPos = pos;");

      if (data.global.options().getDebugTokenManager()) {
        writer.println("   fprintf(debugStream, \"   No more string literal token matches are possible.\");");
        writer.println(
            "   fprintf(debugStream, \"   Currently matched the first %d characters as a \\\"%s\\\" token.\\n\",  (jjmatchedPos + 1),  addUnicodeEscapes(tokenImage[jjmatchedKind]).c_str());");
      }

      writer.println("   return pos + 1;");
      writer.println("}");
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
              } else {
                atLeastOne = true;
              }
              params.append("unsigned long long active" + j);
            }
          }

          if (i <= data.getMaxLenForActive(j)) {
            if (atLeastOne) {
              params.append(", ");
            }
            params.append("unsigned long long active" + j);
          }
        } else {
          for (j = 0; j < (maxLongsReqd - 1); j++) {
            if (i <= (data.getMaxLenForActive(j) + 1)) {
              if (atLeastOne) {
                params.append(", ");
              } else {
                atLeastOne = true;
              }
              params.append("unsigned long long old" + j + ", " + "unsigned long long active" + j);
            }
          }

          if (i <= (data.getMaxLenForActive(j) + 1)) {
            if (atLeastOne) {
              params.append(", ");
            }
            params.append("unsigned long long old" + j + ", " + "unsigned long long active" + j);
          }
        }
      }
      params.append(")");
      writer.print("\n int " + data.getParserName() + "TokenManager::jjMoveStringLiteralDfa" + i
          + data.getLexerStateSuffix() + params);
      writer.println("{");

      if (i != 0) {
        if (i > 1) {
          atLeastOne = false;
          writer.print("   if ((");

          for (j = 0; j < (maxLongsReqd - 1); j++) {
            if (i <= (data.getMaxLenForActive(j) + 1)) {
              if (atLeastOne) {
                writer.print(" | ");
              } else {
                atLeastOne = true;
              }
              writer.print("(active" + j + " &= old" + j + ")");
            }
          }

          if (i <= (data.getMaxLenForActive(j) + 1)) {
            if (atLeastOne) {
              writer.print(" | ");
            }
            writer.print("(active" + j + " &= old" + j + ")");
          }

          writer.println(") == 0L)");
          if (!data.isMixedState() && (data.generatedStates() != 0)) {
            writer.print("      return jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 2) + ", ");
            for (j = 0; j < (maxLongsReqd - 1); j++) {
              if (i <= (data.getMaxLenForActive(j) + 1)) {
                writer.print("old" + j + ", ");
              } else {
                writer.print("0L, ");
              }
            }
            if (i <= (data.getMaxLenForActive(j) + 1)) {
              writer.println("old" + j + ");");
            } else {
              writer.println("0L);");
            }
          } else if (data.generatedStates() != 0) {
            writer.println("      return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", "
                + (i - 1) + ");");
          } else {
            writer.println("      return " + i + ";");
          }
        }

        if ((i != 0) && data.global.options().getDebugTokenManager()) {
          writer.println(
              "   if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
          writer.println(
              "      fprintf(debugStream, \"   Currently matched the first %d characters as a \\\"%s\\\" token.\\n\", (jjmatchedPos + 1), addUnicodeEscapes(tokenImage[jjmatchedKind]).c_str());");
          writer.println("   fprintf(debugStream, \"   Possible string literal matches : { \");");

          StringBuilder fmt = new StringBuilder();
          StringBuilder args = new StringBuilder();
          for (int vecs = 0; vecs < ((data.getMaxStrKind() / 64) + 1); vecs++) {
            if (i <= data.getMaxLenForActive(vecs)) {
              if (fmt.length() > 0) {
                fmt.append(", ");
                args.append(", ");
              }

              fmt.append("%s");
              args.append("         jjKindsForBitVector(" + vecs + ", ");
              args.append("active" + vecs + ").c_str() ");
            }
          }

          fmt.append("}\\n");
          writer.println("    fprintf(debugStream, \"" + fmt + "\"," + args + ");");
        }

        writer.println("   if (reader->endOfInput()) {");

        if (!data.isMixedState() && (data.generatedStates() != 0)) {
          writer.print("      jjStopStringLiteralDfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ");
          for (k = 0; k < (maxLongsReqd - 1); k++) {
            if (i <= data.getMaxLenForActive(k)) {
              writer.print("active" + k + ", ");
            } else {
              writer.print("0L, ");
            }
          }

          if (i <= data.getMaxLenForActive(k)) {
            writer.println("active" + k + ");");
          } else {
            writer.println("0L);");
          }


          if ((i != 0) && data.global.options().getDebugTokenManager()) {
            writer.println(
                "      if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
            writer.println(
                "      fprintf(debugStream, \"   Currently matched the first %d characters as a \\\"%s\\\" token.\\n\", (jjmatchedPos + 1),  addUnicodeEscapes(tokenImage[jjmatchedKind]).c_str());");
          }

          writer.println("      return " + i + ";");
        } else if (data.generatedStates() != 0) {
          writer.println(
              "   return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", " + (i - 1) + ");");
        } else {
          writer.println("      return " + i + ";");
        }

        writer.println("   }");
      }

      if ((i != 0)) {
        writer.println("   curChar = reader->readChar();");
      }

      if ((i != 0) && data.global.options().getDebugTokenManager()) {
        writer
            .println("   fprintf(debugStreoptions.set, " + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
                + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
                + "reader->getEndLine(), reader->getEndColumn());");
      }

      writer.println("   switch(curChar)");
      writer.println("   {");

      for (String key2 : keys) {
        key = key2;
        info = (KindInfo) tab.get(key);
        ifGenerated = false;
        char c = key.charAt(0);

        if ((i == 0) && (c < 128) && (info.finalKindCnt != 0)
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
            writer.println("      case " + (int) Character.toUpperCase(c) + ":");
          }

          if (c != Character.toLowerCase(c)) {
            writer.println("      case " + (int) Character.toLowerCase(c) + ":");
          }
        }

        writer.println("      case " + (int) c + ":");

        long matchedKind;
        String prefix = (i == 0) ? "         " : "            ";

        if (info.finalKindCnt != 0) {
          for (j = 0; j < maxLongsReqd; j++) {
            if ((matchedKind = info.finalKinds[j]) == 0L) {
              continue;
            }

            for (k = 0; k < 64; k++) {
              if ((matchedKind & (1L << k)) == 0L) {
                continue;
              }

              if (ifGenerated) {
                writer.print("         else if ");
              } else if (i != 0) {
                writer.print("         if ");
              }

              ifGenerated = true;

              int kindToPrint;
              if (i != 0) {
                writer.println("((active" + j + " & " + toHexString(1L << k) + ") != 0L)");
              }

              if ((data.getIntermediateKinds() != null) && (data.getIntermediateKinds()[((j * 64) + k)] != null)
                  && (data.getIntermediateKinds()[((j * 64) + k)][i] < ((j * 64) + k))
                  && (data.getIntermediateMatchedPos() != null) && (data.getIntermediateMatchedPos()[((j * 64) + k)][i] == i)) {
                JavaCCErrors.warning(" \"" + Encoding.escape(data.global.getImage((j * 64) + k))
                    + "\" cannot be matched as a string literal token " + "at line "
                    + GetLine(data.global, (j * 64) + k) + ", column " + GetColumn(data.global, (j * 64) + k)
                    + ". It will be matched as " + GetLabel(data.global, data.getIntermediateKinds()[((j * 64) + k)][i])
                    + ".");
                kindToPrint = data.getIntermediateKinds()[((j * 64) + k)][i];
              } else if ((i == 0) && (data.global.canMatchAnyChar(data.getStateIndex()) >= 0)
                  && (data.global.canMatchAnyChar(data.getStateIndex()) < ((j * 64) + k))) {
                JavaCCErrors.warning(" \"" + Encoding.escape(data.global.getImage((j * 64) + k))
                    + "\" cannot be matched as a string literal token " + "at line "
                    + GetLine(data.global, (j * 64) + k) + ", column " + GetColumn(data.global, (j * 64) + k)
                    + ". It will be matched as "
                    + GetLabel(data.global, data.global.canMatchAnyChar(data.getStateIndex())) + ".");
                kindToPrint = data.global.canMatchAnyChar(data.getStateIndex());
              } else {
                kindToPrint = (j * 64) + k;
              }

              if (!data.isSubString((j * 64) + k)) {
                int stateSetName = GetStateSetForKind(data, i, (j * 64) + k);

                if (stateSetName != -1) {
                  writer.println(prefix + "return jjStartNfaWithStates" + data.getLexerStateSuffix() + "(" + i + ", "
                      + kindToPrint + ", " + stateSetName + ");");
                } else {
                  writer.println(prefix + "return jjStopAtPos" + "(" + i + ", " + kindToPrint + ");");
                }
              } else if (((data.global.initMatch(data.getStateIndex()) != 0)
                  && (data.global.initMatch(data.getStateIndex()) != Integer.MAX_VALUE)) || (i != 0)) {
                writer.println("         {");
                writer.println(prefix + "jjmatchedKind = " + kindToPrint + ";");
                writer.println(prefix + "jjmatchedPos = " + i + ";");
                writer.println("         }");
              } else {
                writer.println(prefix + "jjmatchedKind = " + kindToPrint + ";");
              }
            }
          }
        }

        if (info.validKindCnt != 0) {
          atLeastOne = false;

          if (i == 0) {
            writer.print("         return ");

            writer.print("jjMoveStringLiteralDfa" + (i + 1) + data.getLexerStateSuffix() + "(");
            for (j = 0; j < (maxLongsReqd - 1); j++) {
              if ((i + 1) <= data.getMaxLenForActive(j)) {
                if (atLeastOne) {
                  writer.print(", ");
                } else {
                  atLeastOne = true;
                }

                writer.print(toHexString(info.validKinds[j]));
              }
            }

            if ((i + 1) <= data.getMaxLenForActive(j)) {
              if (atLeastOne) {
                writer.print(", ");
              }

              writer.print(toHexString(info.validKinds[j]));
            }
            writer.println(");");
          } else {
            writer.print("         return ");

            writer.print("jjMoveStringLiteralDfa" + (i + 1) + data.getLexerStateSuffix() + "(");

            for (j = 0; j < (maxLongsReqd - 1); j++) {
              if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
                if (atLeastOne) {
                  writer.print(", ");
                } else {
                  atLeastOne = true;
                }

                if (info.validKinds[j] != 0L) {
                  writer.print("active" + j + ", " + toHexString(info.validKinds[j]));
                } else {
                  writer.print("active" + j + ", 0L");
                }
              }
            }

            if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
              if (atLeastOne) {
                writer.print(", ");
              }
              if (info.validKinds[j] != 0L) {
                writer.print("active" + j + ", " + toHexString(info.validKinds[j]));
              } else {
                writer.print("active" + j + ", 0L");
              }
            }

            writer.println(");");
          }
        } else // A very special case.
        if ((i == 0) && data.isMixedState()) {

          if (data.generatedStates() != 0) {
            writer.println(
                "         return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", 0);");
          } else {
            writer.println("         return 1;");
          }
        } else if (i != 0) // No more str literals to look for
        {
          writer.println("         break;");
          startNfaNeeded = true;
        }
      }

      writer.println("      default :");

      if (data.global.options().getDebugTokenManager()) {
        writer.println("      fprintf(debugStream, \"   No string literal matches possible.\");");
      }

      if (data.generatedStates() != 0) {
        if (i == 0) {
          /*
           * This means no string literal is possible. Just move nfa with this guy and return.
           */
          writer
              .println("         return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", 0);");
        } else {
          writer.println("         break;");
          startNfaNeeded = true;
        }
      } else {
        writer.println("         return " + (i + 1) + ";");
      }


      writer.println("   }");

      if ((i != 0) && startNfaNeeded) {
        if (!data.isMixedState() && (data.generatedStates() != 0)) {
          /*
           * Here, a string literal is successfully matched and no more string literals are
           * possible. So set the kind and state set upto and including this position for the
           * matched string.
           */

          writer.print("   return jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ");
          for (k = 0; k < (maxLongsReqd - 1); k++) {
            if (i <= data.getMaxLenForActive(k)) {
              writer.print("active" + k + ", ");
            } else {
              writer.print("0L, ");
            }
          }
          if (i <= data.getMaxLenForActive(k)) {
            writer.println("active" + k + ");");
          } else {
            writer.println("0L);");
          }
        } else if (data.generatedStates() != 0) {
          writer.println(
              "   return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", " + i + ");");
        } else {
          writer.println("   return " + (i + 1) + ";");
        }
      }

      writer.println("}");
    }

    if (!data.isMixedState() && (data.generatedStates() != 0) && data.getCreateStartNfa()) {
      DumpStartWithStates(writer, data);
    }
  }

  protected final void dumpMoveNfa(PrintWriter writer, NfaStateData data) {
    writer.print("\nint " + data.getParserName() + "TokenManager::jjMoveNfa" + data.getLexerStateSuffix()
        + "(int startState, int curPos)");
    writer.println("{");
    if (data.generatedStates() == 0) {
      writer.println("   return curPos;");
      writer.println("}");
      return;
    }

    if (data.isMixedState()) {
      writer.println("   int strKind = jjmatchedKind;");
      writer.println("   int strPos = jjmatchedPos;");
      writer.println("   int seenUpto;");
      writer.println("   reader->backup(seenUpto = curPos + 1);");
      writer.println("   assert(!reader->endOfInput());");
      writer.println("   curChar = reader->read(); // TOL: Support Unicode");
      writer.println("   curPos = 0;");
    }

    writer.println("   int startsAt = 0;");
    writer.println("   jjnewStateCnt = " + data.generatedStates() + ";");
    writer.println("   int i = 1;");
    writer.println("   jjstateSet[0] = startState;");

    if (data.global.options().getDebugTokenManager()) {
      writer.println(
          "      fprintf(debugStream, \"   Starting NFA to match one of : %s\\n\", jjKindsForStateVector(curLexState, jjstateSet, 0, 1).c_str());");
    }

    if (data.global.options().getDebugTokenManager()) {
      writer.println("   fprintf(debugStream, " + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
          + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
          + "reader->getEndLine(), reader->getEndColumn());");
    }

    writer.println("   int kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
    writer.println("   for (;;)");
    writer.println("   {");
    writer.println("      if (++jjround == 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
    writer.println("         ReInitRounds();");
    writer.println("      if (curChar < 64)");
    writer.println("      {");

    DumpAsciiMoves(writer, data, 0);

    writer.println("      }");

    writer.println("      else if (curChar < 128)");

    writer.println("      {");

    DumpAsciiMoves(writer, data, 1);

    writer.println("      }");

    writer.println("      else");
    writer.println("      {");

    DumpCharAndRangeMoves(writer, data);

    writer.println("      }");

    writer.println("      if (kind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
    writer.println("      {");
    writer.println("         jjmatchedKind = kind;");
    writer.println("         jjmatchedPos = curPos;");
    writer.println("         kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
    writer.println("      }");
    writer.println("      ++curPos;");

    if (data.global.options().getDebugTokenManager()) {
      writer.println(
          "      if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
      writer.println(
          "   fprintf(debugStream, \"   Currently matched the first %d characters as a \\\"%s\\\" token.\\n\",  (jjmatchedPos + 1),  addUnicodeEscapes(tokenImage[jjmatchedKind]).c_str());");
    }

    writer.println("      if ((i = jjnewStateCnt), (jjnewStateCnt = startsAt), (i == (startsAt = "
        + data.generatedStates() + " - startsAt)))");
    if (data.isMixedState()) {
      writer.println("         break;");
    } else {
      writer.println("         return curPos;");
    }

    if (data.global.options().getDebugTokenManager()) {
      writer.println(
          "      fprintf(debugStream, \"   Possible kinds of longer matches : %s\\n\", jjKindsForStateVector(curLexState, jjstateSet, startsAt, i).c_str());");
    }

    if (data.isMixedState()) {
      writer.println("      if (reader->endOfInput()) { break; }");
    } else {
      writer.println("      if (reader->endOfInput()) { return curPos; }");
    }
    writer.println("      curChar = reader->read(); // TOL: Support Unicode");

    if (data.global.options().getDebugTokenManager()) {
      writer.println("   fprintf(debugStream, " + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
          + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
          + "reader->getEndLine(), reader->getEndColumn());");
    }

    writer.println("   }");

    if (data.isMixedState()) {
      writer.println("   if (jjmatchedPos > strPos)");
      writer.println("      return curPos;");
      writer.println("");
      writer.println("   int toRet = MAX(curPos, seenUpto);");
      writer.println("");
      writer.println("   if (curPos < toRet)");
      writer.println("      for (i = toRet - MIN(curPos, seenUpto); i-- > 0; )");
      writer.println("        {  assert(!reader->endOfInput());");
      writer.println("           curChar = reader->read(); } // TOL: Support Unicode");
      writer.println("");
      writer.println("   if (jjmatchedPos < strPos)");
      writer.println("   {");
      writer.println("      jjmatchedKind = strKind;");
      writer.println("      jjmatchedPos = strPos;");
      writer.println("   }");
      writer.println("   else if (jjmatchedPos == strPos && jjmatchedKind > strKind)");
      writer.println("      jjmatchedKind = strKind;");
      writer.println("");
      writer.println("   return toRet;");
    }
    writer.println("}");
  }

  private static void getStateNames(PrintWriter writer, LexerData data) {

    String[] arr = new String[data.getStateCount()];
    for (int index = 0; index < data.getStateCount(); index++) {
      arr[index] = data.getStateName(index);
    }

    // First generate char array vars
    for (int i = 0; i < arr.length; i++) {
      writer.println("static const JJChar lexStateNames_arr_" + i + "[] = ");
      String s = arr[i];
      // String literals in CPP become char arrays
      writer.print("{");
      for (int ii = 0; ii < s.length(); ii++) {
        writer.print("0x" + Integer.toHexString(s.charAt(ii)) + ", ");
      }
      writer.print("0}");
      writer.println(";");
    }

    writer.println("static const JJString lexStateNames[] = {");
    for (int i = 0; i < arr.length; i++) {
      writer.println("lexStateNames_arr_" + i + ", ");
    }
    writer.println("};");
  }
}
