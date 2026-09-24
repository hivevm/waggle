// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/RStringLiteral.java, org/javacc/parser/NfaState.java

package org.hivevm.waggle.codegen.rust;

import org.hivevm.waggle.api.Options;
import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.codegen.GetNextTokenEmitter;
import org.hivevm.waggle.codegen.LexerGenerator;
import org.hivevm.waggle.codegen.NfaMoveEmitter;
import org.hivevm.waggle.codegen.StringLiteralDfaEmitter;
import org.hivevm.waggle.lexer.LexerData;
import org.hivevm.waggle.lexer.NfaState;
import org.hivevm.waggle.lexer.NfaStateData;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.source.LinePrinter;
import static org.hivevm.waggle.codegen.rust.RustDebugPrinter.printCurrentCharacter;
import static org.hivevm.waggle.codegen.rust.RustDebugPrinter.printCurrentlyMatched;
import org.hivevm.source.SourceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generate lexer.
 */
class RustLexerGenerator extends LexerGenerator {

    public RustLexerGenerator() {
        super(Language.RUST);
    }

    @Override
    protected GetNextTokenEmitter newGetNextTokenEmitter() {
        return new RustGetNextTokenEmitter(this, this);
    }

    @Override
    protected NfaMoveEmitter newNfaMoveEmitter() {
        return new RustNfaMoveEmitter(this);
    }

    @Override
    protected StringLiteralDfaEmitter newStringLiteralDfaEmitter() {
        return new RustStringLiteralDfaEmitter(this);
    }

    @Override
    protected final void generate(LexerData data, OptionsContext options) {
        // A jjbitVec is a 256-bit map over the low byte: always four u64. This used to be the
        // number of vectors, which is a different thing entirely and only ever matched by accident.
        options.set("LOHI_BYTES_LENGTH", 4);
        var images = RustLexerGenerator.getStrLiteralImageList(data);
        options.add("LITERAL_IMAGES", images).set("LITERAL_IMAGE_NAME", s -> s);
        options.set("LITERAL_IMAGES_LENGTH", images.size());
        options.set("STATES_FOR_STATE", () -> getStatesForState(data));
        options.set("KIND_FOR_STATE", () -> getNextToken().getKindForState(data));
        options.set("STATE_NAMES_LENGTH", data.getStateNames().size());

        RustTemplate.LEXER.render(options);
    }

    @Override
    public String self() {
        return "self.";
    }

    // ---------------------------------------------------------------- dialect

    @Override
    public void printCheckNAdd(LinePrinter printer, int state) {
        printer.println("self.jj_check_n_add(" + state + ");");
    }

    @Override
    public void printAddState(LinePrinter printer, int state) {
        printer.println("self.jjstate_set[self.jjnew_state_cnt] = " + state + ";");
        printer.println("self.jjnew_state_cnt += 1;");
    }

    @Override
    public void printCheckNAddTwoStates(LinePrinter printer, int first, int second) {
        printer.println("self.jj_check_n_add_two_states(" + first + ", " + second + ");");
    }

    @Override
    public void printCheckNAddStates(LinePrinter printer, int first, int last, boolean isRange) {
        printer.print("self.jj_check_n_add_states(" + first);
        if (isRange) {
            printer.print(", " + last);
        }
        printer.println(");");
    }

    @Override
    public void printAddStates(LinePrinter printer, int first, int last) {
        printer.println("self.jj_add_states(" + first + ", " + last + ");");
    }

    /** A match arm does not fall through, so there is no break to emit. */
    @Override
    public void printBreak(LinePrinter printer, String indent) {
    }

    @Override
    public String charEquals(int c) {
        return "self.cur_char == " + c;
    }

    @Override
    public String charNotEquals(int c) {
        return "self.cur_char != " + c;
    }

    @Override
    public String bitIsSet(long mask) {
        return "(" + toHexString(mask) + " & l) != 0";
    }

    @Override
    public String bitIsClear(long mask) {
        return "(" + toHexString(mask) + " & l) == 0";
    }

    /** Rust conditions carry no parentheses. */
    @Override
    public void printIf(LinePrinter printer, String condition) {
        printer.println("if " + condition + " {");
    }

    @Override
    public void printEndIf(LinePrinter printer) {
        printer.println("}");
    }

    @Override
    public String canMove(NfaState state) {
        return "jj_can_move_" + state.nonAsciiMethod + "(hi_byte, i1, i2, l1, l2)";
    }

    /** A match arm carries all its patterns at once, so the labels are held back. */
    @Override
    public void printCaseLabel(LinePrinter printer, List<String> labels, String indent,
                                  int state) {
        labels.add("" + state);
    }

    @Override
    public void printCasesOpen(LinePrinter printer, List<String> labels, String indent) {
        if (!labels.isEmpty()) {
            printer.println(indent + String.join(" | ", labels) + " => {");
        }
    }

    @Override
    public void printCasesClose(LinePrinter printer) {
        printer.println("}");
    }

    /** Rust writes the state tables as bare arrays, without the surrounding braces Java needs. */
    @Override
    public String noStateSet() {
        return "";
    }

    @Override
    public String stateSet(CharSequence body) {
        return body.toString();
    }

    @Override
    public String rowOpen() {
        return "&[";
    }

    @Override
    public String rowClose() {
        return "]";
    }

    /** Rust conditions carry no parentheses, and every branch is a block. */
    @Override
    public void printIfNoBlock(LinePrinter printer, String prefix, String condition) {
        printer.println(prefix + condition + " {");
    }

    /** A match inside a loop, rather than a switch inside a do/while. */
    @Override
    public void printDefaultAndEndLoop(LinePrinter printer, boolean breakInDefault) {
        printer.println("_ => {");
        printer.indent();
        if (breakInDefault) {
            printer.println("break;");
        }
        printer.outdent();
        printer.println("}");

        printer.outdent();
        printer.println("}");
        printer.println("while_cond = i != starts_at;");
        printer.outdent();
        printer.println("}");
    }

    @Override
    public String toHexString(long value) {
        return "0x" + Long.toHexString(value);
    }

    @Override
    protected String getNonAsciiMethod(NfaState state) {
        return "_" + state.nonAsciiMethod;
    }

    @Override
    protected SourceProvider<Options> getConstantsTemplate() {
        return RustTemplate.PARSER_CONSTANTS;
    }

    /**
     * The literal image of each token kind, as the content of a Rust string literal; {@code null}
     * where a kind has none, which the template renders as the empty string.
     *
     * <p>This used to write JavaCC's octal escapes in a form Rust does not know, so {@code "if"}
     * came out as {@code "0o151;0o146;"} -- which compiled, and became the image of every keyword.
     */
    private static List<String> getStrLiteralImageList(LexerData data) {
        var list = new ArrayList<String>();
        for (int i = 0; i < data.getImageCount(); i++) {
            var image = data.getImage(i);
            list.add((image == null) ? null : RustLexerGenerator.toRustStringContent(image));
        }
        return list;
    }

    private static String toRustStringContent(String image) {
        // Code points, not chars: Rust has no escape for half a surrogate pair.
        var text = new StringBuilder();
        image.codePoints().forEach(c -> {
            if ((c == '"') || (c == '\\')) {
                text.append('\\').appendCodePoint(c);
            } else if ((c >= 0x20) && (c < 0x7f)) {
                text.appendCodePoint(c);
            } else {
                text.append("\\u{").append(Integer.toHexString(c)).append('}');
            }
        });
        return text.toString();
    }

    @Override
    public void printMoveStringLiteralDfa0Signature(LinePrinter printer, NfaStateData data) {
        printer.println("fn jj_move_string_literal_dfa0" + data.getLexerStateSuffix()
                + "(&self) -> usize {");
    }

    @Override
    public void printStopAtPosSignature(LinePrinter printer, NfaStateData data) {
        printer.println("fn jj_stop_at_pos(&mut self, pos: usize, kind: u32) -> usize {");
    }

    @Override
    public String matchedKind() {
        return "self.jjmatched_kind";
    }

    @Override
    public String matchedPos() {
        return "self.jjmatched_pos";
    }

    /** A Rust function returns its tail expression. */
    @Override
    public void printReturn(LinePrinter printer, String expression) {
        printer.println(expression);
    }

    @Override
    public void printDebugNoMoreStringLiteralMatches(LinePrinter printer) {
        printer.println("eprintln!(\"   No more string literal token matches are possible.\");");
    }

    @Override
    public void printDebugNoMoreMatches(LinePrinter printer) {
        printer.println("eprintln!(\"No more string literal token matches are possible.\");");
        printCurrentlyMatched(printer, "");
    }

    /** Rust logs differently. */
    @Override
    public void printDebugPossibleMatches(LinePrinter printer, NfaStateData data, int i) {
        if (!data.global.getDebugTokenManager()) {
            return;
        }

        // Java guards only the first of the two lines -- its "if" carries no braces. Wrapping both
        // in a Rust block swallowed the second one.
        printer.println("if self.jjmatched_kind != 0 && self.jjmatched_kind != 0x"
                + Integer.toHexString(Integer.MAX_VALUE) + " {");
        printCurrentlyMatched(printer, "   ");
        printer.println("}");

        var vectors = new ArrayList<String>();
        for (int vecs = 0; vecs < ((data.getMaxStrKind() / 64) + 1); vecs++) {
            if (i <= data.getMaxLenForActive(vecs)) {
                vectors.add("self.jj_kinds_for_bit_vector(" + vecs + ", active" + vecs
                        + ", &mut kind_cnt)");
            }
        }

        printer.println("let mut kind_cnt = 0;");
        printer.println("eprintln!(\"   Possible string literal matches : {{ {} }} \", "
                + String.join(" + &", vectors) + ");");
    }

    @Override
    public void printReadCharGuardOpen(LinePrinter printer) {
        printer.println("let result = self.input_stream.read_char();");
        printer.println("if result.is_err() {");
    }

    @Override
    public void printReadCharAfterGuard(LinePrinter printer) {
        printer.println("self.cur_char = u32::from(result.unwrap());");
        printer.println();
    }

    @Override
    public String longZero() {
        return "0";
    }

    @Override
    public String stopStringLiteralDfaCall(NfaStateData data, int i) {
        return "self.jjStopStringLiteralDfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ";
    }

    @Override
    public String moveNfaCall(NfaStateData data, int position) {
        return "self.jj_move_nfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", "
                + position + ")";
    }

    @Override
    public void printDebugCurrentlyMatched(LinePrinter printer) {
        printer.println("if self.jjmatched_kind != 0 && self.jjmatched_kind != 0x"
                + Integer.toHexString(Integer.MAX_VALUE) + " {");
        printCurrentlyMatched(printer, "   ");
        printer.println("}");
    }

    @Override
    public void printSwitchOnChar(LinePrinter printer) {
        printer.println("match self.cur_char {");
    }

    @Override
    public void printCharCase(LinePrinter printer, int c) {
        printer.println(c + " =>");
    }

    @Override
    public void printCharCaseWithBody(LinePrinter printer, int c) {
        printer.println(c + " => {");
    }

    @Override
    public void printDefaultCaseOpen(LinePrinter printer) {
        printer.println("_ => {");
    }

    @Override
    public String beginLine() {
        return "self.input_stream.get_begin_line()";
    }

    @Override
    public String beginColumn() {
        return "self.input_stream.get_begin_column()";
    }

    @Override
    public String matchedPosVar() {
        return "self.jjmatched_pos";
    }

    @Override
    public String strLiteralImages() {
        return "JJSTR_LITERAL_IMAGES";
    }

    @Override
    public String lengthOfMatch() {
        return "self.length_of_match";
    }

    @Override
    public String imageLen() {
        return "self.jjimage_len";
    }

    @Override
    public void printStopStringLiteralDfaSignature(LinePrinter printer, NfaStateData data,
                                                      int maxKindsReqd) {
        printer.print("fn jjStopStringLiteralDfa" + data.getLexerStateSuffix()
                + "(&mut self, pos: usize");
        for (int i = 0; i < maxKindsReqd; i++) {
            printer.print(", active" + i + ": " + longType());
        }
        printer.println(") -> usize {");
    }

    @Override
    public void printStartNfaSignature(LinePrinter printer, NfaStateData data,
                                          int maxKindsReqd) {
        printer.print("fn jjStartNfa" + data.getLexerStateSuffix() + "(&mut self, pos: usize");
        for (int i = 0; i < maxKindsReqd; i++) {
            printer.print(", active" + i + ": " + longType());
        }
        printer.println(") -> usize {");
    }

    /**
     * A state index is a {@code usize} in Rust, so -1 cannot mean "no state". {@code usize::MAX}
     * does: it reaches no arm of the state dispatch, exactly as -1 reaches no case in Java.
     */
    @Override
    public String noState() {
        return "usize::MAX";
    }

    @Override
    public void printSwitchOnPos(LinePrinter printer) {
        printer.println("match pos {");
    }

    @Override
    public void printPosCase(LinePrinter printer, int pos) {
        printer.println(pos + " => {");
    }

    @Override
    public void printPosCaseEnd(LinePrinter printer) {
        printer.println("}");
    }

    @Override
    public void printPosDefault(LinePrinter printer) {
        printer.println("_ => return " + noState() + ",");
    }

    @Override
    public void printStopDfaBodyOpen(LinePrinter printer, boolean hasKind) {
        printer.println(" {");
    }

    @Override
    public void printStopDfaBodyClose(LinePrinter printer, boolean hasKind) {
        printer.println("}");
    }

    @Override
    public String longType() {
        return "u64";
    }

    @Override
    public String moveNfaName(NfaStateData data) {
        return "self.jj_move_nfa" + data.getLexerStateSuffix();
    }

    @Override
    public String stopStringLiteralDfaName(NfaStateData data) {
        return "self.jjStopStringLiteralDfa" + data.getLexerStateSuffix();
    }

    @Override
    public void printLexStateArrayOpen(LinePrinter printer, LexerData data) {
        printer.print("const JJNEW_LEX_STATE: [i8; " + data.maxOrdinal() + "] = [");
    }

    @Override
    public void printBitVectorOpen(LinePrinter printer, LexerData data, String name) {
        printer.print("const " + constantName(name) + ": [" + longType() + "; "
                + ((data.maxOrdinal() / 64) + 1) + "] = [");
    }

    @Override
    public void printArrayClose(LinePrinter printer) {
        printer.println("];");
    }

    /** {@code jjtoToken} is a constant in Rust, and constants are SCREAMING_SNAKE_CASE. */
    private static String constantName(String name) {
        return name.replaceAll("(?<=[a-z])(?=[A-Z])", "_").toUpperCase(Locale.ROOT);
    }

    /** An empty Rust array is written {@code []}, and its length must match. */
    @Override
    public void printEmptyStateSet(LinePrinter printer) {
    }

    /**
     * Rust would see two mutable borrows of {@code self} in a single call, so the intermediate
     * result gets a name.
     */
    @Override
    public void printStartNfaBody(LinePrinter printer, NfaStateData data, String arguments) {
        printer.println("    let state = " + stopStringLiteralDfaName(data) + "(pos, " + arguments
                + ");");
        printer.println("    return " + moveNfaName(data) + "(state, pos + 1);");
    }

    @Override
    public void printNextStatesOpen(LinePrinter printer, LexerData data) {
        int length = data.getOrderedStateSet().stream().mapToInt(set -> set.length).sum();
        printer.print("const JJNEXT_STATES : [usize; " + length + "] = [");
    }

    @Override
    public void printEofTokenActions(LinePrinter printer) {
        printer.println("    self.token_lexical_actions(&mut matched_token);");
    }

    @Override
    public void printGetNextTokenPrologue(LinePrinter printer) {
        printer.println("    return matched_token;");
        printer.println("}");
    }

    /** The template already opens {@code 'EOFLoop: loop}; this is the inner loop a MORE goes round. */
    @Override
    public void printEofLoop(LinePrinter printer) {
        printer.println("loop {");
    }

    @Override
    public void printSwitchOnLexState(LinePrinter printer) {
        printer.println("match self.cur_lex_state {");
    }

    @Override
    public void printLexStateCase(LinePrinter printer, int state) {
        printer.println(state + " => {");
    }

    @Override
    public void printLexStateCaseEnd(LinePrinter printer) {
        printer.outdent();
        printer.println("}");
    }

    @Override
    public void printSwitchOnLexStateEnd(LinePrinter printer) {
        printer.println("_ => {}");
        printer.outdent();
        printer.println("}");
    }

    @Override
    public void printNoLexState(LinePrinter printer) {
        printer.println("self.jjmatched_kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
    }

    @Override
    public void printDebugCurrentCharacter(LinePrinter printer, LexerData data) {
        printCurrentCharacter(printer, data.maxLexStates() > 1);
    }

    @Override
    public void printDebugCurrentCharacter(LinePrinter printer, NfaStateData data) {
        printCurrentCharacter(printer, data.global.maxLexStates() > 1);
    }

    @Override
    public void printMoveStringLiteralDfa0Call(LinePrinter printer, int state) {
        printer.println("cur_pos = self.jj_move_string_literal_dfa0_" + state + "();");
    }

    @Override
    public void printIfMatchedKind(LinePrinter printer) {
        printer.println("if self.jjmatched_kind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + " {");
    }

    @Override
    public void printDebugFoundMatch(LinePrinter printer) {
        printer.println("eprintln!(\"****** FOUND A {} MATCH ({}) ******\\n\", "
                + "TOKEN_IMAGE[self.jjmatched_kind as usize], "
                + "self.input_stream.get_suffix(self.jjmatched_pos + 1));");
    }

    @Override
    public void printIfToToken(LinePrinter printer) {
        printer.println("if " + bitVectorTest("JJTO_TOKEN") + " {");
    }

    @Override
    public void printCharBits(LinePrinter printer, int byteNum) {
        if (byteNum == 0) {
            printer.println("let l: u64 = 1u64 << self.cur_char;");
        } else if (byteNum == 1) {
            printer.println("let l: u64 = 1u64 << (self.cur_char & 0o77);");
        } else {
            printer.println("let hi_byte: u32 = self.cur_char >> 8;");
            printer.println("let l1: u64 = 1u64 << (hi_byte & 0o77);");
            printer.println("let l2: u64 = 1u64 << (self.cur_char & 0o77);");
            printer.println("let i1: usize = (hi_byte >> 6) as usize;");
            printer.println("let i2: usize = ((self.cur_char & 0xff) >> 6) as usize;");
        }
    }

    @Override
    public void printMatchLoopOpen(LinePrinter printer) {
        printer.println("let mut while_cond = true;");
        printer.println("while while_cond {");
    }

    @Override
    public void printSwitchOnStateSet(LinePrinter printer) {
        printer.println("i -= 1;");
        printer.println("match self.jjstate_set[i] {");
    }

    @Override
    public void printStartNfaWithStatesSignature(LinePrinter printer, NfaStateData data) {
        printer.println();
        printer.println("fn jjStartNfaWithStates" + data.getLexerStateSuffix()
                + "(&mut self, pos: usize, kind: u32, state: usize) -> usize {");
    }

    @Override
    public void printReadCharOrReturn(LinePrinter printer) {
        printer.println("match self.input_stream.read_char() {");
        printer.println("    Ok(c) => self.cur_char = u32::from(c),");
        printer.println("    Err(_) => return pos + 1,");
        printer.println("}");
    }

    @Override
    public void printCanMoveEnd(LinePrinter printer) {
        printer.println("    false");
        printer.println("}");
    }

    @Override
    public void printCanMoveCase(LinePrinter printer, int hiByte) {
        printer.println(hiByte + " => {");
    }

    @Override
    public void printCanMoveCaseEnd(LinePrinter printer) {
        printer.println("}");
    }

    @Override
    public void printCanMoveDefault(LinePrinter printer) {
        printer.println("_ => {");
    }

    @Override
    public void printCanMoveReturnBitVector(LinePrinter printer, int vector) {
        printer.println("    return (JJBIT_VEC" + vector + "[i2] & l2) != 0;");
    }

    @Override
    public void printCanMoveReturnTrue(LinePrinter printer) {
        printer.println("    return true;");
    }

    @Override
    public void printCanMoveArm(LinePrinter printer, int hiVector, int loVector, boolean testHi,
                                   boolean testLo) {
        if (testHi) {
            printer.println("    if (JJBIT_VEC" + hiVector + "[i1] & l1) != 0 {");
        }
        if (testLo) {
            printer.println("        if (JJBIT_VEC" + loVector + "[i2] & l2) == 0 {");
            printer.println("            return false;");
            printer.println("        } else {");
        }
        printer.println("        return true;");
        printer.println("    }");
    }

    @Override
    public void printImageSeparator(LinePrinter printer, int i, List<RExpression> expressions) {
        printer.print(",");
    }

    @Override
    public void printActionCase(LinePrinter printer, int kind) {
        printer.println(kind + " => {");
    }

    @Override
    public void printActionBreak(LinePrinter printer) {
    }

    @Override
    public void printActionCaseEnd(LinePrinter printer) {
        printer.println("}");
    }

    /** The image buffer the MORE and SKIP actions append to. */
    @Override
    public void printImageInit(LinePrinter printer) {
        printer.println("self.image.clear();");
        printer.println("self.jjimage_len = 0;");
    }

    @Override
    public void printImageAppend(LinePrinter printer, LexerData data, int i, String indent) {
        if (data.getImage(i) != null) {
            printer.println("self.image.push_str(" + strLiteralImages() + "[" + i + "]);");
            printer.println(lengthOfMatch() + " = " + strLiteralImages() + "[" + i + "].len();");
        } else {
            // The suffix has to be read out before the borrow of self.image starts.
            printer.println(lengthOfMatch() + " = " + matchedPosVar() + " + 1;");
            printer.println("let suffix = " + inputStream() + getSuffix() + "(" + imageLen()
                    + " + " + lengthOfMatch() + ");");
            printer.println("self.image.push_str(&suffix);");
        }
    }

    @Override
    public void printImageAppendMore(LinePrinter printer, LexerData data, int i) {
        if (data.getImage(i) != null) {
            printer.println("self.image.push_str(" + strLiteralImages() + "[" + i + "]);");
        } else {
            printer.println("let suffix = " + inputStream() + getSuffix() + "(" + imageLen() + ");");
            printer.println("self.image.push_str(&suffix);");
        }
    }

    @Override
    public void printImageReset(LinePrinter printer) {
        printer.println("self.image.clear();");
    }

    @Override
    public void printEmptyLoopCheck(LinePrinter printer, LexerData data, int i) {
        printer.println("if " + matchedPosVar() + " == usize::MAX {");
        printer.println("    if self.jjbeenHere[" + data.getState(i) + "]");
        printer.println("        && self.jjemptyLineNo[" + data.getState(i) + "] == " + beginLine());
        printer.println("        && self.jjemptyColNo[" + data.getState(i) + "] == " + beginColumn()
                + "");
        printer.println("    {");
        printLoopDetected(printer);
        printer.println("    }");
        printer.println("    self.jjemptyLineNo[" + data.getState(i) + "] = " + beginLine() + ";");
        printer.println("    self.jjemptyColNo[" + data.getState(i) + "] = " + beginColumn() + ";");
        printer.println("    self.jjbeenHere[" + data.getState(i) + "] = true;");
        printer.println("}");
    }

    @Override
    public void printLoopDetected(LinePrinter printer) {
        printer.println("        panic!(");
        printer.println("            \"Bailing out of infinite loop caused by repeated empty "
                + "string matches at line {}, column {}.\",");
        printer.println("            " + beginLine() + ", " + beginColumn() + "");
        printer.println("        );");
    }

    @Override
    public String inputStream() {
        return "self.input_stream.";
    }

    @Override
    public String getSuffix() {
        return "get_suffix";
    }

    /** {@code jjtoToken} and friends are constants in Rust, and the index has to be a usize. */
    static String bitVectorTest(String table) {
        return "(" + table + "[(self.jjmatched_kind >> 6) as usize]"
                + " & (1u64 << (self.jjmatched_kind & 0o77))) != 0";
    }
}
