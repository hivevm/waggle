// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/ParseEngine.java

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.analysis.Jj3Routine;
import org.hivevm.waggle.analysis.Decision;
import org.hivevm.waggle.analysis.MaskTable;
import org.hivevm.waggle.analysis.ParserPlan;
import org.hivevm.waggle.analysis.PlanNode;
import org.hivevm.waggle.analysis.ProductionPlan;
import org.hivevm.waggle.analysis.ProductionPlan.Signature;
import org.hivevm.waggle.analysis.ScanCall;
import org.hivevm.waggle.analysis.ScanStep;
import org.hivevm.waggle.analysis.TokenRef;

import org.hivevm.waggle.api.Encoding;
import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.api.Waggle;
import org.hivevm.waggle.grammar.Token;
import org.hivevm.waggle.model.CodeText;
import org.hivevm.waggle.model.NodeScope;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class ParserGenerator {

    private static final String LOOKAHEAD_NEEDED = "LOOKAHEAD_NEEDED";

    /** The lookahead routines, as the templates that write them read them (ADR-0031). */
    private static final String JJ3_ROUTINES = "JJ3_ROUTINES";

    /** The productions, as the templates that write them read them (ADR-0031). */
    private static final String PRODUCTIONS = "PRODUCTIONS";
    private static final String JJ2_INDEX = "JJ2_INDEX";
    private static final String JJ2_OFFSET = "JJ2_OFFSET";
    private static final String JJ2_ROUTINES = "JJ2_ROUTINES";
    private static final String RECORDS_EXPECTED_TOKENS = "RECORDS_EXPECTED_TOKENS";
    private static final String MASK_INDEX = "MASK_INDEX";
    private static final String TOKEN_COUNT = "TOKEN_COUNT";
    private static final String TOKEN_MASKS = "TOKEN_MASKS";

    private final Language language;

    private ParserSyntax syntax;

    private ExpansionDecorator decorator = ExpansionDecorator.NONE;

    /**
     * Constructs an instance of {@link ParserGenerator}.
     */
    protected ParserGenerator(Language language) {
        this.language = language;
    }

    /** What wraps a node scope, if anything does. Set once per generation. */
    final void decorateWith(ExpansionDecorator decorator) {
        this.decorator = decorator;
    }

    public final void generate(ParserPlan data) {
        this.syntax = newParserSyntax();

        var options = OptionsContext.of(data.options());

        options.set(Waggle.USE_AST, data.usesTree());
        options.set(ParserGenerator.LOOKAHEAD_NEEDED, data.isLookAheadNeeded());
        options.set(ParserGenerator.JJ2_INDEX, data.jj2Routines().size());
        var masks = data.maskTable();
        options.set(ParserGenerator.MASK_INDEX, masks.slots());
        options.set(ParserGenerator.TOKEN_COUNT, data.getTokenCount());

        options.set(ParserGenerator.JJ2_OFFSET, data.jj2Routines().stream()
                .map(r -> new ListModel.Jj2Case(r.saveSlot(), r.number())).toList());
        options.set(ParserGenerator.TOKEN_MASKS, IntStream.range(0, masks.wordCount())
                .mapToObj(i -> new ListModel.MaskWord(i, Arrays.stream(masks.words().get(i))
                        .mapToObj(v -> "0x" + Integer.toHexString(v))
                        .collect(Collectors.joining(", ")),
                        (i == 0) ? "" : MaskTable.firstToken(i) + " + "))
                .toList());

        options.set(ParserGenerator.PRODUCTIONS, data.productionPlans().stream()
                .map(n -> productionModel(n, data)).toList());
        options.set(ParserGenerator.RECORDS_EXPECTED_TOKENS, data.recordsExpectedTokens());
        options.set(ParserGenerator.JJ2_ROUTINES, data.jj2Routines().stream()
                .map(r -> new Jj3Model.Jj2(lookaheadRoutineName(r.name()), r.saveSlot()))
                .toList());
        options.set(ParserGenerator.JJ3_ROUTINES,
                data.jj3Routines().stream().map(r -> routineModel(data, r)).toList());

        generate(data, options);
    }

    protected abstract void generate(ParserPlan data, OptionsContext options);

    /** A cursor at the start of {@code t}, for one run of verbatim tokens. */
    protected final TokenCursor cursorAt(Token t) {
        return TokenCursor.at(t, this.language);
    }

    /**
     * Grammar-supplied tokens verbatim, with the comments around them; {@code $NODE} and
     * {@code $BOOL} in them refer to {@code scope}, when there is one. This sequence used to be
     * spelled out at every place that copies tokens into the parser.
     */
    private String code(CodeText code, NodeScope scope) {
        var cursor = cursorAt(code.first());
        var text = new StringBuilder();
        code.tokens().forEach(t -> text.append(cursor.text(t, scope)));
        return text.append(cursor.trailingComments(code.last())).toString();
    }

    /**
     * How this target spells a lookahead routine. The Java back end needs none of its own
     * (ADR-0021).
     */
    protected ParserSyntax newParserSyntax() {
        return ParserSyntax.JAVA;
    }


    /** How this target writes a routine name the analysis gave; Rust snake-cases it. */
    protected String lookaheadRoutineName(String name) {
        return name;
    }

    /** The call that scans: a jj_3 routine, or the token scan an expansion comes down to. */
    private String genjj_3Call(ScanCall call) {
        return this.syntax.callRef(switch (call) {
            case ScanCall.Token t -> this.syntax.scanTokenCall(t.token());
            case ScanCall.Routine r -> "jj_3" + lookaheadRoutineName(r.name()) + "()";
        });
    }

    /**
     * The target's view of one production: what writes its signature and its body, and the wrappers
     * the options ask for around that body, nested (ADR-0031).
     */
    private ProductionModel.Production productionModel(ProductionPlan plan, ParserPlan data) {
        var signature = plan.signature();
        var scope = signature.scope();

        ProductionModel.Wrapped inner = new ProductionModel.Body(
                List.of(nodeModel(data, plan.body())), signature.returnType() == null);
        if (plan.traced()) {
            inner = new ProductionModel.Traced(
                    Encoding.escapeUnicode(signature.name(), this.language), inner);
        }
        if (plan.depthGuarded()) {
            inner = new ProductionModel.DepthGuarded(data.getDepthLimit(), signature.name(), inner);
        }

        var open = (scope == null) ? null : this.decorator.open(scope, true);
        var close = (scope == null) ? null : this.decorator.close(scope);
        return new ProductionModel.Production(signature(signature), open != null,
                (open == null) ? "" : open.descriptor(), open, close, inner,
                signature.returnType() == null);
    }

    /**
     * How this target declares the production {@code p}: as the grammar writes it, with
     * {@code void} for a production without a result. Rust declares it its own way (ADR-0030).
     */
    protected ProductionModel.Signature signature(Signature p) {
        Token t = p.head().first();
        var comments = cursorAt(t);
        return new ProductionModel.Signature(comments.leadingComments(t),
                (p.returnType() == null) ? "void" : p.returnType(), comments.trailingComments(t),
                p.name(), p.parameters().isEmpty() ? "" : code(p.parameters(), null));
    }

    /** The target's view of one planned piece of a production's body. */
    private BodyModel.Node nodeModel(ParserPlan data, PlanNode node) {
        return switch (node) {
            case PlanNode.Consume consume -> new BodyModel.Consume(
                    assignment(consume.lhs(), consume.scope()),
                    this.syntax.tokenName(consume.token()),
                    (consume.rhsField() == null) ? "" : consume.rhsField(),
                    consume.lhs().isEmpty());
            case PlanNode.Call call -> new BodyModel.Call(
                    assignment(call.lhs(), call.scope()), this.syntax.productionName(call.name()),
                    verbatim(call.arguments(), call.scope()));
            case PlanNode.Code code -> new BodyModel.Code(verbatim(code.code(), code.scope()));
            case PlanNode.Seq seq -> new BodyModel.Seq(
                    seq.units().stream().map(u -> nodeModel(data, u)).toList());
            case PlanNode.Decide decide -> new BodyModel.Decide(
                    chainFrom(data, decide.scope(), decide.decision(), i -> {
                        if (i < decide.alternatives().size()) {
                            return nodeModel(data, decide.alternatives().get(i));
                        }
                        return decide.mustMatch()
                                ? new BodyModel.NoAlternative()
                                : new BodyModel.Nothing();
                    }, 0));
            case PlanNode.Repeat repeat -> repeatModel(data, repeat);
            case PlanNode.Scoped scoped -> scopedModel(data, scoped);
        };
    }

    /** A node scope around a piece of a body, when the target builds the tree. */
    private BodyModel.Node scopedModel(ParserPlan data, PlanNode.Scoped scoped) {
        var open = this.decorator.open(scoped.scope(), false);
        return new BodyModel.Scoped(open != null, open, List.of(nodeModel(data, scoped.body())),
                this.decorator.close(scoped.scope()));
    }

    private BodyModel.Node repeatModel(ParserPlan data, PlanNode.Repeat repeat) {
        var label = repeat.label();
        var body = List.of(nodeModel(data, repeat.body()));
        var chain = chainFrom(data, repeat.scope(), repeat.decision(),
                i -> new BodyModel.Break(label, i != 0), 0);
        return new BodyModel.Repeat(label, repeat.atLeastOnce() ? body : List.of(), chain,
                repeat.atLeastOnce() ? List.of() : body);
    }

    /** What the grammar assigns a result to, and the {@code =}; nothing when it assigns none. */
    private String assignment(CodeText lhs, NodeScope scope) {
        return lhs.isEmpty() ? "" : code(lhs, scope) + " = ";
    }

    /** A run of grammar code, or nothing when there is none. */
    private String verbatim(CodeText code, NodeScope scope) {
        return code.isEmpty() ? "" : code(code, scope);
    }

    /**
     * The target's view of one choice point: the planner's flat list of tests folded into the tree
     * the generated blocks nest as (ADR-0031). {@code actions} writes alternative {@code i}.
     */
    private BodyModel.Chain chainFrom(ParserPlan data, NodeScope scope, Decision decision,
                                       IntFunction<BodyModel.Node> actions, int index) {
        var steps = decision.steps();
        if (index >= steps.size()) {
            return fallbackModel(decision, actions);
        }

        var step = steps.get(index);
        var action = actions.apply(index);

        if (step instanceof Decision.Switch) {
            // The arms of one switch are the run of one-token tests that follows; what comes after
            // that run is written in the switch's default arm.
            var arms = new ArrayList<BodyModel.Arm>();
            int next = index;
            while ((next < steps.size()) && (steps.get(next) instanceof Decision.Switch s)) {
                var alternative = next;
                arms.add(arm(s.cases(), actions.apply(alternative)));
                next++;
            }
            var cached = data.getCacheTokens();
            var rest = chainFrom(data, scope, decision, actions, next);
            return (step.opening() == Decision.Opening.IF)
                    ? new BodyModel.ElseSwitch(cached, List.copyOf(arms), rest)
                    : new BodyModel.Switch(cached, List.copyOf(arms), rest);
        }

        var condition = conditionOf(step, scope);
        var rest = chainFrom(data, scope, decision, actions, index + 1);
        return switch (step.opening()) {
            case NOTHING -> new BodyModel.If(condition, action, rest);
            case IF -> new BodyModel.ElseIf(condition, action, rest);
            case SWITCH -> new BodyModel.DefaultIf(slot(step) >= 0, slot(step), condition,
                    action, rest);
        };
    }

    /** The arm that runs when no test held, in the shape the chain it closes asks for. */
    private BodyModel.Chain fallbackModel(Decision decision,
                                          IntFunction<BodyModel.Node> actions) {
        var fallback = decision.fallback();
        var action = actions.apply(decision.defaultAlternative());
        return switch (fallback.opening()) {
            case NOTHING -> new BodyModel.Plain(action);
            case IF -> new BodyModel.Else(action);
            case SWITCH -> new BodyModel.DefaultElse(fallback.slot() >= 0, fallback.slot(),
                    action);
        };
    }

    /** Where a choice is noted for the error message; negative where it is noted nowhere. */
    private static int slot(Decision.Step step) {
        return (step instanceof Decision.Semantic s) ? s.slot()
                : ((Decision.Syntactic) step).slot();
    }

    /** What a test checks. */
    private BodyModel.Test conditionOf(Decision.Step step, NodeScope scope) {
        if (step instanceof Decision.Semantic semantic) {
            return new BodyModel.SemanticTest(code(semantic.condition(), scope));
        }
        var syntactic = (Decision.Syntactic) step;
        return new BodyModel.LookaheadTest(lookaheadRoutineName(syntactic.routine().name()),
                this.syntax.lookaheadAmount(syntactic.amount()),
                syntactic.semantic().isEmpty() ? "" : code(syntactic.semantic(), scope));
    }

    /** The labels of a switch arm. */
    private BodyModel.Arm arm(List<TokenRef> cases, BodyModel.Node action) {
        var labels = new ArrayList<BodyModel.CaseLabel>();
        for (var token : cases) {
            labels.add(new BodyModel.CaseLabel(labels.isEmpty(), this.syntax.tokenName(token)));
        }
        return new BodyModel.Arm(!labels.isEmpty(), List.copyOf(labels), action);
    }


    /**
     * The target's view of one lookahead routine: the planner's steps with every call, token and
     * return already spelled, which is all a template needs (ADR-0031).
     */
    private Jj3Model.Routine routineModel(ParserPlan data, Jj3Routine routine) {
        var traced = routine.tracedProduction();
        return new Jj3Model.Routine(lookaheadRoutineName(routine.name()), traced != null,
                (traced == null) ? "" : Encoding.escapeUnicode(traced, this.language),
                data.recordsExpectedTokens(), new Jj3Model.Trace(), new Jj3Model.Failure(), new Jj3Model.Success(),
                routine.body().stream().map(this::stepModel).toList());
    }

    private Jj3Model.Step stepModel(ScanStep step) {
        return switch (step) {
            case ScanStep.DeclareScanPos d -> new Jj3Model.DeclareScanPos();
            case ScanStep.ScanToken t -> new Jj3Model.ScanToken(this.syntax.tokenRef(t.token()));
            case ScanStep.FailIfCall c -> new Jj3Model.FailIfCall(genjj_3Call(c.call()));
            case ScanStep.Choice c ->
                    new Jj3Model.Choice(c.saveScanPos(), alternativeModel(c.alternatives(), 0));
            case ScanStep.ScanLoop l -> new Jj3Model.ScanLoop(genjj_3Call(l.call()));
            case ScanStep.OptionalScan o -> new Jj3Model.OptionalScan(genjj_3Call(o.call()));
        };
    }

    /**
     * The alternatives from {@code index} on. Each one that is not the last holds the rest, so the
     * blocks the generated code nests are the records the templates nest.
     */
    private Jj3Model.Alternative alternativeModel(List<ScanStep.Alternative> alternatives,
                                                  int index) {
        var alternative = alternatives.get(index);
        var semantic = alternative.semantic();
        var guarded = !semantic.isEmpty();
        var writer = guarded ? code(semantic, null) : "";
        return (index == (alternatives.size() - 1))
                ? new Jj3Model.LastAlternative(genjj_3Call(alternative.call()), guarded, writer)
                : new Jj3Model.TryAlternative(genjj_3Call(alternative.call()), guarded, writer,
                        alternativeModel(alternatives, index + 1));
    }
}
