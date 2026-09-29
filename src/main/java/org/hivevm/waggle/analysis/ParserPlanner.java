// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/ParseEngine.java

package org.hivevm.waggle.analysis;

import org.hivevm.waggle.api.ParserRequest;
import org.hivevm.waggle.api.GenerationException;
import org.hivevm.waggle.model.Action;
import org.hivevm.waggle.model.BNFProduction;
import org.hivevm.waggle.model.CodeText;
import org.hivevm.waggle.model.Choice;
import org.hivevm.waggle.model.Expansion;
import org.hivevm.waggle.model.Lookahead;
import org.hivevm.waggle.model.NodeScope;
import org.hivevm.waggle.model.NonTerminal;
import org.hivevm.waggle.model.NormalProduction;
import org.hivevm.waggle.model.Parameter;
import org.hivevm.waggle.model.OneOrMore;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.Sequence;
import org.hivevm.waggle.model.ZeroOrMore;
import org.hivevm.waggle.model.ZeroOrOne;

import org.hivevm.waggle.tree.TreeModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ParserPlanner {

    private int rIndex;

    /**
     * An expansion that needs a jj_3 routine, passed from phase 2 to phase 3.
     *
     * @param exp   the expansion to generate the jj_3 routine for
     * @param count the number of tokens that can still be consumed, which limits the number of jj_3
     *              routines generated
     */
    private record Phase3Data(Expansion exp, int count) {
    }

    /**
     * Constructs an instance of {@link ParserPlanner}.
     */
    public ParserPlanner() {
        this.rIndex = 0;
    }

    private int nextRIndex() {
        return ++this.rIndex;
    }

    public final ParserPlan build(ParserRequest request, Optional<TreeModel> tree,
                                  PlanningProfile profile) {
        if (request.diagnostics().hasError()) {
            throw new GenerationException("The grammar has " + request.diagnostics().errorCount()
                    + " error(s); no parser was planned.");
        }

        ParserPlan data = new ParserPlan(request, tree, profile);
        ParserPlanner.refuseWhatTheTargetCannotWrite(data, profile);
        for (NormalProduction p : data.getProductions()) {
            if (p instanceof BNFProduction) {
                buildPhase1(data, p.getExpansion());
            }
        }

        var labels = new int[1];
        for (NormalProduction p : data.getProductions()) {
            var signature = ParserPlanner.signature(p);
            data.addProductionPlan(new ProductionPlan(signature,
                    ParserPlanner.planNode(data, p.getExpansion(), p.getNodeScope(), labels),
                    data.getDebugParser(), data.guardsDepth(signature.returnType())));
        }

        List<Phase3Data> phase3list = new ArrayList<>();
        for (Lookahead la : data.getLookaheads()) {
            phase3list.add(new Phase3Data(la.getLaExpansion(), la.getAmount()));
            data.phase3table.put(la.getLaExpansion(), la.getAmount());
        }

        // setupPhase3Builds appends to the list it walks.
        for (int phase3index = 0; phase3index < phase3list.size(); phase3index++) {
            setupPhase3Builds(data, phase3list.get(phase3index), phase3list);
        }

        for (var e : data.phase3table.entrySet()) {
            // An expansion that comes down to a single token needs no routine of its own.
            if (data.scanCall(e.getKey()) instanceof ScanCall.Routine routine) {
                var traced = (data.getDebugLookahead()
                        && (e.getKey().parent() instanceof NormalProduction np)) ? np.getLhs()
                        : null;
                var jj3 = new Jj3Routine(routine.name(), e.getValue(), traced,
                        ParserPlanner.scanSteps(data, e.getKey(), e.getValue()));
                data.addJj3Routine(jj3);
                if (ParserPlanner.evaluatesSemanticLookahead(jj3)) {
                    data.setLookAheadNeeded(true);
                }
            }
        }

        data.finish();
        return data;
    }

    /**
     * Refuses the options a target cannot write, before anything is written (SPECIFICATION.md §3:
     * target feature gaps are tracked, not silently produced).
     */
    private static void refuseWhatTheTargetCannotWrite(ParserPlan data, PlanningProfile profile) {
        if (!profile.depthLimit() && (data.getDepthLimit() > 0)) {
            throw new GenerationException(
                    "DEPTH_LIMIT is not supported for the " + profile.target() + " target.");
        }
        if (!profile.parserTraces() && (data.getDebugParser() || data.getDebugLookahead())) {
            throw new GenerationException("DEBUG_PARSER and DEBUG_LOOKAHEAD are not supported for"
                    + " the " + profile.target() + " target.");
        }
    }

    private void buildPhase1(ParserPlan data, Expansion e) {
        switch (e) {
            case Choice choice -> {
                Lookahead[] conds = new Lookahead[choice.getChoices().size()];
                for (int i = 0; i < choice.getChoices().size(); i++) {
                    Sequence nestedSeq = (Sequence) choice.getChoices().get(i);
                    buildPhase1(data, nestedSeq);
                    conds[i] = (Lookahead) nestedSeq.getUnits().getFirst();
                }
                data.setDecision(e, buildLookahead(data, conds));
            }
            case Sequence sequence -> {
                // The first unit is the sequence's Lookahead.
                for (int i = 1; i < sequence.getUnits().size(); i++) {
                    buildPhase1(data, sequence.getUnits().get(i));
                }
            }
            // The order matters: it numbers the jj_2 routines and the jj_la1 slots. A "(…)*" tests
            // before its body is built, "(…)+" and "[…]" after.
            case ZeroOrMore loop -> {
                Decision plan = buildLookahead(data, loopCondition(data, loop.getExpansion()));
                data.setDecision(e, plan);
                buildPhase1(data, loop.getExpansion());
            }
            case OneOrMore loop -> {
                Lookahead[] conds = loopCondition(data, loop.getExpansion());
                buildPhase1(data, loop.getExpansion());
                data.setDecision(e, buildLookahead(data, conds));
            }
            case ZeroOrOne option -> {
                Lookahead[] conds = loopCondition(data, option.getExpansion());
                buildPhase1(data, option.getExpansion());
                data.setDecision(e, buildLookahead(data, conds));
            }
            default -> {
            }
        }
    }

    /** What a generator writes to declare {@code p}, taken from the grammar once. */
    private static ProductionPlan.Signature signature(NormalProduction p) {
        return new ProductionPlan.Signature(p.getLhs(), new CodeText(List.of(p.getFirstToken())),
                (p.getReturnTypeToken() == null) ? null : p.getReturnTypeToken().image,
                new CodeText(p.getParameterListTokens()),
                Parameter.split(p.getParameterListTokens()), p.getNodeScope());
    }

    /**
     * The plan of {@code e} and what it contains, in the order the parser is written: the loops are
     * labelled in that order. An alternative after the one a decision defaults to is never
     * reached, and is not planned.
     *
     * @param scope  the node scope the grammar code in {@code e} refers to, or {@code null}
     * @param labels the last loop label handed out
     */
    private static PlanNode planNode(ParserPlan data, Expansion e, NodeScope scope, int[] labels) {
        var own = e.getNodeScope();
        var inner = (own != null) ? own : scope;
        PlanNode node = switch (e) {
            case RExpression re -> new PlanNode.Consume(new CodeText(re.getLhsTokens()),
                    (re.getRhsToken() == null) ? null : re.getRhsToken().image,
                    new TokenRef(re.getLabel().isEmpty()
                            ? data.getNameOfToken(re.getOrdinal()) : re.getLabel(),
                            re.getOrdinal()), inner);
            case NonTerminal nt -> new PlanNode.Call(nt.getName(), new CodeText(nt.getLhsTokens()),
                    new CodeText(nt.getArgumentTokens()), inner);
            case Action action -> new PlanNode.Code(new CodeText(action.getActionTokens()), inner);
            case Choice choice -> {
                var decision = data.getDecision(e);
                var reached = Math.min(decision.defaultAlternative() + 1, choice.getChoices().size());
                var alternatives = new ArrayList<PlanNode>();
                for (int i = 0; i < reached; i++) {
                    alternatives.add(planNode(data, choice.getChoices().get(i), inner, labels));
                }
                yield new PlanNode.Decide(decision, List.copyOf(alternatives), true, inner);
            }
            case Sequence seq -> {
                var units = new ArrayList<PlanNode>();
                for (var unit : seq.getUnits()) {
                    var planned = planNode(data, unit, inner, labels);
                    if (planned != null) {
                        units.add(planned);
                    }
                }
                yield new PlanNode.Seq(List.copyOf(units));
            }
            case ZeroOrOne option -> new PlanNode.Decide(data.getDecision(e),
                    List.of(planNode(data, option.getExpansion(), inner, labels)), false, inner);
            case OneOrMore loop -> {
                var label = ++labels[0];
                yield new PlanNode.Repeat(label, true,
                        planNode(data, loop.getExpansion(), inner, labels), data.getDecision(e),
                        inner);
            }
            case ZeroOrMore loop -> {
                var label = ++labels[0];
                yield new PlanNode.Repeat(label, false,
                        planNode(data, loop.getExpansion(), inner, labels), data.getDecision(e),
                        inner);
            }
            // The leading Lookahead unit of a sequence runs as nothing.
            default -> null;
        };
        if (own == null) {
            return node;
        }
        return new PlanNode.Scoped(own, (node != null) ? node : new PlanNode.Seq(List.of()));
    }

    /** The single condition of a loop or an option: its body's own lookahead, or the default one. */
    private static Lookahead[] loopCondition(ParserPlan data, Expansion body) {
        if (body instanceof Sequence seq) {
            return new Lookahead[]{(Lookahead) seq.getUnits().getFirst()};
        }
        Lookahead la = new Lookahead();
        la.setAmount(data.getLookahead());
        la.setLaExpansion(body);
        return new Lookahead[]{la};
    }

    /**
     * Decides how the conditions of one choice point are tested, in order: consecutive one-token
     * lookaheads share a switch on the next token, anything else becomes an {@code if}. A
     * condition that trivially holds — no lookahead wanted, or one that matches the empty sequence,
     * and no semantic lookahead either — ends the chain; its alternative is the default.
     *
     * <p>Registers the token mask of every switch ({@code jj_la1}) and the {@code jj_2} routine of
     * every syntactic lookahead, and follows the shape the chain is in from step to step.
     */
    private static Decision buildLookahead(ParserPlan data, Lookahead[] conds) {
        var steps = new ArrayList<Decision.Step>();
        var opening = Decision.Opening.NOTHING;
        int openBlocks = 0;
        int[] tokenMask = null;
        boolean[] casedValues = null;

        for (Lookahead la : conds) {
            boolean[] firstSet = null;
            boolean semantic;

            if ((la.getAmount() == 0) || Semanticize.emptyExpansionExists(la.getLaExpansion())) {
                if (la.getActionTokens().isEmpty()) {
                    break; // trivially true: this alternative is the default
                }
                semantic = true;
            } else if ((la.getAmount() == 1) && la.getActionTokens().isEmpty()) {
                // One token decides — unless the FIRST set runs into a semantic lookahead.
                firstSet = new boolean[data.getTokenCount()];
                if (!ParserPlanner.genFirstSet(data, la.getLaExpansion(), firstSet, false)) {
                    if (opening != Decision.Opening.SWITCH) {
                        tokenMask = new int[MaskTable.wordCount(data.getTokenCount())];
                        casedValues = new boolean[data.getTokenCount()];
                        openBlocks++;
                    }
                    steps.add(new Decision.Switch(opening,
                            ParserPlanner.caseTokens(data, firstSet, casedValues, tokenMask)));
                    opening = Decision.Opening.SWITCH;
                    continue;
                }
                semantic = false;
            } else {
                semantic = false;
            }

            // An if: after a switch, it goes into the switch's default arm.
            int slot = (opening == Decision.Opening.SWITCH)
                    ? ParserPlanner.recorded(data, data.addMask(tokenMask)) : -1;
            openBlocks += switch (opening) {
                case NOTHING -> 1;
                case IF -> 0;
                case SWITCH -> 2;
            };
            if (semantic) {
                steps.add(new Decision.Semantic(opening, slot,
                        new CodeText(la.getActionTokens())));
            } else {
                // At this point, the lookahead expansion has no scan call yet.
                var routine = data.addLookupAhead(la);
                data.setScanCall(la.getLaExpansion(), new ScanCall.Routine(routine.name()));
                steps.add(new Decision.Syntactic(opening, slot, routine, la.getAmount(),
                        new CodeText(la.getActionTokens())));
            }
            opening = Decision.Opening.IF;
        }

        var inSwitch = opening == Decision.Opening.SWITCH;
        return new Decision(List.copyOf(steps), new Decision.Fallback(opening,
                inSwitch ? ParserPlanner.recorded(data, data.addMask(tokenMask)) : -1,
                inSwitch ? openBlocks + 1 : openBlocks));
    }

    /** The jj_la1 slot a choice records, or -1 when the parser records no expected tokens. */
    private static int recorded(ParserPlan data, int slot) {
        return data.recordsExpectedTokens() ? slot : -1;
    }

    /** The tokens of a FIRST set that no earlier case of the switch claimed. */
    private static List<TokenRef> caseTokens(ParserPlan data, boolean[] firstSet,
                                             boolean[] casedValues, int[] tokenMask) {
        var tokens = new ArrayList<TokenRef>();
        for (int i = 0; i < firstSet.length; i++) {
            if (firstSet[i] && !casedValues[i]) {
                casedValues[i] = true;
                tokenMask[i / 32] |= 1 << (i % 32);
                tokens.add(new TokenRef(data.getNameOfToken(i), i));
            }
        }
        return List.copyOf(tokens);
    }

    /**
     * Collects the FIRST set of an expansion into {@code firstSet}, which the caller resets.
     * Returns whether a semantic lookahead has to be evaluated for the next token, or
     * {@code jj2la} if none is found.
     */
    private static boolean genFirstSet(ParserPlan data, Expansion exp, boolean[] firstSet,
                                       boolean jj2la) {
        switch (exp) {
            case RExpression re -> firstSet[re.getOrdinal()] = true;
            case NonTerminal nt -> jj2la = genFirstSet(data, nt.getProd().getExpansion(), firstSet, jj2la);
            case Choice ch -> {
                for (Expansion element : ch.getChoices()) {
                    jj2la = genFirstSet(data, element, firstSet, jj2la);
                }
            }
            case Sequence seq -> {
                if ((seq.getUnits().getFirst() instanceof Lookahead la)
                        && !la.getActionTokens().isEmpty()) {
                    jj2la = true;
                }
                for (Expansion element : seq.getUnits()) {
                    // Javacode productions can not have FIRST sets. Instead we generate the FIRST set
                    // for the preceding LOOKAHEAD (the semantic checks should have made sure that
                    // the LOOKAHEAD is suitable).
                    jj2la = genFirstSet(data, element, firstSet, jj2la);
                    if (!Semanticize.emptyExpansionExists(element)) {
                        break;
                    }
                }
            }
            case OneOrMore om -> jj2la = genFirstSet(data, om.getExpansion(), firstSet, jj2la);
            case ZeroOrMore zm -> jj2la = genFirstSet(data, zm.getExpansion(), firstSet, jj2la);
            case ZeroOrOne zo -> jj2la = genFirstSet(data, zo.getExpansion(), firstSet, jj2la);
            default -> {
            }
        }
        return jj2la;
    }

    private void setupPhase3Builds(ParserPlan data, Phase3Data p3d, List<Phase3Data> phase3list) {
        switch (p3d.exp()) {
            case NonTerminal e_nrw -> generate3R(data, e_nrw.getProd().getExpansion(), p3d, phase3list);
            case Choice e_nrw -> {
                for (Expansion element : e_nrw.getChoices()) {
                    generate3R(data, element, p3d, phase3list);
                }
            }
            case Sequence e_nrw -> {
                // We skip the first element in the following iteration since it is the
                // Lookahead object.
                int cnt = p3d.count();
                for (int i = 1; i < e_nrw.getUnits().size(); i++) {
                    Expansion eseq = e_nrw.getUnits().get(i);
                    setupPhase3Builds(data, new Phase3Data(eseq, cnt), phase3list);
                    cnt -= data.minimumSize(eseq);
                    if (cnt <= 0) {
                        break;
                    }
                }
            }
            case OneOrMore e_nrw -> generate3R(data, e_nrw.getExpansion(), p3d, phase3list);
            case ZeroOrMore e_nrw -> generate3R(data, e_nrw.getExpansion(), p3d, phase3list);
            case ZeroOrOne e_nrw -> generate3R(data, e_nrw.getExpansion(), p3d, phase3list);
            default -> {
            }
        }
    }

    private void generate3R(ParserPlan data, Expansion e, Phase3Data inf,
                            List<Phase3Data> phase3list) {
        Expansion seq = e;
        if (data.scanCall(e) == null) {
            while (true) {
                if ((seq instanceof Sequence s) && s.getUnits().size() == 2) {
                    seq = s.getUnits().get(1);
                } else if (seq instanceof NonTerminal e_nrw) {
                    seq = e_nrw.getProd().getExpansion();
                } else {
                    break;
                }
            }

            if (seq instanceof RExpression re) {
                data.setScanCall(e, new ScanCall.Token(new TokenRef(
                        re.getLabel().isEmpty() ? null : re.getLabel(), re.getOrdinal())));
                return;
            }

            data.setScanCall(e, new ScanCall.Routine(
                    "R_" + ParserPlanner.getProductionName(e) + "_" + nextRIndex()));
        }

        Integer count = data.phase3table.get(e);
        if ((count == null) || (count < inf.count())) {
            phase3list.add(new Phase3Data(e, inf.count()));
            data.phase3table.put(e, inf.count());
        }
    }

    /**
     * The production {@code e} belongs to. The parents form a tree, so the walk ends; it used to
     * give up after 42 steps "in case there's a cycle", which named deeply nested routines R_null_N.
     */
    private static String getProductionName(Expansion e) {
        for (Expansion next = e; next != null; next = next.parent()) {
            if (next instanceof BNFProduction bnf)
                return bnf.getLhs();
        }
        return null;
    }

    /**
     * The body of the jj_3 routine for {@code e}: the walk the three back ends used to make while
     * printing, made once. A sequence stops at the unit after which {@code count} tokens are
     * certainly scanned; an expansion that comes down to a single token is scanned by its caller.
     */
    private static List<ScanStep> scanSteps(ParserPlan data, Expansion e, int count) {
        var body = new ArrayList<ScanStep>();
        ParserPlanner.scanSteps(data, e, count, body);
        return List.copyOf(body);
    }

    private static void scanSteps(ParserPlan data, Expansion e, int count, List<ScanStep> body) {
        if (data.scanCall(e) instanceof ScanCall.Token) {
            return;
        }

        switch (e) {
            case RExpression re -> {
                var name = re.getLabel().isEmpty()
                        ? data.getNameOfToken(re.getOrdinal())
                        : re.getLabel();
                body.add(new ScanStep.ScanToken(new TokenRef(name, re.getOrdinal())));
            }
            case NonTerminal nt -> body.add(new ScanStep.FailIfCall(
                    data.scanCall(nt.getProd().getExpansion())));
            case Choice choice -> {
                var size = choice.getChoices().size();
                if (size != 1) {
                    ParserPlanner.declareScanPos(body);
                }
                var alternatives = new ArrayList<ScanStep.Alternative>();
                for (int i = 0; i < size; i++) {
                    var seq = (Sequence) choice.getChoices().get(i);
                    var la = (Lookahead) seq.getUnits().getFirst();
                    alternatives.add(new ScanStep.Alternative(data.scanCall(seq),
                            new CodeText(la.getActionTokens()), i == (size - 1)));
                }
                body.add(new ScanStep.Choice(size != 1, List.copyOf(alternatives)));
            }
            case Sequence seq -> {
                // The first unit is the sequence's Lookahead.
                int cnt = count;
                for (int i = 1; i < seq.getUnits().size(); i++) {
                    var unit = seq.getUnits().get(i);
                    ParserPlanner.scanSteps(data, unit, cnt, body);
                    cnt -= data.minimumSize(unit);
                    if (cnt <= 0) {
                        break;
                    }
                }
            }
            case OneOrMore loop -> {
                ParserPlanner.declareScanPos(body);
                var call = data.scanCall(loop.getExpansion());
                body.add(new ScanStep.FailIfCall(call));
                body.add(new ScanStep.ScanLoop(call));
            }
            case ZeroOrMore loop -> {
                ParserPlanner.declareScanPos(body);
                body.add(new ScanStep.ScanLoop(data.scanCall(loop.getExpansion())));
            }
            case ZeroOrOne option -> {
                ParserPlanner.declareScanPos(body);
                body.add(new ScanStep.OptionalScan(data.scanCall(option.getExpansion())));
            }
            default -> {
            }
        }
    }

    /** Declares the scan position before its first use in a routine. */
    private static void declareScanPos(List<ScanStep> body) {
        if (!body.contains(new ScanStep.DeclareScanPos())) {
            body.add(new ScanStep.DeclareScanPos());
        }
    }

    /** Whether a routine evaluates a semantic lookahead, which needs {@code jj_lookingAhead}. */
    private static boolean evaluatesSemanticLookahead(Jj3Routine routine) {
        return routine.body().stream().anyMatch(step -> (step instanceof ScanStep.Choice choice)
                && choice.alternatives().stream().anyMatch(a -> !a.semantic().isEmpty()));
    }
}
