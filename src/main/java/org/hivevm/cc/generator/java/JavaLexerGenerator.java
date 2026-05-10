// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

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

import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

/**
 * Generate lexer.
 */
class JavaLexerGenerator extends LexerGenerator {

    public JavaLexerGenerator() {
        super(Language.JAVA);
    }

    @Override
    protected final void generate(LexerData data, Context options) {
        options.set("STATES_FOR_STATE", () -> getStatesForState(data));
        options.set("KIND_FOR_STATE", () -> getKindForState(data));
        options.set("DUMP_LITERAL_IMAGES", p -> dump_literal_images(data, p));

        JavaTemplate.LEXER.render(options);
    }

    protected SourceProvider getConstantsTemplate() {
        return JavaTemplate.PARSER_CONSTANTS;
    }

    protected void dumpStaticVarDeclarations(LinePrinter printer, LexerData data) {
        if (data.maxLexStates() > 1) {
            printer.println();
            printer.print("public static final int[] jjnewLexState = {");
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
            printer.print("static final long[] jjtoToken = {");
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
            printer.print("static final long[] jjtoSkip = {");
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
            printer.print("static final long[] jjtoSpecial = {");
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
            printer.print("static final long[] jjtoMore = {");
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
            printer.println("    TokenLexicalActions(matchedToken);");
        }

        printer.println("    return matchedToken;");
        printer.println("}");

        if (data.hasMoreActions() || data.hasSkipActions() || data.hasTokenActions()) {
            printer.println("image = jjimage;");
            printer.println("image.setLength(0);");
            printer.println("jjimageLen = 0;");
        }

        printer.println();

        if (data.hasMore()) {
            printer.println("for (; ; ) {");
            printer.indent();
        }

        // this also sets up the start state of the nfa
        if (data.maxLexStates() > 1) {
            printer.println("switch (curLexState) {");
            printer.indent();
        }

        for (int i = 0; i < data.maxLexStates(); i++) {
            if (data.maxLexStates() > 1) {
                printer.println("case " + i + ": {");
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
                    printer.println("while ((curChar < 64" + " && (0x" + Long.toHexString(
                            data.singlesToSkip(i).asciiMoves[0])
                            + "L & (1L << curChar)) != 0L) || \n"
                            + "          (curChar >> 6) == 1"
                            + " && (0x"
                            + Long.toHexString(data.singlesToSkip(i).asciiMoves[1])
                            + "L & (1L << (curChar & 077))) != 0L)");
                } else if (data.singlesToSkip(i).asciiMoves[1] == 0L) {
                    printer.println("while (curChar <= " + (int) LexerGenerator.MaxChar(
                            data.singlesToSkip(i).asciiMoves[0])
                            + " && (0x" + Long.toHexString(data.singlesToSkip(i).asciiMoves[0])
                            + "L & (1L << curChar)) != 0L)");
                } else if (data.singlesToSkip(i).asciiMoves[0] == 0L) {
                    printer.println("while (curChar > 63 && curChar <= "
                            + (LexerGenerator.MaxChar(data.singlesToSkip(i).asciiMoves[1]) + 64)
                            + " && (0x"
                            + Long.toHexString(data.singlesToSkip(i).asciiMoves[1])
                            + "L & (1L << (curChar & 077))) != 0L)");
                }

                if (data.options().getDebugTokenManager()) {
                    printer.println(" {");
                    printer.indent();
                    printer.println("debugStream.println("
                            + (data.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
                            + "\"Skipping character : \" + TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \")\");");
                }

                printer.println("curChar = input_stream.BeginToken();");

                if (data.options().getDebugTokenManager()) {
                    printer.outdent();
                    printer.println("}");
                }

                printer.outdent();
                printer.println("} catch (java.io.IOException e1) { continue EOFLoop; }");
            }

            if ((data.initMatch(i) != Integer.MAX_VALUE) && (data.initMatch(i) != 0)) {
                if (data.options().getDebugTokenManager()) {
                    printer.println("debugStream.println(\"   Matched the empty string as \" + tokenImage["
                            + data.initMatch(i) + "] + \" token.\");");
                }

                printer.println("jjmatchedKind = " + data.initMatch(i) + ";");
                printer.println("jjmatchedPos = -1;");
                printer.println("curPos = 0;");
            } else {
                printer.println("jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
                printer.println("jjmatchedPos = 0;");
            }

            if (data.options().getDebugTokenManager()) {
                printer.println("debugStream.println("
                        + (data.maxLexStates() > 1
                        ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
                        + "\"Current character : \" + TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \") "
                        + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());");
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
                    printer.println("debugStream.println(\"Current character matched as a \" + tokenImage["
                            + data.canMatchAnyChar(i) + "] + \" token.\");");
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
                printer.println("debugStream.println("
                        + "\"   Putting back \" + (curPos - jjmatchedPos - 1) + \" characters into the input stream.\");");
            }

            printer.println("input_stream.backup(curPos - jjmatchedPos - 1);");

            printer.outdent();
            printer.println("}");

            if (data.options().getDebugTokenManager()) {
                printer.println("debugStream.println("
                        + "\"****** FOUND A \" + tokenImage[jjmatchedKind] + \" MATCH "
                        + "(\" + TokenException.addEscapes(new String(input_stream.GetSuffix(jjmatchedPos + 1))) + "
                        + "\") ******\\n\");");
            }

            if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
                printer.println("if ((jjtoToken[jjmatchedKind >> 6] & "
                        + "(1L << (jjmatchedKind & 077))) != 0L) {");
                printer.indent();
            }

            printer.println("matchedToken = jjFillToken();");

            if (data.hasSpecial()) {
                printer.println("matchedToken.specialToken = specialToken;");
            }

            if (data.hasTokenActions()) {
                printer.println("TokenLexicalActions(matchedToken);");
            }

            if (data.maxLexStates() > 1) {
                printer.println("if (jjnewLexState[jjmatchedKind] != -1)");
                printer.indent();
                printer.println("curLexState = jjnewLexState[jjmatchedKind];");
                printer.outdent();
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
                                + "(1L << (jjmatchedKind & 077))) != 0L) {");
                        printer.indent();

                        printer.println("matchedToken = jjFillToken();");

                        printer.println("if (specialToken == null)");
                        printer.indent();
                        printer.println("specialToken = matchedToken;");
                        printer.outdent();
                        printer.println("else {");
                        printer.indent();
                        printer.println("matchedToken.specialToken = specialToken;");
                        printer.println("specialToken = (specialToken.next = matchedToken);");
                        printer.outdent();
                        printer.println("}");

                        if (data.hasSkipActions()) {
                            printer.println("SkipLexicalActions(matchedToken);");
                        }

                        printer.outdent();
                        printer.print("}");

                        if (data.hasSkipActions()) {
                            printer.println(" else");
                            printer.indent();
                            printer.println("SkipLexicalActions(null);");
                            printer.outdent();
                        } else
                            printer.println();
                    } else if (data.hasSkipActions()) {
                        printer.println("SkipLexicalActions(null);");
                    }

                    if (data.maxLexStates() > 1) {
                        printer.println("if (jjnewLexState[jjmatchedKind] != -1)");
                        printer.indent();
                        printer.println("curLexState = jjnewLexState[jjmatchedKind];");
                        printer.outdent();
                    }

                    printer.println("continue EOFLoop;");
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
                        printer.indent();
                        printer.println("curLexState = jjnewLexState[jjmatchedKind];");
                        printer.outdent();
                    }
                    printer.println("curPos = 0;");
                    printer.println("jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");

                    printer.println("try {");
                    printer.indent();
                    printer.println("curChar = input_stream.readChar();");

                    if (data.options().getDebugTokenManager()) {
                        printer.println("debugStream.println("
                                + (data.maxLexStates() > 1
                                ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
                                + "\"Current character : \" + "
                                + "TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \") "
                                + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());");
                    }
                    printer.println("continue;");
                    printer.outdent();
                    printer.println("} catch (java.io.IOException e1) {");
                    printer.println("}");
                }
            }

            printer.outdent();
            printer.print("""
                    }
                    int error_line = input_stream.getEndLine();
                    int error_column = input_stream.getEndColumn();
                    String error_after = null;
                    boolean EOFSeen = false;
                    try {
                        input_stream.readChar();
                        input_stream.backup(1);
                    } catch (java.io.IOException e1) {
                        EOFSeen = true;
                        error_after = curPos <= 1 ? "" : input_stream.GetImage();
                        if (curChar == '\\n' || curChar == '\\r') {
                            error_line++;
                            error_column = 0;
                        } else
                            error_column++;
                    }
                    if (!EOFSeen) {
                        input_stream.backup(1);
                        error_after = curPos <= 1 ? "" : input_stream.GetImage();
                    }
                    throw new TokenException(EOFSeen, curLexState, error_line, error_column, error_after, curChar, TokenException.LEXICAL_ERROR);
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
                    printer.println("         if (jjmatchedPos == -1)");
                    printer.println("         {");
                    printer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
                    printer.println("                jjemptyLineNo[" + data.getState(i)
                            + "] == input_stream.getBeginLine() &&");
                    printer.println("                jjemptyColNo[" + data.getState(i)
                            + "] == input_stream.getBeginColumn())");
                    printer.println("               throw new TokenException("
                            + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                            + "at line \" + input_stream.getBeginLine() + \", "
                            + "column \" + input_stream.getBeginColumn() + \".\"), TokenException.LOOP_DETECTED);");
                    printer.println("            jjemptyLineNo[" + data.getState(i)
                            + "] = input_stream.getBeginLine();");
                    printer.println("            jjemptyColNo[" + data.getState(i)
                            + "] = input_stream.getBeginColumn();");
                    printer.println("            jjbeenHere[" + data.getState(i) + "] = true;");
                    printer.println("         }");
                }

                if (((act = data.actions(i)) == null) || act.getActionTokens().isEmpty()) {
                    break;
                }

                printer.println("         image.append");
                if (data.getImage(i) != null) {
                    printer.println("(jjstrLiteralImages[" + i + "]);");
                    printer.println("        lengthOfMatch = jjstrLiteralImages[" + i + "].length();");
                } else {
                    printer.println("(input_stream.GetSuffix(jjimageLen + (lengthOfMatch = jjmatchedPos + 1)));");
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
                    printer.println("         if (jjmatchedPos == -1)");
                    printer.println("         {");
                    printer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
                    printer.println("                jjemptyLineNo[" + data.getState(i)
                            + "] == input_stream.getBeginLine() &&");
                    printer.println("                jjemptyColNo[" + data.getState(i)
                            + "] == input_stream.getBeginColumn())");
                    printer.println("               throw new TokenException("
                            + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                            + "at line \" + input_stream.getBeginLine() + \", "
                            + "column \" + input_stream.getBeginColumn() + \".\"), TokenException.LOOP_DETECTED);");
                    printer.println("            jjemptyLineNo[" + data.getState(i)
                            + "] = input_stream.getBeginLine();");
                    printer.println("            jjemptyColNo[" + data.getState(i)
                            + "] = input_stream.getBeginColumn();");
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
                    printer.println("(input_stream.GetSuffix(jjimageLen));");
                }

                printer.println("         jjimageLen = 0;");
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
                    printer.println("         if (jjmatchedPos == -1)");
                    printer.println("         {");
                    printer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
                    printer.println("                jjemptyLineNo[" + data.getState(i)
                            + "] == input_stream.getBeginLine() &&");
                    printer.println("                jjemptyColNo[" + data.getState(i)
                            + "] == input_stream.getBeginColumn())");
                    printer.println("               throw new TokenException("
                            + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                            + "at line \" + input_stream.getBeginLine() + \", "
                            + "column \" + input_stream.getBeginColumn() + \".\"), TokenException.LOOP_DETECTED);");
                    printer.println("            jjemptyLineNo[" + data.getState(i)
                            + "] = input_stream.getBeginLine();");
                    printer.println("            jjemptyColNo[" + data.getState(i)
                            + "] = input_stream.getBeginColumn();");
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
                        printer.println("(input_stream.GetSuffix(jjimageLen + (lengthOfMatch = jjmatchedPos + 1)));");
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

        printer.print("static final int[] jjnextStates = {");
        printer.indent();
        if (!data.getOrderedStateSet().isEmpty()) {
            for (int[] set : data.getOrderedStateSet()) {
                for (int element : set) {
                    if ((cnt++ % 16) == 0) {
                        printer.println();
                    }

                    printer.print(element + ", ");
                }
            }
        } else {
            printer.print("0");
        }

        printer.println();
        printer.outdent();
        printer.println("};");
    }

    private static void dump_literal_images(LexerData data, LinePrinter printer) {
        if (data.getImageCount() <= 0) {
            return;
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
                    printer.println();
                    charCnt = 0;
                }

                printer.print("null, ");
                continue;
            }

            String toPrint = "\"";
            for (int j = 0; j < image.length(); j++) {
                if (image.charAt(j) <= 0xff) {
                    toPrint += ("\\" + Integer.toOctalString(image.charAt(j)));
                } else {
                    String hexVal = Integer.toHexString(image.charAt(j));
                    if (hexVal.length() == 3) {
                        hexVal = "0" + hexVal;
                    }
                    toPrint += ("\\u" + hexVal);
                }
            }

            toPrint += ("\", ");

            if ((charCnt += toPrint.length()) >= 80) {
                printer.println();
                charCnt = 0;
            }

            printer.print(toPrint);
        }

        while (++i < data.maxOrdinal()) {
            if ((charCnt += 6) > 80) {
                printer.println();
                charCnt = 0;
            }

            printer.print("null, ");
        }
    }

    private void DumpStartWithStates(LinePrinter printer, NfaStateData data) {
        printer.println();
        printer.println("private int jjStartNfaWithStates" + data.getLexerStateSuffix()
                + "(int pos, int kind, int state) {");
        printer.indent();
        printer.println("jjmatchedKind = kind;");
        printer.println("jjmatchedPos = pos;");

        if (data.global.options().getDebugTokenManager()) {
            printer.println(
                    "debugStream.println(\"   No more string literal token matches are possible.\");");
            printer.println("debugStream.println(\"   Currently matched the first \" + (jjmatchedPos + 1) + \" characters as a \" + tokenImage[jjmatchedKind] + \" token.\");");
        }

        printer.println("try {");
        printer.println("    curChar = input_stream.readChar();");
        printer.println("} catch (java.io.IOException e) {");
        printer.println("    return pos + 1;");
        printer.println("}");

        if (data.global.options().getDebugTokenManager()) {
            printer.println("debugStream.println("
                    + (data.global.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + "
                    : "")
                    + "\"Current character : \" + TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \") "
                    + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());");
        }
        printer.println("return jjMoveNfa" + data.getLexerStateSuffix() + "(state, pos + 1);");
        printer.outdent();
        printer.println("}");
    }

    @Override
    protected final void DumpHeadForCase(LinePrinter printer, int byteNum) {
        if (byteNum == 0) {
            printer.println("long l = 1L << curChar;");
        } else if (byteNum == 1) {
            printer.println("long l = 1L << (curChar & 077);");
        } else {
            printer.println("int hiByte = (curChar >> 8);");
            printer.println("int i1 = hiByte >> 6;");
            printer.println("long l1 = 1L << (hiByte & 077);");
            printer.println("int i2 = (curChar & 0xff) >> 6;");
            printer.println("long l2 = 1L << (curChar & 077);");
        }

        // printer.println(" MatchLoop: do");
        printer.println("do {");
        printer.indent();
        printer.println("switch (jjstateSet[--i]) {");
        printer.indent();
    }

    protected void dumpNonAsciiMoveMethod(LexerData data, NfaState state, LinePrinter printer) {
        for (int j = 0; j < state.loByteVec.size(); j += 2) {
            printer.println("case " + state.loByteVec.get(j) + ":");
            printer.indent();
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.loByteVec.get(j + 1)))) {
                printer.println("return ((jjbitVec" + state.loByteVec.get(j + 1) + "[i2] & l2) != 0L);");
            } else {
                printer.println("return true;");
            }
            printer.outdent();
        }

        printer.println("default:");
        printer.indent();
        for (int j = state.nonAsciiMoveIndices.length; j > 0; j -= 2) {
            boolean hasIf = false;
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.nonAsciiMoveIndices[j - 2]))) {
                printer.println("if ((jjbitVec" + state.nonAsciiMoveIndices[j - 2] + "[i1] & l1) != 0L)");
                printer.indent();
                hasIf = true;
            }
            if (!NfaState.AllBitsSet(data.getAllBitVectors(state.nonAsciiMoveIndices[j - 1]))) {
                printer.println("if ((jjbitVec" + state.nonAsciiMoveIndices[j - 1] + "[i2] & l2) == 0L)");
                printer.indent();
                printer.println("return false;");
                printer.outdent();
                printer.println("else");
                printer.indent();
                hasIf = true;
            }
            printer.println("return true;");
            if (hasIf)
                printer.outdent();
        }
        printer.outdent();
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
            } else {
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
    protected final void dumpNfaStartStatesCode(LinePrinter printer, NfaStateData data,
                                                Hashtable<String, long[]>[] statesForPos) {
        if (data.getMaxStrKind() == 0) // No need to generate this function
            return;

        int i, maxKindsReqd = (data.getMaxStrKind() / 64) + 1;
        boolean condGenerated = false;
        int ind;

        printer.println();
        printer.print("private final int jjStopStringLiteralDfa" + data.getLexerStateSuffix() + "(int pos, ");
        for (i = 0; i < (maxKindsReqd - 1); i++) {
            printer.print("long active" + i + ", ");
        }
        printer.println("long active" + i + ") {");
        printer.indent();

        if (data.global.options().getDebugTokenManager()) {
            printer.println("debugStream.println(\"   No more string literal token matches are possible.\");");
        }

        printer.println("switch (pos) {");
        printer.indent();

        for (i = 0; i < (data.getMaxLen() - 1); i++) {
            if (statesForPos[i] == null) {
                continue;
            }

            printer.println("case " + i + ":");
            printer.indent();

            for (String stateSetString : statesForPos[i].keySet()) {
                long[] actives = statesForPos[i].get(stateSetString);

                for (int j = 0; j < maxKindsReqd; j++) {
                    if (actives[j] == 0L) {
                        continue;
                    }

                    if (condGenerated) {
                        printer.print(" || ");
                    } else {
                        printer.print("if (");
                    }

                    condGenerated = true;

                    printer.print("(active" + j + " & " + toHexString(actives[j]) + ") != 0L");
                }

                if (condGenerated) {
                    printer.print(")");

                    String kindStr = stateSetString.substring(0,
                            ind = stateSetString.indexOf(", "));
                    String afterKind = stateSetString.substring(ind + 2);
                    int jjmatchedPos = Integer.parseInt(
                            afterKind.substring(0, afterKind.indexOf(", ")));

                    if (!kindStr.equals(String.valueOf(Integer.MAX_VALUE))) {
                        printer.println(" {");
                    } else {
                        printer.println();
                    }
                    printer.indent();

                    if (!kindStr.equals(String.valueOf(Integer.MAX_VALUE))) {
                        if (i == 0) {
                            printer.println("jjmatchedKind = " + kindStr + ";");

                            if (((data.global.initMatch(data.getStateIndex()) != 0)
                                    && (data.global.initMatch(data.getStateIndex())
                                    != Integer.MAX_VALUE))) {
                                printer.println("jjmatchedPos = 0;");
                            }
                        } else if (i == jjmatchedPos) {
                            if (data.isSubStringAtPos(i)) {
                                printer.println("if (jjmatchedPos != " + i + ")  {");
                                printer.indent();
                                printer.println("jjmatchedKind = " + kindStr + ";");
                                printer.println("jjmatchedPos = " + i + ";");
                                printer.outdent();
                                printer.println("}");
                            } else {
                                printer.println("jjmatchedKind = " + kindStr + ";");
                                printer.println("jjmatchedPos = " + i + ";");
                            }
                        } else {
                            if (jjmatchedPos > 0) {
                                printer.print("if (jjmatchedPos < " + jjmatchedPos + ")");
                            } else {
                                printer.print("if (jjmatchedPos == 0)");
                            }
                            printer.println(" {");
                            printer.indent();
                            printer.println("jjmatchedKind = " + kindStr + ";");
                            printer.println("jjmatchedPos = " + jjmatchedPos + ";");
                            printer.outdent();
                            printer.println("}");
                        }
                    }

                    kindStr = stateSetString.substring(0, ind = stateSetString.indexOf(", "));
                    afterKind = stateSetString.substring(ind + 2);
                    stateSetString = afterKind.substring(afterKind.indexOf(", ") + 2);

                    if (stateSetString.equals("null;")) {
                        printer.println("return -1;");
                    } else {
                        printer.println("return " + getCompositeStateSet(data, stateSetString) + ";");
                    }

                    printer.outdent();
                    if (!kindStr.equals(String.valueOf(Integer.MAX_VALUE))) {
                        printer.println("}");
                    }
                    condGenerated = false;
                }
            }

            printer.println("return -1;");
            printer.outdent();
        }

        printer.println("default:");
        printer.indent();
        printer.println("return -1;");
        printer.outdent();
        printer.outdent();
        printer.println("}");
        printer.outdent();
        printer.println("}");

        printer.println();
        printer.print("private final int jjStartNfa" + data.getLexerStateSuffix());
        printer.print("(int pos, ");
        for (i = 0; i < (maxKindsReqd - 1); i++) {
            printer.print("long active" + i + ", ");
        }
        printer.print("long active" + i + ")");
        printer.println(" {");

        if (data.isMixedState()) {
            if (data.generatedStates() != 0) {
                printer.println("    return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", pos + 1);");
            } else {
                printer.println("    return pos + 1;");
            }

            printer.println("}");
            return;
        }

        printer.print("    return jjMoveNfa" + data.getLexerStateSuffix() + "(jjStopStringLiteralDfa"
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
            printer.println("private int jjMoveStringLiteralDfa0" + data.getLexerStateSuffix() + "() {");
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
            printer.println();
            printer.println("private int " + "jjStopAtPos(int pos, int kind) {");
            printer.indent();
            printer.println("jjmatchedKind = kind;");
            printer.println("jjmatchedPos = pos;");

            if (data.global.options().getDebugTokenManager()) {
                printer.println("debugStream.println(\"No more string literal token matches are possible.\");");
                printer.println("debugStream.println(\"Currently matched the first \" + (jjmatchedPos + 1) + " + "\" characters as a \" + tokenImage[jjmatchedKind] + \" token.\");");
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
            printer.print("private int jjMoveStringLiteralDfa" + i + data.getLexerStateSuffix() + "(");

            if (i != 0) {
                if (i == 1) {
                    for (j = 0; j < (maxLongsReqd - 1); j++) {
                        if (i <= data.getMaxLenForActive(j)) {
                            if (atLeastOne_) {
                                printer.print(", ");
                            } else {
                                atLeastOne_ = true;
                            }
                            printer.print("long active" + j);
                        }
                    }

                    if (i <= data.getMaxLenForActive(j)) {
                        if (atLeastOne_) {
                            printer.print(", ");
                        }
                        printer.print("long active" + j);
                    }
                } else {
                    for (j = 0; j < (maxLongsReqd - 1); j++) {
                        if (i <= (data.getMaxLenForActive(j) + 1)) {
                            if (atLeastOne_) {
                                printer.print(", ");
                            } else {
                                atLeastOne_ = true;
                            }
                            printer.print("long old" + j + ", long active" + j);
                        }
                    }

                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        if (atLeastOne_) {
                            printer.print(", ");
                        }
                        printer.print("long old" + j + ", long active" + j);
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
                    printer.println("    debugStream.println(\"   Currently matched the first \" + " + "(jjmatchedPos + 1) + \" characters as a \" + tokenImage[jjmatchedKind] + \" token.\");");
                    printer.println("    debugStream.println(\"   Possible string literal matches : { \"");

                    for (int vecs = 0; vecs < ((data.getMaxStrKind() / 64) + 1); vecs++) {
                        if (i <= data.getMaxLenForActive(vecs)) {
                            printer.println(" +");
                            printer.print("         jjKindsForBitVector(" + vecs + ", ");
                            printer.print("active" + vecs + ") ");
                        }
                    }

                    printer.println(" + \" } \");");
                }

                printer.println("try {");
                printer.println("    curChar = input_stream.readChar();");
                printer.println("} catch (java.io.IOException e) {");
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
                        printer.println("    debugStream.println(\"   Currently matched the first \" + " + "(jjmatchedPos + 1) + \" characters as a \" + tokenImage[jjmatchedKind] + \" token.\");");
                    }
                    printer.println("return " + i + ";");
                } else if (data.generatedStates() != 0)
                    printer.println("return jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", " + (i - 1) + ");");
                else
                    printer.println("return " + i + ";");

                printer.outdent();
                printer.println("}");
            }

            if ((i != 0) && data.global.options().getDebugTokenManager()) {
                printer.println("debugStream.println("
                        + (data.global.maxLexStates() > 1
                        ? "\"<\" + lexStateNames[curLexState] + \">\" + "
                        : "")
                        + "\"Current character : \" + TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \") "
                        + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());");
            }

            printer.println("switch (curChar) {");
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
                                printer.print("((active" + j + " & 0x" + Long.toHexString(1L << k) + "L) != 0L)");
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
                                printer.indent();
                                printer.println("jjmatchedKind = " + kindToPrint + ";");
                                printer.println("jjmatchedPos = " + i + ";");
                                printer.outdent();
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
                                printer.print("active" + j + ", 0x" + Long.toHexString(info.validKinds[j]) + "L");
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
                printer.println("    debugStream.println(\"   No string literal matches possible.\");");
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
        printer.println("private int jjMoveNfa" + data.getLexerStateSuffix()
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
                    input_stream.backup(seenUpto = curPos + 1);
                    try {
                        curChar = input_stream.readChar();
                    } catch (java.io.IOException e) {
                        throw new Error("Internal Error");
                    }
                    curPos = 0;
                    """);
        }

        printer.println("int startsAt = 0;");
        printer.println("jjnewStateCnt = " + data.generatedStates() + ";");
        printer.println("int i = 1;");
        printer.println("jjstateSet[0] = startState;");

        if (data.global.options().getDebugTokenManager()) {
            printer.println("debugStream.println(\"   Starting NFA to match one of : \" + " + "jjKindsForStateVector(curLexState, jjstateSet, 0, 1));");
        }

        if (data.global.options().getDebugTokenManager()) {
            printer.println("debugStream.println("
                    + (data.global.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + "
                    : "")
                    + "\"Current character : \" + TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \") "
                    + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());");
        }

        printer.println("int kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
        printer.println("for (; ; ) {");
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
            printer.println("   debugStream.println("
                    + "\"   Currently matched the first \" + (jjmatchedPos + 1) + \" characters as"
                    + " a \" + tokenImage[jjmatchedKind] + \" token.\");");
        }

        printer.println("if ((i = jjnewStateCnt) == (startsAt = " + data.generatedStates() + " - (jjnewStateCnt = startsAt)))");
        printer.indent();
        if (data.isMixedState()) {
            printer.println("break;");
        } else {
            printer.println("return curPos;");
        }
        printer.outdent();

        if (data.global.options().getDebugTokenManager()) {
            printer.println("debugStream.println(\"   Possible kinds of longer matches : \" + "
                    + "jjKindsForStateVector(curLexState, jjstateSet, startsAt, i));");
        }

        printer.println("try {");
        printer.println("    curChar = input_stream.readChar();");
        printer.println("} catch (java.io.IOException e) {");
        printer.indent();
        if (data.isMixedState())
            printer.println("break;");
        else
            printer.println("return curPos;");
        printer.outdent();
        printer.println("}");

        if (data.global.options().getDebugTokenManager()) {
            printer.println("debugStream.println("
                    + (data.global.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
                    + "\"Current character : \" + TokenException.addEscapes(String.valueOf(curChar)) + \" (\" + (int)curChar + \") "
                    + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());");
        }
        printer.outdent();
        printer.println("}");

        if (data.isMixedState()) {
            printer.print("""
                    if (jjmatchedPos > strPos)
                        return curPos;
                    
                    int toRet = Math.max(curPos, seenUpto);
                    if (curPos < toRet)
                        for (i = toRet - Math.min(curPos, seenUpto); i-- > 0; )
                            try {
                                curChar = input_stream.readChar();
                            } catch (java.io.IOException e) {
                                throw new Error("Internal Error : Please send a bug report.");
                            }
                    
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

            if (expressions.indexOf(re) < (expressions.size() - 1)) {
                printer.print(",");
            }
        }
    }
}
