// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/NfaState.java, org/javacc/parser/LexGen.java

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.lexer.LexerData;
import org.hivevm.waggle.lexer.NfaState;
import org.hivevm.waggle.lexer.NfaStateData;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.api.Encoding;
import org.hivevm.waggle.model.RStringLiteral;

import java.util.List;
import java.util.function.IntToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * How one target language spells the pieces a token manager is made of: a comparison, a branch, a
 * switch arm, a break, a call into the state-set helpers.
 *
 * <p>These used to be overridable methods on the 3064-line {@code LexerGenerator}, so a back end
 * altered the emission by extending the algorithm that called them. They are a vocabulary, not
 * behaviour, and an emitter takes one of these rather than inheriting it (ADR-0017). The default
 * answers are Java's, which is why the Java back end overrides almost nothing.
 */
public interface TargetSyntax {

    default String self() {
        return "";
    }

    /** Adds one state, checking first that it is not already in the set. */
    default void printCheckNAdd(LinePrinter printer, int state) {
        printer.println("jjCheckNAdd(" + state + ");");
    }

    /** Adds one state unconditionally. */
    default void printAddState(LinePrinter printer, int state) {
        printer.println("jjstateSet[jjnewStateCnt++] = " + state + ";");
    }

    /** Adds two states, checking each. */
    default void printCheckNAddTwoStates(LinePrinter printer, int first, int second) {
        printer.println("jjCheckNAddTwoStates(" + first + ", " + second + ");");
    }

    /** Adds a whole range of states, checking each. */
    default void printCheckNAddStates(LinePrinter printer, int first, int last, boolean isRange) {
        printer.print("jjCheckNAddStates(" + first);
        if (isRange) {
            printer.print(", " + last);
        }
        printer.println(");");
    }

    /** Adds a whole range of states unconditionally. */
    default void printAddStates(LinePrinter printer, int first, int last) {
        printer.println("jjAddStates(" + first + ", " + last + ");");
    }

    /**
     * Leaves the case of a switch. Rust's match arms fall out on their own, so there it emits
     * nothing.
     */
    default void printBreak(LinePrinter printer, String indent) {
        printer.println(indent + "break;");
    }

    /** "the current character equals c" */
    default String charEquals(int c) {
        return "curChar == " + c;
    }

    /** "the current character does not equal c" */
    default String charNotEquals(int c) {
        return "curChar != " + c;
    }

    /** "the bit for the current character is set in mask" */
    default String bitIsSet(long mask) {
        return "(" + toHexString(mask) + " & l) != 0L";
    }

    /** "the bit for the current character is not set in mask" */
    default String bitIsClear(long mask) {
        return "(" + toHexString(mask) + " & l) == 0L";
    }

    /** "the kind matched so far is weaker than kind" */
    default String kindIsWeakerThan(int kind) {
        return "kind > " + kind;
    }

    /** The call that tests whether a non-ASCII character can move out of "state". */
    default String canMove(NfaState state) {
        return "jjCanMove_" + state.nonAsciiMethod + "(hiByte, i1, i2, l1, l2)";
    }

    /** Adds one label to the case that is being built. */
    default void printCaseLabel(LinePrinter printer, List<String> labels, int state) {
        printCaseLabel(printer, labels, "", state);
    }

    default void printCaseLabel(LinePrinter printer, List<String> labels, String indent,
                                  int state) {
        printer.println(indent + "case " + state + ":");
    }

    /** Opens the body shared by the labels collected so far. */
    default void printCasesOpen(LinePrinter printer, List<String> labels) {
        printCasesOpen(printer, labels, "");
    }

    default void printCasesOpen(LinePrinter printer, List<String> labels, String indent) {
    }

    /** Closes that body again. */
    default void printCasesClose(LinePrinter printer) {
    }

    /**
     * The tail of the state machine: the default case, and the end of the switch and of the loop
     * around it. Rust closes a "match" inside a "loop", Java and C++ a "switch" inside a "do/while",
     * so the whole epilogue differs and lives behind this one hook.
     *
     * @param breakInDefault whether the default case breaks out — Java and C++ always do
     */
    default void printDefaultAndEndLoop(LinePrinter printer, boolean breakInDefault) {
        printer.println("default: {");
        printer.indent();
        printer.println("break;");
        printer.outdent();
        printer.println("}");

        printer.outdent();
        printer.println("}");
        printer.outdent();
        printer.println("} while (i != startsAt);");
    }

    /** An "if" that opens a block. */
    default void printIf(LinePrinter printer, String condition) {
        printer.println("if (" + condition + ") {");
    }

    /**
     * An "if" over a single statement. Java and C++ leave the braces out; Rust has no such form, so
     * there it opens a block that {@link #printEndIf} closes again.
     */
    default void printIfNoBlock(LinePrinter printer, String condition) {
        printIfNoBlock(printer, "if ", condition);
    }

    /** The same, but as a link in an "else if" chain: "prefix" is the "if " or "else if " part. */
    default void printIfNoBlock(LinePrinter printer, String prefix, String condition) {
        printer.println(prefix + "(" + condition + ")");
    }

    /** Closes what {@link #printIfNoBlock} opened — nothing at all, unless the target needs braces. */
    default void printEndIf(LinePrinter printer) {
    }

    /** Opens {@code jjCanMove_N}. Only C++ needs a method of its own; the others are inlined. */
    default void printCanMoveSignature(LinePrinter printer, LexerData data, NfaState state) {
    }

    /** Closes the method opened by {@link #printCanMoveSignature}. */
    default void printCanMoveEnd(LinePrinter printer) {
        printer.outdent();
    }

    /** One {@code case} of the high-byte dispatch. */
    default void printCanMoveCase(LinePrinter printer, int hiByte) {
        printer.println("case " + hiByte + ":");
        printer.indent();
    }

    default void printCanMoveCaseEnd(LinePrinter printer) {
        printer.outdent();
    }

    default void printCanMoveDefault(LinePrinter printer) {
        printer.println("default:");
        printer.indent();
    }

    /** The single-vector answer: the character is in the set iff its bit is set. */
    default void printCanMoveReturnBitVector(LinePrinter printer, int vector) {
        printer.println("return ((jjbitVec" + vector + "[i2] & l2) != " + longZero() + ");");
    }

    default void printCanMoveReturnTrue(LinePrinter printer) {
        printer.println("return true;");
    }

    /**
     * One arm of the default branch: a pair of bit vectors, the first indexed by the high byte, the
     * second by the low byte. An all-ones vector needs no test at all.
     */
    default void printCanMoveArm(LinePrinter printer, int hiVector, int loVector, boolean testHi,
                                   boolean testLo) {
        int nested = 0;
        if (testHi) {
            printer.println("if ((jjbitVec" + hiVector + "[i1] & l1) != " + longZero() + ")");
            printer.indent();
            nested++;
        }
        if (testLo) {
            printer.println("if ((jjbitVec" + loVector + "[i2] & l2) == " + longZero() + ")");
            printer.indent();
            printer.println("return false;");
            printer.outdent();
            printer.println("else");
            printer.indent();
            nested++;
        }
        printer.println("return true;");
        if (nested > 0) {
            printer.outdent();
        }
    }

    /** A lone zero keeps an empty C-style array from being a syntax error. */
    default void printEmptyStateSet(LinePrinter printer) {
        printer.print("0");
    }

    /** Opens the {@code jjnextStates} array. */
    default void printNextStatesOpen(LinePrinter printer, LexerData data) {
        printer.print("static final int[] jjnextStates = {");
    }

    /** The lexical actions of the EOF token, emitted into the token-manager template. */
    default void printEofTokenActions(LinePrinter printer) {
        printer.println("    TokenLexicalActions(matchedToken);");
    }

    /** Closes the EOF branch the template opened and starts the token loop. */
    default void printGetNextTokenPrologue(LinePrinter printer) {
        printer.println("    return matchedToken;");
        printer.println("}");
    }

    /** Resets the image buffer the lexical actions append to. */
    default void printImageInit(LinePrinter printer) {
        printer.println("image = jjimage;");
        printer.println("image.setLength(0);");
        printer.println("jjimageLen = 0;");
    }

    /** The loop a MORE production jumps back to. */
    default void printEofLoop(LinePrinter printer) {
        printer.println("for (; ; ) {");
    }

    /** Dispatches on the current lexical state. This also sets up the start state of the NFA. */
    default void printSwitchOnLexState(LinePrinter printer) {
        printer.println("switch (curLexState) {");
    }

    default void printLexStateCase(LinePrinter printer, int state) {
        printer.println("case " + state + ": {");
    }

    default void printLexStateCaseEnd(LinePrinter printer) {
        printer.println("break;");
        printer.outdent();
        printer.println("}");
    }

    default void printSwitchOnLexStateEnd(LinePrinter printer) {
        printer.outdent();
        printer.println("}");
    }

    /** Without a lexical state there is nothing to match. */
    default void printNoLexState(LinePrinter printer) {
        printer.println("jjmatchedKind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
    }

    default void printDebugCurrentCharacter(LinePrinter printer, LexerData data) {
        printer.println("debugStream.println("
                + (data.maxLexStates() > 1 ? "\"<\" + lexStateNames[curLexState] + \">\" + " : "")
                + "\"Current character : \" + TokenException.addEscapes(String.valueOf((char) curChar)) + \" (\" + (int)curChar + \") "
                + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());");
    }

    default void printMoveStringLiteralDfa0Call(LinePrinter printer, int state) {
        printer.println("curPos = jjMoveStringLiteralDfa0_" + state + "();");
    }

    default void printIfMatchedKind(LinePrinter printer) {
        printer.println("if (jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ") {");
    }

    default void printDebugFoundMatch(LinePrinter printer) {
        printer.println("debugStream.println("
                + "\"****** FOUND A \" + " + tokenImages() + "[jjmatchedKind] + \" MATCH "
                + "(\" + TokenException.addEscapes(new String(input_stream.GetSuffix(jjmatchedPos + 1))) + "
                + "\") ******\\n\");");
    }

    default void printIfToToken(LinePrinter printer) {
        printer.println("if ((jjtoToken[jjmatchedKind >> 6] & "
                + "(1L << (jjmatchedKind & 077))) != 0L) {");
    }

    /** Opens the array that maps a token kind to the lexical state it switches to. */
    default void printLexStateArrayOpen(LinePrinter printer, LexerData data) {
        printer.println();
        printer.print("public static final int[] jjnewLexState = {");
    }

    /** Opens one of the {@code jjtoToken}/{@code jjtoSkip}/… bit vectors. */
    default void printBitVectorOpen(LinePrinter printer, LexerData data, String name) {
        printer.print("static final " + longType() + "[] " + name + " = {");
    }

    /** Closes an array opened by one of the two hooks above. */
    default void printArrayClose(LinePrinter printer) {
        printer.println("};");
    }

    /** Emits one {@code jjto…} bit vector, 64 token kinds per element, 4 elements per line. */
    default void dumpBitVector(LinePrinter printer, LexerData data, String name,
                               IntToLongFunction bits) {
        printBitVectorOpen(printer, data, name);
        printer.indent();
        for (int i = 0; i < ((data.maxOrdinal() / 64) + 1); i++) {
            if ((i % 4) == 0) {
                printer.println();
            }
            printer.print(toHexString(bits.applyAsLong(i)) + ", ");
        }
        printer.println();
        printer.outdent();
        printArrayClose(printer);
    }

    /** Renders one entry of the token-image table. */
    default void printTokenImage(LinePrinter printer, String image) {
        printer.print("\"" + image + "\"");
    }

    /**
     * Renders a string literal's entry. Only C++ has two tables — the label and the raw image — and
     * so only C++ looks at {@code isImage}; the other back ends have no {@code REXPRESSION_IMAGE}
     * placeholder in their templates.
     */
    default void printStringLiteralImage(LinePrinter printer, RStringLiteral literal,
                                           boolean isImage) {
        printer.print("\"\\\"" + Encoding.escape(Encoding.escape(literal.getImage())) + "\\\"\"");
    }

    /** Separates two entries of the table. C++ emits one array per entry and needs none. */
    default void printImageSeparator(LinePrinter printer, int i, List<RExpression> expressions) {
        if ((i == 0) || (i < expressions.size())) { // every entry but the last, and always <EOF>
            printer.print(",");
        }
    }

    /** The signature of {@code jjStopStringLiteralDfa}. */
    default void printStopStringLiteralDfaSignature(LinePrinter printer, NfaStateData data,
                                                      int maxKindsReqd) {
        printer.print("private final int jjStopStringLiteralDfa" + data.getLexerStateSuffix()
                + "(int pos");
        for (int i = 0; i < maxKindsReqd; i++) {
            printer.print(", " + longType() + " active" + i);
        }
        printer.println(") {");
    }

    /** The signature of {@code jjStartNfa}. */
    default void printStartNfaSignature(LinePrinter printer, NfaStateData data,
                                          int maxKindsReqd) {
        printer.print("private final int jjStartNfa" + data.getLexerStateSuffix() + "(int pos");
        for (int i = 0; i < maxKindsReqd; i++) {
            printer.print(", " + longType() + " active" + i);
        }
        printer.println(") {");
    }

    /** Hands the position the string-literal DFA stopped at over to the NFA. */
    default void printStartNfaBody(LinePrinter printer, NfaStateData data, String arguments) {
        printer.println("    return " + moveNfaName(data) + "(" + stopStringLiteralDfaName(data)
                + "(pos, " + arguments + "), pos + 1);");
    }

    /** The name {@code jjMoveNfa} is called under. */
    default String moveNfaName(NfaStateData data) {
        return "jjMoveNfa" + data.getLexerStateSuffix();
    }

    /** The name {@code jjStopStringLiteralDfa} is called under. */
    default String stopStringLiteralDfaName(NfaStateData data) {
        return "jjStopStringLiteralDfa" + data.getLexerStateSuffix();
    }

    /** The value {@code jjStopStringLiteralDfa} returns when no NFA state is left to go to. */
    default String noState() {
        return "-1";
    }

    /** Dispatches on how far the string-literal DFA got. */
    default void printSwitchOnPos(LinePrinter printer) {
        printer.println("switch (pos) {");
    }

    default void printPosCase(LinePrinter printer, int pos) {
        printer.println("case " + pos + ":");
    }

    default void printPosCaseEnd(LinePrinter printer) {
    }

    default void printPosDefault(LinePrinter printer) {
        printer.println("default:");
        printer.indent();
        printer.println("return " + noState() + ";");
        printer.outdent();
    }

    /** Opens the body of a guard. Rust has no braceless {@code if}. */
    default void printStopDfaBodyOpen(LinePrinter printer, boolean hasKind) {
        if (hasKind) {
            printer.println(" {");
        } else {
            printer.println();
        }
    }

    default void printStopDfaBodyClose(LinePrinter printer, boolean hasKind) {
        if (hasKind) {
            printer.println("}");
        }
    }

    /** The trace {@code jjStopStringLiteralDfa} writes when it is entered. */
    default void printDebugNoMoreStringLiteralMatches(LinePrinter printer) {
        printer.println(
                "debugStream.println(\"   No more string literal token matches are possible.\");");
    }

    /** {@code active0, active1, …} — the arguments the two functions above are called with. */
    static String activeArguments(int maxKindsReqd) {
        return IntStream.range(0, maxKindsReqd).mapToObj(i -> "active" + i)
                .collect(Collectors.joining(", "));
    }

    /** The declaration of the trivial jjMoveStringLiteralDfa0, used when there is nothing to match. */
    default void printMoveStringLiteralDfa0Signature(LinePrinter printer, NfaStateData data) {
        printer.println("private int jjMoveStringLiteralDfa0" + data.getLexerStateSuffix() + "() {");
    }

    /** The declaration of jjStopAtPos. */
    default void printStopAtPosSignature(LinePrinter printer, NfaStateData data) {
        printer.println();
        printer.println("private int " + "jjStopAtPos(int pos, int kind) {");
    }

    /** The field holding the kind matched so far. */
    default String matchedKind() {
        return "jjmatchedKind";
    }

    /** The field holding the position matched so far. */
    default String matchedPos() {
        return "jjmatchedPos";
    }

    /** Returns a value from the emitted function. Rust's tail expression carries no "return". */
    default void printReturn(LinePrinter printer, String expression) {
        printer.println("return " + expression + ";");
    }

    /** The trace written when no string literal can match any more. */
    default void printDebugNoMoreMatches(LinePrinter printer) {
        printer.println("debugStream.println(\"No more string literal token matches are possible.\");");
        printer.println("debugStream.println(\"Currently matched the first \" + (jjmatchedPos + 1) + " + "\" characters as a \" + " + tokenImages() + "[jjmatchedKind] + \" token.\");");
    }

    /** The declaration of jjMoveStringLiteralDfa<i>, up to the opening parenthesis. */
    default void printMoveStringLiteralDfaHead(LinePrinter printer, NfaStateData data, int i) {
        printer.print("private int jjMoveStringLiteralDfa" + i + data.getLexerStateSuffix() + "(");
    }

    /** The 64-bit integer type of the target. */
    default String longType() {
        return "long";
    }

    /**
     * The DEBUG_TOKEN_MANAGER trace listing the string literals that can still match. Every target
     * spells its logging differently — Java concatenates, C++ builds a printf format — so this is a
     * pure dialect method.
     */
    default void printDebugPossibleMatches(LinePrinter printer, NfaStateData data, int i) {
        if ((i != 0) && data.global.getDebugTokenManager()) {
            printer.println("if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
            printer.println("    debugStream.println(\"   Currently matched the first \" + " + "(jjmatchedPos + 1) + \" characters as a \" + " + tokenImages() + "[jjmatchedKind] + \" token.\");");
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
    }

    /** Opens the guard around reading the next character. */
    default void printReadCharGuardOpen(LinePrinter printer) {
        printer.println("try {");
        printer.println("    curChar = input_stream.readChar();");
        printer.println("} catch (java.io.IOException e) {");
    }

    /** What follows the guard — the targets that do not read inside it read here. */
    default void printReadCharAfterGuard(LinePrinter printer) {
    }

    /** The zero of a 64-bit literal. */
    default String longZero() {
        return "0L";
    }

    /** The call that records how far the string-literal DFA got. */
    default String stopStringLiteralDfaCall(NfaStateData data, int i) {
        return "jjStopStringLiteralDfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ";
    }

    /** The call that hands control to the NFA. */
    default String moveNfaCall(NfaStateData data, int position) {
        return "jjMoveNfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", " + position
                + ")";
    }

    /** The trace of what has been matched so far. */
    default void printDebugCurrentlyMatched(LinePrinter printer) {
        printer.println("if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
        printer.println("    debugStream.println(\"   Currently matched the first \" + " + "(jjmatchedPos + 1) + \" characters as a \" + " + tokenImages() + "[jjmatchedKind] + \" token.\");");
    }

    /** The switch over the current character. */
    default void printSwitchOnChar(LinePrinter printer) {
        printer.println("switch (curChar) {");
    }

    /** A case label for a character, without a body of its own. */
    default void printCharCase(LinePrinter printer, int c) {
        printer.println("case " + c + ":");
    }

    /** A case label for a character that opens its own body. */
    default void printCharCaseWithBody(LinePrinter printer, int c) {
        printer.println("case " + c + ": {");
    }

    /** The default case. */
    default void printDefaultCaseOpen(LinePrinter printer) {
        printer.println("default: {");
    }

    /** The trace of the character the token manager is looking at. */
    default void printDebugCurrentCharacter(LinePrinter printer, NfaStateData data) {
        printer.println("debugStream.println("
                        + (data.global.maxLexStates() > 1
                        ? "\"<\" + lexStateNames[curLexState] + \">\" + "
                        : "")
                        + "\"Current character : \" + TokenException.addEscapes(String.valueOf((char) curChar)) + \" (\" + (int)curChar + \") "
                        + "at line \" + input_stream.getEndLine() + \" column \" + input_stream.getEndColumn());");
    }

    /** The trace saying that no string literal can match any more. */
    default void printDebugNoMatchPossible(LinePrinter printer) {
        printer.println("    debugStream.println(\"   No string literal matches possible.\");");
    }

    /** The table of token names the trace prints. It lives outside the lexer in Java. */
    default String tokenImages() {
        return "ParserConstants.tokenImage";
    }

    /** The reader method that returns the text of the current match. */
    default String getSuffix() {
        return "GetSuffix";
    }

    /** How the input stream is addressed. */
    default String inputStream() {
        return "input_stream.";
    }

    /** The line the current token started on. */
    default String beginLine() {
        return inputStream() + "getBeginLine()";
    }

    /** The column the current token started on. */
    default String beginColumn() {
        return inputStream() + "getBeginColumn()";
    }

    /** The position matched so far, unqualified. */
    default String matchedPosVar() {
        return "jjmatchedPos";
    }

    /** The table of string-literal images. */
    default String strLiteralImages() {
        return "jjstrLiteralImages";
    }

    /** The length of the current match. */
    default String lengthOfMatch() {
        return "lengthOfMatch";
    }

    /** How much of the image has been consumed. */
    default String imageLen() {
        return "jjimageLen";
    }

    /**
     * Opens the block the lexical actions live in. C++ needs a method of its own; for Java and Rust
     * the template already provides it, so nothing is emitted.
     *
     * @param preamble a line to run before the switch, or null
     */
    default void printActionsPrologue(LinePrinter printer, LexerData data, String method,
                                        String preamble) {
    }

    /** Closes it again. */
    default void printActionsEpilogue(LinePrinter printer) {
    }

    /** The case for one token kind. */
    default void printActionCase(LinePrinter printer, int kind) {
        printer.println("      case " + kind + " :");
    }

    /** What a MORE appends: it keeps the image, so there is no length to record. */
    default void printImageAppendMore(LinePrinter printer, LexerData data, int i) {
        printer.print("         image.append");
        if (data.getImage(i) != null) {
            printer.println("(" + strLiteralImages() + "[" + i + "]);");
        } else {
            printer.println("(" + inputStream() + getSuffix() + "(" + imageLen() + "));");
        }
    }

    /** Empties the image buffer. */
    default void printImageReset(LinePrinter printer) {
        printer.println("      image.setLength(0);");
    }

    /** Leaves that case. A Rust match arm falls out on its own. */
    default void printActionBreak(LinePrinter printer) {
        printer.println("         break;");
    }

    /** Closes that case. */
    default void printActionCaseEnd(LinePrinter printer) {
    }

    /** Reports the empty-match loop the lexer has run into. */
    default void printLoopDetected(LinePrinter printer) {
        printer.println("               throw new TokenException("
                + "(\"Error: Bailing out of infinite loop caused by repeated empty string matches "
                + "at line \" + " + beginLine() + " + \", "
                + "column \" + " + beginColumn() + " + \".\"), TokenException.LOOP_DETECTED);");
    }

    /**
     * Guards against a token production that matches the empty string over and over: if the lexer is
     * back at the same line and column with nothing consumed, it is looping.
     */
    default void printEmptyLoopCheck(LinePrinter printer, LexerData data, int i) {
        printer.println("         if (" + matchedPosVar() + " == -1)");
        printer.println("         {");
        printer.println("            if (jjbeenHere[" + data.getState(i) + "] &&");
        printer.println("                jjemptyLineNo[" + data.getState(i) + "] == " + beginLine() + " &&");
        printer.println("                jjemptyColNo[" + data.getState(i) + "] == " + beginColumn() + ")");
        printLoopDetected(printer);
        printer.println("            jjemptyLineNo[" + data.getState(i) + "] = " + beginLine() + ";");
        printer.println("            jjemptyColNo[" + data.getState(i) + "] = " + beginColumn() + ";");
        printer.println("            jjbeenHere[" + data.getState(i) + "] = true;");
        printer.println("         }");
    }

    /** Appends what was matched to the image being built. */
    default void printImageAppend(LinePrinter printer, LexerData data, int i, String indent) {
        printer.print(indent + "image.append");
        if (data.getImage(i) != null) {
            printer.println("(" + strLiteralImages() + "[" + i + "]);");
            printer.println("        " + lengthOfMatch() + " = " + strLiteralImages() + "[" + i
                    + "].length();");
        } else {
            printer.println("(" + inputStream() + getSuffix() + "(" + imageLen() + " + (" + lengthOfMatch()
                    + " = " + matchedPosVar() + " + 1)));");
        }
    }

    /** The signature of {@code jjStartNfaWithStates}. */
    default void printStartNfaWithStatesSignature(LinePrinter printer, NfaStateData data) {
        printer.println();
        printer.println("private int jjStartNfaWithStates" + data.getLexerStateSuffix()
                + "(int pos, int kind, int state) {");
    }

    /** Reads the next character; on end of input the match found so far is the answer. */
    default void printReadCharOrReturn(LinePrinter printer) {
        printer.println("try {");
        printer.println("    curChar = input_stream.readChar();");
        printer.println("} catch (java.io.IOException e) {");
        printer.println("    return pos + 1;");
        printer.println("}");
    }

    /** The signature of {@code jjMoveNfa}. */
    default void printMoveNfaSignature(LinePrinter printer, NfaStateData data) {
        printer.println("private int jjMoveNfa" + data.getLexerStateSuffix()
                + "(int startState, int curPos) {");
    }

    /**
     * A mixed lexical state can reach the NFA with a string literal already matched. The prologue
     * saves that match, rewinds the reader and starts over at position 0; the epilogue below decides
     * which of the two matches wins.
     */
    default void printMoveNfaMixedPrologue(LinePrinter printer) {
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

    /** The endless loop the NFA runs in. */
    default void printForEver(LinePrinter printer) {
        printer.println("for (; ; ) {");
    }

    /**
     * Swaps the two halves of {@code jjstateSet} — the states reached in this round become the states
     * to advance in the next — and leaves the loop when no state is left.
     */
    default void printSwapStateSets(LinePrinter printer, NfaStateData data) {
        printer.println("if ((i = jjnewStateCnt) == (startsAt = " + data.generatedStates()
                + " - (jjnewStateCnt = startsAt)))");
        printer.indent();
        printer.println(data.isMixedState() ? "break;" : "return curPos;");
        printer.outdent();
    }

    /** Reads the next character; on end of input the NFA is done. */
    default void printReadCharOrLeave(LinePrinter printer, NfaStateData data) {
        printer.println("try {");
        printer.println("    curChar = input_stream.readChar();");
        printer.println("} catch (java.io.IOException e) {");
        printer.indent();
        printer.println(data.isMixedState() ? "break;" : "return curPos;");
        printer.outdent();
        printer.println("}");
    }

    default void printDebugStartingNfa(LinePrinter printer) {
        printer.println("debugStream.println(\"   Starting NFA to match one of : \" + "
                + "jjKindsForStateVector(curLexState, jjstateSet, 0, 1));");
    }

    default void printDebugPossibleLongerMatches(LinePrinter printer) {
        printer.println("debugStream.println(\"   Possible kinds of longer matches : \" + "
                + "jjKindsForStateVector(curLexState, jjstateSet, startsAt, i));");
    }

    /** Declares the bit masks the NFA state tests the current character against. */
    default void printCharBits(LinePrinter printer, int byteNum) {
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
    }

    /** Opens the loop that advances every state currently in the state set. */
    default void printMatchLoopOpen(LinePrinter printer) {
        printer.println("do {");
    }

    /** Dispatches on the next state to advance. */
    default void printSwitchOnStateSet(LinePrinter printer) {
        printer.println("switch (jjstateSet[--i]) {");
    }

    /** What an absent table looks like. */
    default String noStateSet() {
        return "null";
    }

    /** How a table of state sets is wrapped. */
    default String stateSet(CharSequence body) {
        return "{" + body + "}";
    }

    /** How one row of such a table opens. Rust writes a slice where the others write a brace. */
    default String rowOpen() {
        return "{";
    }

    default String rowClose() {
        return "}";
    }

    default String toHexString(long value) {
        return "0x" + Long.toHexString(value) + "L";
    }

    // Assumes l != 0L
    static char MaxChar(long l) {
        for (int i = 64; i-- > 0; ) {
            if ((l & (1L << i)) != 0L) {
                return (char) i;
            }
        }
        return 0xffff;
    }

    default String getLohiBytes(LexerData data, int i) {
        return String.join(", ",
                toHexString(data.getLohiByte(i, 0)),
                toHexString(data.getLohiByte(i, 1)),
                toHexString(data.getLohiByte(i, 2)),
                toHexString(data.getLohiByte(i, 3)));
    }

    /** The name of the composite state the NFA starts in, or -1 when it has no epsilon moves. */
    default int InitStateName(NfaStateData data) {
        if (data.getInitialState().usefulEpsilonMoves == 0) {
            return -1;
        }
        return data.stateNameForComposite.get(data.getInitialState().GetEpsilonMovesString());
    }
}
