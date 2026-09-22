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
import org.hivevm.waggle.model.RegularExpression;
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
 * The parser model the back ends render: the productions, plus everything {@link ParserPlanner}
 * decides about lookahead — how every choice point is tested ({@link LookaheadPlan}), the token
 * masks of the one-token switches ({@code jj_la1}), the syntactic lookaheads that need a
 * {@code jj_2} routine (phase 2) and the {@code jj_3} routines they call, each with the largest
 * number of tokens it is ever asked to scan (phase 3).
 */
public class ParserData {

    private final ParserRequest request;

    private int jj2index;
    private boolean lookaheadNeeded;

    private final List<int[]> maskVals;
    private final Map<Expansion, LookaheadPlan> lookaheadPlans;

    /** The syntactic lookaheads, each of which gets a jj_2 routine. */
    private final List<Lookahead> phase2list;
    final List<Phase3Data> phase3list = new ArrayList<>();
    // LinkedHashMap (not Hashtable): iteration follows insertion order, so getExpansions() emits
    // phase-3 routines deterministically instead of in hash-bucket order (reproducible output).
    final LinkedHashMap<Expansion, Integer> phase3table = new LinkedHashMap<>();

    private final Optional<TreeModel> tree;
    private final ParserOptions parserOptions;

    /**
     * The name the parser generator invents for each lookahead routine, and the expansions
     * {@code minimumSize} is currently inside. Both used to be fields of the expansion itself, so
     * the model carried the scratch space of a walk over it (ADR-0019).
     */
    private final Map<Expansion, String> internalNames = new IdentityHashMap<>();
    private final Set<Expansion> inMinimumSize =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Constructs an instance of {@link ParserData}.
     */
    ParserData(ParserRequest request, Optional<TreeModel> tree) {
        this.request = request;
        this.tree = tree;
        this.parserOptions = ParserOptions.from(request.options());

        this.jj2index = 0;
        this.lookaheadNeeded = false;
        this.maskVals = new ArrayList<>();
        this.phase2list = new ArrayList<>();
        this.lookaheadPlans = new HashMap<>();
    }

    public final Options options() {
        return this.request.options();
    }

    /** The settings this plan was made under, as values (ADR-0019). */
    public final ParserOptions parserOptions() {
        return this.parserOptions;
    }

    public final String getParserName() {
        return this.request.getParserName();
    }

    /** Whether the grammar builds a tree. */
    public final boolean usesTree() {
        return this.tree.isPresent();
    }

    /** The tree this grammar builds, if it builds one. */
    public final Optional<TreeModel> treeModel() {
        return this.tree;
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

    public final NormalProduction getProduction(String name) {
        return this.request.getProductionTable(name);
    }

    /** The name of the lookahead routine for {@code e}, or the empty string before one is given. */
    public final String internalName(Expansion e) {
        return this.internalNames.getOrDefault(e, "");
    }

    final void setInternalName(Expansion e, String name) {
        this.internalNames.put(e, name);
    }

    public final int maskIndex() {
        return this.maskVals.size();
    }

    public final List<int[]> maskVals() {
        return this.maskVals;
    }

    public final boolean isLookAheadNeeded() {
        return this.lookaheadNeeded;
    }

    public final Iterable<Lookahead> getLookaheads() {
        return this.phase2list;
    }

    /** Phase-3 expansions paired with their lookahead count, in deterministic insertion order. */
    public final Iterable<Map.Entry<Expansion, Integer>> getExpansionCounts() {
        return this.phase3table.entrySet();
    }

    public final LookaheadPlan getLookaheadPlan(Expansion e) {
        return this.lookaheadPlans.get(e);
    }

    public final int jj2Index() {
        return this.jj2index;
    }

    public final boolean getDebugLookahead() {
        return this.parserOptions.debugLookahead();
    }

    public final boolean getErrorReporting() {
        return this.parserOptions.errorReporting();
    }

    /** Registers the token mask of a switch and returns its jj_la1 slot. */
    final int addMask(int[] maskVal) {
        this.maskVals.add(maskVal);
        return this.maskVals.size() - 1;
    }

    protected final int addLookupAhead(Lookahead lookahead) {
        this.phase2list.add(lookahead);
        return ++this.jj2index;
    }

    final void setLookaheadPlan(Expansion e, LookaheadPlan plan) {
        this.lookaheadPlans.put(e, plan);
    }

    protected final void addExpansion(Lookahead la) {
        Expansion e = la.getLaExpansion();
        Phase3Data p3d = new Phase3Data(e, la.getAmount());
        this.phase3list.add(p3d);
        this.phase3table.put(e, la.getAmount());
    }

    protected final void setLookAheadNeeded(boolean lookaheadNeeded) {
        this.lookaheadNeeded = lookaheadNeeded;
    }

    /*
     * Returns the minimum number of tokens that can parse to this expansion.
     */
    public final int minimumSize(Expansion e) {
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
                case RegularExpression regularExpression -> 1;
                case NonTerminal e_nrw -> {
                    NormalProduction ntprod = getProduction(e_nrw.getName());
                    Expansion ntexp = ntprod.getExpansion();
                    yield minimumSize(ntexp);
                }
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

    /**
     * This class stores information to pass from phase 2 to phase 3.
     *
     * @param exp   This is the expansion to generate the jj3 method for.
     * @param count This is the number of tokens that can still be consumed. This number is used to
     *              limit the number of jj3 methods generated.
     */
    record Phase3Data(Expansion exp, int count) {

    }
}
