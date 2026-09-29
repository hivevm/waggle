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
import org.hivevm.waggle.codegen.LexState;
import org.hivevm.waggle.codegen.NfaMoveEmitter;
import org.hivevm.waggle.codegen.StringLiteralDfaEmitter;
import org.hivevm.waggle.lexer.LexerData;
import org.hivevm.waggle.lexer.LexerPlan.Handoff;
import org.hivevm.waggle.lexer.LexerPlan.LexStatePlan;
import org.hivevm.waggle.lexer.LexerPlan.Tables;
import org.hivevm.waggle.lexer.LexerPlan.DfaPos;
import org.hivevm.source.LinePrinter;
import org.hivevm.source.TemplateSet;

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
        var shape = data.plan().shape();
        options.set("HAS_MORE_ACTIONS", shape.moreActions());
        options.set("HAS_SKIP_ACTIONS", shape.skipActions());
        options.set("HAS_TOKEN_ACTIONS", shape.tokenActions());
        // Only C++ walks the lexical states, for the table of their names.
        options.add("MAX_LEX_STATES", shape.lexStates())
                .set("MAX_LEX_STATES_INDEX", i -> i);
        options.add("STATE_NAMES_AS_CHARS", shape.lexStates())
                .set("STATE_NAMES_AS_CHARS_INDEX", i -> i)
                .set("STATE_NAMES_AS_CHARS_CHARS", (i, w) -> CppLexerGenerator.getTextAsChars(shape.stateNames().get(i), w));
        options.set("DUMP_STR_LITERAL_IMAGES", p -> DumpStrLiteralImages(p, shape.images()));
        options.set("DUMP_STATES_FOR_STATE_CPP", p -> DumpStatesForStateCPP(p, data.plan().tables()));
        options.set("DUMP_STATES_FOR_KIND", p -> DumpStatesForKind(p, data.plan().tables()));
        options.set("DUMP_NFA_AND_DFA_HEADER",
                w -> data.plan().states().forEach(state -> dump_nfa_and_dfa_header(state, w)));

        CppTemplate.LEXER.render(options, data.getParserName());
        CppTemplate.LEXER_H.render(options, data.getParserName());
    }

    @Override
    protected TemplateSet.Source<Options> getConstantsTemplate() {
        return CppTemplate.PARSER_CONSTANTS;
    }

    /**
     * The kind each NFA state of a lexical state accepts, for the DEBUG_TOKEN_MANAGER trace:
     * {@code kindForState[lexState][state]}. It used to be one rectangular array, printed as
     * {@code = null;} for a grammar without an NFA, which is not C++.
     */
    private void DumpStatesForKind(LinePrinter printer, Tables tables) {
        if (!tables.nfa()) {
            printer.println("static const int* const kindForState[] = { nullptr };");
            return;
        }

        var kinds = tables.kindsForState();
        for (int i = 0; i < kinds.size(); i++) {
            if (!kinds.get(i).isEmpty()) {
                printer.println("static const int kindForState_" + i + "[] = { "
                        + joined(kinds.get(i)) + " };");
            }
        }
        printer.print("static const int* const kindForState[] = {");
        for (int i = 0; i < kinds.size(); i++) {
            printer.print((i > 0 ? ", " : " ") + (kinds.get(i).isEmpty() ? "nullptr" : "kindForState_" + i));
        }
        printer.println(" };");
    }

    /**
     * The NFA states each composite state stands for, per lexical state, for the
     * DEBUG_TOKEN_MANAGER trace: {@code statesForState[lexState][state]}, with the length of each
     * set in {@code statesForStateLen}. The sets used to be padded to one fixed length, so a
     * reader could not tell a set's zeros from state 0.
     */
    private void DumpStatesForStateCPP(LinePrinter printer, Tables tables) {
        var states = tables.statesForState();
        if (!tables.nfa()) { // a grammar made only of string literals has no NFA
            printer.println("static const int* const* const statesForState[] = { nullptr };");
            printer.println("static const int* const statesForStateLen[] = { nullptr };");
            return;
        }

        for (int i = 0; i < states.size(); i++) {
            if (states.get(i).isEmpty()) {
                continue;
            }
            var rows = states.get(i);
            var lengths = new ArrayList<Integer>();
            for (int j = 0; j < rows.size(); j++) {
                List<Integer> set = rows.get(j);
                lengths.add(set.size());
                printer.println("static const int stateSet_" + i + "_" + j + "[] = { "
                        + joined(set) + " };");
            }
            printer.print("static const int* const stateSet_" + i + "[] = {");
            for (int j = 0; j < rows.size(); j++) {
                printer.print((j > 0 ? ", " : " ") + "stateSet_" + i + "_" + j);
            }
            printer.println(" };");
            printer.println("static const int stateSetLen_" + i + "[] = { " + joined(lengths) + " };");
        }

        printer.print("static const int* const* const statesForState[] = {");
        for (int i = 0; i < states.size(); i++) {
            printer.print((i > 0 ? ", " : " ") + (states.get(i).isEmpty() ? "nullptr" : "stateSet_" + i));
        }
        printer.println(" };");
        printer.print("static const int* const statesForStateLen[] = {");
        for (int i = 0; i < states.size(); i++) {
            printer.print((i > 0 ? ", " : " ") + (states.get(i).isEmpty() ? "nullptr" : "stateSetLen_" + i));
        }
        printer.println(" };");
    }

    private static String joined(List<Integer> values) {
        var text = new StringBuilder();
        for (int value : values) {
            text.append(text.isEmpty() ? "" : ", ").append(value);
        }
        return text.toString();
    }

    private void DumpStrLiteralImages(LinePrinter printer, List<String> images) {
        if (images.isEmpty()) {
            printer.println("static const JJString jjstrLiteralImages[] = {};");
            return;
        }

        LexerGenerator.printLiteralImages(images, printer, true, (kind, image) -> {
            var toPrint = new StringBuilder("static JJChar jjstrLiteralChars_" + kind + "[] = {");
            if (image != null) {
                toPrint.append(CppLexerGenerator.charElements(image));
            }
            return toPrint.append("0};").toString(); // the terminating null char
        });

        printer.println("static const JJString " + "jjstrLiteralImages[] = {");
        for (int j = 0; j < images.size(); j++) {
            printer.println("jjstrLiteralChars_" + j + ", ");
        }
        printer.println("};");
    }

    /** Declares what {@code dump_nfa_and_dfa} defines for a lexical state. */
    private void dump_nfa_and_dfa_header(LexStatePlan state, LinePrinter printer) {
        var lexer_state_suffix = "_" + state.index();
        if (state.stopDfa() != null) {
            printer.println("int jjStopStringLiteralDfa" + lexer_state_suffix + "(int pos, "
                    + activeParameters(state.words()) + ");");
            printer.println("int jjStartNfa" + lexer_state_suffix + "(int pos, "
                    + activeParameters(state.words()) + ");");
        }

        if (state.startNfaWithStates()) {
            printer.println("int jjStartNfaWithStates" + lexer_state_suffix + "(int pos, int kind, int state);");
        }
        if (state.handoff() != Handoff.NONE) {
            printer.println("int jjMoveNfa" + lexer_state_suffix + "(int startState, int curPos);");
        }

        if (state.positions().isEmpty()) {
            printer.println("int jjMoveStringLiteralDfa0" + lexer_state_suffix + "();");
        } else if (state.stopAtPos()) {
            printer.println("int jjStopAtPos(int pos, int kind);");
        }

        for (var pos : state.positions()) {
            printer.print("int jjMoveStringLiteralDfa" + pos.pos() + lexer_state_suffix + "(");
            printer.print(StringLiteralDfaEmitter.parameterList(pos, longType()));
            printer.println(");");
        }
        printer.println();
    }

    @Override
    public void printMoveStringLiteralDfa0Signature(LinePrinter printer, LexState lex) {
        printer.print("int " + lex.parserName() + "TokenManager::jjMoveStringLiteralDfa0"
                + lex.suffix() + "() {");
    }

    @Override
    public void printStopAtPosSignature(LinePrinter printer, LexState lex) {
        printer.println("int " + lex.parserName() + "TokenManager::jjStopAtPos(int pos, int kind) {");
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
    public void printActionsPrologue(LinePrinter printer, String parserName, String method,
                                        String preamble) {
        printer.print("\nvoid " + parserName + "TokenManager::" + method);
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
        printer.println("         break;");
        printer.println("       }");
    }

    @Override
    public void printLoopDetected(LinePrinter printer) {
        printer.println("               loopDetected();");
    }

    @Override
    public void printMoveStringLiteralDfaHead(LinePrinter printer, LexState lex, int i) {
        printer.print("int " + lex.parserName() + "TokenManager::jjMoveStringLiteralDfa" + i
                + lex.suffix() + "(");
    }

    @Override
    public String longType() {
        return "unsigned long long";
    }

    /** Cpp logs differently. */
    @Override
    public void printDebugPossibleMatches(LinePrinter printer, DfaPos pos) {
        printer.println("if (jjmatchedKind != 0 && jjmatchedKind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + ")");
        printer.println("    fprintf(debugStream, \"   Currently matched the first %d characters as a \\\"%s\\\" token.\\n\", (jjmatchedPos + 1), addUnicodeEscapes(tokenImages[jjmatchedKind]).c_str());");
        printer.println("    fprintf(debugStream, \"   Possible string literal matches : { \");");

        StringBuilder fmt = new StringBuilder();
        StringBuilder args = new StringBuilder();
        for (int vecs = 0; vecs < pos.active().size(); vecs++) {
            if (pos.active().get(vecs)) {
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
    public void printPosAndActivesSignature(LinePrinter printer, LexState lex, String name,
                                              int maxKindsReqd) {
        printer.println("int " + lex.parserName() + "TokenManager::" + name + "(int pos, "
                + activeParameters(maxKindsReqd) + ") {");
    }

    @Override
    public void printDebugNoMoreStringLiteralMatches(LinePrinter printer) {
        printer.println(
                "fprintf(debugStream, \"   No more string literal token matches are possible.\");");
    }

    @Override
    public void printLexStateArrayOpen(LinePrinter printer, int length) {
        printer.println();
        printer.println("/** Lex State array. */");
        printer.print("static const int jjnewLexState[] = {");
    }

    @Override
    public void printBitVectorOpen(LinePrinter printer, String name, int length) {
        printer.print("static const " + longType() + " " + name + "[] = {");
    }

    @Override
    public void printNextStatesOpen(LinePrinter printer, int length) {
        printer.print("static const int jjnextStates[] = {");
    }

    @Override
    public void printMoveNfaSignature(LinePrinter printer, LexState lex) {
        printer.print("int " + lex.parserName() + "TokenManager::jjMoveNfa"
                + lex.suffix() + "(int startState, int curPos) {");
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
    public void printSwapStateSets(LinePrinter printer, LexState lex) {
        printer.println("if ((i = jjnewStateCnt), (jjnewStateCnt = startsAt), (i == (startsAt = "
                + lex.generatedStates() + " - startsAt)))");
        printer.indent();
        printer.println(lex.mixed() ? "break;" : "return curPos;");
        printer.outdent();
    }

    @Override
    public void printReadCharOrLeave(LinePrinter printer, LexState lex) {
        printer.println(lex.mixed()
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
    public void printDebugCurrentCharacter(LinePrinter printer, boolean withLexState) {
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
    public void printStartNfaWithStatesSignature(LinePrinter printer, LexState lex) {
        printer.print("\nint " + lex.parserName() + "TokenManager::jjStartNfaWithStates"
                + lex.suffix() + "(int pos, int kind, int state)");
        printer.println("{");
    }

    @Override
    public void printReadCharOrReturn(LinePrinter printer) {
        printer.println("if (reader->endOfInput()) { return pos + 1; }");
        printer.println("curChar = reader->read(); // UTF8: Support Unicode");
    }

    @Override
    public void printCanMoveSignature(LinePrinter printer, String parserName, int method) {
        printer.print("\nbool " + parserName + "TokenManager::jjCanMove_"
                + method
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
    public void printStringLiteralImage(LinePrinter printer, String image, String label,
                                           boolean isImage) {
        CppLexerGenerator.printCharArray(printer, isImage ? image : "<" + label + ">");
    }

    @Override
    public void printImageSeparator(LinePrinter printer, int i, int last) {
    }
}
