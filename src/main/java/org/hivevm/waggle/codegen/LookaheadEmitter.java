// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/ParseEngine.java

package org.hivevm.waggle.codegen;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.analysis.Decision;
import org.hivevm.waggle.model.CodeText;
import org.hivevm.waggle.model.NodeScope;

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
    public final void semantic(LinePrinter printer, Decision.Semantic step,
            Consumer<LinePrinter> action, NodeScope scope) {
        this.syntax.openSemanticCondition(printer, step.opening(), step.slot());
        writeActionTokens(printer, step.condition(), scope);
        printer.print(") {"); // closes the condition and opens its block in every target
        printer.indent();
        action.accept(printer);
    }

    /** A lookahead of one token: a switch over the next token's kind. */
    public final void oneToken(LinePrinter printer, Decision.Switch step,
            Consumer<LinePrinter> action, boolean cacheTokens) {
        this.syntax.openTokenSwitch(printer, step.opening(), cacheTokens);
        this.syntax.caseLabels(printer, step.cases().stream().map(this.syntax::tokenName).toList());
        action.accept(printer);
        this.syntax.closeSwitchArm(printer);
    }

    /** A lookahead of more than one token: a call to a jj_2 routine, plus any semantic condition. */
    public final void syntactic(LinePrinter printer, Decision.Syntactic step,
            Consumer<LinePrinter> action, NodeScope scope) {
        this.syntax.openLookaheadCondition(printer, step.opening(), step.slot());

        printer.print(this.syntax.lookaheadCall(
                this.parser.lookaheadRoutineName(step.routine().name()),
                this.syntax.lookaheadAmount(step.amount())));
        if (!step.semantic().isEmpty()) {
            // In addition, there is also a semantic lookahead. So concatenate
            // the semantic check with the syntactic one.
            printer.print(" && (");
            writeActionTokens(printer, step.semantic(), scope);
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
    public final void fallback(LinePrinter printer, Decision.Fallback fallback,
            Consumer<LinePrinter> action) {
        this.syntax.openFallback(printer, fallback.opening(), fallback.slot(), action);
        this.syntax.endBlocks(printer, fallback.closeBlocks());
    }

    private void writeActionTokens(LinePrinter printer, CodeText code, NodeScope scope) {
        this.parser.printTokens(code, scope, printer);
    }
}
