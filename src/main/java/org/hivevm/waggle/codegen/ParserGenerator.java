// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/ParseEngine.java

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.analysis.Jj2Routine;
import org.hivevm.waggle.analysis.Jj3Routine;
import org.hivevm.waggle.analysis.Decision;
import org.hivevm.waggle.analysis.MaskTable;
import org.hivevm.waggle.analysis.ParserPlan;
import org.hivevm.waggle.analysis.PlanNode;
import org.hivevm.waggle.analysis.ProductionPlan;
import org.hivevm.waggle.analysis.ProductionPlan.Signature;
import org.hivevm.waggle.analysis.ScanCall;

import org.hivevm.waggle.api.Encoding;
import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.model.CodeText;
import org.hivevm.waggle.model.NodeScope;
import org.hivevm.source.LinePrinter;

import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class ParserGenerator extends CodeGenerator<ParserPlan> {

    private ParserSyntax syntax;
    private Phase3Emitter phase3;
    private LookaheadEmitter lookahead;

    protected static final String LOOKAHEAD_NEEDED = "LOOKAHEAD_NEEDED";
    protected static final String JJ2_INDEX = "JJ2_INDEX";
    protected static final String JJ2_OFFSET = "JJ2_OFFSET";
    protected static final String MASK_INDEX = "MASK_INDEX";
    protected static final String TOKEN_COUNT = "TOKEN_COUNT";
    protected static final String TOKEN_MASKS = "TOKEN_MASKS";
    protected static final String USE_AST = "USE_AST";

    private ExpansionDecorator decorator = ExpansionDecorator.NONE;

    /**
     * Constructs an instance of {@link ParserGenerator}.
     */
    protected ParserGenerator(Language language) {
        super(language);
    }

    /** What wraps a node scope, if anything does. Set once per generation. */
    final void decorateWith(ExpansionDecorator decorator) {
        this.decorator = decorator;
    }

    @Override
    public final void generate(ParserPlan data) {
        validate(data);
        this.syntax = newParserSyntax();
        this.phase3 = new Phase3Emitter(this.syntax, this);
        this.lookahead = new LookaheadEmitter(this.syntax, this);

        var options = OptionsContext.of(data.options());

        options.set(ParserGenerator.USE_AST, data.usesTree());
        options.set(ParserGenerator.LOOKAHEAD_NEEDED, data.isLookAheadNeeded());
        options.set(ParserGenerator.JJ2_INDEX, data.jj2Routines().size());
        var masks = data.maskTable();
        options.set(ParserGenerator.MASK_INDEX, masks.slots());
        options.set(ParserGenerator.TOKEN_COUNT, data.getTokenCount());

        options.add(ParserGenerator.JJ2_OFFSET, data.jj2Routines())
                .set("JJ2_OFFSET_INDEX", Jj2Routine::saveSlot)
                .set("JJ2_OFFSET_VALUE", Jj2Routine::number);
        options.add(ParserGenerator.TOKEN_MASKS, masks.wordCount())
                .set("TOKEN_MASKS_INDEX", i -> i)
                .set("TOKEN_MASKS_VALUE", i -> Arrays.stream(masks.words().get(i))
                        .mapToObj(v -> "0x" + Integer.toHexString(v))
                        .collect(Collectors.joining(", ")));
        options.add(ParserGenerator.TOKEN_MASKS + "_LA1", masks.wordCount())
                .set("TOKEN_MASKS_LA1_INDEX", i -> i)
                .set("TOKEN_MASKS_LA1_VALUE", i -> (i == 0) ? "" : MaskTable.firstToken(i) + " + ");

        options.set("DUMP_NORMALPRODUCTIONS", w ->
                data.productionPlans().forEach(n -> generatePhase1(n, w, data)));
        options.set("DUMP_LOOKAHEADS", w ->
                data.jj2Routines().forEach(r -> generate_phase2(r, w, data)));
        options.set("DUMP_EXPANSIONS", w ->
                data.jj3Routines().forEach(r -> generate_phase3_routine(data, r, w)));

        generate(data, options);
    }

    protected abstract void generate(ParserPlan data, OptionsContext options);

    /**
     * Refuses a plan this target cannot write, before any of the parser is written; most targets
     * can write every plan.
     */
    protected void validate(ParserPlan data) {
    }

    /**
     * Prints grammar-supplied tokens verbatim, with the comments around them; {@code $NODE} and
     * {@code $BOOL} in them refer to {@code scope}, when there is one. This sequence used to be
     * spelled out at every place that copies tokens into the parser.
     */
    protected final void printTokens(CodeText code, NodeScope scope, LinePrinter printer) {
        var cursor = cursorAt(code.first());
        code.tokens().forEach(t -> cursor.print(t, scope, printer));
        cursor.trailingComments(printer, code.last());
    }

    /**
     * The value returned from a jj_3 lookahead routine. Shared by the Java and C++ back ends; the
     * Rust back end overrides it (snake-case production name, no {@code return ...;} wrapper).
     */
    protected String genReturn(String traced, boolean value, ParserPlan data) {
        String retval = Boolean.toString(value);
        if (data.getDebugLookahead() && (traced != null)) {
            String tracecode =
                    "trace_return(\"" + Encoding.escapeUnicode(traced, getLanguage())
                            + "(LOOKAHEAD " + (value ? "FAILED" : "SUCCEEDED") + ")\");";
            if (data.recordsExpectedTokens()) {
                tracecode = "if (!jj_rescan) " + tracecode;
            }
            return "{ " + tracecode + " return " + retval + "; }";
        } else {
            return "return " + retval + ";";
        }
    }

    /**
     * How this target spells a lookahead routine. The Java back end needs none of its own
     * (ADR-0021).
     */
    protected ParserSyntax newParserSyntax() {
        return ParserSyntax.JAVA;
    }

    /** The lookahead-routine body, shared by every target. */
    protected final Phase3Emitter phase3() {
        return this.phase3;
    }

    /** How this target writes a routine name the analysis gave; Rust snake-cases it. */
    protected String lookaheadRoutineName(String name) {
        return name;
    }

    /** The call that scans: a jj_3 routine, or the token scan an expansion comes down to. */
    protected final String genjj_3Call(ScanCall call) {
        return switch (call) {
            case ScanCall.Token t -> this.syntax.scanTokenCall(t.token());
            case ScanCall.Routine r -> "jj_3" + lookaheadRoutineName(r.name()) + "()";
        };
    }

    private void generatePhase1(ProductionPlan plan, LinePrinter printer, ParserPlan data) {
        var signature = plan.signature();
        generate_phase1_head(signature, printer, data);
        printer.indent();

        var node_scope = signature.scope();
        if (node_scope != null) {
            this.decorator.beforeProduction(node_scope, printer);
        }

        generate_phase1_body(signature, printer, data, w -> emit(data, plan.body(), printer));

        if (node_scope != null) {
            this.decorator.after(node_scope, printer);
        }

        generate_phase1_tail(signature, printer);
    }

    protected abstract void generate_phase1_head(Signature p, LinePrinter printer, ParserPlan data);

    protected abstract void generate_phase1_body(Signature p, LinePrinter printer, ParserPlan data, Consumer<LinePrinter> consumer);

    protected void generate_phase1_tail(Signature p, LinePrinter printer) {
        printer.println();
        printer.outdent();
        printer.println("}");
        printer.println();
    }

    /** Writes one planned piece of a production's body. */
    private void emit(ParserPlan data, PlanNode node, LinePrinter printer) {
        switch (node) {
            case PlanNode.Consume consume -> {
                printer.println();
                if (!consume.lhs().isEmpty()) {
                    printTokens(consume.lhs(), consume.scope(), printer);
                    printer.print(" = ");
                }
                this.syntax.consumeToken(printer);
                printer.print(this.syntax.tokenName(consume.token()));
                this.syntax.consumeTokenEnd(consume.lhs().isEmpty(), consume.rhsField(), printer);
            }
            case PlanNode.Call call -> {
                printer.println();
                if (!call.lhs().isEmpty()) {
                    printTokens(call.lhs(), call.scope(), printer);
                    printer.print(" = ");
                }
                this.syntax.callProduction(call.name(), printer);
                if (!call.arguments().isEmpty()) {
                    printTokens(call.arguments(), call.scope(), printer);
                }
                this.syntax.callProductionEnd(printer);
            }
            case PlanNode.Code code -> {
                printer.println();
                if (!code.code().isEmpty()) {
                    printTokens(code.code(), code.scope(), printer);
                }
            }
            case PlanNode.Seq seq -> seq.units().forEach(unit -> emit(data, unit, printer));
            case PlanNode.Decide decide ->
                    print_lookahead_checker(printer, data, decide.scope(), decide.decision(),
                            (p, i) -> {
                                if (i < decide.alternatives().size()) {
                                    emit(data, decide.alternatives().get(i), p);
                                } else if (decide.mustMatch()) {
                                    this.syntax.noAlternativeMatched(p);
                                }
                            });
            case PlanNode.Repeat repeat -> {
                var label = repeat.label();
                printer.println();
                this.syntax.openRepetition(label, printer);
                if (repeat.atLeastOnce()) {
                    emit(data, repeat.body(), printer);
                }
                print_lookahead_checker(printer, data, repeat.scope(), repeat.decision(),
                        (p, i) -> this.syntax.breakRepetition(label, p, i));
                if (!repeat.atLeastOnce()) {
                    emit(data, repeat.body(), printer);
                }
                printer.outdent();
                printer.println();
                printer.print("}");
                this.syntax.closeRepetition(label, printer);
            }
            case PlanNode.Scoped scoped -> {
                this.decorator.beforeExpansion(scoped.scope(), printer);
                emit(data, scoped.body(), printer);
                this.decorator.after(scoped.scope(), printer);
            }
        }
    }

    /**
     * Writes how a choice point picks its alternative, as the planner decided it: an
     * {@code if}/{@code else if} chain, with runs of one-token lookaheads folded into a switch on the
     * next token, and the default alternative last. {@code actions} emits alternative {@code i}.
     */
    private void print_lookahead_checker(LinePrinter printer, ParserPlan data, NodeScope scope,
                                         Decision decision,
                                         BiConsumer<LinePrinter, Integer> actions) {
        for (int index = 0; index < decision.steps().size(); index++) {
            var alternative = index;
            Consumer<LinePrinter> action = p -> actions.accept(p, alternative);

            switch (decision.steps().get(index)) {
                case Decision.Semantic step -> this.lookahead.semantic(printer, step, action, scope);
                case Decision.Switch step ->
                        this.lookahead.oneToken(printer, step, action, data.getCacheTokens());
                case Decision.Syntactic step ->
                        this.lookahead.syntactic(printer, step, action, scope);
            }
        }

        this.lookahead.fallback(printer, decision.fallback(),
                p -> actions.accept(p, decision.defaultAlternative()));
    }

    /**
     * Opens the DEBUG_LOOKAHEAD trace of a jj_3 routine: prints its "LOOKING AHEAD..." call when the
     * routine checks a production's own expansion. Returns the expansion the routine's returns
     * trace, or null when it traces nothing.
     */
    protected final String traceLookingAhead(ParserPlan data, Jj3Routine routine, String indent,
                                             LinePrinter printer) {
        var traced = routine.tracedProduction();
        if (!data.getDebugLookahead() || (traced == null)) {
            return null;
        }
        printer.println(indent + (data.recordsExpectedTokens() ? "if (!jj_rescan) " : "") + "trace_call(\""
                + Encoding.escapeUnicode(traced, getLanguage()) + "(LOOKING AHEAD...)\");");
        return traced;
    }

    protected abstract void generate_phase2(Jj2Routine routine, LinePrinter printer, ParserPlan data);

    protected abstract void generate_phase3_routine(ParserPlan data, Jj3Routine routine, LinePrinter printer);
}
