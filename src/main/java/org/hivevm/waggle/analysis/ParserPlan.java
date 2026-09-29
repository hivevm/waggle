// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/ParseEngine.java

package org.hivevm.waggle.analysis;

import org.hivevm.waggle.api.ParserRequest;
import org.hivevm.waggle.model.Action;
import org.hivevm.waggle.model.Choice;
import org.hivevm.waggle.model.Expansion;
import org.hivevm.waggle.model.Lookahead;
import org.hivevm.waggle.model.NonTerminal;
import org.hivevm.waggle.model.NormalProduction;
import org.hivevm.waggle.model.OneOrMore;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.Sequence;
import org.hivevm.waggle.model.ZeroOrMore;
import org.hivevm.waggle.model.ZeroOrOne;
import org.hivevm.waggle.api.Options;
import org.hivevm.waggle.api.ParserOptions;
import org.hivevm.waggle.tree.TreeModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

/**
 * The finished plan of a parser, which the back ends only write (ADR-0029): every production's
 * body as {@link PlanNode}s with its choice points decided ({@link Decision}), the token masks of
 * the one-token switches ({@link MaskTable}), the {@code jj_2} routines of the syntactic lookaheads
 * (phase 2) and the {@code jj_3} routines they call with their bodies (phase 3). {@link
 * ParserPlanner} builds it.
 *
 * <p>It was {@code ParserData}; ADR-0019 decided the name when it moved here.
 */
public class ParserPlan {

    private final ParserRequest request;
    private final PlanningProfile profile;

    private boolean lookaheadNeeded;

    private final List<int[]> maskVals;
    private MaskTable maskTable;
    private final Map<Expansion, Decision> decisions;

    /** The syntactic lookaheads, each of which gets a jj_2 routine, and those routines. */
    private final List<Lookahead> phase2list;
    private final List<Jj2Routine> jj2Routines;
    // LinkedHashMap (not Hashtable): iteration follows insertion order, so the jj_3 routines are
    // planned deterministically instead of in hash-bucket order (reproducible output).
    final LinkedHashMap<Expansion, Integer> phase3table = new LinkedHashMap<>();
    private final List<Jj3Routine> jj3Routines = new ArrayList<>();
    private final List<ProductionPlan> productionPlans = new ArrayList<>();

    private final Optional<TreeModel> tree;
    private final ParserOptions parserOptions;

    /**
     * How a lookahead routine scans each expansion it reaches, and the expansions
     * {@code minimumSize} is currently inside. Both used to be fields of the expansion itself, so
     * the model carried the scratch space of a walk over it (ADR-0019).
     */
    private final Map<Expansion, ScanCall> scanCalls = new IdentityHashMap<>();
    private final Set<Expansion> inMinimumSize =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Constructs an instance of {@link ParserPlan}.
     */
    ParserPlan(ParserRequest request, Optional<TreeModel> tree, PlanningProfile profile) {
        this.request = request;
        this.profile = profile;
        this.tree = tree;
        this.parserOptions = ParserOptions.from(request.options());

        this.lookaheadNeeded = false;
        this.maskVals = new ArrayList<>();
        this.phase2list = new ArrayList<>();
        this.jj2Routines = new ArrayList<>();
        this.decisions = new HashMap<>();
    }

    public final Options options() {
        return this.request.options();
    }

    public final String getParserName() {
        return this.request.getParserName();
    }

    /** Whether the grammar builds a tree. */
    public final boolean usesTree() {
        return this.tree.isPresent();
    }

    public final int getDepthLimit() {
        return this.parserOptions.depthLimit();
    }

    public final int getLookahead() {
        return this.parserOptions.lookahead();
    }

    public final boolean getCacheTokens() {
        return this.parserOptions.cacheTokens();
    }

    public final boolean getDebugParser() {
        return this.parserOptions.debugParser();
    }

    public final int getTokenCount() {
        return this.request.getTokenCount();
    }

    public final String getNameOfToken(int index) {
        return this.request.getNameOfToken(index);
    }

    public final Iterable<NormalProduction> getProductions() {
        return this.request.getNormalProductions();
    }

    /** How a lookahead routine scans {@code e}, or {@code null} before the planner decided it. */
    final ScanCall scanCall(Expansion e) {
        return this.scanCalls.get(e);
    }

    final void setScanCall(Expansion e, ScanCall call) {
        this.scanCalls.put(e, call);
    }

    /** The token masks of the switches, once planning is done. */
    public final MaskTable maskTable() {
        return this.maskTable;
    }

    /** Fixes what is derived from the whole plan; the planner calls it last. */
    final void finish() {
        this.maskTable = MaskTable.of(getTokenCount(), this.maskVals);
    }

    public final boolean isLookAheadNeeded() {
        return this.lookaheadNeeded;
    }

    final Iterable<Lookahead> getLookaheads() {
        return this.phase2list;
    }

    /** The jj_2 routines, in the order of their numbers. */
    public final List<Jj2Routine> jj2Routines() {
        return Collections.unmodifiableList(this.jj2Routines);
    }

    /** The jj_3 routines, in the order they were planned. */
    public final List<Jj3Routine> jj3Routines() {
        return Collections.unmodifiableList(this.jj3Routines);
    }

    final void addJj3Routine(Jj3Routine routine) {
        this.jj3Routines.add(routine);
    }

    /** The productions as the parser writes them, in grammar order. */
    public final List<ProductionPlan> productionPlans() {
        return Collections.unmodifiableList(this.productionPlans);
    }

    final void addProductionPlan(ProductionPlan plan) {
        this.productionPlans.add(plan);
    }

    final Decision getDecision(Expansion e) {
        return this.decisions.get(e);
    }

    public final boolean getDebugLookahead() {
        return this.parserOptions.debugLookahead();
    }

    public final boolean getErrorReporting() {
        return this.parserOptions.errorReporting();
    }

    /**
     * Whether the parser records the tokens it expected: ERROR_REPORTING asks for it, and the
     * target can. It decides whether a choice records its {@code jj_la1} slot and whether the
     * lookahead routines are run again, which their trace must stay silent for.
     */
    public final boolean recordsExpectedTokens() {
        return this.profile.recordsExpectedTokens() && getErrorReporting();
    }

    /** Registers the token mask of a switch and returns its jj_la1 slot. */
    final int addMask(int[] maskVal) {
        this.maskVals.add(maskVal);
        return this.maskVals.size() - 1;
    }

    final Jj2Routine addLookupAhead(Lookahead lookahead) {
        this.phase2list.add(lookahead);
        var routine = new Jj2Routine(this.jj2Routines.size() + 1);
        this.jj2Routines.add(routine);
        return routine;
    }

    final void setDecision(Expansion e, Decision plan) {
        this.decisions.put(e, plan);
    }

    protected final void setLookAheadNeeded(boolean lookaheadNeeded) {
        this.lookaheadNeeded = lookaheadNeeded;
    }

    /*
     * Returns the minimum number of tokens that can parse to this expansion.
     */
    final int minimumSize(Expansion e) {
        return minimumSize(e, Integer.MAX_VALUE);
    }

    /*
     * Returns the minimum number of tokens that can parse to this expansion.
     */
    private int minimumSize(Expansion e, int oldMin) {
        if (!this.inMinimumSize.add(e))
            // recursive search for minimum size unnecessary.
            return Integer.MAX_VALUE;
        try {
            return switch (e) {
                case RExpression regularExpression -> 1;
                case NonTerminal e_nrw -> minimumSize(e_nrw.getProd().getExpansion());
                case Choice e_nrw -> {
                    int min = oldMin;
                    Expansion nested_e;
                    for (int i = 0; (min > 1) && (i < e_nrw.getChoices().size()); i++) {
                        nested_e = (e_nrw.getChoices().get(i));
                        int min1 = minimumSize(nested_e, min);
                        if (min > min1) {
                            min = min1;
                        }
                    }
                    yield min;
                }
                case Sequence e_nrw -> {
                    int min = 0;
                    // We skip the first element in the following iteration since it is the
                    // Lookahead object.
                    for (int i = 1; i < e_nrw.getUnits().size(); i++) {
                        Expansion eseq = e_nrw.getUnits().get(i);
                        int mineseq = minimumSize(eseq);
                        if ((min == Integer.MAX_VALUE) || (mineseq == Integer.MAX_VALUE)) {
                            min = Integer.MAX_VALUE; // Adding infinity to something results in infinity.
                        } else {
                            min += mineseq;
                            if (min > oldMin) {
                                break;
                            }
                        }
                    }
                    yield min;
                }
                case OneOrMore e_nrw -> minimumSize(e_nrw.getExpansion(), Integer.MAX_VALUE);
                case ZeroOrMore zeroOrMore -> 0;
                case ZeroOrOne zeroOrOne -> 0;
                case Lookahead lookahead -> 0;
                case Action action -> 0;
                default -> 0;
            };
        } finally {
            this.inMinimumSize.remove(e);
        }
    }
}
