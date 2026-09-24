// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/ParseEngine.java

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.analysis.LookaheadPlan;

import org.hivevm.waggle.analysis.ParserData;

import org.hivevm.waggle.api.Encoding;
import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.model.Action;
import org.hivevm.waggle.model.Choice;
import org.hivevm.waggle.model.Expansion;
import org.hivevm.waggle.model.Lookahead;
import org.hivevm.waggle.model.NodeScope;
import org.hivevm.waggle.model.NonTerminal;
import org.hivevm.waggle.model.NormalProduction;
import org.hivevm.waggle.model.OneOrMore;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.Sequence;
import org.hivevm.waggle.model.ZeroOrMore;
import org.hivevm.waggle.model.ZeroOrOne;
import org.hivevm.waggle.grammar.Token;
import org.hivevm.source.LinePrinter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class ParserGenerator extends CodeGenerator<ParserData> {

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

    /**
     * What the lookahead checker has opened so far: nothing, an if chain or a switch.
     *
     * <p>Public, not protected: it is part of the {@link ParserSyntax} a back end implements, and
     * the back ends live in their own packages.
     */
    public enum LookaheadState {
        NOOPENSTM,
        OPENIF,
        OPENSWITCH
    }

    private int labelIndex;
    private ExpansionDecorator decorator = ExpansionDecorator.NONE;

    /** The plan being rendered. One generator renders one plan, so it may be held. */
    private ParserData plan;

    /**
     * Constructs an instance of {@link ParserGenerator}.
     */
    protected ParserGenerator(Language language) {
        super(language);
        this.labelIndex = 0;
    }

    /** What wraps a node scope, if anything does. Set once per generation. */
    final void decorateWith(ExpansionDecorator decorator) {
        this.decorator = decorator;
    }

    @Override
    public final void generate(ParserData data) {
        this.plan = data;
        var options = OptionsContext.of(data.options());

        options.set(ParserGenerator.USE_AST, data.usesTree());
        options.set(ParserGenerator.LOOKAHEAD_NEEDED, data.isLookAheadNeeded());
        options.set(ParserGenerator.JJ2_INDEX, data.jj2Index());
        options.set(ParserGenerator.MASK_INDEX, data.maskIndex());
        options.set(ParserGenerator.TOKEN_COUNT, data.getTokenCount());

        options.add(ParserGenerator.JJ2_OFFSET, data.jj2Index())
                .set("JJ2_OFFSET_INDEX", i -> i)
                .set("JJ2_OFFSET_VALUE", i -> getStringIndex(i + 1));
        options.add(ParserGenerator.TOKEN_MASKS, ((data.getTokenCount() - 1) / 32) + 1)
                .set("TOKEN_MASKS_INDEX", this::getStringIndex)
                .set("TOKEN_MASKS_VALUE", i -> data.maskVals().stream().map(v ->
                        "0x" + Integer.toHexString(v[i])).collect(Collectors.joining(", ")));

        options.set("DUMP_NORMALPRODUCTIONS", w ->
                data.getProductions().forEach(n -> generatePhase1(n, w, data)));
        options.set("DUMP_LOOKAHEADS", w ->
                data.getLookaheads().forEach(e -> generate_phase2(e.getLaExpansion(), w, data)));
        options.set("DUMP_EXPANSIONS", w ->
                data.getExpansionCounts().forEach(e -> generate_phase3_routine(data, e.getKey(), e.getValue(), w)));

        generate(data, options);
    }

    protected abstract void generate(ParserData data, OptionsContext options);

    protected String getStringIndex(int i) {
        return "" + i;
    }

    protected final int nextLabelIndex() {
        return ++this.labelIndex;
    }

    /**
     * Prints grammar-supplied tokens verbatim, with the comments around them; {@code $NODE} and
     * {@code $BOOL} in them refer to {@code scope}, when there is one. This sequence used to be
     * spelled out at every place that copies tokens into the parser.
     */
    protected final void printTokens(List<Token> tokens, NodeScope scope, LinePrinter printer) {
        setup_token(tokens.getFirst());
        tokens.forEach(t -> printToken(t, scope, printer));
        printTrailingComments(printer, tokens.getLast());
    }

    protected final void printTrailingComments(LinePrinter printer, Token t) {
        if (t.next != null) {
            printLeadingComments(printer, t.next);
        }
    }

    /**
     * The value returned from a jj_3 lookahead routine. Shared by the Java and C++ back ends; the
     * Rust back end overrides it (snake-case production name, no {@code return ...;} wrapper).
     */
    protected String genReturn(Expansion expansion, boolean value, ParserData data) {
        String retval = Boolean.toString(value);
        if (data.getDebugLookahead() && (expansion != null)) {
            String tracecode =
                    "trace_return(\"" + Encoding.escapeUnicode(
                            ((NormalProduction) expansion.parent()).getLhs(), getLanguage())
                            + "(LOOKAHEAD " + (value ? "FAILED" : "SUCCEEDED") + ")\");";
            if (data.getErrorReporting()) {
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

    /** The spelling this target uses, created once. */
    protected final ParserSyntax syntax() {
        if (this.syntax == null) {
            this.syntax = newParserSyntax();
        }
        return this.syntax;
    }

    /** The lookahead-routine body, shared by every target. */
    protected final Phase3Emitter phase3() {
        if (this.phase3 == null) {
            this.phase3 = new Phase3Emitter(syntax(), this);
        }
        return this.phase3;
    }

    /** The lookahead chain, shared by every target. */
    protected final LookaheadEmitter lookahead() {
        if (this.lookahead == null) {
            this.lookahead = new LookaheadEmitter(syntax(), this);
        }
        return this.lookahead;
    }

    /** The name a lookahead call uses for the routine of {@code e}; Rust snake-cases it. */
    protected String lookaheadRoutineName(Expansion e) {
        return internalName(e);
    }

    /** The name the analysis gave the lookahead routine of {@code e} (ADR-0019). */
    protected final String internalName(Expansion e) {
        return this.plan.internalName(e);
    }

    /**
     * The call to a jj_3 routine (or a raw {@code jj_scan_token...}). Shared by the Java and C++ back
     * ends; the Rust back end overrides it to snake-case the internal name.
     */
    protected String genjj_3Call(Expansion e) {
        var name = internalName(e);
        return name.startsWith("jj_scan_token") ? name : "jj_3" + name + "()";
    }

    private void generatePhase1(NormalProduction p, LinePrinter printer, ParserData data) {
        var default_return = generate_phase1_head(p, printer, data);
        printer.indent();

        var node_scope = p.getNodeScope();
        if (node_scope != null) {
            this.decorator.beforeProduction(node_scope, printer);
        }

        generate_phase1_body(p, printer, data, default_return, w ->
                generate_phase1_expansion(data, p.getExpansion(), node_scope, printer)
        );

        if (node_scope != null) {
            this.decorator.after(node_scope, printer);
        }

        generate_phase1_tail(printer);
    }

    protected abstract String generate_phase1_head(NormalProduction p, LinePrinter printer, ParserData data);

    protected abstract void generate_phase1_body(NormalProduction p, LinePrinter printer, ParserData data, String returnType, Consumer<LinePrinter> consumer);

    protected void generate_phase1_tail(LinePrinter printer) {
        printer.println();
        printer.outdent();
        printer.println("}");
        printer.println();
    }

    protected final void generate_phase1_expansion(ParserData data, Expansion e, NodeScope ns, LinePrinter printer) {
        var node_scope = e.getNodeScope();
        if (node_scope != null) {
            this.decorator.beforeExpansion(node_scope, printer);
        }
        var scope = node_scope != null ? node_scope : ns;

        switch (e) {
            case RExpression re -> {
                printer.println();
                if (!re.getLhsTokens().isEmpty()) {
                    printTokens(re.getLhsTokens(), scope, printer);
                    printer.print(" = ");
                }

                syntax().consumeToken(printer);
                if (re.getLabel().isEmpty()) {
                    String label = data.getNameOfToken(re.getOrdinal());
                    printer.print(label != null ? label : "" + re.getOrdinal());
                } else {
                    printer.print(re.getLabel());
                }
                syntax().consumeTokenEnd(re, printer);
            }
            case NonTerminal e_nrw -> {
                printer.println();
                if (!e_nrw.getLhsTokens().isEmpty()) {
                    printTokens(e_nrw.getLhsTokens(), scope, printer);
                    printer.print(" = ");
                }
                syntax().callProduction(e_nrw, printer);
                if (!e_nrw.getArgumentTokens().isEmpty()) {
                    printTokens(e_nrw.getArgumentTokens(), scope, printer);
                }
                syntax().callProductionEnd(printer);
            }
            case Action e_nrw -> {
                printer.println();
                if (!e_nrw.getActionTokens().isEmpty()) {
                    printTokens(e_nrw.getActionTokens(), scope, printer);
                }
            }
            case Choice e_nrw -> {
                print_lookahead_checker(printer, data, scope, data.getLookaheadPlan(e), (p, i) -> {
                    if (i == e_nrw.getChoices().size()) {
                        syntax().noAlternativeMatched(printer);
                    } else {
                        generate_phase1_expansion(data, e_nrw.getChoices().get(i), scope, p);
                    }
                });
            }
            case Sequence e_nrw -> {
                // The leading Lookahead unit renders as nothing.
                e_nrw.getUnits().forEach(exp -> generate_phase1_expansion(data, exp, scope, printer));
            }
            case ZeroOrOne e_nrw -> {
                print_lookahead_checker(printer, data, scope, data.getLookaheadPlan(e), (p, i) -> {
                    if (i == 0) {
                        generate_phase1_expansion(data, e_nrw.getExpansion(), scope, p);
                    }
                });
            }
            case OneOrMore e_nrw -> {
                printer.println();
                int labelIndex = nextLabelIndex();
                syntax().openRepetition(labelIndex, printer);
                generate_phase1_expansion(data, e_nrw.getExpansion(), scope, printer);
                print_lookahead_checker(printer, data, scope, data.getLookaheadPlan(e),
                        (p, i) -> syntax().breakRepetition(labelIndex, p, i));
                printer.outdent();
                printer.println();
                printer.print("}");
                syntax().closeRepetition(labelIndex, printer);
            }
            case ZeroOrMore e_nrw -> {
                printer.println();
                int labelIndex = nextLabelIndex();
                syntax().openRepetition(labelIndex, printer);
                print_lookahead_checker(printer, data, scope, data.getLookaheadPlan(e),
                        (p, i) -> syntax().breakRepetition(labelIndex, p, i));
                generate_phase1_expansion(data, e_nrw.getExpansion(), scope, printer);
                printer.outdent();
                printer.println();
                printer.print("}");
                syntax().closeRepetition(labelIndex, printer);
            }
            default -> {
            }
        }

        if (node_scope != null) {
            this.decorator.after(node_scope, printer);
        }
    }

    /**
     * Renders how a choice point picks its alternative, as {@link ParserBuilder} planned it: an
     * {@code if}/{@code else if} chain, with runs of one-token lookaheads folded into a switch on the
     * next token, and the default alternative last. {@code actions} emits alternative {@code i}.
     */
    private void print_lookahead_checker(LinePrinter printer, ParserData data, NodeScope scope,
                                         LookaheadPlan plan,
                                         BiConsumer<LinePrinter, Integer> actions) {
        var state = LookaheadState.NOOPENSTM;
        int indentAmt = 0;

        for (int index = 0; index < plan.steps().size(); index++) {
            var step = plan.steps().get(index);
            var alternative = index;
            Consumer<LinePrinter> action = p -> actions.accept(p, alternative);

            switch (step.kind()) {
                case SEMANTIC -> {
                    indentAmt += ParserGenerator.openedBlocks(state);
                    lookahead().semantic(printer, state, action, step.la(), scope,
                            maskIndex(data, step.mask()));
                    state = LookaheadState.OPENIF;
                }
                case SWITCH -> {
                    if (state != LookaheadState.OPENSWITCH) {
                        indentAmt++;
                    }
                    var cases = new ArrayList<String>();
                    for (int kind : step.tokens()) {
                        String name = data.getNameOfToken(kind);
                        cases.add((name == null) ? "" + kind : name);
                    }
                    lookahead().oneToken(printer, state, action, data.getCacheTokens(), cases);
                    state = LookaheadState.OPENSWITCH;
                }
                case SYNTACTIC -> {
                    indentAmt += ParserGenerator.openedBlocks(state);
                    lookahead().syntactic(printer, state, action, step.la(), scope,
                            maskIndex(data, step.mask()));
                    state = LookaheadState.OPENIF;
                }
            }
        }

        lookahead().fallback(printer, state, p -> actions.accept(p, plan.defaultAlternative()),
                state == LookaheadState.OPENSWITCH ? indentAmt + 1 : indentAmt,
                maskIndex(data, plan.defaultMask()));
    }

    /** The blocks an {@code if} opens, depending on what it follows. */
    private static int openedBlocks(LookaheadState state) {
        return switch (state) {
            case NOOPENSTM -> 1;
            case OPENIF -> 0;
            case OPENSWITCH -> 2;
        };
    }

    /** The jj_la1 slot to record, or -1 when there is none or ERROR_REPORTING is off. */
    private static int maskIndex(ParserData data, int mask) {
        return data.getErrorReporting() ? mask : -1;
    }

    protected abstract void generate_phase2(Expansion e, LinePrinter printer, ParserData data);

    protected abstract void generate_phase3_routine(ParserData data, Expansion e, int count, LinePrinter printer);
}
