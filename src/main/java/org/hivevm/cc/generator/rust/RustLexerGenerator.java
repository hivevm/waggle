// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.rust;

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

import java.util.ArrayList;
import java.util.Hashtable;
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
    protected final void generate(LexerData data, Context options) {
        // A jjbitVec is a 256-bit map over the low byte: always four u64. This used to be the
        // number of vectors, which is a different thing entirely and only ever matched by accident.
        options.set("LOHI_BYTES_LENGTH", 4);
        options.add("LITERAL_IMAGES", RustLexerGenerator.getStrLiteralImageList(data))
                .set("LITERAL_IMAGE_NAME", s -> s);
        options.set("LITERAL_IMAGES_LENGTH",
                RustLexerGenerator.getStrLiteralImageList(data).size());
        options.set("STATES_FOR_STATE", () -> getStatesForState(data));
        options.set("KIND_FOR_STATE", () -> getKindForState(data));
        options.set("STATE_NAMES_LENGTH", data.getStateNames().size());

        RustTemplate.LEXER.render(options);
    }

    protected String self() {
        return "self.";
    }

    // ---------------------------------------------------------------- dialect

    @Override
    protected void printCheckNAdd(LinePrinter printer, int state) {
        printer.println("self.jj_check_n_add(" + state + ");");
    }

    @Override
    protected void printAddState(LinePrinter printer, int state) {
        printer.println("self.jjstate_set[self.jjnew_state_cnt] = " + state + ";");
        printer.println("self.jjnew_state_cnt += 1;");
    }

    @Override
    protected void printCheckNAddTwoStates(LinePrinter printer, int first, int second) {
        printer.println("self.jj_check_n_add_two_states(" + first + ", " + second + ");");
    }

    @Override
    protected void printCheckNAddStates(LinePrinter printer, int first, int last, boolean isRange) {
        printer.print("self.jj_check_n_add_states(" + first);
        if (isRange) {
            printer.print(", " + last);
        }
        printer.println(");");
    }

    @Override
    protected void printAddStates(LinePrinter printer, int first, int last) {
        printer.println("self.jj_add_states(" + first + ", " + last + ");");
    }

    /** A match arm does not fall through, so there is no break to emit. */
    @Override
    protected void printBreak(LinePrinter printer, String indent) {
    }

    @Override
    protected String charEquals(int c) {
        return "self.cur_char == " + c;
    }

    @Override
    protected String charNotEquals(int c) {
        return "self.cur_char != " + c;
    }

    @Override
    protected String bitIsSet(long mask) {
        return "(" + toHexString(mask) + " & l) != 0";
    }

    @Override
    protected String bitIsClear(long mask) {
        return "(" + toHexString(mask) + " & l) == 0";
    }

    /** Rust conditions carry no parentheses. */
    @Override
    protected void printIf(LinePrinter printer, String condition) {
        printer.println("if " + condition + " {");
    }

    @Override
    protected void printEndIf(LinePrinter printer) {
        printer.println("}");
    }

    @Override
    protected String canMove(NfaState state) {
        return "jj_can_move_" + state.nonAsciiMethod + "(hi_byte, i1, i2, l1, l2)";
    }

    /** A match arm carries all its patterns at once, so the labels are held back. */
    @Override
    protected void printCaseLabel(LinePrinter printer, List<String> labels, String indent,
                                  int state) {
        labels.add("" + state);
    }

    @Override
    protected void printCasesOpen(LinePrinter printer, List<String> labels, String indent) {
        if (!labels.isEmpty()) {
            printer.println(indent + String.join(" | ", labels) + " => {");
        }
    }

    @Override
    protected void printCasesClose(LinePrinter printer) {
        printer.println("}");
    }

    @Override
    protected void print_case(LinePrinter printer, String case_value) {
        printer.println(case_value + " => {");
    }

    /** Rust writes the state tables as bare arrays, without the surrounding braces Java needs. */
    @Override
    protected String noStateSet() {
        return "";
    }

    @Override
    protected String stateSet(CharSequence body) {
        return body.toString();
    }

    @Override
    protected String rowOpen() {
        return "&[";
    }

    @Override
    protected String rowClose() {
        return "]";
    }

    /** Rust conditions carry no parentheses, and every branch is a block. */
    @Override
    protected void printIfNoBlock(LinePrinter printer, String prefix, String condition) {
        printer.println(prefix + condition + " {");
    }

    /** A match inside a loop, rather than a switch inside a do/while. */
    @Override
    protected void printDefaultAndEndLoop(LinePrinter printer, boolean breakInDefault) {
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
    protected String toHexString(long value) {
        return "0x" + Long.toHexString(value);
    }

    protected String getNonAsciiMethod(NfaState state) {
        return "_" + state.nonAsciiMethod;
    }

    protected SourceProvider getConstantsTemplate() {
        return RustTemplate.PARSER_CONSTANTS;
    }

    private static List<String> getStrLiteralImageList(LexerData data) {
        var list = new ArrayList<String>();
        if (data.getImageCount() <= 0) {
            return list;
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
                    charCnt = 0;
                }

                list.add(null);
                continue;
            }

            String toPrint = "";
            for (int j = 0; j < image.length(); j++) {
                if (image.charAt(j) <= 0xff) {
                    toPrint += ("0o" + Integer.toOctalString(image.charAt(j))) + ";";
                } else {
                    String hexVal = Integer.toHexString(image.charAt(j));
                    if (hexVal.length() == 3) {
                        hexVal = "0x" + hexVal + ";";
                    }
                    toPrint += ("\\u" + hexVal);
                }
            }

            if ((charCnt += toPrint.length()) >= 80) {
                charCnt = 0;
            }

            list.add(toPrint);
        }

        while (++i < data.maxOrdinal()) {
            if ((charCnt += 6) > 80) {
                charCnt = 0;
            }
            list.add(null);
        }
        return list;
    }

    @Override
    protected void printMoveStringLiteralDfa0Signature(LinePrinter printer, NfaStateData data) {
        printer.println("fn jj_move_string_literal_dfa0" + data.getLexerStateSuffix()
                + "(&self) -> usize {");
    }

    @Override
    protected void printStopAtPosSignature(LinePrinter printer, NfaStateData data) {
        printer.println("fn jj_stop_at_pos(&mut self, pos: usize, kind: u32) -> usize {");
    }

    @Override
    protected String matchedKind() {
        return "self.jjmatched_kind";
    }

    @Override
    protected String matchedPos() {
        return "self.jjmatched_pos";
    }

    /** A Rust function returns its tail expression. */
    @Override
    protected void printReturn(LinePrinter printer, String expression) {
        printer.println(expression);
    }

    /**
     * The current-character trace. Rust has no redirectable debugStream; the trace goes to stderr,
     * which is what stderr is for.
     */
    private void printCurrentCharacter(LinePrinter printer, boolean withLexState) {
        var prefix = withLexState
                ? "<{}>Current character : {}({}) at line {} column {}\", "
                        + "LEX_STATE_NAMES[self.cur_lex_state as usize], "
                : "Current character : {}({}) at line {} column {}\", ";
        printer.println("eprintln!(\"" + prefix
                + "char::from_u32(self.cur_char).unwrap_or('\\u{fffd}'), self.cur_char, "
                + "self.input_stream.get_end_line(), self.input_stream.get_end_column());");
    }

    /** "Currently matched the first N characters as a X token." */
    private void printCurrentlyMatched(LinePrinter printer, String indent) {
        printer.println(indent + "eprintln!(\"   Currently matched the first {} characters as a {} "
                + "token.\", self.jjmatched_pos + 1, "
                + "TOKEN_IMAGE[self.jjmatched_kind as usize]);");
    }

    @Override
    protected void printDebugNoMoreStringLiteralMatches(LinePrinter printer) {
        printer.println("eprintln!(\"   No more string literal token matches are possible.\");");
    }

    @Override
    protected void printDebugNoMoreMatches(LinePrinter printer) {
        printer.println("eprintln!(\"No more string literal token matches are possible.\");");
        printCurrentlyMatched(printer, "");
    }

    /** Rust always leads with "&mut self", so every parameter prepends its own comma. */
    @Override
    protected void printMoveStringLiteralDfaSignature(LinePrinter printer, NfaStateData data, int i,
                                                      int maxLongsReqd) {
        int j;

        printer.print("fn jj_move_string_literal_dfa" + i + data.getLexerStateSuffix() + "(&mut self");

        if (i != 0) {
            if (i == 1) {
                for (j = 0; j < (maxLongsReqd - 1); j++) {
                    if (i <= data.getMaxLenForActive(j)) {
                        printer.print(", active" + j + ": u64");
                    }
                }

                if (i <= data.getMaxLenForActive(j)) {
                    printer.print(", active" + j + ": u64");
                }
            } else {
                for (j = 0; j < (maxLongsReqd - 1); j++) {
                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        printer.print(", old" + j + ": u64, active_old" + j + ": u64");
                    }
                }

                if (i <= (data.getMaxLenForActive(j) + 1)) {
                    printer.print(", old" + j + ": u64, active_old" + j + ": u64");
                }
            }
        }

        printer.println(") -> usize {");
        printer.indent();
    }

    /** Rust needs a "let" per vector: an assignment is not an expression. */
    @Override
    protected void printActiveCheck(LinePrinter printer, NfaStateData data, int i,
                                   int maxLongsReqd) {
        int j;

        if (i > 1) {
            var atLeastOne = false;
            for (j = 0; j < (maxLongsReqd - 1); j++) {
                if (i <= (data.getMaxLenForActive(j) + 1)) {
                    if (atLeastOne)
                        printer.print(" | ");
                    else
                        atLeastOne = true;
                    printer.println("let active" + j + " = active_old" + j + " & old" + j + ";");
                }
            }

            if (i <= (data.getMaxLenForActive(j) + 1)) {
                printer.println("let active" + j + " = active_old" + j + " & old" + j + ";");
            }

            atLeastOne = false;
            printer.print("if (");

            for (j = 0; j < (maxLongsReqd - 1); j++) {
                if (i <= (data.getMaxLenForActive(j) + 1)) {
                    if (atLeastOne) {
                        printer.print(" | ");
                    } else {
                        atLeastOne = true;
                    }
                    printer.print("active" + j);
                }
            }

            if (i <= (data.getMaxLenForActive(j) + 1)) {
                if (atLeastOne)
                    printer.print(" | ");
                printer.print("active" + j);
            }
            printer.println(") == 0 {");
            printer.indent();

            if (!data.isMixedState() && (data.generatedStates() != 0)) {
                printer.print("return self.jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 2) + ", ");
                for (j = 0; j < (maxLongsReqd - 1); j++) {
                    if (i <= (data.getMaxLenForActive(j) + 1)) {
                        printer.print("old" + j + ", ");
                    } else {
                        printer.print("0, ");
                    }
                }
                if (i <= (data.getMaxLenForActive(j) + 1)) {
                    printer.println("old" + j + ");");
                } else {
                    printer.println("0);");
                }
            } else if (data.generatedStates() != 0) {
                printer.println("return self.jj_move_nfa" + data.getLexerStateSuffix() +
                        "(" + InitStateName(data) + ", " + (i - 1) + ");");
            } else {
                printer.println("return " + i + ";");
            }
            printer.outdent();
            printer.println("}");
        }
    }

    /** Rust logs differently. */
    @Override
    protected void printDebugPossibleMatches(LinePrinter printer, NfaStateData data, int i) {
        if (!data.global.options().getDebugTokenManager()) {
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
    protected void printReadCharGuardOpen(LinePrinter printer) {
        printer.println("let result = self.input_stream.read_char();");
        printer.println("if result.is_err() {");
    }

    @Override
    protected void printReadCharAfterGuard(LinePrinter printer) {
        printer.println("self.cur_char = u32::from(result.unwrap());");
        printer.println();
    }

    @Override
    protected String longZero() {
        return "0";
    }

    @Override
    protected String stopStringLiteralDfaCall(NfaStateData data, int i) {
        return "self.jjStopStringLiteralDfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ";
    }

    @Override
    protected String moveNfaCall(NfaStateData data, int position) {
        return "self.jj_move_nfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", "
                + position + ")";
    }

    @Override
    protected void printDebugCurrentlyMatched(LinePrinter printer) {
        printer.println("if self.jjmatched_kind != 0 && self.jjmatched_kind != 0x"
                + Integer.toHexString(Integer.MAX_VALUE) + " {");
        printCurrentlyMatched(printer, "   ");
        printer.println("}");
    }

    @Override
    protected void printSwitchOnChar(LinePrinter printer) {
        printer.println("match self.cur_char {");
    }

    @Override
    protected void printCharCase(LinePrinter printer, int c) {
        printer.println(c + " =>");
    }

    @Override
    protected void printCharCaseWithBody(LinePrinter printer, int c) {
        printer.println(c + " => {");
    }

    @Override
    protected void printDefaultCaseOpen(LinePrinter printer) {
        printer.println("_ => {");
    }

    @Override
    protected String beginLine() {
        return "self.input_stream.get_begin_line()";
    }

    @Override
    protected String beginColumn() {
        return "self.input_stream.get_begin_column()";
    }

    @Override
    protected String matchedPosVar() {
        return "self.jjmatched_pos";
    }

    @Override
    protected String strLiteralImages() {
        return "JJSTR_LITERAL_IMAGES";
    }

    @Override
    protected String lengthOfMatch() {
        return "self.length_of_match";
    }

    @Override
    protected String imageLen() {
        return "self.jjimage_len";
    }

    @Override
    protected void dumpDfaStates(LinePrinter printer, NfaStateData data) {
        Hashtable<String, ?> tab;
        String key;
        KindInfo info;
        int maxLongsReqd = (data.getMaxStrKind() / 64) + 1;
        int i, j, k;
        boolean ifGenerated;

        for (i = 0; i < data.getMaxLen(); i++) {
            boolean startNfaNeeded = false;
            tab = data.getCharPosKind(i);
            var keys = LexerGenerator.re_arrange(tab);

            printMoveStringLiteralDfaSignature(printer, data, i, maxLongsReqd);

            if (i != 0) {
                printActiveCheck(printer, data, i, maxLongsReqd);

printDebugPossibleMatches(printer, data, i);

                printEofBailout(printer, data, i, maxLongsReqd);
            }

            if ((i != 0) && data.global.options().getDebugTokenManager()) {
                printCurrentCharacter(printer, data.global.maxLexStates() > 1);
            }

            printSwitchOnChar(printer);
            printer.indent();

            for (String key2 : keys) {
                key = key2;
                info = (KindInfo) tab.get(key);
                ifGenerated = false;
                char c = key.charAt(0);

                if (isPlainSkip(data, info, i, maxLongsReqd, c)) {
                    continue;
                }

                // Since we know key is a single character ...
                if (data.ignoreCase()) {
                    if (c != Character.toUpperCase(c)) {
                        printCharCase(printer, Character.toUpperCase(c));
                    }

                    if (c != Character.toLowerCase(c)) {
                        printCharCase(printer, Character.toLowerCase(c));
                    }
                }

                printCharCaseWithBody(printer, c);
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

                            // Rust has no braceless "if": the guard has to open a block, and only
                            // then may one be closed again. Emitting the "}" unconditionally left a
                            // stray brace whenever there was no guard at all (i == 0).
                            boolean guarded = (i != 0);
                            if (guarded) {
                                printer.println((ifGenerated ? "else if " : "if ") + "(active" + j
                                        + " & 0x" + Long.toHexString(1L << k) + ") != 0 {");
                                printer.indent();
                            }
                            ifGenerated = true;

                            int kindToPrint;

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
                                    printer.println("return self.jjStartNfaWithStates"
                                            + data.getLexerStateSuffix() + "(" + i
                                            + ", " + kindToPrint + ", " + stateSetName + ");");
                                } else {
                                    printer.println("return self.jj_stop_at_pos(" + i + ", "
                                            + kindToPrint + ");");
                                }
                            } else if (((data.global.initMatch(data.getStateIndex()) != 0)
                                    && (data.global.initMatch(data.getStateIndex())
                                    != Integer.MAX_VALUE)) || (i != 0)) {
                                printer.println("self.jjmatched_kind = " + kindToPrint + ";");
                                printer.println("self.jjmatched_pos = " + i + ";");
                            } else {
                                printer.println("self.jjmatched_kind = " + kindToPrint + ";");
                            }

                            if (guarded) {
                                printer.outdent();
                                printer.println("}");
                            }
                        }
                    }
                }

                if (info.hasValidKindCnt()) {
                    var atLeastOne = false;

                    if (i == 0) {
                        printer.print("return self.jj_move_string_literal_dfa" + (i + 1) + data.getLexerStateSuffix() + "(");
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
                        printer.print("return self.jj_move_string_literal_dfa" + (i + 1) + data.getLexerStateSuffix() + "(");

                        for (j = 0; j < (maxLongsReqd - 1); j++) {
                            if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
                                if (atLeastOne)
                                    printer.print(", ");
                                else
                                    atLeastOne = true;

                                if (info.validKinds[j] != 0L)
                                    printer.print("active" + j + ", 0x" + Long.toHexString(info.validKinds[j]));
                                else
                                    printer.print("active" + j + ", 0");
                            }
                        }

                        if ((i + 1) <= (data.getMaxLenForActive(j) + 1)) {
                            if (atLeastOne)
                                printer.print(", ");
                            if (info.validKinds[j] != 0L)
                                printer.print("active" + j + ", 0x" + Long.toHexString(info.validKinds[j]));
                            else
                                printer.print("active" + j + ", 0");
                        }
                    }

                    printer.println(");");
                } else { // A very special case.
                    if ((i == 0) && data.isMixedState()) {

                        if (data.generatedStates() != 0) {
                            printer.println("return self.jj_move_nfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", 0);");
                        } else {
                            printer.println("return 1;");
                        }
                    } else if (i != 0) // No more str literals to look for
                    {
                        startNfaNeeded = true;
                    }
                }

                printer.outdent();
                printer.println("}");
            }

            printDefaultCaseOpen(printer);
            printer.indent();

            if (data.global.options().getDebugTokenManager()) {
                printer.println("eprintln!(\"No string literal matches possible.\");");
            }

            if (data.generatedStates() != 0) {
                if (i == 0) {
                    // This means no string literal is possible. Just move nfa with this guy and return.
                    printer.println("return self.jj_move_nfa" + data.getLexerStateSuffix() + "("
                            + InitStateName(data) + ", 0);");
                } else {
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

                    printer.print("self.jjStartNfa" + data.getLexerStateSuffix() + "(" + (i - 1) + ", ");
                    for (k = 0; k < (maxLongsReqd - 1); k++) {
                        if (i <= data.getMaxLenForActive(k))
                            printer.print("active" + k + ", ");
                        else
                            printer.print("0, ");
                    }
                    if (i <= data.getMaxLenForActive(k))
                        printer.println("active" + k + ")");
                    else
                        printer.println("0)");
                } else if (data.generatedStates() != 0)
                    printer.println("self.jj_move_nfa" + data.getLexerStateSuffix() + "(" + InitStateName(data) + ", " + i + ")");
                else
                    printer.println("return " + (i + 1));
            }

            printer.outdent();
            printer.println("}");
            printer.println();
        }

        if (!data.isMixedState() && (data.generatedStates() != 0) && data.getCreateStartNfa()) {
            DumpStartWithStates(printer, data);
        }
    }

    @Override
    protected void dumpMoveNfa(LinePrinter printer, NfaStateData data) {
        printer.println();
        printer.println("fn jj_move_nfa" + data.getLexerStateSuffix()
                + "(&mut self, start_state: usize, mut cur_pos: usize) -> usize {");
        printer.indent();

        if (data.generatedStates() == 0) {
            printer.println("return cur_pos;");
            printer.outdent();
            printer.println("}");
            return;
        }

        if (data.isMixedState()) {
            printer.print("""
                    let str_kind = self.jjmatched_kind;
                    let str_pos = self.jjmatched_pos;
                    let seen_upto: usize = cur_pos + 1;
                    self.input_stream.backup(seen_upto);
                    let result = self.input_stream.read_char();
                    if result.is_err() {
                        panic!("Internal Error");
                    }
                    self.cur_char = u32::from(result.unwrap());
                    let mut cur_pos: usize = 0;
                    """);
        }

        printer.println("let mut starts_at: usize = 0;");
        printer.println("self.jjnew_state_cnt = " + data.generatedStates() + ";");
        printer.println("let mut i: usize = 1;");
        printer.println("self.jjstate_set[0] = start_state;");

        if (data.global.options().getDebugTokenManager()) {
            printer.println("eprintln!(\"   Starting NFA to match one of : {}\", "
                    + "self.jj_kinds_for_state_vector(self.cur_lex_state as usize, "
                    + "&self.jjstate_set, 0, 1));");
            printCurrentCharacter(printer, data.global.maxLexStates() > 1);
        }

        printer.println("let mut kind: u32 = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
        printer.println("loop {");
        printer.indent();
        printer.println("self.jjround += 1;");
        printer.println("if self.jjround == 0x" + Integer.toHexString(Integer.MAX_VALUE) + " {");
        printer.println("    self.re_init_rounds();");
        printer.println("}");
        printer.println("if self.cur_char < 64 {");

        printer.indent();
        DumpAsciiMoves(printer, data, 0);
        printer.outdent();

        printer.println("} else if self.cur_char < 128 {");

        printer.indent();
        DumpAsciiMoves(printer, data, 1);
        printer.outdent();

        printer.println("} else {");

        printer.indent();
        DumpCharAndRangeMoves(printer, data);
        printer.outdent();

        printer.println("}");
        printer.println("if kind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + " {");
        printer.println("   self.jjmatched_kind = kind;");
        printer.println("   self.jjmatched_pos = cur_pos;");
        printer.println("   kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
        printer.println("}");
        printer.println("cur_pos += 1;");

        if (data.global.options().getDebugTokenManager()) {
            printer.println("if self.jjmatched_kind != 0 && self.jjmatched_kind != 0x"
                    + Integer.toHexString(Integer.MAX_VALUE) + " {");
            printCurrentlyMatched(printer, "   ");
            printer.println("}");
        }

        printer.println("i = self.jjnew_state_cnt;");
        printer.println("self.jjnew_state_cnt = starts_at;");
        printer.println("starts_at = " + data.generatedStates() + " - self.jjnew_state_cnt;");
        printer.println("if i == starts_at {");
        if (data.isMixedState())
            printer.println("    break;");
        else
            printer.println("    return cur_pos;");
        printer.println("}");

        if (data.global.options().getDebugTokenManager()) {
            printer.println("eprintln!(\"   Possible kinds of longer matches : {}\", "
                    + "self.jj_kinds_for_state_vector(self.cur_lex_state as usize, "
                    + "&self.jjstate_set, starts_at, i));");
        }

        printer.println("let result = self.input_stream.read_char();");
        printer.println("if result.is_err() {");
        if (data.isMixedState())
            printer.println("    break;");
        else
            printer.println("    return cur_pos;");
        printer.println("}");
        printer.println("self.cur_char = u32::from(result.unwrap());");

        if (data.global.options().getDebugTokenManager()) {
            printCurrentCharacter(printer, data.global.maxLexStates() > 1);
        }
        printer.outdent();
        printer.println("}");

        if (data.isMixedState()) {
            printer.print("""
                    if self.jjmatched_pos > str_pos {
                       return cur_pos;
                    }
                    
                    let to_ret = cmp::max(cur_pos, seen_upto);
                    if cur_pos < to_ret {
                        let mut i = cmp::min(cur_pos, seen_upto);
                        while i > 0 {
                            let result = self.input_stream.read_char();
                            if result.is_err() {
                                panic!("Internal Error : Please send a bug report.");
                            }
                            self.cur_char = u32::from(result.unwrap());
                            i -= 1;
                        }
                    }
                    if self.jjmatched_pos < str_pos {
                        self.jjmatched_kind = str_kind;
                        self.jjmatched_pos = str_pos;
                    } else if self.jjmatched_pos == str_pos && self.jjmatched_kind > str_kind {
                        self.jjmatched_kind = str_kind;
                    }
                    
                    to_ret
                    """);
        }

        printer.outdent();
        printer.println("}");
    }

    @Override
    protected void printStopStringLiteralDfaSignature(LinePrinter printer, NfaStateData data,
                                                      int maxKindsReqd) {
        printer.print("fn jjStopStringLiteralDfa" + data.getLexerStateSuffix()
                + "(&mut self, pos: usize");
        for (int i = 0; i < maxKindsReqd; i++) {
            printer.print(", active" + i + ": " + longType());
        }
        printer.println(") -> usize {");
    }

    @Override
    protected void printStartNfaSignature(LinePrinter printer, NfaStateData data,
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
    protected String noState() {
        return "usize::MAX";
    }

    @Override
    protected void printSwitchOnPos(LinePrinter printer) {
        printer.println("match pos {");
    }

    @Override
    protected void printPosCase(LinePrinter printer, int pos) {
        printer.println(pos + " => {");
    }

    @Override
    protected void printPosCaseEnd(LinePrinter printer) {
        printer.println("}");
    }

    @Override
    protected void printPosDefault(LinePrinter printer) {
        printer.println("_ => return " + noState() + ",");
    }

    @Override
    protected void printStopDfaBodyOpen(LinePrinter printer, boolean hasKind) {
        printer.println(" {");
    }

    @Override
    protected void printStopDfaBodyClose(LinePrinter printer, boolean hasKind) {
        printer.println("}");
    }

    @Override
    protected String longType() {
        return "u64";
    }

    @Override
    protected String moveNfaName(NfaStateData data) {
        return "self.jj_move_nfa" + data.getLexerStateSuffix();
    }

    @Override
    protected String stopStringLiteralDfaName(NfaStateData data) {
        return "self.jjStopStringLiteralDfa" + data.getLexerStateSuffix();
    }

    @Override
    protected void printLexStateArrayOpen(LinePrinter printer, LexerData data) {
        printer.print("const JJNEW_LEX_STATE: [i8; " + data.maxOrdinal() + "] = [");
    }

    @Override
    protected void printBitVectorOpen(LinePrinter printer, LexerData data, String name) {
        printer.print("const " + constantName(name) + ": [" + longType() + "; "
                + ((data.maxOrdinal() / 64) + 1) + "] = [");
    }

    @Override
    protected void printArrayClose(LinePrinter printer) {
        printer.println("];");
    }

    /** {@code jjtoToken} is a constant in Rust, and constants are SCREAMING_SNAKE_CASE. */
    private static String constantName(String name) {
        return name.replaceAll("(?<=[a-z])(?=[A-Z])", "_").toUpperCase(Locale.ROOT);
    }

    /** An empty Rust array is written {@code []}, and its length must match. */
    @Override
    protected void printEmptyStateSet(LinePrinter printer) {
    }

    /**
     * Rust would see two mutable borrows of {@code self} in a single call, so the intermediate
     * result gets a name.
     */
    @Override
    protected void printStartNfaBody(LinePrinter printer, NfaStateData data, String arguments) {
        printer.println("    let state = " + stopStringLiteralDfaName(data) + "(pos, " + arguments
                + ");");
        printer.println("    return " + moveNfaName(data) + "(state, pos + 1);");
    }

    @Override
    protected void printNextStatesOpen(LinePrinter printer, LexerData data) {
        int length = data.getOrderedStateSet().stream().mapToInt(set -> set.length).sum();
        printer.print("const JJNEXT_STATES : [usize; " + length + "] = [");
    }

    @Override
    protected void printEofTokenActions(LinePrinter printer) {
        printer.println("    self.token_lexical_actions(&mut matched_token);");
    }

    @Override
    protected void printGetNextTokenPrologue(LinePrinter printer) {
        printer.println("    return matched_token;");
        printer.println("}");
    }

    /** The template already opens {@code 'EOFLoop: loop}; this is the inner loop a MORE goes round. */
    @Override
    protected void printEofLoop(LinePrinter printer) {
        printer.println("loop {");
    }

    @Override
    protected void printSwitchOnLexState(LinePrinter printer) {
        printer.println("match self.cur_lex_state {");
    }

    @Override
    protected void printLexStateCase(LinePrinter printer, int state) {
        printer.println(state + " => {");
    }

    @Override
    protected void printLexStateCaseEnd(LinePrinter printer) {
        printer.outdent();
        printer.println("}");
    }

    @Override
    protected void printSwitchOnLexStateEnd(LinePrinter printer) {
        printer.println("_ => {}");
        printer.outdent();
        printer.println("}");
    }

    @Override
    protected void printNoLexState(LinePrinter printer) {
        printer.println("self.jjmatched_kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
    }

    /** The trace the skip loop writes for every character it throws away. */
    private void printDebugSkippingCharacter(LinePrinter printer, LexerData data) {
        var prefix = (data.maxLexStates() > 1)
                ? "<{}>Skipping character : {}({})\", LEX_STATE_NAMES[self.cur_lex_state as usize], "
                : "Skipping character : {}({})\", ";
        printer.println("eprintln!(\"" + prefix
                + "char::from_u32(self.cur_char).unwrap_or('\\u{fffd}'), self.cur_char);");
    }

    @Override
    protected void printSkipSingles(LinePrinter printer, LexerData data, int state) {
        // the backup(0) is there to make the JIT happy
        printer.println("self.input_stream.backup(0);");

        long lower = data.singlesToSkip(state).asciiMoves[0];
        long upper = data.singlesToSkip(state).asciiMoves[1];
        String condition;
        if ((lower != 0L) && (upper != 0L)) {
            condition = "(self.cur_char < 64 && (0x" + Long.toHexString(lower)
                    + "u64 & (1u64 << self.cur_char)) != 0) || ((self.cur_char >> 6) == 1 && (0x"
                    + Long.toHexString(upper) + "u64 & (1u64 << (self.cur_char & 0o77))) != 0)";
        } else if (upper == 0L) {
            condition = "self.cur_char <= " + (int) LexerGenerator.MaxChar(lower) + " && (0x"
                    + Long.toHexString(lower) + "u64 & (1u64 << self.cur_char)) != 0";
        } else {
            condition = "self.cur_char > 63 && self.cur_char <= "
                    + (LexerGenerator.MaxChar(upper) + 64) + " && (0x" + Long.toHexString(upper)
                    + "u64 & (1u64 << (self.cur_char & 0o77))) != 0";
        }

        printer.println("while " + condition + " {");
        printer.indent();

        if (data.options().getDebugTokenManager()) {
            printDebugSkippingCharacter(printer, data);
        }

        // begin_token yields a Result; running out of input ends the token loop.
        printer.println("match self.input_stream.begin_token() {");
        printer.println("    Ok(c) => self.cur_char = u32::from(c),");
        printer.println("    Err(_) => continue 'EOFLoop,");
        printer.println("}");

        printer.outdent();
        printer.println("}");
    }

    @Override
    protected void printInitialMatch(LinePrinter printer, LexerData data, int state) {
        if (hasInitialMatch(data, state)) {
            if (data.options().getDebugTokenManager()) {
                printer.println("eprintln!(\"   Matched the empty string as {} token.\", "
                        + "TOKEN_IMAGE[" + data.initMatch(state) + "]);");
            }
            printer.println("self.jjmatched_kind = " + data.initMatch(state) + ";");
            printer.println("self.jjmatched_pos = usize::MAX;");
            printer.println("cur_pos = 0;");
        } else {
            printer.println("self.jjmatched_kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");
            printer.println("self.jjmatched_pos = 0;");
        }
    }

    @Override
    protected void printDebugCurrentCharacter(LinePrinter printer, LexerData data) {
        printCurrentCharacter(printer, data.maxLexStates() > 1);
    }

    @Override
    protected void printDebugCurrentCharacter(LinePrinter printer, NfaStateData data) {
        printCurrentCharacter(printer, data.global.maxLexStates() > 1);
    }

    @Override
    protected void printMoveStringLiteralDfa0Call(LinePrinter printer, int state) {
        printer.println("cur_pos = self.jj_move_string_literal_dfa0_" + state + "();");
    }

    @Override
    protected void printCanMatchAnyChar(LinePrinter printer, LexerData data, int state) {
        int kind = data.canMatchAnyChar(state);
        if (hasInitialMatch(data, state)) {
            printer.println("if self.jjmatched_pos == usize::MAX || (self.jjmatched_pos == 0"
                    + " && self.jjmatched_kind > " + kind + ") {");
        } else {
            printer.println("if self.jjmatched_pos == 0 && self.jjmatched_kind > " + kind + " {");
        }
        printer.indent();

        if (data.options().getDebugTokenManager()) {
            printer.println("eprintln!(\"   Current character matched as a {} token.\", "
                    + "TOKEN_IMAGE[" + kind + "]);");
        }
        printer.println("self.jjmatched_kind = " + kind + ";");

        if (hasInitialMatch(data, state)) {
            printer.println("self.jjmatched_pos = 0;");
        }

        printer.outdent();
        printer.println("}");
    }

    @Override
    protected void printIfMatchedKind(LinePrinter printer) {
        printer.println("if self.jjmatched_kind != 0x" + Integer.toHexString(Integer.MAX_VALUE) + " {");
    }

    @Override
    protected void printBackupBlock(LinePrinter printer, LexerData data) {
        printer.println("if self.jjmatched_pos + 1 < cur_pos {");
        printer.indent();

        if (data.options().getDebugTokenManager()) {
            printer.println("eprintln!(\"   Putting back {} characters into the input stream.\", "
                    + "cur_pos - self.jjmatched_pos - 1);");
        }

        printer.println("self.input_stream.backup(cur_pos - self.jjmatched_pos - 1);");
        printer.outdent();
        printer.println("}");
    }

    @Override
    protected void printDebugFoundMatch(LinePrinter printer) {
        printer.println("eprintln!(\"****** FOUND A {} MATCH ({}) ******\\n\", "
                + "TOKEN_IMAGE[self.jjmatched_kind as usize], "
                + "self.input_stream.get_suffix(self.jjmatched_pos + 1));");
    }

    /** {@code jjtoToken} and friends are constants in Rust, and the index has to be a usize. */
    private static String bitVectorTest(String table) {
        return "(" + table + "[(self.jjmatched_kind >> 6) as usize]"
                + " & (1u64 << (self.jjmatched_kind & 0o77))) != 0";
    }

    @Override
    protected void printIfToToken(LinePrinter printer) {
        printer.println("if " + bitVectorTest("JJTO_TOKEN") + " {");
    }

    @Override
    protected void printTokenBranch(LinePrinter printer, LexerData data) {
        printer.println("matched_token = self.jj_fill_token();");

        if (data.hasSpecial()) {
            printer.println("matched_token.special = special_token.take();");
        }
        if (data.hasTokenActions()) {
            printer.println("self.token_lexical_actions(&mut matched_token);");
        }
        if (data.maxLexStates() > 1) {
            printNewLexState(printer);
        }
        printer.println("return matched_token;");
    }

    /** Rust needs braces around the body of an {@code if}. */
    private void printNewLexState(LinePrinter printer) {
        printer.println("if JJNEW_LEX_STATE[self.jjmatched_kind as usize] != -1 {");
        printer.println("   self.cur_lex_state = JJNEW_LEX_STATE[self.jjmatched_kind as usize];");
        printer.println("}");
    }

    @Override
    protected void printSkipBranch(LinePrinter printer, LexerData data) {
        if (data.hasMore()) {
            printer.print("else if " + bitVectorTest("JJTO_SKIP"));
        } else {
            printer.print("else");
        }

        printer.println(" {");
        printer.indent();

        if (data.hasSpecial()) {
            printer.println("if " + bitVectorTest("JJTO_SPECIAL") + " {");
            printer.indent();

            // Link the new special token behind the previous one: it points back at its
            // predecessor, and the predecessor's "next" holds it. Both ends are shared, so the
            // chain is Rc<RefCell<Token>> rather than the raw references Java gets away with.
            printer.println("let token = Rc::new(RefCell::new(self.jj_fill_token()));");
            printer.println("if let Some(previous) = special_token.take() {");
            printer.println("    token.borrow_mut().special = Some(Rc::clone(&previous));");
            printer.println("    previous.borrow_mut().next = Some(Rc::clone(&token));");
            printer.println("}");
            printer.println("special_token = Some(Rc::clone(&token));");

            if (data.hasSkipActions()) {
                printer.println("self.skip_lexical_actions(Some(&token.borrow()));");
            }

            printer.outdent();

            if (data.hasSkipActions()) {
                printer.println("} else {");
                printer.println("    self.skip_lexical_actions(None);");
                printer.println("}");
            } else {
                printer.println("}");
            }
        } else if (data.hasSkipActions()) {
            printer.println("self.skip_lexical_actions(None);");
        }

        if (data.maxLexStates() > 1) {
            printNewLexState(printer);
        }

        printer.println("continue 'EOFLoop;");
        printer.outdent();
        printer.println("}");
    }

    @Override
    protected void printMoreBranch(LinePrinter printer, LexerData data) {
        if (data.hasMoreActions()) {
            printer.println("self.more_lexical_actions();");
        } else if (data.hasSkipActions() || data.hasTokenActions()) {
            printer.println("self.jjimage_len += self.jjmatched_pos + 1;");
        }

        if (data.maxLexStates() > 1) {
            printNewLexState(printer);
        }
        printer.println("cur_pos = 0;");
        printer.println("self.jjmatched_kind = 0x" + Integer.toHexString(Integer.MAX_VALUE) + ";");

        printer.println("match self.input_stream.read_char() {");
        printer.indent();
        printer.println("Ok(c) => {");
        printer.println("    self.cur_char = u32::from(c);");
        printer.println("    continue;");
        printer.println("}");
        printer.println("Err(_) => {}");
        printer.outdent();
        printer.println("}");
    }

    @Override
    protected void printLexicalErrorEpilogue(LinePrinter printer) {
        printer.outdent();
        printer.println("}");
        printer.println("""
                let mut error_line = self.input_stream.get_end_line();
                let mut error_column = self.input_stream.get_end_column();
                let mut error_after = String::new();
                let mut eof_seen = false;
                let result = self.input_stream.read_char();
                if result.is_ok() {
                    self.input_stream.backup(1);
                } else {
                    eof_seen = true;
                    if cur_pos <= 1 {
                        error_after = String::new();
                    } else {
                        error_after = self.input_stream.get_image();
                    }
                    if self.cur_char == '\\n'.try_into().unwrap()
                        || self.cur_char == '\\r'.try_into().unwrap()
                    {
                        error_line += 1;
                        error_column = 0;
                    } else {
                        error_column += 1;
                    }
                }
                if !eof_seen {
                    self.input_stream.backup(1);
                    if cur_pos <= 1 {
                        error_after = String::new();
                    } else {
                        error_after = self.input_stream.get_image();
                    }
                }
                return Token::empty();
                """);
    }

    @Override
    protected void printCharBits(LinePrinter printer, int byteNum) {
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
    protected void printMatchLoopOpen(LinePrinter printer) {
        printer.println("let mut while_cond = true;");
        printer.println("while while_cond {");
    }

    @Override
    protected void printSwitchOnStateSet(LinePrinter printer) {
        printer.println("i -= 1;");
        printer.println("match self.jjstate_set[i] {");
    }

    @Override
    protected void printStartNfaWithStatesSignature(LinePrinter printer, NfaStateData data) {
        printer.println();
        printer.println("fn jjStartNfaWithStates" + data.getLexerStateSuffix()
                + "(&mut self, pos: usize, kind: u32, state: usize) -> usize {");
    }

    @Override
    protected void printReadCharOrReturn(LinePrinter printer) {
        printer.println("match self.input_stream.read_char() {");
        printer.println("    Ok(c) => self.cur_char = u32::from(c),");
        printer.println("    Err(_) => return pos + 1,");
        printer.println("}");
    }

    @Override
    protected void printCanMoveEnd(LinePrinter printer) {
        printer.println("    false");
        printer.println("}");
    }

    @Override
    protected void printCanMoveCase(LinePrinter printer, int hiByte) {
        printer.println(hiByte + " => {");
    }

    @Override
    protected void printCanMoveCaseEnd(LinePrinter printer) {
        printer.println("}");
    }

    @Override
    protected void printCanMoveDefault(LinePrinter printer) {
        printer.println("_ => {");
    }

    @Override
    protected void printCanMoveReturnBitVector(LinePrinter printer, int vector) {
        printer.println("    return (JJBIT_VEC" + vector + "[i2] & l2) != 0;");
    }

    @Override
    protected void printCanMoveReturnTrue(LinePrinter printer) {
        printer.println("    return true;");
    }

    @Override
    protected void printCanMoveArm(LinePrinter printer, int hiVector, int loVector, boolean testHi,
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
    protected void printImageSeparator(LinePrinter printer, int i, List<RExpression> expressions) {
        printer.print(",");
    }

    @Override
    protected void printActionCase(LinePrinter printer, int kind) {
        printer.println(kind + " => {");
    }

    @Override
    protected void printActionBreak(LinePrinter printer) {
    }

    @Override
    protected void printActionCaseEnd(LinePrinter printer) {
        printer.println("}");
    }

    /** The image buffer the MORE and SKIP actions append to. */
    @Override
    protected void printImageInit(LinePrinter printer) {
        printer.println("self.image.clear();");
        printer.println("self.jjimage_len = 0;");
    }

    @Override
    protected void printImageAppend(LinePrinter printer, LexerData data, int i, String indent) {
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
    protected void printImageAppendMore(LinePrinter printer, LexerData data, int i) {
        if (data.getImage(i) != null) {
            printer.println("self.image.push_str(" + strLiteralImages() + "[" + i + "]);");
        } else {
            printer.println("let suffix = " + inputStream() + getSuffix() + "(" + imageLen() + ");");
            printer.println("self.image.push_str(&suffix);");
        }
    }

    @Override
    protected void printImageReset(LinePrinter printer) {
        printer.println("self.image.clear();");
    }

    @Override
    protected void printEmptyLoopCheck(LinePrinter printer, LexerData data, int i) {
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
    protected void printLoopDetected(LinePrinter printer) {
        printer.println("        panic!(");
        printer.println("            \"Bailing out of infinite loop caused by repeated empty "
                + "string matches at line {}, column {}.\",");
        printer.println("            " + beginLine() + ", " + beginColumn() + "");
        printer.println("        );");
    }

    @Override
    protected String inputStream() {
        return "self.input_stream.";
    }

    @Override
    protected String getSuffix() {
        return "get_suffix";
    }
}
