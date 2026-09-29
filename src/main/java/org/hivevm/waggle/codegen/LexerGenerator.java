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
import org.hivevm.waggle.lexer.LexerData;
import org.hivevm.waggle.lexer.LexerPlan;
import org.hivevm.waggle.lexer.LexerPlan.ByteMask;
import org.hivevm.waggle.lexer.LexerPlan.CanMove;
import org.hivevm.waggle.lexer.LexerPlan.Handoff;
import org.hivevm.waggle.lexer.LexerPlan.KindSet;
import org.hivevm.waggle.lexer.LexerPlan.KindTable;
import org.hivevm.waggle.lexer.LexerPlan.Tables;
import org.hivevm.source.LinePrinter;
import org.hivevm.source.TemplateSet;

import java.util.List;
import java.util.function.BiFunction;

/**
 * The {@link LexerGenerator} class.
 */
public abstract class LexerGenerator extends CodeGenerator<LexerData> implements TargetSyntax {

    protected static final String LOHI_BYTES = "LOHI_BYTES";
    protected static final String NON_ASCII_TABLE = "NON_ASCII_TABLE";

    private static final String HAS_LOOP = "HAS_LOOP";
    private static final String HAS_SPECIAL = "HAS_SPECIAL";

    private static final String HAS_EMPTY_MATCH = "HAS_EMPTY_MATCH";

    private static final String DEFAULT_LEX_STATE = "DEFAULT_LEX_STATE";
    private static final String MAX_LEX_STATES = "MAX_LEX_STATES";
    private static final String STATE_NAMES = "STATE_NAMES";
    private static final String STATE_COUNT = "STATE_COUNT";
    private static final String STATE_SET_SIZE = "STATE_SET_SIZE";
    private static final String DUAL_NEED = "CHECK_NADD_STATES_DUAL_NEEDED";
    private static final String UNARY_NEED = "CHECK_NADD_STATES_UNARY_NEEDED";

    private StringLiteralDfaEmitter stringLiterals;
    private NfaMoveEmitter nfaMoves;
    private GetNextTokenEmitter getNextToken;

    protected LexerGenerator(Language language) {
        super(language);
    }

    @Override
    public final void generate(LexerData data) {
        this.stringLiterals = newStringLiteralDfaEmitter();
        this.nfaMoves = newNfaMoveEmitter();
        this.getNextToken = newGetNextTokenEmitter();

        var options = OptionsContext.of(data.options());
        LexerPlan plan = data.plan();
        options.add(LexerGenerator.LOHI_BYTES, plan.tables().byteMasks())
                .set("LOHI_BYTES_INDEX", ByteMask::index)
                .set("LOHI_BYTES_VALUE", this::getLohiBytes);
        options.add(LexerGenerator.NON_ASCII_TABLE, plan.canMoves())
                .set("NON_ASCII_TABLE_NAME", this::getNonAsciiMethod)
                .set("NON_ASCII_TABLE_METHOD",
                        (m, w) -> dumpNonAsciiMoveMethod(data.getParserName(), m, w));

        var shape = plan.shape();
        options.set(LexerGenerator.HAS_LOOP, shape.hasLoop());
        options.set(LexerGenerator.HAS_SPECIAL, shape.hasSpecial());

        options.set(LexerGenerator.HAS_EMPTY_MATCH, shape.hasEmptyMatch());

        options.set(LexerGenerator.DEFAULT_LEX_STATE, shape.defaultLexState());
        options.set(LexerGenerator.MAX_LEX_STATES, shape.lexStates());
        options.add(LexerGenerator.STATE_NAMES, shape.stateNames())
                .set(LexerGenerator.STATE_NAMES + "_VALUE", i -> i);
        options.set(LexerGenerator.STATE_COUNT, shape.lexStates());
        options.set(LexerGenerator.STATE_SET_SIZE, shape.stateSetSize());
        options.set(LexerGenerator.STATE_SET_SIZE + "_2", shape.stateSetSize() * 2);
        options.set(LexerGenerator.DUAL_NEED, shape.dualNeed());
        options.set(LexerGenerator.UNARY_NEED, shape.unaryNeed());

        options.set("DUMP_SKIP_ACTIONS",
                p -> this.getNextToken.dumpSkipActions(p, data.getParserName(), plan.actions().skip()));
        options.set("DUMP_MORE_ACTIONS",
                p -> this.getNextToken.dumpMoreActions(p, data.getParserName(), plan.actions().more()));
        options.set("DUMP_TOKEN_ACTIONS",
                p -> this.getNextToken.dumpTokenActions(p, data.getParserName(), plan.actions().token()));

        options.set("STATES_FOR_STATE", () -> getStatesForState(plan.tables()));
        options.set("KIND_FOR_STATE", () -> getKindForState(plan.tables()));
        options.set("DUMP_STATE_SETS", p -> dumpStateSets(p, plan.tables()));
        options.set("DUMP_GET_NEXT_TOKEN", p -> this.getNextToken.dumpGetNextToken(p, plan));
        options.set("DUMP_STATIC_VAR_DECLARATIONS", p -> dumpStaticVarDeclarations(p, plan.tables()));
        options.set("DUMP_NFA_AND_DFA", w -> plan.states().forEach(
                state -> dump_nfa_and_dfa(new LexState(data.getParserName(), plan, state), w)));

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
                .set("REXPRESSION_LABEL", (i, w) -> this.getNextToken.getRegExp(w, i, names, false))
                .set("REXPRESSION_IMAGE", (i, w) -> this.getNextToken.getRegExp(w, i, names, true));

        getConstantsTemplate().render(options, options.getParserName());
    }

    protected abstract TemplateSet.Source<Options> getConstantsTemplate();

    /** A name from the grammar as an identifier of the target: the name itself, unless overridden. */
    protected String identifier(String name) {
        return name;
    }

    protected abstract void generate(LexerData data, OptionsContext context);

    protected String getNonAsciiMethod(CanMove canMove) {
        return "" + canMove.method();
    }

    /**
     * Emits {@code jjCanMove_N}: whether a non-ASCII character is in the state's character set. The
     * set is stored as a two-level bit vector, indexed by the high and the low byte.
     */
    protected final void dumpNonAsciiMoveMethod(String parserName, CanMove canMove,
                                                LinePrinter printer) {
        printCanMoveSignature(printer, parserName, canMove.method());

        for (var hiCase : canMove.cases()) {
            printCanMoveCase(printer, hiCase.hiByte());
            if (hiCase.any()) {
                printCanMoveReturnTrue(printer);
            } else {
                printCanMoveReturnBitVector(printer, hiCase.mask());
            }
            printCanMoveCaseEnd(printer);
        }

        printCanMoveDefault(printer);
        for (var arm : canMove.arms()) {
            printCanMoveArm(printer, arm.hiMask(), arm.loMask(), arm.testHi(), arm.testLo());
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

    /**
     * Prints the literal-image table: one entry per token kind, {@code null} for a kind without a
     * literal. A new line starts where a line would pass 80 columns, and a missing image counts as
     * six. Java and C++ each carried this loop, and the dead tail of JavaCC's version with it.
     *
     * @param linePerEntry whether every entry ends its own line, as the C++ declarations do
     * @param entry        the text of the entry for a token kind and its image
     */
    protected static void printLiteralImages(List<String> images, LinePrinter printer,
                                             boolean linePerEntry,
                                             BiFunction<Integer, String, String> entry) {
        int charCnt = 0;
        for (int kind = 0; kind < images.size(); kind++) {
            var image = images.get(kind);
            var text = entry.apply(kind, image);
            boolean wrap = (image == null) ? ((charCnt += 6) > 80)
                    : ((charCnt += text.length()) >= 80);
            if (wrap) {
                printer.println();
                charCnt = 0;
            }

            if (linePerEntry) {
                printer.println(text);
            } else {
                printer.print(text);
            }
        }
    }


    protected final void dump_nfa_and_dfa(LexState state, LinePrinter printer) {
        this.stringLiterals.dumpNfaStartStatesCode(printer, state);
        this.stringLiterals.dumpDfaCode(printer, state);
        if (state.plan().handoff() != Handoff.NONE) {
            // ADR-0029: the arms of the NFA were decided, in output order, by the lexer stage.
            this.nfaMoves.dumpMoveNfa(printer, state);
        }
    }

    /** The flattened NFA state sets the DFA jumps into, 16 per line. */
    protected final void dumpStateSets(LinePrinter printer, Tables tables) {
        printNextStatesOpen(printer, tables.nextStates().size());
        printer.indent();

        if (tables.nextStates().isEmpty()) {
            printEmptyStateSet(printer);
        } else {
            int cnt = 0;
            for (int element : tables.nextStates()) {
                if ((cnt++ % 16) == 0) {
                    printer.println();
                }
                printer.print(element + ", ");
            }
        }

        printer.println();
        printer.outdent();
        printArrayClose(printer);
    }

    /** The tables the lexer reads at run time: the lexical-state map and the four kind bit vectors. */
    protected final void dumpStaticVarDeclarations(LinePrinter printer, Tables tables) {
        if (!tables.newLexState().isEmpty()) {
            printLexStateArrayOpen(printer, tables.newLexState().size());
            printer.indent();
            for (int i = 0; i < tables.newLexState().size(); i++) {
                if ((i % 25) == 0) {
                    printer.println();
                }
                printer.print(tables.newLexState().get(i) + ", ");
            }
            printer.println();
            printer.outdent();
            printArrayClose(printer);
        }

        for (var table : tables.kindTables()) {
            dumpBitVector(printer, table);
        }
    }

    /** Emits one {@code jjto…} bit vector, 64 token kinds per element, 4 elements per line. */
    private void dumpBitVector(LinePrinter printer, KindTable table) {
        printBitVectorOpen(printer, kindTableName(table.set()), table.words().size());
        printer.indent();
        for (int i = 0; i < table.words().size(); i++) {
            if ((i % 4) == 0) {
                printer.println();
            }
            printer.print(toHexString(table.words().get(i)) + ", ");
        }
        printer.println();
        printer.outdent();
        printArrayClose(printer);
    }

    /** The name of the table that holds a kind set. */
    private static String kindTableName(KindSet set) {
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
            for (int j = 0; j < sets.size(); j++) {
                List<Integer> stateSet = sets.get(j);
                if (stateSet.isEmpty()) {
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
