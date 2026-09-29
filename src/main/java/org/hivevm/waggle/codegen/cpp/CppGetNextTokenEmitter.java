// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/LexGenCPP.java, org/javacc/parser/LexGen.java

package org.hivevm.waggle.codegen.cpp;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.codegen.GetNextTokenEmitter;
import org.hivevm.waggle.codegen.LexerGenerator;
import org.hivevm.waggle.codegen.TargetSyntax;
import org.hivevm.waggle.lexer.LexerData;

/**
 * How C++ spells {@code getNextToken} (ADR-0017).
 */
class CppGetNextTokenEmitter extends GetNextTokenEmitter {

    CppGetNextTokenEmitter(TargetSyntax syntax, LexerGenerator tokens) {
        super(syntax, tokens);
    }

    @Override
    protected void printSkipSingles(LinePrinter printer, LexerData data, int state) {
        printer.print("while (" + skipSinglesCondition(data, state) + ")");

        // the loop body must be braced: it advances curChar, and without the braces only the
        // end-of-input check would be repeated -- forever
        printer.println(" {");
        printer.indent();

        if (data.getDebugTokenManager()) {
            if (data.maxLexStates() > 1) {
                printer.println("fprintf(debugStream, \"<%s>\" , addUnicodeEscapes(lexStateNames[curLexState]).c_str());");
            }
            printer.println("fprintf(debugStream, \"Skipping character : %c(%d)\\n\", curChar, (int)curChar);");
        }

        printer.println("if (reader->endOfInput()) { goto EOFLoop; }");
        printer.println("curChar = reader->beginToken();");

        printer.outdent();
        printer.println("}");
    }

    @Override
    protected String longOne() {
        return "1ULL";
    }

    @Override
    protected void printDebugEmptyStringMatched(LinePrinter printer, int kind) {
        printer.println("fprintf(debugStream, \"   Matched the empty string as %s token.\\n\", addUnicodeEscapes(tokenImages["
                + kind + "]).c_str());");
    }

    @Override
    protected void printDebugCurrentCharacterMatched(LinePrinter printer, int kind) {
        printer.println("fprintf(debugStream, \"   Current character matched as a %s token.\\n\", addUnicodeEscapes(tokenImages["
                + kind + "]).c_str());");
    }

    @Override
    protected void printDebugPuttingBack(LinePrinter printer) {
        printer.println("fprintf(debugStream, "
                + "\"   Putting back %d characters into the input stream.\\n\", (curPos - jjmatchedPos - 1));");
    }

    @Override
    protected void printTokenBranch(LinePrinter printer, LexerData data) {
        printer.println("matchedToken = jjFillToken();");

        if (data.hasSpecial()) {
            printer.println("matchedToken->specialToken() = specialToken;");
        }
        if (data.hasTokenActions()) {
            printer.println("TokenLexicalActions(matchedToken);");
        }
        if (data.maxLexStates() > 1) {
            printNewLexState(printer);
        }
        printer.println("return matchedToken;");
    }

    @Override
    protected void printSkipBranch(LinePrinter printer, LexerData data) {
        if (data.hasMore()) {
            printer.print("else if ((jjtoSkip[jjmatchedKind >> 6] & (1ULL << (jjmatchedKind & 077))) != 0L)");
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
            printer.println("    matchedToken->specialToken() = specialToken;");
            printer.println("    specialToken = (specialToken->next() = matchedToken);");
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
            printNewLexState(printer);
        }

        printer.println("goto EOFLoop;");
        printer.outdent();
        printer.println("}");
    }

    @Override
    protected void printMoreBranch(LinePrinter printer, LexerData data) {
        if (data.hasMoreActions()) {
            printer.println("MoreLexicalActions();");
        } else if (data.hasSkipActions() || data.hasTokenActions()) {
            printer.println("jjimageLen += jjmatchedPos + 1;");
        }

        if (data.maxLexStates() > 1) {
            printNewLexState(printer);
        }
        printer.println("curPos = 0;");
        printer.println("jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");

        printer.println("if (!reader->endOfInput()) {");
        printer.println("    curChar = reader->read(); // UTF8: Support Unicode");

        if (data.getDebugTokenManager()) {
            this.syntax.printDebugCurrentCharacter(printer, data);
        }
        printer.println("    continue;");
        printer.println("}");
    }

    /**
     * Closes the branch for a match. The C++ token manager reports the lexical error from its
     * template, after the loop that accumulates MORE, so nothing matching leaves that loop. It used
     * to go round again on the same character: every lexical error hung the lexer.
     */
    @Override
    protected void printLexicalErrorEpilogue(LinePrinter printer, LexerData data) {
        printer.outdent();
        printer.println("}");
        if (data.hasMore()) {
            printer.println("break;");
        }
    }
}
