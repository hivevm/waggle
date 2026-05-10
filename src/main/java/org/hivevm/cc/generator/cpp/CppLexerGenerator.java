// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

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
class CppLexerGenerator extends LexerGenerator {

    public CppLexerGenerator() {
        super(Language.CPP);
    }

    @Override
    protected final void generate(LexerData data, Context options) {
        options.add("STATE_NAMES_AS_CHARS", data.getStateCount())
                .set("STATE_NAMES_AS_CHARS_INDEX", i -> i)
                .set("STATE_NAMES_AS_CHARS_CHARS", (i, w) -> CppLexerGenerator.getTextAsChars(data.getStateName(i), w));
        options.set("DUMP_STR_LITERAL_IMAGES", p -> DumpStrLiteralImages(p, data));
        options.set("DUMP_STATES_FOR_STATE_CPP", p -> DumpStatesForStateCPP(p, data));
        options.set("DUMP_STATES_FOR_KIND", p -> DumpStatesForKind(p, data));
        options.set("DUMP_NFA_AND_DFA_HEADER", w ->
                data.getStateNames().forEach(name -> dump_nfa_and_dfa_header(data.getStateData(name), w))
        );

        CppTemplate.LEXER.render(options, data.getParserName());

        data.setBoilerPlateDumped(false);
        CppTemplate.LEXER_H.render(options, data.getParserName());
    }

    protected SourceProvider getConstantsTemplate() {
        return CppTemplate.PARSER_CONSTANTS;
    }

    protected void dumpStaticVarDeclarations(LinePrinter printer, LexerData data) {
        if (data.maxLexStates() > 1) {
            printer.println();
            printer.println("/** Lex State array. */");
            printer.print("static const int jjnewLexState[] = {");
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
            printer.println("};");
        }

        if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
            // Bit vector for TOKEN
            printer.print("static const unsigned long long jjtoToken[] = {");
            printer.indent();
            for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
                if ((i % 4) == 0) {
                    printer.println();
                }
                printer.print(toHexString(data.toToken(i)) + ", ");
            }
            printer.println();
            printer.outdent();
            printer.println("};");
        }

        if (data.hasSkip() || data.hasSpecial()) {
            // Bit vector for SKIP
            printer.print("static const unsigned long long jjtoSkip[] = {");
            printer.indent();
            for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
                if ((i % 4) == 0) {
                    printer.println();
                }
                printer.print(toHexString(data.toSkip(i)) + ", ");
            }
            printer.println();
            printer.outdent();
            printer.println("};");
        }

        if (data.hasSpecial()) {
            // Bit vector for SPECIAL
            printer.print("static const unsigned long long jjtoSpecial[] = {");
            printer.indent();
            for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
                if ((i % 4) == 0) {
                    printer.println();
                }
                printer.print(toHexString(data.toSpecial(i)) + ", ");
            }
            printer.println();
            printer.outdent();
            printer.println("};");
        }

        if (data.hasMore()) {
            // Bit vector for MORE
            printer.print("static const unsigned long long jjtoMore[] = {");
            printer.indent();
            for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
                if ((i % 4) == 0) {
                    printer.println();
                }
                printer.print(toHexString(data.toMore(i)) + ", ");
            }
            printer.println();
            printer.outdent();
            printer.println("};");
        }
    }

    protected void dumpGetNextToken(LinePrinter printer, LexerData data) {
        if (data.hasEof()) {
            printer.println("      TokenLexicalActions(matchedToken);");
        }

        printer.println("return matchedToken;");
        printer.outdent();
        printer.println("}");
        printer.println("curChar = reader->beginToken();");

        if (data.hasMoreActions() || data.hasSkipActions() || data.hasTokenActions()) {
            printer.println("image = jjimage;");
            printer.println("image.clear();");
            printer.println("jjimageLen = 0;");
        }

        printer.println();

        if (data.hasMore()) {
            printer.println("for (;;) {");
            printer.indent();
        }

        // this also sets up the start state of the nfa
        if (data.maxLexStates() > 1) {
            printer.println("switch(curLexState) {");
            printer.indent();
        }

        for (int i = 0; i < data.maxLexStates(); i++) {
            if (data.maxLexStates() > 1) {
                printer.println("case " + i + ": {");
                printer.indent();
            }

            if (data.singlesToSkip(i).HasTransitions()) {
                // added the backup(0) to make JIT happy
                printer.println("{");
                printer.indent();
                printer.println("reader->backup(0);");
                if ((data.singlesToSkip(i).asciiMoves[0] != 0L) && (
                        data.singlesToSkip(i).asciiMoves[1]
                                != 0L)) {
                    printer.print("while ((curChar < 64" + " && (" + Long.toHexString(
                            data.singlesToSkip(i).asciiMoves[0])
                            + " & (1L << curChar)) != 0L) || \n"
                            + "          (curChar >> 6) == 1"
                            + " && ("
                            + toHexString(data.singlesToSkip(i).asciiMoves[1])
                            + " & (1L << (curChar & 077))) != 0L)");
                } else if (data.singlesToSkip(i).asciiMoves[1] == 0L) {
                    printer.print("while (curChar <= " + (int) LexerGenerator.MaxChar(
                            data.singlesToSkip(i).asciiMoves[0])
                            + " && (" + toHexString(data.singlesToSkip(i).asciiMoves[0])
                            + " & (1L << curChar)) != 0L)");
                } else if (data.singlesToSkip(i).asciiMoves[0] == 0L) {
                    printer.print("while (curChar > 63 && curChar <= "
                            + (LexerGenerator.MaxChar(data.singlesToSkip(i).asciiMoves[1]) + 64)
                            + " && ("
                            + toHexString(data.singlesToSkip(i).asciiMoves[1])
                            + " & (1L << (curChar & 077))) != 0L)");
                }

                if (data.options().getDebugTokenManager()) {
                    printer.println(" {");
                    printer.indent();
                    if (data.maxLexStates() > 1) {
                        printer.println("fprintf(debugStream, \"<%s>\" , addUnicodeEscapes(lexStateNames[curLexState]).c_str());");
                    }

                    printer.println("fprintf(debugStream, \"Skipping character : %c(%d)\\n\", curChar, (int)curChar);");
                }

                printer.println("if (reader->endOfInput()) { goto EOFLoop; }");
                printer.println("curChar = reader->beginToken();");

                printer.outdent();
                printer.println("}");

                printer.outdent();
                printer.println("}");
            }

            if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
                if (data.options().getDebugTokenManager()) {
                    printer.println("fprintf(debugStream, \"   Matched the empty string as %s token.\\n\", addUnicodeEscapes(tokenImage["
                            + data.initMatch(i) + "]).c_str());");
                }

                printer.println("jjmatchedKind = " + data.initMatch(i) + ";");
                printer.println("jjmatchedPos = -1;");
                printer.println("curPos = 0;");
            } else {
                printer.println("jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
                printer.println("jjmatchedPos = 0;");
            }

            if (data.options().getDebugTokenManager()) {
                printer.println("fprintf(debugStream, "
                        + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
                        + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
                        + "reader->getEndLine(), reader->getEndColumn());");
            }

            printer.println("curPos = jjMoveStringLiteralDfa0_" + i + "();");
            if (data.canMatchAnyChar(i) != -1) {
                if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
                    printer.println("if (jjmatchedPos < 0 || (jjmatchedPos == 0 && jjmatchedKind > "
                            + data.canMatchAnyChar(i) + ")) {");
                } else {
                    printer.println("if (jjmatchedPos == 0 && jjmatchedKind > " + data.canMatchAnyChar(i) + ") {");
                }
                printer.indent();

                if (data.options().getDebugTokenManager()) {
                    printer.println("fprintf(debugStream, \"   Current character matched as a %s token.\\n\", addUnicodeEscapes(tokenImage["
                            + data.canMatchAnyChar(i) + "]).c_str());");
                }
                printer.println("jjmatchedKind = " + data.canMatchAnyChar(i) + ";");

                if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
                    printer.println("jjmatchedPos = 0;");
                }

                printer.outdent();
                printer.println("}");
            }

            if (data.maxLexStates() > 1) {
                printer.println("break;");
                printer.outdent();
                printer.println("}");
            }
        }

        if (data.maxLexStates() > 1) {
            printer.outdent();
            printer.println("}");
        } else if (data.maxLexStates() == 0) {
            printer.println("jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
        }

        if (data.maxLexStates() > 0) {
            printer.println("if (jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ") {");
            printer.indent();

            printer.println("if (jjmatchedPos + 1 < curPos) {");
            printer.indent();

            if (data.options().getDebugTokenManager()) {
                printer.println("      {");
                printer.println("fprintf(debugStream, "
                        + "\"   Putting back %d characters into the input stream.\\n\", (curPos - jjmatchedPos - 1));");
            }

            printer.println("reader->backup(curPos - jjmatchedPos - 1);");

            printer.outdent();
            printer.println("}");

            if (data.options().getDebugTokenManager()) {
                printer.println("fprintf(debugStream, \"****** FOUND A %d(%s) MATCH (%s) ******\\n\", jjmatchedKind, addUnicodeEscapes(tokenImage[jjmatchedKind]).c_str(), addUnicodeEscapes(reader->GetSuffix(jjmatchedPos + 1)).c_str());");
            }

            if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
                printer.println("if ((jjtoToken[jjmatchedKind >> 6] & "
                        + "(1ULL << (jjmatchedKind & 077))) != 0L) {");
                printer.indent();
            }

            printer.println("matchedToken = jjFillToken();");

            if (data.hasSpecial()) {
                printer.println("matchedToken->specialToken = specialToken;");
            }

            if (data.hasTokenActions()) {
                printer.println("TokenLexicalActions(matchedToken);");
            }

            if (data.maxLexStates() > 1) {
                printer.println("if (jjnewLexState[jjmatchedKind] != -1)");
                printer.println("    curLexState = jjnewLexState[jjmatchedKind];");
            }

            printer.println("return matchedToken;");

            if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
                printer.outdent();
                printer.println("}");

                if (data.hasSkip() || data.hasSpecial()) {
                    if (data.hasMore()) {
                        printer.print("else if ((jjtoSkip[jjmatchedKind >> 6] & (1L << (jjmatchedKind & 077))) != 0L)");
                    } else {
                        printer.print("else");
                    }

                    printer.println(" {");
                    printer.indent();

                    if (data.hasSpecial()) {
                        printer.println("if ((jjtoSpecial[jjmatchedKind >> 6] & "
                                + "(1ULL << (jjmatchedKind & 077))) != 0L) {");
                        printer.indent();

                        printer.println("matchedToken = jjFillToken();");

                        printer.println("if (specialToken == nullptr)");
                        printer.println("    specialToken = matchedToken;");
                        printer.println("else {");
                        printer.println("    matchedToken->specialToken = specialToken;");
                        printer.println("    specialToken = (specialToken->next = matchedToken);");
                        printer.println("}");

                        if (data.hasSkipActions()) {
                            printer.println("SkipLexicalActions(matchedToken);");
                        }

                        printer.outdent();
                        printer.println("}");

                        if (data.hasSkipActions()) {
                            printer.println("else");
                            printer.println("    SkipLexicalActions(nullptr);");
                        }
                    } else if (data.hasSkipActions()) {
                        printer.println("SkipLexicalActions(nullptr);");
                    }

                    if (data.maxLexStates() > 1) {
                        printer.println("if (jjnewLexState[jjmatchedKind] != -1)");
                        printer.println("    curLexState = jjnewLexState[jjmatchedKind];");
                    }

                    printer.println("goto EOFLoop;");
                    printer.outdent();
                    printer.println("}");
                }

                if (data.hasMore()) {
                    if (data.hasMoreActions()) {
                        printer.println("MoreLexicalActions();");
                    } else if (data.hasSkipActions() || data.hasTokenActions()) {
                        printer.println("jjimageLen += jjmatchedPos + 1;");
                    }

                    if (data.maxLexStates() > 1) {
                        printer.println("if (jjnewLexState[jjmatchedKind] != -1)");
                        printer.println("    curLexState = jjnewLexState[jjmatchedKind];");
                    }
                    printer.println("curPos = 0;");
                    printer.println("jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");

                    printer.println("if (!reader->endOfInput()) {");
                    printer.println("    curChar = reader->read(); // UTF8: Support Unicode");

                    if (data.options().getDebugTokenManager()) {
                        printer.println("fprintf(debugStream, "
                                + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
                                + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
                                + "reader->getEndLine(), reader->getEndColumn());");
                    }
                    printer.println("    continue;");
                    printer.println("}");
                }
            }
        }

        if (data.hasMore()) {
            printer.outdent();
            printer.println("}");
        }
    }

    @Override
    protected void dumpSkipActions(LinePrinter printer, LexerData data) {
        printer.print(
                "\nvoid " + data.getParserName()
                        + "TokenManager::SkipLexicalActions(Token *matchedToken)");

        printer.println("{");
        printer.println("   switch(jjmatchedKind)");
        printer.println("   {");

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

                printer.println("      case " + i + " : {");

                if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
                    printer.println("         if (jjmatchedPos == -1)");
                    printer.println("         {");
                    printer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
                    printer.println("                jjemptyLineNo[" + data.getState(i)
                            + "] == reader->getBeginLine() &&");
                    printer.println("                jjemptyColNo[" + data.getState(i)
                            + "] == reader->getBeginColumn())");
                    printer.println("               errorHandler->lexicalError(JJString(\"(\"Error: Bailing out of infinite loop caused by repeated empty string matches \" + \"at line \" + reader->getBeginLine() + \", \" + \"column \" + reader->getBeginColumn() + \".\")), this);");
                    printer.println("            jjemptyLineNo[" + data.getState(i)
                            + "] = reader->getBeginLine();");
                    printer.println("            jjemptyColNo[" + data.getState(i)
                            + "] = reader->getBeginColumn();");
                    printer.println("            jjbeenHere[" + data.getState(i) + "] = true;");
                    printer.println("         }");
                }

                if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
                    break;
                }

                printer.print("         image.append");
                if (data.getImage(i) != null) {
                    printer.println("(jjstrLiteralImages[" + i + "]);");
                    printer.println("        lengthOfMatch = jjstrLiteralImages[" + i + "].length();");
                } else {
                    printer.println("(reader->GetSuffix(jjimageLen + (lengthOfMatch = jjmatchedPos + 1)));");
                }

                setup_token(act.getActionTokens().getFirst());
                reset_column();

                printActionToken(printer, act);
                printer.println();

                break;
            }

            printer.println("         break;");
            printer.println("       }");
        }

        printer.println("      default:");
        printer.println("         break;");
        printer.println("   }");
        printer.println("}");
    }

    @Override
    protected void dumpMoreActions(LinePrinter printer, LexerData data) {
        printer.print("\nvoid " + data.getParserName() + "TokenManager::MoreLexicalActions()");
        printer.println("{");
        printer.println("   jjimageLen += (lengthOfMatch = jjmatchedPos + 1);");
        printer.println("   switch(jjmatchedKind)");
        printer.println("   {");

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

                printer.println("      case " + i + " : {");

                if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
                    printer.println("         if (jjmatchedPos == -1)");
                    printer.println("         {");
                    printer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
                    printer.println("                jjemptyLineNo[" + data.getState(i)
                            + "] == reader->getBeginLine() &&");
                    printer.println("                jjemptyColNo[" + data.getState(i)
                            + "] == reader->getBeginColumn())");
                    printer.println("               errorHandler->lexicalError(JJString(\"(\"Error: Bailing out of infinite loop caused by repeated empty string matches \" + \"at line \" + reader->getBeginLine() + \", \" + \"column \" + reader->getBeginColumn() + \".\")), this);");
                    printer.println("            jjemptyLineNo[" + data.getState(i) + "] = reader->getBeginLine();");
                    printer.println("            jjemptyColNo[" + data.getState(i) + "] = reader->getBeginColumn();");
                    printer.println("            jjbeenHere[" + data.getState(i) + "] = true;");
                    printer.println("         }");
                }

                if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
                    break;
                }

                printer.print("         image.append");

                if (data.getImage(i) != null) {
                    printer.println("(jjstrLiteralImages[" + i + "]);");
                } else {
                    printer.println("(reader->GetSuffix(jjimageLen));");
                }

                printer.println("         jjimageLen = 0;");
                setup_token(act.getActionTokens().getFirst());
                reset_column();

                printActionToken(printer, act);
                printer.println();

                break;
            }

            printer.println("         break;");
            printer.println("       }");
        }

        printer.println("      default:");
        printer.println("         break;");

        printer.println("   }");
        printer.println("}");
    }

    @Override
    protected void dumpTokenActions(LinePrinter printer, LexerData data) {
        Action act;
        int i;
        printer.print("\nvoid " + data.getParserName()
                + "TokenManager::TokenLexicalActions(Token *matchedToken)");
        printer.println("{");
        printer.println("   switch(jjmatchedKind)");
        printer.println("   {");

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

                printer.println("      case " + i + " : {");

                if ((data.initMatch(data.getState(i)) == i) && data.canLoop(data.getState(i))) {
                    printer.println("         if (jjmatchedPos == -1)");
                    printer.println("         {");
                    printer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
                    printer.println("                jjemptyLineNo[" + data.getState(i)
                            + "] == reader->getBeginLine() &&");
                    printer.println("                jjemptyColNo[" + data.getState(i)
                            + "] == reader->getBeginColumn())");
                    printer.println("               errorHandler->lexicalError(JJString(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                            + "at line \" + reader->getBeginLine() + \", "
                            + "column \" + reader->getBeginColumn() + \".\"), this);");
                    printer.println("            jjemptyLineNo[" + data.getState(i)
                            + "] = reader->getBeginLine();");
                    printer.println("            jjemptyColNo[" + data.getState(i)
                            + "] = reader->getBeginColumn();");
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
                        printer.println("(jjstrLiteralImages[" + i + "]);");
                        printer.println("        lengthOfMatch = jjstrLiteralImages[" + i + "].length();");
                    } else {
                        printer.println("(reader->GetSuffix(jjimageLen + (lengthOfMatch = jjmatchedPos + 1)));");
                    }
                }

                setup_token(act.getActionTokens().getFirst());
                reset_column();

                printActionToken(printer, act);
                printer.println();

                break;
            }

            printer.println("         break;");
            printer.println("       }");
        }

        printer.println("      default:");
        printer.println("         break;");
        printer.println("   }");
        printer.println("}");
    }

    private void DumpStatesForKind(LinePrinter printer, LexerData data) {
        boolean moreThanOne = false;
        int cnt;

        printer.print(
                "static const int kindForState[" + data.stateSetSize() + "][" + data.stateSetSize()
                        + "] = ");

        if (data.getKinds() == null) {
            printer.println("null;");
            return;
        } else {
            printer.println("{");
        }

        for (int[] kind : data.getKinds()) {
            if (moreThanOne) {
                printer.println(",");
            }
            moreThanOne = true;

            if (kind == null) {
                printer.println("{}");
            } else {
                cnt = 0;
                printer.print("{ ");
                for (int element : kind) {
                    if ((cnt % 15) == 0) {
                        printer.print("\n  ");
                    } else if (cnt > 1) {
                        printer.print(" ");
                    }

                    printer.print("" + element);
                    printer.print(", ");

                }

                printer.print("}");
            }
        }
        printer.println("\n};");
    }

    private void DumpStatesForStateCPP(LinePrinter printer, LexerData data) {
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

                printer.print(
                        "const int stateSet_" + i + "_" + j + "[" + data.stateSetSize() + "] = ");
                if (stateSet == null) {
                    printer.println("   { " + j + " };");
                    continue;
                }

                printer.print("   { ");

                for (int element : stateSet) {
                    printer.print(element + ", ");
                }

                printer.println("};");
            }
        }

        for (int i = 0; i < data.maxLexStates(); i++) {
            printer.println("const int *stateSet_" + i + "[] = {");
            if (data.getStatesForState()[i] == null) {
                printer.println(" NULL, ");
                printer.println("};");
                continue;
            }

            for (int j = 0; j < data.getStatesForState()[i].length; j++) {
                printer.print("stateSet_" + i + "_" + j + ",");
            }
            printer.println("};");
        }

        printer.print("const int** statesForState[] = { ");
        for (int i = 0; i < data.maxLexStates(); i++) {
            printer.println("stateSet_" + i + ", ");
        }

        printer.println("\n};");
    }

    protected void dumpStateSets(LinePrinter printer, LexerData data) {
        int cnt = 0;

        printer.print("static const int jjnextStates[] = {");
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

        printer.println("\n};");
    }

    private void DumpStrLiteralImages(LinePrinter printer, LexerData data) {
        // For C++
        String image;
        int i;
        int charCnt = 0; // Set to zero in reInit() but just to be sure

        int literalCount = 0;

        if (data.getImageCount() <= 0) {
            printer.println("static const JJString jjstrLiteralImages[] = {};");
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
                    printer.println();
                    charCnt = 0;
                }

                printer.println("static JJChar jjstrLiteralChars_" + literalCount++ + "[] = {0};");
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
                printer.println();
                charCnt = 0;
            }

            printer.println(toPrint);
        }

        while (++i < data.maxOrdinal()) {
            if ((charCnt += 6) > 80) {
                printer.println();
                charCnt = 0;
            }

            printer.println("static JJChar jjstrLiteralChars_" + literalCount++ + "[] = {0};");
        }
        // Generate the array here.
        printer.println("static const JJString " + "jjstrLiteralImages[] = {");
        for (int j = 0; j < literalCount; j++) {
            printer.println("jjstrLiteralChars_" + j + ", ");
        }
        printer.println("};");
    }

    private void DumpStartWithStates(LinePrinter printer, NfaStateData data) {
        printer.print("\nint " + data.getParserName() + "TokenManager::jjStartNfaWithStates" + data.getLexerStateSuffix()
                + "(int pos, int kind, int state)");

        printer.println("{");
        printer.println("   jjmatchedKind = kind;");
        printer.println("   jjmatchedPos = pos;");

        if (data.global.options().getDebugTokenManager()) {
            printer.println("   fprintf(debugStream, \"   No more string literal token matches are possible.\");");
            printer.println("   fprintf(debugStream, \"   Currently matched the first %d characters as a \\\"%s\\\" token.\\n\",  (jjmatchedPos + 1),  addUnicodeEscapes(tokenImage[jjmatchedKind]).c_str());");
        }

        printer.println("   if (reader->endOfInput()) { return pos + 1; }");
        printer.println("   curChar = reader->read(); // UTF8: Support Unicode");

        if (data.global.options().getDebugTokenManager()) {
            printer.println("   fprintf(debugStream, "
                    + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
                    + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
                    + "reader->getEndLine(), reader->getEndColumn());");
        }
        printer.println("   return jjMoveNfa" + data.getLexerStateSuffix() + "(state, pos + 1);");
        printer.println("}");
    }

    @Override
    protected final void DumpHeadForCase(LinePrinter printer, int byteNum) {
        if (byteNum == 0) {
            printer.println("unsigned long long l = 1ULL << curChar;");
            printer.println("(void)l;");
        } else if (byteNum == 1) {
            printer.println("unsigned long long l = 1ULL << (curChar & 077);");
            printer.println("(void)l;");
        } else {
            printer.println("int hiByte = (curChar >> 8);");
            printer.println("int i1 = hiByte >> 6;");
            printer.println("unsigned long long l1 = 1ULL << (hiByte & 077);");
            printer.println("int i2 = (curChar & 0xff) >> 6;");
            printer.println("unsigned long long l2 = 1ULL << (curChar & 077);");
        }

        // printer.println(" MatchLoop: do");
        printer.println("do {");
        printer.indent();
        printer.println("switch(jjstateSet[--i]) {");
        printer.indent();
    }

    protected void dumpNonAsciiMoveMethod(LexerData data, NfaState state, LinePrinter printer) {
        printer.print(
                "\nbool " + data.getParserName() + "TokenManager::jjCanMove_" + state.nonAsciiMethod
                        + "(int hiByte, int i1, int i2, unsigned long long l1, unsigned long long l2)");

        printer.println("{");
        printer.println("   switch(hiByte)");
        printer.println("   {");

        for (int j = 0; j < state.loByteVec.size(); j += 2) {
            printer.println("      case " + state.loByteVec.get(j) + ":");
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.loByteVec.get(j + 1))))
                printer.println("         return ((jjbitVec" + state.loByteVec.get(j + 1)
                        + "[i2] & l2) != 0L);");
            else {
                printer.println("            return true;");
            }
        }

        printer.println("      default:");
        for (int j = state.nonAsciiMoveIndices.length; j > 0; j -= 2) {
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.nonAsciiMoveIndices[j - 2]))) {
                printer.println("         if ((jjbitVec" + state.nonAsciiMoveIndices[j - 2] + "[i1] & l1) != 0L)");
            }
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.nonAsciiMoveIndices[j - 1]))) {
                printer.println("            if ((jjbitVec" + state.nonAsciiMoveIndices[j - 1] + "[i2] & l2) == 0L)");
                printer.println("               return false;");
                printer.println("            else");
            }
            printer.println("            return true;");
        }

        printer.println("         return false;");
        printer.println("   }");
        printer.println("}");
    }

    private void dump_nfa_and_dfa_header(NfaStateData data, LinePrinter printer) {
        var lexer_state_suffix = data.getLexerStateSuffix();
        int maxKindsReqd = (data.getMaxStrKind() / 64) + 1;
        if (data.hasNFA() && !data.isMixedState() && (data.getMaxStrKind() > 0)) {
            printer.print("int jjStopStringLiteralDfa" + lexer_state_suffix + "(int pos, ");
            for (int i = 0; i < maxKindsReqd; i++) {
                if (i > 0) {
                    printer.print(", ");
                }
                printer.print("unsigned long long active");
                printer.print("" + i);
            }
            printer.println(");");

            printer.print("int jjStartNfa" + lexer_state_suffix + "(int pos, ");
            for (int i = 0; i < maxKindsReqd; i++) {
                if (i > 0) {
                    printer.print(", ");
                }
                printer.print("unsigned long long active");
                printer.print("" + i);
            }
            printer.println(");");
        }

        if (!data.isMixedState() && (data.generatedStates() != 0) && data.getCreateStartNfa()) {
            printer.println("int jjStartNfaWithStates" + lexer_state_suffix + "(int pos, int kind, int state);");
        }
        if (data.hasNFA()) {
            printer.println("int jjMoveNfa" + lexer_state_suffix + "(int startState, int curPos);");
        }

        if (data.getMaxLen() == 0) {
            printer.println("int jjMoveStringLiteralDfa0" + lexer_state_suffix + "();");
        } else if (!data.global.isBoilerPlateDumped()) {
            printer.println("int jjStopAtPos(int pos, int kind);");
            data.global.setBoilerPlateDumped(true);
        }

        // Dump DFA code
        if (data.getMaxLen() > 0) {
            for (int i = 0; i < data.getMaxLen(); i++) {
                printer.print("int jjMoveStringLiteralDfa" + i + lexer_state_suffix + "(");
                if (i != 0) {
                    if (i == 1) {
                        for (int j = 0; j < maxKindsReqd; j++) {
                            if (i <= data.getMaxLenForActive(j)) {
                                if (j > 0) {
                                    printer.print(", ");
                                }
                                printer.print("unsigned long long active");
                                printer.print("" + j);
                            }
                        }
                    } else {
                        for (int j = 0; j < maxKindsReqd; j++) {
                            if (i <= (data.getMaxLenForActive(j) + 1)) {
                                if (j > 0) {
                                    printer.print(", ");
                                }
                                printer.print("unsigned long long old" + j + ", ");
                                printer.print("unsigned long long active" + j);
                            }
                        }
                    }
                }
                printer.println(");");
            }
        }
        // End: Dump DFA code
        printer.println();
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
        printer.print("int " + data.getParserName() + "TokenManager::jjStopStringLiteralDfa"
                + data.getLexerStateSuffix() + "(int pos");
        for (i = 0; i < maxKindsReqd; i++) {
            printer.print(", unsigned long long active" + i);
        }
        printer.println(") {");

        if (data.global.options().getDebugTokenManager()) {
            printer.println("      fprintf(debugStream, \"   No more string literal token matches are possible.\");");
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
                            printer.println("            jjmatchedKind = " + kindStr + ";");

                            if (((data.global.initMatch(data.getStateIndex()) != 0)
                                    && (data.global.initMatch(data.getStateIndex())
                                    != Integer.MAX_VALUE))) {
                                printer.println("            jjmatchedPos = 0;");
                            }
                        } else if (i == jjmatchedPos) {
                            if (data.isSubStringAtPos(i)) {
                                printer.println("            if (jjmatchedPos != " + i + ")");
                                printer.println("            {");
                                printer.println("               jjmatchedKind = " + kindStr + ";");
                                printer.println("               jjmatchedPos = " + i + ";");
                                printer.println("            }");
                            } else {
                                printer.println("            jjmatchedKind = " + kindStr + ";");
                                printer.println("            jjmatchedPos = " + i + ";");
                            }
                        } else {
                            if (jjmatchedPos > 0) {
                                printer.println("            if (jjmatchedPos < " + jjmatchedPos + ")");
                            } else {
                                printer.println("            if (jjmatchedPos == 0)");
                            }
                            printer.println("            {");
                            printer.println("               jjmatchedKind = " + kindStr + ";");
                            printer.println("               jjmatchedPos = " + jjmatchedPos + ";");
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
        printer.print("int " + data.getParserName() + "TokenManager::jjStartNfa" + data.getLexerStateSuffix() + "(int pos");
        for (i = 0; i < maxKindsReqd; i++) {
            printer.print(", unsigned long long active" + i);
        }
        printer.println(") {");

        if (data.isMixedState()) {
            if (data.generatedStates() != 0) {
                printer.println("   return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", pos + 1);");
            } else {
                printer.println("   return pos + 1;");
            }

            printer.println("}");
            return;
        }

        printer.print("   return jjMoveNfa" + data.getLexerStateSuffix() + "(jjStopStringLiteralDfa"
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
            printer.print("int " + data.getParserName() + "TokenManager::jjMoveStringLiteralDfa0" + data.getLexerStateSuffix() + "() {");
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
            printer.println("int " + data.getParserName() + "TokenManager::jjStopAtPos(int pos, int kind) {");
            printer.indent();
            printer.println("jjmatchedKind = kind;");
            printer.println("jjmatchedPos = pos;");

            if (data.global.options().getDebugTokenManager()) {
                printer.println("fprintf(debugStream, \"No more string literal token matches are possible.\");");
                printer.println("fprintf(debugStream, \"Currently matched the first %d characters as a \\\"%s\\\" token.\\n\",  (jjmatchedPos + 1),  addUnicodeEscapes(tokenImage[jjmatchedKind]).c_str());");
            }

            printer.println("return pos + 1;");
            printer.outdent();
            printer.println("}");
            data.global.setBoilerPlateDumped(true);
        }

        for (i = 0; i < data.getMaxLen(); i++) {
            boolean atLeastOne_ = false;
            boolean startNfaNeeded = false;
            tab = data.getCharPosKind(i);
            var keys = LexerGenerator.re_arrange(tab);

            printer.println();
            printer.print("int " + data.getParserName() + "TokenManager::jjMoveStringLiteralDfa" + i + data.getLexerStateSuffix() + "(");

            if (i != 0) {
                if (i == 1) {
                    for (j = 0; j < (maxLongsReqd - 1); j++) {
                        if (i <= data.getMaxLenForActive(j)) {
                            if (atLeastOne_) {
                                printer.print(", ");
                            } else {
                                atLeastOne_ = true;
                            }
                            printer.print("unsigned long long active" + j);
                        }
                    }

                    if (i <= data.getMaxLenForActive(j)) {
                        if (atLeastOne_) {
                            printer.print(", ");
                        }
                        printer.print("unsigned long long active" + j);
                    }
                } else {
                    for (j = 0; j < (maxLongsReqd - 1); j++) {
                        if (i <= (data.getMaxLenForActive(j) + 1)) {
                            if (atLeastOne_) {
                                printer.print(", ");
                            } else {
                                atLeastOne_ = true;
                            }
                            printer.print("unsigned long long old" + j + ", unsigned long long active" + j);
                        }
                    }

                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        if (atLeastOne_) {
                            printer.print(", ");
                        }
                        printer.print("unsigned long long old" + j + ", unsigned long long active" + j);
                    }
                }
            }

            printer.println(") {");
            printer.indent();

            if (i != 0) {
                if (i > 1) {
                    var atLeastOne = false;
                    printer.print("if ((");

                    for (j = 0; j < (maxLongsReqd - 1); j++) {
                        if (i <= (data.getMaxLenForActive(j) + 1)) {
                            if (atLeastOne) {
                                printer.print(" | ");
                            } else {
                                atLeastOne = true;
                            }
                            printer.print("(active" + j + " &= old" + j + ")");
                        }
                    }

                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        if (atLeastOne)
                            printer.print(" | ");
                        printer.print("(active" + j + " &= old" + j + ")");
                    }
                    printer.println(") == 0L) {");
                    printer.indent();

                    if (!data.isMixedState() && (data.generatedStates() != 0)) {
                        printer.print("return jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 2) + ", ");
                        for (j = 0; j < (maxLongsReqd - 1); j++) {
                            if (i <= (data.getMaxLenForActive(j) + 1)) {
                                printer.print("old" + j + ", ");
                            } else {
                                printer.print("0L, ");
                            }
                        }
                        if (i <= (data.getMaxLenForActive(j) + 1)) {
                            printer.println("old" + j + ");");
                        } else {
                            printer.println("0L);");
                        }
                    } else if (data.generatedStates() != 0) {
                        printer.println("return jjMoveNfa" + data.getLexerStateSuffix() + "("
                                + InitStateName(data) + ", " + (i - 1) + ");");
                    } else {
                        printer.println("return " + i + ";");
                    }
                    printer.outdent();
                    printer.println("}");
                }

                if ((i != 0) && data.global.options().getDebugTokenManager()) {
                    printer.println("if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
                    printer.println("    fprintf(debugStream, \"   Currently matched the first %d characters as a \\\"%s\\\" token.\\n\", (jjmatchedPos + 1), addUnicodeEscapes(tokenImage[jjmatchedKind]).c_str());");
                    printer.println("    fprintf(debugStream, \"   Possible string literal matches : { \");");

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
                    printer.println("    fprintf(debugStream, \"" + fmt + "\"," + args + ");");
                }

                printer.println("if (reader->endOfInput()) {");
                printer.indent();

                if (!data.isMixedState() && (data.generatedStates() != 0)) {
                    printer.print("jjStopStringLiteralDfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ");
                    for (k = 0; k < (maxLongsReqd - 1); k++) {
                        if (i <= data.getMaxLenForActive(k)) {
                            printer.print("active" + k + ", ");
                        } else {
                            printer.print("0L, ");
                        }
                    }

                    if (i <= data.getMaxLenForActive(k))
                        printer.println("active" + k + ");");
                    else
                        printer.println("0L);");

                    if ((i != 0) && data.global.options().getDebugTokenManager()) {
                        printer.println("if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
                        printer.println("    fprintf(debugStream, \"   Currently matched the first %d characters as a \\\"%s\\\" token.\\n\", (jjmatchedPos + 1),  addUnicodeEscapes(tokenImage[jjmatchedKind]).c_str());");
                    }
                    printer.println("return " + i + ";");
                } else if (data.generatedStates() != 0)
                    printer.println("return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", " + (i - 1) + ");");
                else
                    printer.println("return " + i + ";");

                printer.outdent();
                printer.println("}");
            }

            if ((i != 0))
                printer.println("   curChar = reader->readChar();");

            if ((i != 0) && data.global.options().getDebugTokenManager()) {
                printer.println("fprintf(debugStreoptions.set, "
                        + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
                        + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
                        + "reader->getEndLine(), reader->getEndColumn());");
            }

            printer.println("switch(curChar) {");
            printer.indent();

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
                }

                // Since we know key is a single character ...
                if (data.ignoreCase()) {
                    if (c != Character.toUpperCase(c)) {
                        printer.println("case " + (int) Character.toUpperCase(c) + ":");
                    }

                    if (c != Character.toLowerCase(c)) {
                        printer.println("case " + (int) Character.toLowerCase(c) + ":");
                    }
                }

                printer.println("case " + (int) c + ": {");
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
                                printer.print("((active" + j + " & " + toHexString(1L << k) + ") != 0L)");
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
                                    printer.println("return jjStartNfaWithStates"
                                            + data.getLexerStateSuffix() + "(" + i
                                            + ", " + kindToPrint + ", " + stateSetName + ");");
                                } else {
                                    printer.println("return jjStopAtPos" + "(" + i + ", " + kindToPrint + ");");
                                }
                            } else if (((data.global.initMatch(data.getStateIndex()) != 0)
                                    && (data.global.initMatch(data.getStateIndex())
                                    != Integer.MAX_VALUE)) || (i
                                    != 0)) {
                                printer.println(" {");
                                printer.println("    jjmatchedKind = " + kindToPrint + ";");
                                printer.println("    jjmatchedPos = " + i + ";");
                                printer.println("}");
                            } else {
                                printer.println("jjmatchedKind = " + kindToPrint + ";");
                            }
                        }
                    }
                }

                if (info.hasValidKindCnt()) {
                    var atLeastOne = false;

                    if (i == 0) {
                        printer.print("return jjMoveStringLiteralDfa" + (i + 1) + data.getLexerStateSuffix() + "(");
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
                        printer.print("return jjMoveStringLiteralDfa" + (i + 1) + data.getLexerStateSuffix() + "(");

                        for (j = 0; j < (maxLongsReqd - 1); j++) {
                            if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
                                if (atLeastOne)
                                    printer.print(", ");
                                else
                                    atLeastOne = true;

                                if (info.validKinds[j] != 0L)
                                    printer.print("active" + j + ", " + toHexString(info.validKinds[j]));
                                else
                                    printer.print("active" + j + ", 0L");
                            }
                        }

                        if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
                            if (atLeastOne)
                                printer.print(", ");
                            if (info.validKinds[j] != 0L)
                                printer.print("active" + j + ", " + toHexString(info.validKinds[j]));
                            else
                                printer.print("active" + j + ", 0L");
                        }
                    }

                    printer.println(");");
                } else { // A very special case.
                    if ((i == 0) && data.isMixedState()) {

                        if (data.generatedStates() != 0) {
                            printer.println("return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", 0);");
                        } else {
                            printer.println("return 1;");
                        }
                    } else if (i != 0) // No more str literals to look for
                    {
                        printer.println("break;");
                        startNfaNeeded = true;
                    }
                }

                printer.outdent();
                printer.println("}");
            }

            printer.println("default: {");
            printer.indent();

            if (data.global.options().getDebugTokenManager()) {
                printer.println("    fprintf(debugStream, \"   No string literal matches possible.\");");
            }

            if (data.generatedStates() != 0) {
                if (i == 0) {
                    // This means no string literal is possible. Just move nfa with this guy and return.
                    printer.println("return jjMoveNfa" + data.getLexerStateSuffix() + "("
                            + InitStateName(data) + ", 0);");
                } else {
                    printer.println("break;");
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

                    printer.print("return jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ");
                    for (k = 0; k < (maxLongsReqd - 1); k++) {
                        if (i <= data.getMaxLenForActive(k))
                            printer.print("active" + k + ", ");
                        else
                            printer.print("0L, ");
                    }
                    if (i <= data.getMaxLenForActive(k))
                        printer.println("active" + k + ");");
                    else
                        printer.println("0L);");
                } else if (data.generatedStates() != 0)
                    printer.println("return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", " + i + ");");
                else
                    printer.println("return " + (i + 1) + ";");
            }

            printer.outdent();
            printer.println("}");
        }

        if (!data.isMixedState() && (data.generatedStates() != 0) && data.getCreateStartNfa()) {
            DumpStartWithStates(printer, data);
        }
    }

    @Override
    protected final void dumpMoveNfa(LinePrinter printer, NfaStateData data) {
        printer.println();
        printer.print("int " + data.getParserName() + "TokenManager::jjMoveNfa" + data.getLexerStateSuffix()
                + "(int startState, int curPos) {");
        printer.indent();

        if (data.generatedStates() == 0) {
            printer.println("return curPos;");
            printer.outdent();
            printer.println("}");
            return;
        }

        if (data.isMixedState()) {
            printer.print("""
                    int strKind = jjmatchedKind;
                    int strPos = jjmatchedPos;
                    int seenUpto;
                    reader->backup(seenUpto = curPos + 1);
                    assert(!reader->endOfInput());
                    curChar = reader->read(); // UTF8: Support Unicode
                    curPos = 0;
                    """);
        }

        printer.println("int startsAt = 0;");
        printer.println("jjnewStateCnt = " + data.generatedStates() + ";");
        printer.println("int i = 1;");
        printer.println("jjstateSet[0] = startState;");

        if (data.global.options().getDebugTokenManager()) {
            printer.println("fprintf(debugStream, \"   Starting NFA to match one of : %s\\n\", jjKindsForStateVector(curLexState, jjstateSet, 0, 1).c_str());");
        }

        if (data.global.options().getDebugTokenManager()) {
            printer.println("fprintf(debugStream, "
                    + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
                    + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
                    + "reader->getEndLine(), reader->getEndColumn());");
        }

        printer.println("int kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
        printer.println("for (;;) {");
        printer.indent();
        printer.println("if (++jjround == 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
        printer.println("    ReInitRounds();");
        printer.println("if (curChar < 64) {");

        printer.indent();
        DumpAsciiMoves(printer, data, 0);
        printer.outdent();

        printer.println("} else if (curChar < 128) {");

        printer.indent();
        DumpAsciiMoves(printer, data, 1);
        printer.outdent();

        printer.println("} else {");

        printer.indent();
        DumpCharAndRangeMoves(printer, data);
        printer.outdent();

        printer.println("}");
        printer.println("if (kind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ") {");
        printer.println("    jjmatchedKind = kind;");
        printer.println("    jjmatchedPos = curPos;");
        printer.println("    kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
        printer.println("}");
        printer.println("curPos++;");

        if (data.global.options().getDebugTokenManager()) {
            printer.println("if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
            printer.println("    fprintf(debugStream, \"   Currently matched the first %d characters as a \\\"%s\\\" token.\\n\",  (jjmatchedPos + 1),  addUnicodeEscapes(tokenImage[jjmatchedKind]).c_str());");
        }

        printer.println("if ((i = jjnewStateCnt), (jjnewStateCnt = startsAt), (i == (startsAt = " + data.generatedStates() + " - startsAt)))");
        printer.indent();
        if (data.isMixedState()) {
            printer.println("break;");
        } else {
            printer.println("return curPos;");
        }
        printer.outdent();

        if (data.global.options().getDebugTokenManager()) {
            printer.println("fprintf(debugStream, \"   Possible kinds of longer matches : %s\\n\", jjKindsForStateVector(curLexState, jjstateSet, startsAt, i).c_str());");
        }

        if (data.isMixedState())
            printer.println("if (reader->endOfInput()) { break; }");
        else
            printer.println("if (reader->endOfInput()) { return curPos; }");
        printer.println("curChar = reader->read(); // UTF8: Support Unicode");

        if (data.global.options().getDebugTokenManager()) {
            printer.println("fprintf(debugStream, "
                    + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
                    + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
                    + "reader->getEndLine(), reader->getEndColumn());");
        }
        printer.outdent();
        printer.println("}");

        if (data.isMixedState()) {
            printer.print("""
                    if (jjmatchedPos > strPos)
                        return curPos;
                    
                    int toRet = MAX(curPos, seenUpto);
                    if (curPos < toRet)
                        for (i = toRet - MIN(curPos, seenUpto); i-- > 0; ) {
                            assert(!reader->endOfInput());
                            curChar = reader->read();
                        } // UTF8: Support Unicode
                    
                    if (jjmatchedPos < strPos) {
                        jjmatchedKind = strKind;
                        jjmatchedPos = strPos;
                    } else if (jjmatchedPos == strPos && jjmatchedKind > strKind)
                        jjmatchedKind = strKind;
                    
                    return toRet;
                    """);
        }

        printer.outdent();
        printer.println("}");
    }

    protected void getRegExp(LinePrinter printer, int i, List<RExpression> expressions, boolean isImage) {
        if (i == 0)
            CppLexerGenerator.printCharArray(printer, "<EOF>");
        else {
            var re = expressions.get(i - 1);
            if (re instanceof RStringLiteral rl) {
                CppLexerGenerator.printCharArray(printer, isImage ? rl.getImage() : "<" + re.getLabel() + ">");
            } else if (!re.getLabel().isEmpty()) {
                CppLexerGenerator.printCharArray(printer, "<" + re.getLabel() + ">");
            } else if (re.getTokenKind() == TokenKind.TOKEN) {
                JavaCCErrors.warning(re,
                        "Consider giving this non-string token a label for better error reporting.");
            } else {
                CppLexerGenerator.printCharArray(printer,
                        "<token of kind " + re.getOrdinal() + ">");
            }
        }
    }

    // Used by the CPP code generatror
    private static void printCharArray(LinePrinter printer, String s) {
        for (int i = 0; i < s.length(); i++) {
            printer.print("0x" + Integer.toHexString(s.charAt(i)) + ", ");
        }
    }

    private static void getTextAsChars(String text, LinePrinter printer) {
        List<String> chars = new ArrayList<>();
        for (int j = 0; j < text.length(); j++) {
            chars.add("0x" + Integer.toHexString(text.charAt(j)));
        }
        printer.print(String.join(", ", chars));
    }

    @Override
    protected String toHexString(long value) {
        return "0x" + Long.toHexString(value) + "ULL";
    }
}
