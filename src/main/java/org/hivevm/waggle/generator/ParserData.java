// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.generator;

import org.hivevm.waggle.ParserRequest;
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
import org.hivevm.waggle.parser.Options;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The parser model the back ends render: the productions, plus everything {@link ParserBuilder}
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

    private final NodeData nodeData;

    /**
     * Constructs an instance of {@link ParserData}.
     */
    ParserData(ParserRequest request) {
        this.request = request;

        this.jj2index = 0;
        this.lookaheadNeeded = false;
        this.maskVals = new ArrayList<>();
        this.phase2list = new ArrayList<>();
        this.lookaheadPlans = new HashMap<>();
        this.nodeData = new NodeData();
    }

    public final Options options() {
        return this.request.options();
    }

    public final String getParserName() {
        return this.request.getParserName();
    }

    /** Whether the grammar builds a tree, i.e. declares at least one node. */
    public final boolean usesTree() {
        return this.nodeData.usesTree();
    }

    public final int getDepthLimit() {
        return options().getDepthLimit();
    }

    public final int getLookahead() {
        return options().getLookahead();
    }

    public final boolean getCacheTokens() {
        return options().getCacheTokens();
    }

    public final boolean getDebugParser() {
        return options().getDebugParser();
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

    final LookaheadPlan getLookaheadPlan(Expansion e) {
        return this.lookaheadPlans.get(e);
    }

    public final int jj2Index() {
        return this.jj2index;
    }

    public final boolean getDebugLookahead() {
        return options().getDebugLookahead();
    }

    public final boolean getErrorReporting() {
        return options().getErrorReporting();
    }

    public final NodeData getNodeData() {
        return this.nodeData;
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
        if (e.inMinimumSize())
            // recursive search for minimum size unnecessary.
            return Integer.MAX_VALUE;

        e.setInMinimumSize(true);
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
            e.setInMinimumSize(false);
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
