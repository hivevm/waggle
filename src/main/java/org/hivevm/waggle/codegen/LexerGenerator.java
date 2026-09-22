// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.api.Options;
import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.lexer.LexerData;
import org.hivevm.waggle.lexer.NfaState;
import org.hivevm.waggle.lexer.NfaStateData;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.grammar.Token;
import org.hivevm.source.LinePrinter;
import org.hivevm.source.SourceProvider;

import java.util.ArrayList;

/**
 * The {@link LexerGenerator} class.
 */
public abstract class LexerGenerator extends CodeGenerator<LexerData> implements TargetSyntax {

    protected static final String LOHI_BYTES = "LOHI_BYTES";
    protected static final String NON_ASCII_TABLE = "NON_ASCII_TABLE";

    private static final String HAS_LOOP = "HAS_LOOP";
    private static final String HAS_SKIP = "HAS_SKIP";
    private static final String HAS_MORE = "HAS_MORE";
    private static final String HAS_SPECIAL = "HAS_SPECIAL";

    private static final String HAS_MOPRE_ACTIONS = "HAS_MORE_ACTIONS";
    private static final String HAS_SKIP_ACTIONS = "HAS_SKIP_ACTIONS";
    private static final String HAS_TOKEN_ACTIONS = "HAS_TOKEN_ACTIONS";
    private static final String HAS_EMPTY_MATCH = "HAS_EMPTY_MATCH";

    private static final String DEFAULT_LEX_STATE = "DEFAULT_LEX_STATE";
    private static final String MAX_LEX_STATES = "MAX_LEX_STATES";
    private static final String STATE_NAMES = "STATE_NAMES";
    private static final String STATE_COUNT = "STATE_COUNT";
    private static final String STATE_SET_SIZE = "STATE_SET_SIZE";
    private static final String DUAL_NEED = "CHECK_NADD_STATES_DUAL_NEEDED";
    private static final String UNARY_NEED = "CHECK_NADD_STATES_UNARY_NEEDED";

    // jjStopAtPos is shared by all lexical states and must be emitted once per rendered file. It is
    // state of this rendering, so it lives here — it used to be a flag on the lexer model.
    private boolean stopAtPosDumped;

    protected LexerGenerator(Language language) {
        super(language);
    }

    // ---------------------------------------------------------------- dialect
    // How a target spells the moves into the next NFA state set. The defaults are the Java/C++ form;
    // Rust overrides them. These used to be "if (__IS_RUST__) … else …" inside the emitter itself,
    // four times over.

    // The pieces a condition is built from. Only the spelling differs per target, so they are the
    // dialect; the emitters below just compose them.

    // A switch over states. Java and C++ write each label out as it arrives and let the cases fall
    // into one body; Rust has to join them into a single match arm, so it collects them first. Both
    // shapes are driven from the same call sites through these three hooks.

    @Override
    public final void generate(LexerData data) {
        var options = OptionsContext.of(data.options());
        options.add(LexerGenerator.LOHI_BYTES, data.getLohiByte())
                .set("LOHI_BYTES_INDEX", i -> i)
                .set("LOHI_BYTES_VALUE", i -> getLohiBytes(data, i));
        options.add(LexerGenerator.NON_ASCII_TABLE, data.getNonAsciiTableForMethod())
                .set("NON_ASCII_TABLE_NAME", this::getNonAsciiMethod)
                .set("NON_ASCII_TABLE_METHOD", (s, w) -> dumpNonAsciiMoveMethod(data, s, w));

        options.set(LexerGenerator.HAS_SKIP, data.hasSkip());
        options.set(LexerGenerator.HAS_MORE, data.hasMore());
        options.set(LexerGenerator.HAS_LOOP, data.hasLoop());
        options.set(LexerGenerator.HAS_SPECIAL, data.hasSpecial());

        options.set(LexerGenerator.HAS_MOPRE_ACTIONS, data.hasMoreActions());
        options.set(LexerGenerator.HAS_SKIP_ACTIONS, data.hasSkipActions());
        options.set(LexerGenerator.HAS_TOKEN_ACTIONS, data.hasTokenActions());
        options.set(LexerGenerator.HAS_EMPTY_MATCH, data.hasEmptyMatch());

        options.set(LexerGenerator.DEFAULT_LEX_STATE, data.defaultLexState());
        options.add(LexerGenerator.MAX_LEX_STATES, data.maxLexStates())
                .set(LexerGenerator.MAX_LEX_STATES + "_INDEX", i -> i);
        options.add(LexerGenerator.STATE_NAMES, data.getStateNames())
                .set(LexerGenerator.STATE_NAMES + "_VALUE", i -> i);
        options.set(LexerGenerator.STATE_COUNT, data.getStateCount());
        options.set(LexerGenerator.STATE_SET_SIZE, data.stateSetSize());
        options.set(LexerGenerator.STATE_SET_SIZE + "_2", data.stateSetSize() * 2);
        options.set(LexerGenerator.DUAL_NEED, data.jjCheckNAddStatesDualNeeded());
        options.set(LexerGenerator.UNARY_NEED, data.jjCheckNAddStatesUnaryNeeded());

        options.set("DUMP_SKIP_ACTIONS", p -> getNextToken().dumpSkipActions(p, data));
        options.set("DUMP_MORE_ACTIONS", p -> getNextToken().dumpMoreActions(p, data));
        options.set("DUMP_TOKEN_ACTIONS", p -> getNextToken().dumpTokenActions(p, data));

        options.set("DUMP_STATE_SETS", p -> dumpStateSets(p, data));
        options.set("DUMP_GET_NEXT_TOKEN", p -> getNextToken().dumpGetNextToken(p, data));
        options.set("DUMP_STATIC_VAR_DECLARATIONS", p -> dumpStaticVarDeclarations(p, data));
        options.set("DUMP_NFA_AND_DFA", w ->
                data.getStateNames().forEach(name -> dump_nfa_and_dfa(data.getStateData(name), w))
        );

        generate(data, options);

        // Generate Constants
        options = OptionsContext.of(data.options());
        options.add("STATES", data.getStateCount())
                .set("STATES_INDEX", i -> i)
                .set("STATES_NAME", data::getStateName);
        options.add("TOKENS", data.getOrderedsTokens())
                .set("TOKENS_ORDINAL", RExpression::getOrdinal)
                .set("TOKENS_LABEL", RExpression::getLabel);

        var expressions = new ArrayList<RExpression>();
        for (var production : data.getTokenProductions()) {
            for (var spec : production.getRespecs()) {
                expressions.add(spec.rexp);
            }
        }
        options.add("REXPRESSION_COUNT", expressions.size() + 1)
                .set("REXPRESSION_INDEX", i -> i)
                .set("REXPRESSION_LABEL", (i, w) -> getNextToken().getRegExp(w, i, expressions, false))
                .set("REXPRESSION_IMAGE", (i, w) -> getNextToken().getRegExp(w, i, expressions, true));

        getConstantsTemplate().render(options, options.getParserName());
    }

    protected abstract SourceProvider<Options> getConstantsTemplate();

    protected abstract void generate(LexerData data, OptionsContext context);

    protected String getNonAsciiMethod(NfaState state) {
        return "" + state.nonAsciiMethod;
    }

    /**
     * Emits {@code jjCanMove_N}: whether a non-ASCII character is in the state's character set. The
     * set is stored as a two-level bit vector, indexed by the high and the low byte.
     */
    protected final void dumpNonAsciiMoveMethod(LexerData data, NfaState state, LinePrinter printer) {
        printCanMoveSignature(printer, data, state);

        for (int j = 0; j < state.loByteVec.size(); j += 2) {
            printCanMoveCase(printer, state.loByteVec.get(j));
            int vector = state.loByteVec.get(j + 1);
            if (data.hasAllBitsSet(vector)) {
                printCanMoveReturnTrue(printer);
            } else {
                printCanMoveReturnBitVector(printer, vector);
            }
            printCanMoveCaseEnd(printer);
        }

        printCanMoveDefault(printer);
        for (int j = state.nonAsciiMoveIndices.length; j > 0; j -= 2) {
            int hiVector = state.nonAsciiMoveIndices[j - 2];
            int loVector = state.nonAsciiMoveIndices[j - 1];
            printCanMoveArm(printer, hiVector, loVector,
                    !data.hasAllBitsSet(hiVector),
                    !data.hasAllBitsSet(loVector));
        }
        printCanMoveEnd(printer);
    }

    /** The emitters this back end composes; a target may supply its own (ADR-0017). */
    protected StringLiteralDfaEmitter newStringLiteralDfaEmitter() {
        return new StringLiteralDfaEmitter(this);
    }

    protected NfaMoveEmitter newNfaMoveEmitter() {
        return new NfaMoveEmitter(this);
    }

    protected GetNextTokenEmitter newGetNextTokenEmitter() {
        return new GetNextTokenEmitter(this, this);
    }

    private StringLiteralDfaEmitter stringLiterals;
    private NfaMoveEmitter nfaMoves;
    private GetNextTokenEmitter getNextToken;

    private StringLiteralDfaEmitter stringLiterals() {
        if (this.stringLiterals == null) {
            this.stringLiterals = newStringLiteralDfaEmitter();
        }
        return this.stringLiterals;
    }

    private NfaMoveEmitter nfaMoves() {
        if (this.nfaMoves == null) {
            this.nfaMoves = newNfaMoveEmitter();
        }
        return this.nfaMoves;
    }

    protected final GetNextTokenEmitter getNextToken() {
        if (this.getNextToken == null) {
            this.getNextToken = newGetNextTokenEmitter();
        }
        return this.getNextToken;
    }

    /** Whether {@code jjStopAtPos} has already been declared for this rendering. */
    protected final boolean isStopAtPosDumped() {
        return stringLiterals().isStopAtPosDumped();
    }

    /** Resets the once-only {@code jjStopAtPos} emission between two renderings. */
    protected final void setStopAtPosDumped(boolean dumped) {
        stringLiterals().setStopAtPosDumped(dumped);
    }

    /** The emitters print grammar actions verbatim, which needs the generator's token state. */
    final void printTokenPublic(Token token, LinePrinter printer) {
        printToken(token, printer);
    }

    final void setupTokenPublic(Token token) {
        setup_token(token);
    }

    final void resetColumnPublic() {
        reset_column();
    }

    protected final void dump_nfa_and_dfa(NfaStateData stateData, LinePrinter printer) {
        if (stateData.hasNFA() && !stateData.isMixedState())
            stringLiterals().dumpNfaStartStatesCode(printer, stateData, stateData.statesForPos);
        stringLiterals().dumpDfaCode(printer, stateData);
        if (stateData.hasNFA()) {
            // ADR-0012: no NFA-state preparation here — stage 4 (DfaBuilder.getMoveNfa, run from
            // LexerBuilder for every hasNFA state) already rearranged the states, populated
            // global.kinds / global.statesForState, and fixed the state sets on these very objects.
            nfaMoves().dumpMoveNfa(printer, stateData);
        }
    }

    /** The flattened NFA state sets the DFA jumps into, 16 per line. */
    protected final void dumpStateSets(LinePrinter printer, LexerData data) {
        printNextStatesOpen(printer, data);
        printer.indent();

        if (data.getOrderedStateSet().isEmpty()) {
            printEmptyStateSet(printer);
        } else {
            int cnt = 0;
            for (int[] set : data.getOrderedStateSet()) {
                for (int element : set) {
                    if ((cnt++ % 16) == 0) {
                        printer.println();
                    }
                    printer.print(element + ", ");
                }
            }
        }

        printer.println();
        printer.outdent();
        printArrayClose(printer);
    }

    /** The tables the lexer reads at run time: the lexical-state map and the four kind bit vectors. */
    protected final void dumpStaticVarDeclarations(LinePrinter printer, LexerData data) {
        if (data.maxLexStates() > 1) {
            printLexStateArrayOpen(printer, data);
            printer.indent();
            for (int i = 0; i < data.maxOrdinal(); i++) {
                if ((i % 25) == 0) {
                    printer.println();
                }
                String state = data.newLexState(i);
                printer.print((state == null ? -1 : data.getStateIndex(state)) + ", ");
            }
            printer.println();
            printer.outdent();
            printArrayClose(printer);
        }

        if (data.hasSkip() || data.hasMore() || data.hasSpecial()) {
            dumpBitVector(printer, data, "jjtoToken", data::toToken);
        }
        if (data.hasSkip() || data.hasSpecial()) {
            dumpBitVector(printer, data, "jjtoSkip", data::toSkip);
        }
        if (data.hasSpecial()) {
            dumpBitVector(printer, data, "jjtoSpecial", data::toSpecial);
        }
        if (data.hasMore()) {
            dumpBitVector(printer, data, "jjtoMore", data::toMore);
        }
    }

    // ////////////////////////// NFaState

    // ---------------------------------------------------------------- DFA prologue

    // ---------------------------------------------------------------- lexical actions
    // Names and shapes the three action dumpers need. Java is the default; Rust renames, C++ reads
    // through a "reader" and wraps the whole thing in a method of its own.

    protected final String getStatesForState(LexerData data) {
        // A grammar made only of string literals has no NFA, hence no state table. The assert that
        // used to sit here claimed the opposite and blew up as soon as assertions were on.
        if (data.getStatesForState() == null) {
            return noStateSet();
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < data.maxLexStates(); i++) {
            if (data.getStatesForState()[i] == null) {
                builder.append(rowOpen()).append(rowClose()).append(",");
                continue;
            }
            builder.append(rowOpen());
            for (int j = 0; j < data.getStatesForState()[i].length; j++) {
                int[] stateSet = data.getStatesForState()[i][j];
                if (stateSet == null) {
                    builder.append(rowOpen()).append(" ").append(j).append(" ").append(rowClose())
                            .append(",");
                    continue;
                }
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

}
