// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/RStringLiteral.java, org/javacc/parser/NfaState.java

package org.hivevm.waggle.codegen.cpp;

import org.hivevm.waggle.api.Options;
import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.codegen.GetNextTokenEmitter;
import org.hivevm.waggle.codegen.LexerGenerator;
import org.hivevm.waggle.codegen.NfaMoveEmitter;
import org.hivevm.waggle.lexer.LexerData;
import org.hivevm.waggle.lexer.NfaState;
import org.hivevm.waggle.lexer.NfaStateData;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.RStringLiteral;
import org.hivevm.source.LinePrinter;
import org.hivevm.source.SourceProvider;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Generate lexer.
 */
class CppLexerGenerator extends LexerGenerator {

    public CppLexerGenerator() {
        super(Language.CPP);
    }

    @Override
    protected GetNextTokenEmitter newGetNextTokenEmitter() {
        return new CppGetNextTokenEmitter(this, this);
    }

    @Override
    protected NfaMoveEmitter newNfaMoveEmitter() {
        return new CppNfaMoveEmitter(this);
    }

    @Override
    protected final void generate(LexerData data, OptionsContext options) {
        options.add("STATE_NAMES_AS_CHARS", data.getStateCount())
                .set("STATE_NAMES_AS_CHARS_INDEX", i -> i)
                .set("STATE_NAMES_AS_CHARS_CHARS", (i, w) -> CppLexerGenerator.getTextAsChars(data.getStateName(i), w));
        options.set("DUMP_STR_LITERAL_IMAGES", p -> DumpStrLiteralImages(p, data));
        options.set("DUMP_STATES_FOR_STATE_CPP", p -> DumpStatesForStateCPP(p, data));
        options.set("DUMP_STATES_FOR_KIND", p -> DumpStatesForKind(p, data));
        options.set("DUMP_NFA_AND_DFA_HEADER", w -> {
            var stopAtPosDeclared = new boolean[1]; // jjStopAtPos is shared by all states
            data.getStateNames().forEach(name ->
                    dump_nfa_and_dfa_header(data.getStateData(name), w, stopAtPosDeclared));
        });

        CppTemplate.LEXER.render(options, data.getParserName());
        CppTemplate.LEXER_H.render(options, data.getParserName());
    }

    @Override
    protected SourceProvider<Options> getConstantsTemplate() {
        return CppTemplate.PARSER_CONSTANTS;
    }

    /**
     * The kind each NFA state of a lexical state accepts, for the DEBUG_TOKEN_MANAGER trace:
     * {@code kindForState[lexState][state]}. It used to be one rectangular array, printed as
     * {@code = null;} for a grammar without an NFA, which is not C++.
     */
    private void DumpStatesForKind(LinePrinter printer, LexerData data) {
        if (data.getKinds() == null) {
            printer.println("static const int* const kindForState[] = { nullptr };");
            return;
        }

        int[][] kinds = data.getKinds();
        for (int i = 0; i < kinds.length; i++) {
            if (kinds[i] != null) {
                printer.println("static const int kindForState_" + i + "[] = { "
                        + joined(kinds[i]) + " };");
            }
        }
        printer.print("static const int* const kindForState[] = {");
        for (int i = 0; i < kinds.length; i++) {
            printer.print((i > 0 ? ", " : " ") + ((kinds[i] == null) ? "nullptr" : "kindForState_" + i));
        }
        printer.println(" };");
    }

    /**
     * The NFA states each composite state stands for, per lexical state, for the
     * DEBUG_TOKEN_MANAGER trace: {@code statesForState[lexState][state]}, with the length of each
     * set in {@code statesForStateLen}. The sets used to be padded to one fixed length, so a
     * reader could not tell a set's zeros from state 0.
     */
    private void DumpStatesForStateCPP(LinePrinter printer, LexerData data) {
        int[][][] states = data.getStatesForState();
        if (states == null) { // a grammar made only of string literals has no NFA
            printer.println("static const int* const* const statesForState[] = { nullptr };");
            printer.println("static const int* const statesForStateLen[] = { nullptr };");
            return;
        }

        for (int i = 0; i < data.maxLexStates(); i++) {
            if (states[i] == null) {
                continue;
            }
            var lengths = new int[states[i].length];
            for (int j = 0; j < states[i].length; j++) {
                int[] set = (states[i][j] == null) ? new int[] {j} : states[i][j];
                lengths[j] = set.length;
                printer.println("static const int stateSet_" + i + "_" + j + "[] = { "
                        + joined(set) + " };");
            }
            printer.print("static const int* const stateSet_" + i + "[] = {");
            for (int j = 0; j < states[i].length; j++) {
                printer.print((j > 0 ? ", " : " ") + "stateSet_" + i + "_" + j);
            }
            printer.println(" };");
            printer.println("static const int stateSetLen_" + i + "[] = { " + joined(lengths) + " };");
        }

        printer.print("static const int* const* const statesForState[] = {");
        for (int i = 0; i < data.maxLexStates(); i++) {
            printer.print((i > 0 ? ", " : " ") + ((states[i] == null) ? "nullptr" : "stateSet_" + i));
        }
        printer.println(" };");
        printer.print("static const int* const statesForStateLen[] = {");
        for (int i = 0; i < data.maxLexStates(); i++) {
            printer.print((i > 0 ? ", " : " ") + ((states[i] == null) ? "nullptr" : "stateSetLen_" + i));
        }
        printer.println(" };");
    }

    private static String joined(int[] values) {
        var text = new StringBuilder();
        for (int value : values) {
            text.append(text.isEmpty() ? "" : ", ").append(value);
        }
        return text.toString();
    }

    private void DumpStrLiteralImages(LinePrinter printer, LexerData data) {
        if (data.getImageCount() <= 0) {
            printer.println("static const JJString jjstrLiteralImages[] = {};");
            return;
        }

        LexerGenerator.printLiteralImages(data, printer, true, (kind, image) -> {
            var toPrint = new StringBuilder("static JJChar jjstrLiteralChars_" + kind + "[] = {");
            if (image != null) {
                toPrint.append(CppLexerGenerator.charElements(image));
            }
            return toPrint.append("0};").toString(); // the terminating null char
        });

        printer.println("static const JJString " + "jjstrLiteralImages[] = {");
        for (int j = 0; j < data.getImageCount(); j++) {
            printer.println("jjstrLiteralChars_" + j + ", ");
        }
        printer.println("};");
    }

    private void dump_nfa_and_dfa_header(NfaStateData data, LinePrinter printer,
                                         boolean[] stopAtPosDeclared) {
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
        } else if (!stopAtPosDeclared[0]) {
            printer.println("int jjStopAtPos(int pos, int kind);");
            stopAtPosDeclared[0] = true;
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
    public void printMoveStringLiteralDfa0Signature(LinePrinter printer, NfaStateData data) {
        printer.print("int " + data.getParserName() + "TokenManager::jjMoveStringLiteralDfa0"
                + data.getLexerStateSuffix() + "() {");
    }

    @Override
    public void printStopAtPosSignature(LinePrinter printer, NfaStateData data) {
        printer.println("int " + data.getParserName() + "TokenManager::jjStopAtPos(int pos, int kind) {");
    }

    @Override
    public void printDebugNoMoreMatches(LinePrinter printer) {
        printer.println("fprintf(debugStream, \"No more string literal token matches are possible.\");");
        printer.println("fprintf(debugStream, \"Currently matched the first %d characters as a \\\"%s\\\" token.\\n\",  (jjmatchedPos + 1),  addUnicodeEscapes(tokenImages[jjmatchedKind]).c_str());");
    }

    @Override
    public String tokenImages() {
        return "tokenImages";
    }

    @Override
    public String getSuffix() {
        return "getSuffix";
    }

    @Override
    public String inputStream() {
        return "reader->";
    }

    /** C++ puts the lexical actions in a method of their own, and switches inside it. */
    @Override
    public void printActionsPrologue(LinePrinter printer, LexerData data, String method,
                                        String preamble) {
        printer.print("\nvoid " + data.getParserName() + "TokenManager::" + method);
        printer.println("{");
        if (preamble != null) {
            printer.println(preamble);
        }
        printer.println("   switch(jjmatchedKind)");
        printer.println("   {");
    }

    @Override
    public void printActionsEpilogue(LinePrinter printer) {
        printer.println("      default:");
        printer.println("         break;");
        printer.println("   }");
        printer.println("}");
    }

    @Override
    public void printActionCase(LinePrinter printer, int kind) {
        printer.println("      case " + kind + " : {");
    }

    @Override
    public void printActionCaseEnd(LinePrinter printer) {
        printer.println("       }");
    }

    @Override
    public void printLoopDetected(LinePrinter printer) {
        printer.println("               errorHandler->lexicalError(JJString(\"(\"Error: Bailing out of infinite loop caused by repeated empty string matches \" + \"at line \" + reader->getBeginLine() + \", \" + \"column \" + reader->getBeginColumn() + \".\")), this);");
    }

    @Override
    public void printMoveStringLiteralDfaHead(LinePrinter printer, NfaStateData data, int i) {
        printer.print("int " + data.getParserName() + "TokenManager::jjMoveStringLiteralDfa" + i
                + data.getLexerStateSuffix() + "(");
    }

    @Override
    public String longType() {
        return "unsigned long long";
    }

    /** Cpp logs differently. */
    @Override
    public void printDebugPossibleMatches(LinePrinter printer, NfaStateData data, int i) {
        if ((i != 0) && data.global.getDebugTokenManager()) {
            printer.println("if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
            printer.println("    fprintf(debugStream, \"   Currently matched the first %d characters as a \\\"%s\\\" token.\\n\", (jjmatchedPos + 1), addUnicodeEscapes(tokenImages[jjmatchedKind]).c_str());");
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
    }

    @Override
    public void printReadCharGuardOpen(LinePrinter printer) {
        printer.println("if (reader->endOfInput()) {");
    }

    @Override
    public void printReadCharAfterGuard(LinePrinter printer) {
        printer.println("   curChar = reader->read(); // UTF8: as the NFA reads it");
    }

    @Override
    public void printDebugCurrentlyMatched(LinePrinter printer) {
        printer.println("if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
        printer.println("    fprintf(debugStream, \"   Currently matched the first %d characters as a \\\"%s\\\" token.\\n\", (jjmatchedPos + 1),  addUnicodeEscapes(tokenImages[jjmatchedKind]).c_str());");
    }

    @Override
    public void printSwitchOnChar(LinePrinter printer) {
        printer.println("switch(curChar) {");
    }

    @Override
    public void printDebugCurrentCharacter(LinePrinter printer, NfaStateData data) {
        printer.println("fprintf(debugStream, "
                        + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
                        + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
                        + "reader->getEndLine(), reader->getEndColumn());");
    }

    @Override
    public void printDebugNoMatchPossible(LinePrinter printer) {
        printer.println("    fprintf(debugStream, \"   No string literal matches possible.\");");
    }

    private static void printCharArray(LinePrinter printer, String s) {
        printer.print(CppLexerGenerator.charElements(s));
    }

    /**
     * The elements of a JJChar array that holds {@code s}, each followed by ", ". The reader hands
     * the lexer UTF-8, so the text is UTF-8 too; it used to be written as UTF-16 code units, which
     * do not fit a char beyond ASCII and did not compile.
     */
    private static String charElements(String s) {
        var elements = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            elements.append((b >= 0) ? "0x" + Integer.toHexString(b)
                    : String.format("'\\x%02x'", b & 0xff)).append(", ");
        }
        return elements.toString();
    }

    private static void getTextAsChars(String text, LinePrinter printer) {
        List<String> chars = new ArrayList<>();
        for (int j = 0; j < text.length(); j++) {
            chars.add("0x" + Integer.toHexString(text.charAt(j)));
        }
        printer.print(String.join(", ", chars));
    }

    @Override
    public String toHexString(long value) {
        return "0x" + Long.toHexString(value) + "ULL";
    }

    @Override
    public void printStopStringLiteralDfaSignature(LinePrinter printer, NfaStateData data,
                                                      int maxKindsReqd) {
        printer.print("int " + data.global.getParserName() + "TokenManager::jjStopStringLiteralDfa"
                + data.getLexerStateSuffix() + "(int pos");
        for (int i = 0; i < maxKindsReqd; i++) {
            printer.print(", " + longType() + " active" + i);
        }
        printer.println(") {");
    }

    @Override
    public void printStartNfaSignature(LinePrinter printer, NfaStateData data,
                                          int maxKindsReqd) {
        printer.print("int " + data.global.getParserName() + "TokenManager::jjStartNfa"
                + data.getLexerStateSuffix() + "(int pos");
        for (int i = 0; i < maxKindsReqd; i++) {
            printer.print(", " + longType() + " active" + i);
        }
        printer.println(") {");
    }

    @Override
    public void printDebugNoMoreStringLiteralMatches(LinePrinter printer) {
        printer.println(
                "fprintf(debugStream, \"   No more string literal token matches are possible.\");");
    }

    @Override
    public void printLexStateArrayOpen(LinePrinter printer, LexerData data) {
        printer.println();
        printer.println("/** Lex State array. */");
        printer.print("static const int jjnewLexState[] = {");
    }

    @Override
    public void printBitVectorOpen(LinePrinter printer, LexerData data, String name) {
        printer.print("static const " + longType() + " " + name + "[] = {");
    }

    @Override
    public void printNextStatesOpen(LinePrinter printer, LexerData data) {
        printer.print("static const int jjnextStates[] = {");
    }

    @Override
    public void printMoveNfaSignature(LinePrinter printer, NfaStateData data) {
        printer.print("int " + data.getParserName() + "TokenManager::jjMoveNfa"
                + data.getLexerStateSuffix() + "(int startState, int curPos) {");
    }

    @Override
    public void printMoveNfaMixedPrologue(LinePrinter printer) {
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

    @Override
    public void printForEver(LinePrinter printer) {
        printer.println("for (;;) {");
    }

    @Override
    public void printSwapStateSets(LinePrinter printer, NfaStateData data) {
        printer.println("if ((i = jjnewStateCnt), (jjnewStateCnt = startsAt), (i == (startsAt = "
                + data.generatedStates() + " - startsAt)))");
        printer.indent();
        printer.println(data.isMixedState() ? "break;" : "return curPos;");
        printer.outdent();
    }

    @Override
    public void printReadCharOrLeave(LinePrinter printer, NfaStateData data) {
        printer.println(data.isMixedState()
                ? "if (reader->endOfInput()) { break; }"
                : "if (reader->endOfInput()) { return curPos; }");
        printer.println("curChar = reader->read(); // UTF8: Support Unicode");
    }

    @Override
    public void printDebugStartingNfa(LinePrinter printer) {
        printer.println("fprintf(debugStream, \"   Starting NFA to match one of : %s\\n\", "
                + "jjKindsForStateVector(curLexState, jjstateSet, 0, 1).c_str());");
    }

    @Override
    public void printDebugPossibleLongerMatches(LinePrinter printer) {
        printer.println("fprintf(debugStream, \"   Possible kinds of longer matches : %s\\n\", "
                + "jjKindsForStateVector(curLexState, jjstateSet, startsAt, i).c_str());");
    }

    @Override
    public void printEofTokenActions(LinePrinter printer) {
        printer.println("      TokenLexicalActions(matchedToken);");
    }

    @Override
    public void printGetNextTokenPrologue(LinePrinter printer) {
        printer.println("return matchedToken;");
        printer.outdent();
        printer.println("}");
        printer.println("curChar = reader->beginToken();");
    }

    @Override
    public void printImageInit(LinePrinter printer) {
        printer.println("image = jjimage;");
        printer.println("image.clear();");
        printer.println("jjimageLen = 0;");
    }

    /** An EOF action: the image is a std::basic_string, which has no setLength. */
    @Override
    public void printImageReset(LinePrinter printer) {
        printer.println("      image.clear();");
    }

    @Override
    public void printEofLoop(LinePrinter printer) {
        printer.println("for (;;) {");
    }

    @Override
    public void printSwitchOnLexState(LinePrinter printer) {
        printer.println("switch(curLexState) {");
    }

    @Override
    public void printDebugCurrentCharacter(LinePrinter printer, LexerData data) {
        printer.println("fprintf(debugStream, "
                + "\"<%s>Current character : %c(%d) at line %d column %d\\n\","
                + "addUnicodeEscapes(lexStateNames[curLexState]).c_str(), curChar, (int)curChar, "
                + "reader->getEndLine(), reader->getEndColumn());");
    }

    @Override
    public void printDebugFoundMatch(LinePrinter printer) {
        printer.println("fprintf(debugStream, \"****** FOUND A %d(%s) MATCH (%s) ******\\n\", jjmatchedKind, addUnicodeEscapes(tokenImages[jjmatchedKind]).c_str(), addUnicodeEscapes(reader->getSuffix(jjmatchedPos + 1)).c_str());");
    }

    @Override
    public void printIfToToken(LinePrinter printer) {
        printer.println("if ((jjtoToken[jjmatchedKind >> 6] & "
                + "(1ULL << (jjmatchedKind & 077))) != 0L) {");
    }

    @Override
    public void printCharBits(LinePrinter printer, int byteNum) {
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
    }

    @Override
    public void printSwitchOnStateSet(LinePrinter printer) {
        printer.println("switch(jjstateSet[--i]) {");
    }

    @Override
    public void printStartNfaWithStatesSignature(LinePrinter printer, NfaStateData data) {
        printer.print("\nint " + data.getParserName() + "TokenManager::jjStartNfaWithStates"
                + data.getLexerStateSuffix() + "(int pos, int kind, int state)");
        printer.println("{");
    }

    @Override
    public void printReadCharOrReturn(LinePrinter printer) {
        printer.println("if (reader->endOfInput()) { return pos + 1; }");
        printer.println("curChar = reader->read(); // UTF8: Support Unicode");
    }

    @Override
    public void printCanMoveSignature(LinePrinter printer, LexerData data, NfaState state) {
        printer.print("\nbool " + data.getParserName() + "TokenManager::jjCanMove_"
                + state.nonAsciiMethod
                + "(int hiByte, int i1, int i2, unsigned long long l1, unsigned long long l2)");
        printer.println("{");
        printer.println("   switch(hiByte)");
        printer.println("   {");
    }

    @Override
    public void printCanMoveEnd(LinePrinter printer) {
        printer.println("         return false;");
        printer.println("   }");
        printer.println("}");
    }

    @Override
    public void printCanMoveCase(LinePrinter printer, int hiByte) {
        printer.println("      case " + hiByte + ":");
    }

    @Override
    public void printCanMoveCaseEnd(LinePrinter printer) {
    }

    @Override
    public void printCanMoveDefault(LinePrinter printer) {
        printer.println("      default:");
    }

    @Override
    public void printCanMoveReturnBitVector(LinePrinter printer, int vector) {
        printer.println("         return ((jjbitVec" + vector + "[i2] & l2) != 0L);");
    }

    @Override
    public void printCanMoveReturnTrue(LinePrinter printer) {
        printer.println("            return true;");
    }

    @Override
    public void printCanMoveArm(LinePrinter printer, int hiVector, int loVector, boolean testHi,
                                   boolean testLo) {
        if (testHi) {
            printer.println("         if ((jjbitVec" + hiVector + "[i1] & l1) != 0L)");
        }
        if (testLo) {
            printer.println("            if ((jjbitVec" + loVector + "[i2] & l2) == 0L)");
            printer.println("               return false;");
            printer.println("            else");
        }
        printer.println("            return true;");
    }

    @Override
    public void printTokenImage(LinePrinter printer, String image) {
        CppLexerGenerator.printCharArray(printer, image);
    }

    @Override
    public void printStringLiteralImage(LinePrinter printer, RStringLiteral literal,
                                           boolean isImage) {
        CppLexerGenerator.printCharArray(printer,
                isImage ? literal.getImage() : "<" + literal.getLabel() + ">");
    }

    @Override
    public void printImageSeparator(LinePrinter printer, int i, List<RExpression> expressions) {
    }
}
