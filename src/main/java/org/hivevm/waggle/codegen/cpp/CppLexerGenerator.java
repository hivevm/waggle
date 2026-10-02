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
import org.hivevm.waggle.codegen.LexerGenerator;
import org.hivevm.waggle.codegen.StringLiteralDfaEmitter;
import org.hivevm.waggle.lexer.LexerData;
import org.hivevm.waggle.lexer.LexerPlan.Handoff;
import org.hivevm.waggle.lexer.LexerPlan.LexStatePlan;
import org.hivevm.waggle.lexer.LexerPlan.Tables;
import org.hivevm.source.TemplateSet;

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
                        i -> CppChars.of(shape.stateNames().get(i)));
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
        var states = IntStream.range(0, kinds.size()).boxed().toList();
        return new CppDebugTables.KindForState(tables.nfa(),
                states.stream().filter(i -> !kinds.get(i).isEmpty())
                        .map(i -> new CppDebugTables.KindRow(i, joined(kinds.get(i)))).toList(),
                states.stream().map(i -> new CppDebugTables.KindRef(i, !kinds.get(i).isEmpty()))
                        .toList());
    }

    /**
     * The NFA states each composite state stands for, per lexical state, for the
     * DEBUG_TOKEN_MANAGER trace: {@code statesForState[lexState][state]}, with the length of each
     * set in {@code statesForStateLen}. The sets used to be padded to one fixed length, so a
     * reader could not tell a set's zeros from state 0.
     */
    private static CppDebugTables.StatesForState statesForState(Tables tables) {
        var sets = tables.statesForState();
        var states = IntStream.range(0, sets.size()).boxed().toList();
        return new CppDebugTables.StatesForState(tables.nfa(),
                states.stream().filter(i -> !sets.get(i).isEmpty())
                        .map(i -> stateSets(i, sets.get(i))).toList(),
                states.stream().map(i -> new CppDebugTables.StateSetsRef(i,
                        !sets.get(i).isEmpty())).toList(),
                states.stream().map(i -> new CppDebugTables.StateSetsLenRef(i,
                        !sets.get(i).isEmpty())).toList());
    }

    /** The sets of lexical state {@code index}, one per state. */
    private static CppDebugTables.StateSets stateSets(int index, List<List<Integer>> rows) {
        var members = IntStream.range(0, rows.size()).boxed().toList();
        return new CppDebugTables.StateSets(index,
                members.stream().map(j -> new CppDebugTables.StateSet(index, j,
                        joined(rows.get(j)))).toList(),
                members.stream().map(j -> new CppDebugTables.StateSetRef(index, j)).toList(),
                joined(rows.stream().map(List::size).toList()));
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
                kind, (images.get(kind) == null) ? "" : CppChars.of(images.get(kind)))).toList();
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

    @Override
    public String toHexString(long value) {
        return "0x" + Long.toHexString(value) + "ULL";
    }

    @Override
    public String tokenImage(String image) {
        return CppChars.of(image);
    }

    @Override
    public String stringLiteralImage(String image, String label, boolean isImage) {
        return CppChars.of(isImage ? image : "<" + label + ">");
    }

    @Override
    public String imageSeparator(int i, int last) {
        return "";
    }
}
