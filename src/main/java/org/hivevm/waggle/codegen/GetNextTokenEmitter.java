// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/LexGen.java, org/javacc/parser/LexGenCPP.java

package org.hivevm.waggle.codegen;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.lexer.LexerPlan;
import org.hivevm.waggle.lexer.LexerPlan.ActionCase;
import org.hivevm.waggle.lexer.LexerPlan.Dispatch;
import org.hivevm.waggle.lexer.LexerPlan.ImageSource;
import org.hivevm.waggle.lexer.LexerPlan.LexStatePlan;
import org.hivevm.waggle.lexer.LexerPlan.SkipSingles;
import org.hivevm.waggle.lexer.LexerPlan.TokenLoop;
import org.hivevm.waggle.lexer.LexerPlan.TokenName;
import org.hivevm.waggle.model.CodeText;
import org.hivevm.waggle.grammar.Token;

import java.util.List;
import java.util.function.Consumer;

/**
 * Emits {@code getNextToken}: the lexical-state switch, the skip, more and token branches, the
 * lexical actions and the lexical-error epilogue.
 *
 * <p>These used to be methods of the 3064-line {@code LexerGenerator}, reachable only by extending
 * it (ADR-0017).
 */
public class GetNextTokenEmitter {

    /** How the target spells what this emitter prints. Composed, not inherited (ADR-0017). */
    protected final TargetSyntax syntax;

    /** Prints the tokens of a grammar action verbatim. */
    protected final LexerGenerator tokens;

    public GetNextTokenEmitter(TargetSyntax syntax, LexerGenerator tokens) {
        this.syntax = syntax;
        this.tokens = tokens;
    }

    /** Skips over the characters that can only ever be skipped, without going through the NFA. */
    protected void printSkipSingles(LinePrinter printer, LexerPlan plan, SkipSingles skip) {
        printer.println("try {");
        printer.indent();

        printer.println("while (" + skipSinglesCondition(skip) + ")");

        if (plan.debug()) {
            printer.println(" {");
            printer.indent();
            printer.println("debugStream.println("
                    + (plan.tokenLoop().switchOnLexState() ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
                    + "\"Skipping character : \" + TokenException.addEscapes(String.valueOf((char) curChar)) + \" (\" + (int)curChar + \")\");");
        }

        printer.println("curChar = input_stream.BeginToken();");

        if (plan.debug()) {
            printer.outdent();
            printer.println("}");
        }

        printer.outdent();
        printer.println("} catch (java.io.IOException e1) { continue EOFLoop; }");
    }

    /**
     * Whether the current character is one of those that can only ever be skipped: a test against
     * the lower, the upper or both halves of the ASCII bit vector. Java and C++ share it.
     */
    protected final String skipSinglesCondition(SkipSingles skip) {
        return switch (skip.range()) {
            case BOTH -> "(curChar < 64 && (" + this.syntax.toHexString(skip.lower())
                    + " & (" + longOne() + " << curChar)) != 0L) || \n"
                    + "          (curChar >> 6) == 1 && (" + this.syntax.toHexString(skip.upper())
                    + " & (" + longOne() + " << (curChar & 077))) != 0L";
            case LOWER -> "curChar <= " + skip.maxChar()
                    + " && (" + this.syntax.toHexString(skip.lower()) + " & (" + longOne() + " << curChar)) != 0L";
            case UPPER -> "curChar > 63 && curChar <= "
                    + skip.maxChar() + " && (" + this.syntax.toHexString(skip.upper())
                    + " & (" + longOne() + " << (curChar & 077))) != 0L";
        };
    }

    /** A 64-bit one, to shift into a bit mask. */
    protected String longOne() {
        return "1L";
    }

    /** A lexical state can start out having matched the empty string. */
    protected void printInitialMatch(LinePrinter printer, LexerPlan plan, LexStatePlan state) {
        if (state.matchesEmpty()) {
            if (plan.debug()) {
                printDebugEmptyStringMatched(printer, state.emptyMatch());
            }
            printer.println(this.syntax.matchedKind() + " = " + state.emptyMatch() + ";");
            printer.println(this.syntax.matchedPos() + " = " + noMatchPos() + ";");
            printer.println(curPos() + " = 0;");
        } else {
            printer.println(this.syntax.matchedKind() + " = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
            printer.println(this.syntax.matchedPos() + " = 0;");
        }
    }

    /** The variable that holds the position the lexer has read up to. */
    protected String curPos() {
        return "curPos";
    }

    /** The matched position that says the empty string was matched. */
    protected String noMatchPos() {
        return "-1";
    }

    /** Whether the matched position is {@link #noMatchPos}. */
    protected String isNoMatchPos() {
        return this.syntax.matchedPos() + " < 0";
    }

    /** The length of the match, one past the matched position. */
    protected String matchLength() {
        return this.syntax.matchedPos() + " + 1";
    }

    /** The number of characters read past the match. */
    protected String charsPastMatch() {
        return curPos() + " - " + this.syntax.matchedPos() + " - 1";
    }

    /** The trace of a lexical state that starts out having matched the empty string as kind. */
    protected void printDebugEmptyStringMatched(LinePrinter printer, int kind) {
        printer.println("debugStream.println(\"   Matched the empty string as \" + " + this.syntax.tokenImages() + "["
                + kind + "] + \" token.\");");
    }

    /** The state has a catch-all token that matches whatever the string-literal DFA did not. */
    protected void printCanMatchAnyChar(LinePrinter printer, LexerPlan plan, LexStatePlan state) {
        int kind = state.anyChar();
        String condition = this.syntax.matchedPos() + " == 0 && " + this.syntax.matchedKind()
                + " > " + kind;
        this.syntax.printIf(printer, state.matchesEmpty()
                ? isNoMatchPos() + " || (" + condition + ")"
                : condition);
        printer.indent();

        if (plan.debug()) {
            printDebugCurrentCharacterMatched(printer, kind);
        }
        printer.println(this.syntax.matchedKind() + " = " + kind + ";");

        if (state.matchesEmpty()) {
            printer.println(this.syntax.matchedPos() + " = 0;");
        }

        printer.outdent();
        printer.println("}");
    }

    /** The trace of the catch-all token matching the current character as kind. */
    protected void printDebugCurrentCharacterMatched(LinePrinter printer, int kind) {
        printer.println("debugStream.println(\"Current character matched as a \" + " + this.syntax.tokenImages() + "["
                + kind + "] + \" token.\");");
    }

    /** Puts back the characters the DFA read past the longest match. */
    protected void printBackupBlock(LinePrinter printer, LexerPlan plan) {
        this.syntax.printIf(printer, matchLength() + " < " + curPos());
        printer.indent();

        if (plan.debug()) {
            printDebugPuttingBack(printer);
        }

        printer.println(this.syntax.inputStream() + "backup(" + charsPastMatch() + ");");
        printer.outdent();
        printer.println("}");
    }

    /** The trace of the characters read past the longest match being put back. */
    protected void printDebugPuttingBack(LinePrinter printer) {
        printer.println("debugStream.println("
                + "\"   Putting back \" + (curPos - jjmatchedPos - 1) + \" characters into the input stream.\");");
    }

    /** The matched kind is a TOKEN: build it, run its actions and hand it to the parser. */
    protected void printTokenBranch(LinePrinter printer, Dispatch dispatch) {
        printer.println("matchedToken = jjFillToken();");

        if (dispatch.special()) {
            printer.println("matchedToken.specialToken = specialToken;");
        }
        if (dispatch.tokenActions()) {
            printer.println("TokenLexicalActions(matchedToken);");
        }
        if (dispatch.newLexState()) {
            printNewLexState(printer);
        }
        printer.println("return matchedToken;");
    }

    /** The matched kind is a SKIP or a SPECIAL_TOKEN: keep it out of the parser's way. */
    protected void printSkipBranch(LinePrinter printer, Dispatch dispatch) {
        if (dispatch.moreBranch()) {
            printer.print("else if ((jjtoSkip[jjmatchedKind >> 6] & (1L << (jjmatchedKind & 077))) != 0L)");
        } else {
            printer.print("else");
        }

        printer.println(" {");
        printer.indent();

        if (dispatch.special()) {
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

            if (dispatch.skipActions()) {
                printer.println("SkipLexicalActions(matchedToken);");
            }

            printer.outdent();
            printer.print("}");

            if (dispatch.skipActions()) {
                printer.println(" else");
                printer.indent();
                printer.println("SkipLexicalActions(null);");
                printer.outdent();
            } else {
                printer.println();
            }
        } else if (dispatch.skipActions()) {
            printer.println("SkipLexicalActions(null);");
        }

        if (dispatch.newLexState()) {
            printNewLexState(printer);
        }

        printer.println("continue EOFLoop;");
        printer.outdent();
        printer.println("}");
    }

    /** The matched kind is a MORE: keep the image and go round again. */
    protected void printMoreBranch(LinePrinter printer, LexerPlan plan, Dispatch dispatch) {
        if (dispatch.moreActions()) {
            printer.println("MoreLexicalActions();");
        } else if (dispatch.moreImageLen()) {
            printer.println("jjimageLen += jjmatchedPos + 1;");
        }

        if (dispatch.newLexState()) {
            printNewLexState(printer);
        }
        printer.println("curPos = 0;");
        printer.println("jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");

        printer.println("try {");
        printer.indent();
        printer.println("curChar = input_stream.readChar();");

        if (plan.debug()) {
            this.syntax.printDebugCurrentCharacter(printer, plan.tokenLoop().switchOnLexState());
        }
        printer.println("continue;");
        printer.outdent();
        printer.println("} catch (java.io.IOException e1) {");
        printer.println("}");
    }

    /** Nothing matched: report where the input went wrong. */
    protected void printLexicalErrorEpilogue(LinePrinter printer, Dispatch dispatch) {
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

    /**
     * Emits the body of {@code getNextToken}: skip what can be skipped, run the string-literal DFA,
     * fall back to the NFA, then dispatch the matched kind to TOKEN, SKIP/SPECIAL_TOKEN or MORE.
     */
    public void dumpGetNextToken(LinePrinter printer, LexerPlan plan) {
        boolean debug = plan.debug();
        TokenLoop loop = plan.tokenLoop();

        if (loop.eofActions()) {
            this.syntax.printEofTokenActions(printer);
        }
        this.syntax.printGetNextTokenPrologue(printer);

        if (loop.imageInit()) {
            this.syntax.printImageInit(printer);
        }

        printer.println();

        if (loop.moreLoop()) {
            this.syntax.printEofLoop(printer);
            printer.indent();
        }

        if (loop.switchOnLexState()) {
            this.syntax.printSwitchOnLexState(printer);
            printer.indent();
        }

        for (var state : plan.states()) {
            if (loop.switchOnLexState()) {
                this.syntax.printLexStateCase(printer, state.index());
                printer.indent();
            }

            if (state.skip() != null) {
                printSkipSingles(printer, plan, state.skip());
            }

            printInitialMatch(printer, plan, state);

            if (debug) {
                this.syntax.printDebugCurrentCharacter(printer, loop.switchOnLexState());
            }

            this.syntax.printMoveStringLiteralDfa0Call(printer, state.index());

            if (state.anyChar() != -1) {
                printCanMatchAnyChar(printer, plan, state);
            }

            if (loop.switchOnLexState()) {
                this.syntax.printLexStateCaseEnd(printer);
            }
        }

        Dispatch dispatch = loop.dispatch();
        if (loop.switchOnLexState()) {
            this.syntax.printSwitchOnLexStateEnd(printer);
        } else if (dispatch == null) {
            this.syntax.printNoLexState(printer);
        }

        if (dispatch != null) {
            this.syntax.printIfMatchedKind(printer);
            printer.indent();

            printBackupBlock(printer, plan);

            if (debug) {
                this.syntax.printDebugFoundMatch(printer);
            }

            if (dispatch.tokenTest()) {
                this.syntax.printIfToToken(printer);
                printer.indent();
            }

            printTokenBranch(printer, dispatch);

            if (dispatch.tokenTest()) {
                printer.outdent();
                printer.println("}");

                if (dispatch.skipBranch()) {
                    printSkipBranch(printer, dispatch);
                }
                if (dispatch.moreBranch()) {
                    printMoreBranch(printer, plan, dispatch);
                }
            }

            printLexicalErrorEpilogue(printer, dispatch);
        }

        if (loop.moreLoop()) {
            printer.outdent();
            printer.println("}");
        }
    }

    /** One entry of the table the parser reports an unexpected token with. */
    protected void getRegExp(LinePrinter printer, int i, List<TokenName> names, boolean isImage) {
        var name = names.get(i);
        switch (name.form()) {
            case EOF -> this.syntax.printTokenImage(printer, "<EOF>");
            case LITERAL -> this.syntax.printStringLiteralImage(printer, name.image(), name.label(),
                    isImage);
            case LABEL -> this.syntax.printTokenImage(printer, "<" + name.label() + ">");
            // A Java or C++ initializer with an empty element does not even compile.
            case KIND -> this.syntax.printTokenImage(printer,
                    "<token of kind " + name.ordinal() + ">");
        }
        this.syntax.printImageSeparator(printer, i, names.size() - 1);
    }

    /** Writes the code of a lexical action, laid out from the first column as the grammar wrote it. */
    protected void printActionToken(LinePrinter printer, CodeText code) {
        var cursor = this.tokens.cursorAt(code.first());
        cursor.resetColumn();
        for (Token token : code.tokens()) {
            cursor.print(token, null, printer);
        }
    }

    /**
     * One case per token kind that has a lexical action or can loop on the empty string: the loop
     * guard, then the image update and the action itself.
     */
    protected void dumpActions(LinePrinter printer, List<ActionCase> cases,
                               Consumer<ActionCase> imageUpdate) {
        for (var actionCase : cases) {
            this.syntax.printActionCase(printer, actionCase.kind());

            if (actionCase.loopCheck()) {
                this.syntax.printEmptyLoopCheck(printer, actionCase.lexState());
            }

            if (!actionCase.code().isEmpty()) {
                imageUpdate.accept(actionCase);
                printActionToken(printer, actionCase.code());
                printer.println();
            }

            this.syntax.printActionCaseEnd(printer);
        }
    }

    /** The lexical actions of the TOKEN productions. */
    public void dumpTokenActions(LinePrinter printer, String parserName, List<ActionCase> cases) {
        this.syntax.printActionsPrologue(printer, parserName, "TokenLexicalActions(Token *matchedToken)", null);
        dumpActions(printer, cases, c -> {
            if (c.image() == ImageSource.RESET) {
                this.syntax.printImageReset(printer); // for EOF there is no image
            } else {
                this.syntax.printImageAppend(printer, c.kind(), c.image() == ImageSource.LITERAL, "        ");
            }
        });
        this.syntax.printActionsEpilogue(printer);
    }

    /** The lexical actions of the MORE productions. */
    public void dumpMoreActions(LinePrinter printer, String parserName, List<ActionCase> cases) {
        this.syntax.printActionsPrologue(printer, parserName, "MoreLexicalActions()",
                "   " + this.syntax.imageLen() + " += (" + this.syntax.lengthOfMatch() + " = " + this.syntax.matchedPos() + " + 1);");
        dumpActions(printer, cases, c -> {
            this.syntax.printImageAppendMore(printer, c.kind(), c.image() == ImageSource.LITERAL);
            printer.println("         " + this.syntax.imageLen() + " = 0;");
        });
        this.syntax.printActionsEpilogue(printer);
    }

    /** The lexical actions of the SKIP productions. */
    public void dumpSkipActions(LinePrinter printer, String parserName, List<ActionCase> cases) {
        this.syntax.printActionsPrologue(printer, parserName, "SkipLexicalActions(Token *matchedToken)", null);
        dumpActions(printer, cases, c -> this.syntax.printImageAppend(printer, c.kind(),
                c.image() == ImageSource.LITERAL, "         "));
        this.syntax.printActionsEpilogue(printer);
    }

    /** Switches to the lexical state the matched kind asks for, if any. */
    protected void printNewLexState(LinePrinter printer) {
        printer.println("if (jjnewLexState[jjmatchedKind] != -1)");
        printer.indent();
        printer.println("curLexState = jjnewLexState[jjmatchedKind];");
        printer.outdent();
    }
}
