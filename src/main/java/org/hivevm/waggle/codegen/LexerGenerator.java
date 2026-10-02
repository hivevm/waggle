// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// Copyright 2012 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/NfaState.java, org/javacc/parser/OtherFilesGenCPP.java

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.api.Options;
import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.grammar.Token;
import org.hivevm.waggle.lexer.LexerData;
import org.hivevm.waggle.lexer.LexerPlan;
import org.hivevm.waggle.lexer.LexerPlan.ByteMask;
import org.hivevm.waggle.lexer.LexerPlan.CanMove;
import org.hivevm.waggle.lexer.LexerPlan.Handoff;
import org.hivevm.waggle.lexer.LexerPlan.ImageSource;
import org.hivevm.waggle.lexer.LexerPlan.KindSet;
import org.hivevm.waggle.lexer.LexerPlan.KindTable;
import org.hivevm.waggle.lexer.LexerPlan.Tables;
import org.hivevm.source.TemplateSet;
import org.hivevm.waggle.model.CodeText;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * The {@link LexerGenerator} class.
 */
public abstract class LexerGenerator implements TargetSyntax {

    /** The lexical states, as the templates that write them read them (ADR-0031). */
    private static final String LEX_STATES = "LEX_STATES";

    private static final String LOHI_BYTES = "LOHI_BYTES";
    private static final String NON_ASCII_TABLE = "NON_ASCII_TABLE";

    private static final String HAS_LOOP = "HAS_LOOP";

    /**
     * Whether there is more than one lexical state: the token loop switches on it, and a trace
     * names it. Every lexer template reads it, the applied ones included.
     */
    private static final String SWITCH_ON_LEX_STATE = "SWITCH_ON_LEX_STATE";

    private static final String HAS_SPECIAL = "HAS_SPECIAL";

    private static final String HAS_EMPTY_MATCH = "HAS_EMPTY_MATCH";

    private static final String DEFAULT_LEX_STATE = "DEFAULT_LEX_STATE";
    private static final String MAX_LEX_STATES = "MAX_LEX_STATES";
    private static final String STATE_NAMES = "STATE_NAMES";
    private static final String STATE_SET_SIZE = "STATE_SET_SIZE";
    private static final String DUAL_NEED = "CHECK_NADD_STATES_DUAL_NEEDED";
    private static final String UNARY_NEED = "CHECK_NADD_STATES_UNARY_NEEDED";

    private final Language language;

    private StringLiteralDfaEmitter stringLiterals;
    private NfaMoveEmitter nfaMoves;
    private GetNextTokenEmitter getNextToken;

    protected LexerGenerator(Language language) {
        this.language = language;
    }

    protected NfaMoveEmitter newNfaMoveEmitter() {
        return new NfaMoveEmitter(this);
    }

    public final void generate(LexerData data) {
        this.stringLiterals = newStringLiteralDfaEmitter();
        this.nfaMoves = newNfaMoveEmitter();
        this.getNextToken = new GetNextTokenEmitter(this);

        var options = OptionsContext.of(data.options());
        LexerPlan plan = data.plan();
        options.add(LexerGenerator.LOHI_BYTES, plan.tables().byteMasks())
                .set("LOHI_BYTES_INDEX", ByteMask::index)
                .set("LOHI_BYTES_VALUE", this::getLohiBytes);
        // The jjCanMove functions are rendered from the plan's records (ADR-0031): they hold
        // numbers and flags only, which every target writes alike.
        options.add(LexerGenerator.NON_ASCII_TABLE, plan.canMoves())
                .set("NON_ASCII_TABLE_NAME", CanMove::method);

        var shape = plan.shape();
        options.set(LexerGenerator.HAS_LOOP, shape.hasLoop());
        options.set(LexerGenerator.SWITCH_ON_LEX_STATE, plan.tokenLoop().switchOnLexState());
        options.set(LexerGenerator.HAS_SPECIAL, shape.hasSpecial());

        options.set(LexerGenerator.HAS_EMPTY_MATCH, shape.hasEmptyMatch());

        options.set(LexerGenerator.DEFAULT_LEX_STATE, shape.defaultLexState());
        options.set(LexerGenerator.MAX_LEX_STATES, shape.lexStates());
        options.add(LexerGenerator.STATE_NAMES, shape.stateNames())
                .set(LexerGenerator.STATE_NAMES + "_VALUE", i -> i);
        options.set(LexerGenerator.STATE_SET_SIZE, shape.stateSetSize());
        options.set(LexerGenerator.STATE_SET_SIZE + "_2", shape.stateSetSize() * 2);
        options.set(LexerGenerator.DUAL_NEED, shape.dualNeed());
        options.set(LexerGenerator.UNARY_NEED, shape.unaryNeed());

        options.add("SKIP_ACTIONS", plan.actions().skip().stream()
                .map(c -> new ActionModel.SkipAction(c.kind(), c.lexState(), c.loopCheck(),
                        !c.code().isEmpty(), actionCode(c.code()), c.image() == ImageSource.LITERAL))
                .toList());
        options.add("MORE_ACTIONS", plan.actions().more().stream()
                .map(c -> new ActionModel.MoreAction(c.kind(), c.lexState(), c.loopCheck(),
                        !c.code().isEmpty(), actionCode(c.code()), c.image() == ImageSource.LITERAL))
                .toList());
        options.add("TOKEN_ACTIONS", plan.actions().token().stream()
                .map(c -> new ActionModel.TokenAction(c.kind(), c.lexState(), c.loopCheck(),
                        !c.code().isEmpty(), actionCode(c.code()), c.image() == ImageSource.RESET,
                        c.image() == ImageSource.LITERAL))
                .toList());

        options.set("STATES_FOR_STATE", () -> getStatesForState(plan.tables()));
        options.set("KIND_FOR_STATE", () -> getKindForState(plan.tables()));
        options.add("NEXT_STATES", List.of(nextStates(plan.tables())));
        options.add("GET_NEXT_TOKEN", List.of(this.getNextToken.model(plan)));
        options.add("LEX_STATE_TABLE", lexStateTable(plan.tables()));
        options.add("KIND_VECTORS", plan.tables().kindTables().stream()
                .map(this::kindVector).toList());
        options.add(LexerGenerator.LEX_STATES, plan.states().stream()
                .map(state -> lexicalState(new LexState(data.getParserName(), state)))
                .toList());

        generate(data, options);

        // Generate Constants
        options = OptionsContext.of(data.options());
        options.add("STATES", shape.lexStates())
                .set("STATES_INDEX", i -> i)
                .set("STATES_NAME", i -> identifier(shape.stateNames().get(i)));
        options.add("TOKENS", data.plan().namedTokens())
                .set("TOKENS_ORDINAL", LexerPlan.NamedToken::ordinal)
                .set("TOKENS_LABEL", e -> identifier(e.label()));

        var names = data.plan().tokenNames();
        options.add("REXPRESSION_COUNT", names.size())
                .set("REXPRESSION_INDEX", i -> i)
                .set("REXPRESSION_LABEL", i -> this.getNextToken.getRegExp(i, names, false))
                .set("REXPRESSION_IMAGE", i -> this.getNextToken.getRegExp(i, names, true));

        getConstantsTemplate().render(options, options.getParserName());
    }

    protected abstract TemplateSet.Source<Options> getConstantsTemplate();

    /** A name from the grammar as an identifier of the target: the name itself, unless overridden. */
    protected String identifier(String name) {
        return name;
    }

    protected abstract void generate(LexerData data, OptionsContext context);

    /** The emitters this back end composes; a target may supply its own (ADR-0017). */
    protected StringLiteralDfaEmitter newStringLiteralDfaEmitter() {
        return new StringLiteralDfaEmitter(this);
    }

    /**
     * The literal-image table as the lines it is written on: one entry per token kind, whatever
     * the target writes for a kind without a literal. A new line starts where a line would pass 80
     * columns, and a missing image counts as six.
     *
     * @param entry the text of the entry for a token kind and its image
     */
    protected static List<TableModel.Row> literalImageRows(List<String> images,
                                                          BiFunction<Integer, String, String> entry) {
        var rows = new ArrayList<TableModel.Row>();
        var line = new StringBuilder();
        int charCnt = 0;
        for (int kind = 0; kind < images.size(); kind++) {
            var image = images.get(kind);
            var text = entry.apply(kind, image);
            boolean wrap = (image == null) ? ((charCnt += 6) > 80)
                    : ((charCnt += text.length()) >= 80);
            if (wrap) {
                rows.add(new TableModel.Row(line.toString()));
                line.setLength(0);
                charCnt = 0;
            }
            line.append(text);
        }
        rows.add(new TableModel.Row(line.toString()));
        return List.copyOf(rows);
    }

    /**
     * The code of a lexical action, laid out from the first column as the grammar wrote it;
     * nothing when there is none.
     */
    private String actionCode(CodeText code) {
        if (code.isEmpty()) {
            return "";
        }
        var cursor = cursorAt(code.first());
        cursor.resetColumn();
        var text = new StringBuilder();
        for (Token token : code.tokens()) {
            text.append(cursor.text(token, null));
        }
        return text.toString();
    }

    /** A cursor at the start of {@code t}, for one run of verbatim tokens. */
    private TokenCursor cursorAt(Token t) {
        return TokenCursor.at(t, this.language);
    }

    /** The target's view of one lexical state: its string-literal DFA, and its NFA when it has one. */
    private NfaModel.LexicalState lexicalState(LexState state) {
        return new NfaModel.LexicalState(this.stringLiterals.stopDfa(state),
                this.stringLiterals.dfaCode(state),
                (state.plan().handoff() == Handoff.NONE) ? null : moveNfa(state));
    }

    /**
     * The NFA loop of {@code state}. The arms were decided, in output order, by the lexer stage
     * (ADR-0029); what is spelled here is the loop around them.
     */
    private NfaModel.MoveNfa moveNfa(LexState state) {
        var moves = state.plan().moves();
        return new NfaModel.MoveNfa(state.parserName(), state.suffix(),
                state.generatedStates(), state.mixed(), this.nfaMoves.section(moves.low(), 0),
                this.nfaMoves.section(moves.high(), 1), this.nfaMoves.section(moves.other(), -1));
    }

    /** The flattened NFA state sets the DFA jumps into, 16 per line. */
    private static TableModel.NextStates nextStates(Tables tables) {
        return new TableModel.NextStates(tables.nextStates().size(), TableModel.rows(
                tables.nextStates().stream().map(String::valueOf).toList(), 16));
    }

    /** The lexical state each kind switches to, 25 per line; none with one lexical state. */
    private static List<TableModel.LexStateTable> lexStateTable(Tables tables) {
        if (tables.newLexState().isEmpty()) {
            return List.of();
        }
        return List.of(new TableModel.LexStateTable(tables.newLexState().size(), TableModel.rows(
                tables.newLexState().stream().map(String::valueOf).toList(), 25)));
    }

    /** One {@code jjto…} bit vector, 64 token kinds per word, 4 words per line. */
    private TableModel.KindVector kindVector(KindTable table) {
        return new TableModel.KindVector(kindVectorName(table.set()), table.words().size(),
                TableModel.rows(table.words().stream().map(this::toHexString).toList(), 4));
    }

    /** The name of the table that holds a kind set. */
    protected String kindVectorName(KindSet set) {
        return switch (set) {
            case TOKEN -> "jjtoToken";
            case SKIP -> "jjtoSkip";
            case SPECIAL -> "jjtoSpecial";
            case MORE -> "jjtoMore";
        };
    }

    /** The four 64-bit words of a byte mask, as initializer text. */
    private String getLohiBytes(ByteMask mask) {
        return String.join(", ", mask.words().stream().map(this::toHexString).toList());
    }

    private String getStatesForState(Tables tables) {
        // A grammar made only of string literals has no NFA, hence no state table. The assert that
        // used to sit here claimed the opposite and blew up as soon as assertions were on.
        if (!tables.nfa()) {
            return noStateSet();
        }

        StringBuilder builder = new StringBuilder();
        for (var sets : tables.statesForState()) {
            builder.append(rowOpen());
            for (List<Integer> stateSet : sets) {
                builder.append(rowOpen()).append(" ");
                for (int element : stateSet) {
                    builder.append(element).append(",");
                }
                builder.append(rowClose()).append(",");
            }
            builder.append(rowClose()).append(",");
        }
        return stateSet(builder);
    }

    private String getKindForState(Tables tables) {
        if (!tables.nfa()) {
            return noStateSet();
        }

        StringBuilder builder = new StringBuilder();
        boolean moreThanOne = false;
        for (var kinds : tables.kindsForState()) {
            if (moreThanOne) {
                builder.append(",");
            }
            moreThanOne = true;
            if (kinds.isEmpty()) {
                builder.append(rowOpen()).append(rowClose());
            } else {
                builder.append(rowOpen()).append(" ");
                for (int element : kinds) {
                    builder.append(element);
                    builder.append(",");
                }
                builder.append(rowClose());
            }
        }
        return stateSet(builder);
    }

}
