// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.generator.cpp;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.generator.GetNextTokenEmitter;
import org.hivevm.waggle.generator.LexerGenerator;
import org.hivevm.waggle.generator.TargetSyntax;
import org.hivevm.waggle.lexer.LexerData;
import org.hivevm.waggle.model.Action;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.RStringLiteral;

/**
 * How C++ spells {@code getNextToken} (ADR-0017).
 */
class CppGetNextTokenEmitter extends GetNextTokenEmitter {

    CppGetNextTokenEmitter(TargetSyntax syntax, LexerGenerator tokens) {
        super(syntax, tokens);
    }

    @Override
    protected void printSkipSingles(LinePrinter printer, LexerData data, int state) {
        // the backup(0) is there to make the JIT happy
        printer.println("{");
        printer.indent();
        printer.println("reader->backup(0);");

        long lower = data.singlesToSkip(state).asciiMoves[0];
        long upper = data.singlesToSkip(state).asciiMoves[1];
        if ((lower != 0L) && (upper != 0L)) {
            printer.print("while ((curChar < 64 && (" + Long.toHexString(lower)
                    + " & (1L << curChar)) != 0L) || \n"
                    + "          (curChar >> 6) == 1 && (" + this.syntax.toHexString(upper)
                    + " & (1L << (curChar & 077))) != 0L)");
        } else if (upper == 0L) {
            printer.print("while (curChar <= " + (int) TargetSyntax.MaxChar(lower)
                    + " && (" + this.syntax.toHexString(lower) + " & (1L << curChar)) != 0L)");
        } else if (lower == 0L) {
            printer.print("while (curChar > 63 && curChar <= "
                    + (TargetSyntax.MaxChar(upper) + 64) + " && (" + this.syntax.toHexString(upper)
                    + " & (1L << (curChar & 077))) != 0L)");
        }

        // the loop body must be braced: it advances curChar, and without the braces only the
        // end-of-input check would be repeated -- forever
        printer.println(" {");
        printer.indent();

        if (data.options().getDebugTokenManager()) {
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

    @Override
    protected void printInitialMatch(LinePrinter printer, LexerData data, int state) {
        if (hasInitialMatch(data, state)) {
            if (data.options().getDebugTokenManager()) {
                printer.println("fprintf(debugStream, \"   Matched the empty string as %s token.\\n\", addUnicodeEscapes(tokenImages["
                        + data.initMatch(state) + "]).c_str());");
            }
            printer.println("jjmatchedKind = " + data.initMatch(state) + ";");
            printer.println("jjmatchedPos = -1;");
            printer.println("curPos = 0;");
        } else {
            printer.println("jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
            printer.println("jjmatchedPos = 0;");
        }
    }

    @Override
    protected void printCanMatchAnyChar(LinePrinter printer, LexerData data, int state) {
        int kind = data.canMatchAnyChar(state);
        if (hasInitialMatch(data, state)) {
            printer.println("if (jjmatchedPos < 0 || (jjmatchedPos == 0 && jjmatchedKind > " + kind
                    + ")) {");
        } else {
            printer.println("if (jjmatchedPos == 0 && jjmatchedKind > " + kind + ") {");
        }
        printer.indent();

        if (data.options().getDebugTokenManager()) {
            printer.println("fprintf(debugStream, \"   Current character matched as a %s token.\\n\", addUnicodeEscapes(tokenImages["
                    + kind + "]).c_str());");
        }
        printer.println("jjmatchedKind = " + kind + ";");

        if (hasInitialMatch(data, state)) {
            printer.println("jjmatchedPos = 0;");
        }

        printer.outdent();
        printer.println("}");
    }

    @Override
    protected void printBackupBlock(LinePrinter printer, LexerData data) {
        printer.println("if (jjmatchedPos + 1 < curPos) {");
        printer.indent();

        if (data.options().getDebugTokenManager()) {
            printer.println("fprintf(debugStream, "
                    + "\"   Putting back %d characters into the input stream.\\n\", (curPos - jjmatchedPos - 1));");
        }

        printer.println("reader->backup(curPos - jjmatchedPos - 1);");
        printer.outdent();
        printer.println("}");
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
            printer.println("if (jjnewLexState[jjmatchedKind] != -1)");
            printer.println("    curLexState = jjnewLexState[jjmatchedKind];");
        }
        printer.println("return matchedToken;");
    }

    @Override
    protected void printSkipBranch(LinePrinter printer, LexerData data) {
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
            printer.println("if (jjnewLexState[jjmatchedKind] != -1)");
            printer.println("    curLexState = jjnewLexState[jjmatchedKind];");
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
            printer.println("if (jjnewLexState[jjmatchedKind] != -1)");
            printer.println("    curLexState = jjnewLexState[jjmatchedKind];");
        }
        printer.println("curPos = 0;");
        printer.println("jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");

        printer.println("if (!reader->endOfInput()) {");
        printer.println("    curChar = reader->read(); // UTF8: Support Unicode");

        if (data.options().getDebugTokenManager()) {
            this.syntax.printDebugCurrentCharacter(printer, data);
        }
        printer.println("    continue;");
        printer.println("}");
    }

    /** The C++ token manager reports the lexical error from its template, not from here. */
    @Override
    protected void printLexicalErrorEpilogue(LinePrinter printer) {
    }
}
