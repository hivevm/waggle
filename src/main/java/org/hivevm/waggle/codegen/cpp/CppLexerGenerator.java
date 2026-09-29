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
import org.hivevm.waggle.codegen.StringLiteralDfaEmitter;
import org.hivevm.waggle.lexer.LexerData;
import org.hivevm.waggle.lexer.LexerPlan.Handoff;
import org.hivevm.waggle.lexer.LexerPlan.LexStatePlan;
import org.hivevm.waggle.lexer.LexerPlan.Tables;
import org.hivevm.source.TemplateSet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Generate lexer.
 */
class CppLexerGenerator extends LexerGenerator {

    public CppLexerGenerator() {
        super(Language.CPP);
    }

    @Override
    protected GetNextTokenEmitter newGetNextTokenEmitter() {
        return new CppGetNextTokenEmitter(this);
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
                .set("STATE_NAMES_AS_CHARS_CHARS",
                        i -> CppLexerGenerator.getTextAsChars(shape.stateNames().get(i)));
        options.set("LITERAL_IMAGE_COUNT", shape.images().size());
        options.add("LITERAL_IMAGE_ROWS", literalChars(shape.images()));
        options.add("LITERAL_IMAGE_REFS", IntStream.range(0, shape.images().size())
                .mapToObj(CppDebugTables.LiteralRef::new).toList());
        options.add("STATES_FOR_STATE_CPP", List.of(statesForState(data.plan().tables())));
        options.add("KIND_FOR_STATE_CPP", List.of(kindForState(data.plan().tables())));
        options.add("STATE_DECLS", data.plan().states().stream().map(this::stateDecls).toList());

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
    private static CppDebugTables.KindForState kindForState(Tables tables) {
        var kinds = tables.kindsForState();
        var rows = new ArrayList<CppDebugTables.KindRow>();
        var refs = new StringBuilder();
        for (int i = 0; i < kinds.size(); i++) {
            if (!kinds.get(i).isEmpty()) {
                rows.add(new CppDebugTables.KindRow(i, joined(kinds.get(i))));
            }
            refs.append((i > 0) ? ", " : " ")
                    .append(kinds.get(i).isEmpty() ? "nullptr" : "kindForState_" + i);
        }
        return new CppDebugTables.KindForState(tables.nfa(), List.copyOf(rows), refs.toString());
    }

    /**
     * The NFA states each composite state stands for, per lexical state, for the
     * DEBUG_TOKEN_MANAGER trace: {@code statesForState[lexState][state]}, with the length of each
     * set in {@code statesForStateLen}. The sets used to be padded to one fixed length, so a
     * reader could not tell a set's zeros from state 0.
     */
    private static CppDebugTables.StatesForState statesForState(Tables tables) {
        var states = tables.statesForState();
        var sets = new ArrayList<CppDebugTables.StateSets>();
        var setRefs = new StringBuilder();
        var lenRefs = new StringBuilder();
        for (int i = 0; i < states.size(); i++) {
            var rows = states.get(i);
            if (!rows.isEmpty()) {
                var members = new ArrayList<CppDebugTables.StateSet>();
                var names = new StringBuilder();
                var lengths = new ArrayList<Integer>();
                for (int j = 0; j < rows.size(); j++) {
                    members.add(new CppDebugTables.StateSet(i, j, joined(rows.get(j))));
                    names.append((j > 0) ? ", " : " ").append("stateSet_" + i + "_" + j);
                    lengths.add(rows.get(j).size());
                }
                sets.add(new CppDebugTables.StateSets(i, List.copyOf(members), names.toString(),
                        joined(lengths)));
            }
            setRefs.append((i > 0) ? ", " : " ")
                    .append(rows.isEmpty() ? "nullptr" : "stateSet_" + i);
            lenRefs.append((i > 0) ? ", " : " ")
                    .append(rows.isEmpty() ? "nullptr" : "stateSetLen_" + i);
        }
        return new CppDebugTables.StatesForState(tables.nfa(), List.copyOf(sets),
                setRefs.toString(), lenRefs.toString());
    }

    private static String joined(List<Integer> values) {
        var text = new StringBuilder();
        for (int value : values) {
            text.append(text.isEmpty() ? "" : ", ").append(value);
        }
        return text.toString();
    }

    /** One {@code jjstrLiteralChars_<kind>} array per token kind. */
    private static List<CppDebugTables.LiteralChars> literalChars(List<String> images) {
        return IntStream.range(0, images.size()).mapToObj(kind -> new CppDebugTables.LiteralChars(
                kind, (images.get(kind) == null) ? "" : charElements(images.get(kind)))).toList();
    }

    /** What the header declares of what the lexer defines for a lexical state. */
    private CppDebugTables.StateDecls stateDecls(LexStatePlan state) {
        return new CppDebugTables.StateDecls("_" + state.index(), state.stopDfa() != null,
                activeParameters(state.words()), state.startNfaWithStates(),
                state.handoff() != Handoff.NONE, state.positions().isEmpty(), state.stopAtPos(),
                state.positions().stream().map(pos -> new CppDebugTables.DfaDecl(pos.pos(),
                        StringLiteralDfaEmitter.parameterList(pos, longType()))).toList());
    }

    @Override
    public String longType() {
        return "unsigned long long";
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

    private static String getTextAsChars(String text) {
        List<String> chars = new ArrayList<>();
        for (int j = 0; j < text.length(); j++) {
            chars.add("0x" + Integer.toHexString(text.charAt(j)));
        }
        return String.join(", ", chars);
    }

    @Override
    public String toHexString(long value) {
        return "0x" + Long.toHexString(value) + "ULL";
    }

    @Override
    public String tokenImage(String image) {
        return CppLexerGenerator.charElements(image);
    }

    @Override
    public String stringLiteralImage(String image, String label, boolean isImage) {
        return CppLexerGenerator.charElements(isImage ? image : "<" + label + ">");
    }

    @Override
    public String imageSeparator(int i, int last) {
        return "";
    }
}
