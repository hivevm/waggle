// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/LexGen.java, org/javacc/parser/LexGenCPP.java

package org.hivevm.waggle.codegen;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.lexer.LexerData;
import org.hivevm.waggle.model.Action;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.RStringLiteral;
import org.hivevm.waggle.grammar.Token;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntToLongFunction;

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
    protected void printSkipSingles(LinePrinter printer, LexerData data, int state) {
        // the backup(0) is there to make the JIT happy
        printer.println("try {");
        printer.indent();
        printer.println("input_stream.backup(0);");

        long lower = data.singlesToSkip(state).asciiMoves[0];
        long upper = data.singlesToSkip(state).asciiMoves[1];
        if ((lower != 0L) && (upper != 0L)) {
            printer.println("while ((curChar < 64 && (0x" + Long.toHexString(lower)
                    + "L & (1L << curChar)) != 0L) || \n"
                    + "          (curChar >> 6) == 1 && (0x" + Long.toHexString(upper)
                    + "L & (1L << (curChar & 077))) != 0L)");
        } else if (upper == 0L) {
            printer.println("while (curChar <= " + (int) TargetSyntax.MaxChar(lower)
                    + " && (0x" + Long.toHexString(lower) + "L & (1L << curChar)) != 0L)");
        } else if (lower == 0L) {
            printer.println("while (curChar > 63 && curChar <= "
                    + (TargetSyntax.MaxChar(upper) + 64) + " && (0x" + Long.toHexString(upper)
                    + "L & (1L << (curChar & 077))) != 0L)");
        }

        if (data.options().getDebugTokenManager()) {
            printer.println(" {");
            printer.indent();
            printer.println("debugStream.println("
                    + (data.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
                    + "\"Skipping character : \" + TokenException.addEscapes(String.valueOf((char) curChar)) + \" (\" + (int)curChar + \")\");");
        }

        printer.println("curChar = input_stream.BeginToken();");

        if (data.options().getDebugTokenManager()) {
            printer.outdent();
            printer.println("}");
        }

        printer.outdent();
        printer.println("} catch (java.io.IOException e1) { continue EOFLoop; }");
    }

    /** A lexical state can start out having matched the empty string. */
    protected void printInitialMatch(LinePrinter printer, LexerData data, int state) {
        if (hasInitialMatch(data, state)) {
            if (data.options().getDebugTokenManager()) {
                printer.println("debugStream.println(\"   Matched the empty string as \" + " + this.syntax.tokenImages() + "["
                        + data.initMatch(state) + "] + \" token.\");");
            }
            printer.println("jjmatchedKind = " + data.initMatch(state) + ";");
            printer.println("jjmatchedPos = -1;");
            printer.println("curPos = 0;");
        } else {
            printer.println("jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
            printer.println("jjmatchedPos = 0;");
        }
    }

    /** Whether the lexical state matches the empty string. */
    protected static boolean hasInitialMatch(LexerData data, int state) {
        return (data.initMatch(state) != Integer.MAX_VALUE) && (data.initMatch(state) != 0);
    }

    /** The state has a catch-all token that matches whatever the string-literal DFA did not. */
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
            printer.println("debugStream.println(\"Current character matched as a \" + " + this.syntax.tokenImages() + "["
                    + kind + "] + \" token.\");");
        }
        printer.println("jjmatchedKind = " + kind + ";");

        if (hasInitialMatch(data, state)) {
            printer.println("jjmatchedPos = 0;");
        }

        printer.outdent();
        printer.println("}");
    }

    /** Puts back the characters the DFA read past the longest match. */
    protected void printBackupBlock(LinePrinter printer, LexerData data) {
        printer.println("if (jjmatchedPos + 1 < curPos) {");
        printer.indent();

        if (data.options().getDebugTokenManager()) {
            printer.println("debugStream.println("
                    + "\"   Putting back \" + (curPos - jjmatchedPos - 1) + \" characters into the input stream.\");");
        }

        printer.println("input_stream.backup(curPos - jjmatchedPos - 1);");
        printer.outdent();
        printer.println("}");
    }

    /** The matched kind is a TOKEN: build it, run its actions and hand it to the parser. */
    protected void printTokenBranch(LinePrinter printer, LexerData data) {
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
    }

    /** The matched kind is a SKIP or a SPECIAL_TOKEN: keep it out of the parser's way. */
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
            } else {
                printer.println();
            }
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

    /** The matched kind is a MORE: keep the image and go round again. */
    protected void printMoreBranch(LinePrinter printer, LexerData data) {
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
                    + (data.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
                    + "\"Current character : \" + "
                    + "TokenException.addEscapes(String.valueOf((char) curChar)) + \" (\" + (int)curChar + \") "
                    + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());");
        }
        printer.println("continue;");
        printer.outdent();
        printer.println("} catch (java.io.IOException e1) {");
        printer.println("}");
    }

    /** Nothing matched: report where the input went wrong. */
    protected void printLexicalErrorEpilogue(LinePrinter printer) {
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
    public void dumpGetNextToken(LinePrinter printer, LexerData data) {
        boolean debug = data.options().getDebugTokenManager();
        boolean notPlainToken = data.hasSkip() || data.hasMore() || data.hasSpecial();

        if (data.hasEof()) {
            this.syntax.printEofTokenActions(printer);
        }
        this.syntax.printGetNextTokenPrologue(printer);

        if (data.hasMoreActions() || data.hasSkipActions() || data.hasTokenActions()) {
            this.syntax.printImageInit(printer);
        }

        printer.println();

        if (data.hasMore()) {
            this.syntax.printEofLoop(printer);
            printer.indent();
        }

        if (data.maxLexStates() > 1) {
            this.syntax.printSwitchOnLexState(printer);
            printer.indent();
        }

        for (int i = 0; i < data.maxLexStates(); i++) {
            if (data.maxLexStates() > 1) {
                this.syntax.printLexStateCase(printer, i);
                printer.indent();
            }

            if (data.singlesToSkip(i).HasTransitions()) {
                printSkipSingles(printer, data, i);
            }

            printInitialMatch(printer, data, i);

            if (debug) {
                this.syntax.printDebugCurrentCharacter(printer, data);
            }

            this.syntax.printMoveStringLiteralDfa0Call(printer, i);

            if (data.canMatchAnyChar(i) != -1) {
                printCanMatchAnyChar(printer, data, i);
            }

            if (data.maxLexStates() > 1) {
                this.syntax.printLexStateCaseEnd(printer);
            }
        }

        if (data.maxLexStates() > 1) {
            this.syntax.printSwitchOnLexStateEnd(printer);
        } else if (data.maxLexStates() == 0) {
            this.syntax.printNoLexState(printer);
        }

        if (data.maxLexStates() > 0) {
            this.syntax.printIfMatchedKind(printer);
            printer.indent();

            printBackupBlock(printer, data);

            if (debug) {
                this.syntax.printDebugFoundMatch(printer);
            }

            if (notPlainToken) {
                this.syntax.printIfToToken(printer);
                printer.indent();
            }

            printTokenBranch(printer, data);

            if (notPlainToken) {
                printer.outdent();
                printer.println("}");

                if (data.hasSkip() || data.hasSpecial()) {
                    printSkipBranch(printer, data);
                }
                if (data.hasMore()) {
                    printMoreBranch(printer, data);
                }
            }

            printLexicalErrorEpilogue(printer);
        }

        if (data.hasMore()) {
            printer.outdent();
            printer.println("}");
        }
    }

    /** One entry of the table the parser reports an unexpected token with. */
    protected void getRegExp(LinePrinter printer, int i, List<RExpression> expressions,
                                   boolean isImage) {
        if (i == 0) {
            this.syntax.printTokenImage(printer, "<EOF>");
        } else {
            var re = expressions.get(i - 1);
            if (re instanceof RStringLiteral literal) {
                this.syntax.printStringLiteralImage(printer, literal, isImage);
            } else if (!re.getLabel().isEmpty()) {
                this.syntax.printTokenImage(printer, "<" + re.getLabel() + ">");
            } else {
                // An unlabelled token still needs its entry, or every later index is off by one —
                // and a Java or C++ initializer with an empty element does not even compile.
                this.syntax.printTokenImage(printer, "<token of kind " + re.getOrdinal() + ">");
            }
        }
        this.syntax.printImageSeparator(printer, i, expressions);
    }

    public String getKindForState(LexerData data) {
        if (data.getKinds() == null) {
            return this.syntax.noStateSet();
        }

        StringBuilder builder = new StringBuilder();
        boolean moreThanOne = false;
        for (int[] kind : data.getKinds()) {
            if (moreThanOne) {
                builder.append(",");
            }
            moreThanOne = true;
            if (kind == null) {
                builder.append(this.syntax.rowOpen()).append(this.syntax.rowClose());
            } else {
                builder.append(this.syntax.rowOpen()).append(" ");
                for (int element : kind) {
                    builder.append(element);
                    builder.append(",");
                }
                builder.append(this.syntax.rowClose());
            }
        }
        return this.syntax.stateSet(builder);
    }

    protected void printActionToken(LinePrinter printer, Action action) {
        for (Token token : action.getActionTokens()) {
            this.tokens.printTokenPublic(token, printer);
        }
    }

    /**
     * One case per token kind of {@code kinds} that has a lexical action or can loop on the empty
     * string: the loop guard, then the image update and the action itself.
     */
    protected void dumpActions(LinePrinter printer, LexerData data, IntToLongFunction kinds,
                             IntConsumer imageUpdate) {
        for (int i = 0; i < data.maxOrdinal(); i++) {
            if ((kinds.applyAsLong(i / 64) & (1L << (i % 64))) == 0L) {
                continue;
            }

            Action act = data.actions(i);
            boolean hasAction = (act != null) && !act.getActionTokens().isEmpty();
            boolean canLoop = data.canLoop(data.getState(i));
            if (!hasAction && !canLoop) {
                continue;
            }

            this.syntax.printActionCase(printer, i);

            if ((data.initMatch(data.getState(i)) == i) && canLoop) {
                this.syntax.printEmptyLoopCheck(printer, data, i);
            }

            if (hasAction) {
                imageUpdate.accept(i);
                this.tokens.setupTokenPublic(act.getActionTokens().getFirst());
                this.tokens.resetColumnPublic();
                printActionToken(printer, act);
                printer.println();
            }

            this.syntax.printActionBreak(printer);
            this.syntax.printActionCaseEnd(printer);
        }
    }

    /** The lexical actions of the TOKEN productions. */
    public void dumpTokenActions(LinePrinter printer, LexerData data) {
        this.syntax.printActionsPrologue(printer, data, "TokenLexicalActions(Token *matchedToken)", null);
        dumpActions(printer, data, data::toToken, i -> {
            if (i == 0) {
                this.syntax.printImageReset(printer); // for EOF there is no image
            } else {
                this.syntax.printImageAppend(printer, data, i, "        ");
            }
        });
        this.syntax.printActionsEpilogue(printer);
    }

    /** The lexical actions of the MORE productions. */
    public void dumpMoreActions(LinePrinter printer, LexerData data) {
        this.syntax.printActionsPrologue(printer, data, "MoreLexicalActions()",
                "   " + this.syntax.imageLen() + " += (" + this.syntax.lengthOfMatch() + " = " + this.syntax.matchedPosVar() + " + 1);");
        dumpActions(printer, data, data::toMore, i -> {
            this.syntax.printImageAppendMore(printer, data, i);
            printer.println("         " + this.syntax.imageLen() + " = 0;");
        });
        this.syntax.printActionsEpilogue(printer);
    }

    /** The lexical actions of the SKIP productions. */
    public void dumpSkipActions(LinePrinter printer, LexerData data) {
        this.syntax.printActionsPrologue(printer, data, "SkipLexicalActions(Token *matchedToken)", null);
        dumpActions(printer, data, data::toSkip, i -> this.syntax.printImageAppend(printer, data, i, "         "));
        this.syntax.printActionsEpilogue(printer);
    }
}
