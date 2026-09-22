// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.model.Lookahead;
import org.hivevm.waggle.model.NodeScope;

import java.util.List;
import java.util.function.Consumer;

/**
 * The chain a choice compiles to: one test per alternative, and the arm that runs when none
 * matched.
 *
 * <p>Its four routines were four methods on each of the three back ends — twelve in all — running
 * the same {@code switch} over the same three states in the same order. Java and C++ were
 * byte-identical in two of the four (ADR-0021). What differs is spelling, which comes from the
 * {@link ParserSyntax}.
 */
public class LookaheadEmitter {

    protected final ParserSyntax syntax;
    private final ParserGenerator parser;

    public LookaheadEmitter(ParserSyntax syntax, ParserGenerator parser) {
        this.syntax = syntax;
        this.parser = parser;
    }

    /** A lookahead of zero tokens: a semantic condition and nothing else. */
    public final void semantic(LinePrinter printer, ParserGenerator.LookaheadState state,
            Consumer<LinePrinter> action, Lookahead la, NodeScope scope, int index) {
        this.syntax.openSemanticCondition(printer, state, index);
        writeActionTokens(printer, la, scope);
        this.syntax.closeSemanticCondition(printer);
        printer.indent();
        action.accept(printer);
    }

    /** A lookahead of one token: a switch over the next token's kind. */
    public final void oneToken(LinePrinter printer, ParserGenerator.LookaheadState state,
            Consumer<LinePrinter> action, boolean cacheTokens, List<String> cases) {
        this.syntax.openTokenSwitch(printer, state, cacheTokens);
        this.syntax.caseLabels(printer, cases);
        action.accept(printer);
        this.syntax.closeSwitchArm(printer);
    }

    /** A lookahead of more than one token: a call to a jj_3 routine, plus any semantic condition. */
    public final void syntactic(LinePrinter printer, ParserGenerator.LookaheadState state,
            Consumer<LinePrinter> action, Lookahead la, NodeScope scope, int index) {
        this.syntax.openLookaheadCondition(printer, state, index);

        printer.print(this.syntax.lookaheadCall(
                this.parser.lookaheadRoutineName(la.getLaExpansion()),
                this.syntax.lookaheadAmount(la)));
        if (!la.getActionTokens().isEmpty()) {
            // In addition, there is also a semantic lookahead. So concatenate
            // the semantic check with the syntactic one.
            printer.print(" && (");
            writeActionTokens(printer, la, scope);
            printer.print(")");
        }
        this.syntax.closeLookaheadCondition(printer);
        printer.indent();
        action.accept(printer);
    }

    /**
     * The arm that runs when no alternative matched, and the blocks the chain leaves open.
     *
     * <p>It may not be the last entry of the chain: a condition can be statically known to be
     * always true.
     */
    public final void fallback(LinePrinter printer, ParserGenerator.LookaheadState state,
            Consumer<LinePrinter> action, int indents, int index) {
        this.syntax.openFallback(printer, state, index, action);
        this.syntax.endBlocks(printer, indents);
    }

    private void writeActionTokens(LinePrinter printer, Lookahead la, NodeScope scope) {
        this.parser.setup_token(la.getActionTokens().getFirst());
        la.getActionTokens().forEach(t -> this.parser.printToken(t, scope, printer));
        this.parser.printTrailingComments(printer, la.getActionTokens().getLast());
    }
}
