// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.analysis;

import org.hivevm.waggle.api.ParserRequest;
import org.hivevm.waggle.api.GenerationException;
import org.hivevm.waggle.analysis.ParserData.Phase3Data;
import org.hivevm.waggle.model.BNFProduction;
import org.hivevm.waggle.model.Choice;
import org.hivevm.waggle.model.Expansion;
import org.hivevm.waggle.model.Lookahead;
import org.hivevm.waggle.model.NonTerminal;
import org.hivevm.waggle.model.NormalProduction;
import org.hivevm.waggle.model.OneOrMore;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.RegularExpression;
import org.hivevm.waggle.model.Sequence;
import org.hivevm.waggle.model.ZeroOrMore;
import org.hivevm.waggle.model.ZeroOrOne;

import org.hivevm.waggle.tree.TreeModel;

import java.util.ArrayList;
import java.util.Optional;

public class ParserPlanner {

    private int rIndex;

    /**
     * Constructs an instance of {@link ParserPlanner}.
     */
    public ParserPlanner() {
        this.rIndex = 0;
    }

    private int nextRIndex() {
        return ++this.rIndex;
    }

    public final ParserData build(ParserRequest request, Optional<TreeModel> tree) {
        if (request.diagnostics().hasError()) {
            throw new GenerationException("The grammar has " + request.diagnostics().errorCount()
                    + " error(s); no parser was planned.");
        }

        ParserData data = new ParserData(request, tree);
        for (NormalProduction p : data.getProductions()) {
            if (p instanceof BNFProduction) {
                buildPhase1(data, p.getExpansion());
            }
        }

        for (Lookahead la : data.getLookaheads()) {
            data.addExpansion(la);
        }

        // setupPhase3Builds appends to the list it walks.
        for (int phase3index = 0; phase3index < data.phase3list.size(); phase3index++) {
            setupPhase3Builds(data, data.phase3list.get(phase3index));
        }

        for (var e : data.getExpansionCounts()) {
            buildPhase3Routine(data, e.getKey(), e.getValue());
        }

        return data;
    }

    private void buildPhase1(ParserData data, Expansion e) {
        switch (e) {
            case Choice choice -> {
                Lookahead[] conds = new Lookahead[choice.getChoices().size()];
                for (int i = 0; i < choice.getChoices().size(); i++) {
                    Sequence nestedSeq = (Sequence) choice.getChoices().get(i);
                    buildPhase1(data, nestedSeq);
                    conds[i] = (Lookahead) nestedSeq.getUnits().getFirst();
                }
                data.setLookaheadPlan(e, buildLookahead(data, conds));
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
                LookaheadPlan plan = buildLookahead(data, loopCondition(data, loop.getExpansion()));
                data.setLookaheadPlan(e, plan);
                buildPhase1(data, loop.getExpansion());
            }
            case OneOrMore loop -> {
                Lookahead[] conds = loopCondition(data, loop.getExpansion());
                buildPhase1(data, loop.getExpansion());
                data.setLookaheadPlan(e, buildLookahead(data, conds));
            }
            case ZeroOrOne option -> {
                Lookahead[] conds = loopCondition(data, option.getExpansion());
                buildPhase1(data, option.getExpansion());
                data.setLookaheadPlan(e, buildLookahead(data, conds));
            }
            default -> {
            }
        }
    }

    /** The single condition of a loop or an option: its body's own lookahead, or the default one. */
    private static Lookahead[] loopCondition(ParserData data, Expansion body) {
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
     * every syntactic lookahead.
     */
    private static LookaheadPlan buildLookahead(ParserData data, Lookahead[] conds) {
        var steps = new ArrayList<LookaheadPlan.Step>();
        var inSwitch = false;
        int[] tokenMask = null;
        boolean[] casedValues = null;

        for (Lookahead la : conds) {
            LookaheadPlan.Kind kind;
            boolean[] firstSet = null;

            if ((la.getAmount() == 0) || Semanticize.emptyExpansionExists(la.getLaExpansion())) {
                if (la.getActionTokens().isEmpty()) {
                    break; // trivially true: this alternative is the default
                }
                kind = LookaheadPlan.Kind.SEMANTIC;
            } else if ((la.getAmount() == 1) && la.getActionTokens().isEmpty()) {
                // One token decides — unless the FIRST set runs into a semantic lookahead.
                firstSet = new boolean[data.getTokenCount()];
                kind = ParserPlanner.genFirstSet(data, la.getLaExpansion(), firstSet, false)
                        ? LookaheadPlan.Kind.SYNTACTIC
                        : LookaheadPlan.Kind.SWITCH;
            } else {
                kind = LookaheadPlan.Kind.SYNTACTIC;
            }

            int[] tokens = null;
            int mask = -1;
            if (kind == LookaheadPlan.Kind.SWITCH) {
                if (!inSwitch) {
                    tokenMask = new int[((data.getTokenCount() - 1) / 32) + 1];
                    casedValues = new boolean[data.getTokenCount()];
                }
                tokens = ParserPlanner.caseTokens(firstSet, casedValues, tokenMask);
                inSwitch = true;
            } else {
                if (inSwitch) {
                    mask = data.addMask(tokenMask);
                }
                if (kind == LookaheadPlan.Kind.SYNTACTIC) {
                    // At this point, la.la_expansion.internal_name must be "".
                    data.setInternalName(la.getLaExpansion(), "_" + data.addLookupAhead(la));
                }
                inSwitch = false;
            }
            steps.add(new LookaheadPlan.Step(kind, la, tokens, mask));
        }

        return new LookaheadPlan(steps, inSwitch ? data.addMask(tokenMask) : -1);
    }

    /** The token kinds of a FIRST set that no earlier case of the switch claimed. */
    private static int[] caseTokens(boolean[] firstSet, boolean[] casedValues, int[] tokenMask) {
        var tokens = new ArrayList<Integer>();
        for (int i = 0; i < firstSet.length; i++) {
            if (firstSet[i] && !casedValues[i]) {
                casedValues[i] = true;
                tokenMask[i / 32] |= 1 << (i % 32);
                tokens.add(i);
            }
        }
        return tokens.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Collects the FIRST set of an expansion into {@code firstSet}, which the caller resets.
     * Returns whether a semantic lookahead has to be evaluated for the next token, or
     * {@code jj2la} if none is found.
     */
    private static boolean genFirstSet(ParserData data, Expansion exp, boolean[] firstSet,
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

    private void setupPhase3Builds(ParserData data, Phase3Data p3d) {
        Expansion e = p3d.exp();
        if (e instanceof RegularExpression) {
            // nothing to here
        } else if (e instanceof NonTerminal e_nrw) {
            // All expansions of non-terminals have the "name" fields set. So
            // there's no need to check it below for "e_nrw" and "ntexp". In
            // fact, we rely here on the fact that the "name" fields of both these
            // variables are the same.
            NormalProduction ntprod = data.getProduction(e_nrw.getName());
            generate3R(data, ntprod.getExpansion(), p3d);
        } else if (e instanceof Choice e_nrw) {
            for (Expansion element : e_nrw.getChoices()) {
                generate3R(data, element, p3d);
            }
        } else if (e instanceof Sequence e_nrw) {
            // We skip the first element in the following iteration since it is the
            // Lookahead object.
            int cnt = p3d.count();
            for (int i = 1; i < e_nrw.getUnits().size(); i++) {
                Expansion eseq = e_nrw.getUnits().get(i);
                setupPhase3Builds(data, new Phase3Data(eseq, cnt));
                cnt -= data.minimumSize(eseq);
                if (cnt <= 0) {
                    break;
                }
            }
        } else if (e instanceof OneOrMore e_nrw) {
            generate3R(data, e_nrw.getExpansion(), p3d);
        } else if (e instanceof ZeroOrMore e_nrw) {
            generate3R(data, e_nrw.getExpansion(), p3d);
        } else if (e instanceof ZeroOrOne e_nrw) {
            generate3R(data, e_nrw.getExpansion(), p3d);
        }
    }

    private void generate3R(ParserData data, Expansion e, Phase3Data inf) {
        Expansion seq = e;
        if (data.internalName(e).isEmpty()) {
            while (true) {
                if ((seq instanceof Sequence s) && s.getUnits().size() == 2) {
                    seq = s.getUnits().get(1);
                } else if (seq instanceof NonTerminal e_nrw) {
                    NormalProduction ntprod = data.getProduction(e_nrw.getName());
                    seq = ntprod.getExpansion();
                } else {
                    break;
                }
            }

            if (seq instanceof RExpression re) {
                data.setInternalName(e, "jj_scan_token("
                        + ((re.getLabel() == null) || re.getLabel().isEmpty() ? "" + re.getOrdinal()
                        : re.getLabel()) + ")");
                return;
            }

            data.setInternalName(e, "R_" + ParserPlanner.getProductionName(e) + "_" + nextRIndex());
        }

        Integer count = data.phase3table.get(e);
        if ((count == null) || (count < inf.count())) {
            data.phase3list.add(new Phase3Data(e, inf.count()));
            data.phase3table.put(e, inf.count());
        }
    }

    private static String getProductionName(Expansion e) {
        Object next = e;
        // Limit the number of iterations in case there's a cycle
        for (int i = 0; (i < 42) && (next != null); i++) {
            if (next instanceof BNFProduction bnf)
                return bnf.getLhs();
            else if (next instanceof Expansion exp)
                next = exp.parent();
            else
                return null;
        }
        return null;
    }

    private void buildPhase3Routine(ParserData data, Expansion e, int count) {
        if (data.internalName(e).startsWith("jj_scan_token")) {
            return;
        }

        if (e instanceof Choice e_nrw) {
            Sequence nested_seq;
            for (Expansion element : e_nrw.getChoices()) {
                nested_seq = (Sequence) (element);
                Lookahead la = (Lookahead) nested_seq.getUnits().getFirst();
                if (!la.getActionTokens().isEmpty()) {
                    // We have semantic lookahead that must be evaluated.
                    data.setLookAheadNeeded(true);
                }
            }
        } else if (e instanceof Sequence e_nrw) {
            // We skip the first element in the following iteration since it is the
            // Lookahead object.
            int cnt = count;
            for (int i = 1; i < e_nrw.getUnits().size(); i++) {
                var eseq = e_nrw.getUnits().get(i);
                buildPhase3Routine(data, eseq, cnt);
                cnt -= data.minimumSize(eseq);
                if (cnt <= 0) {
                    break;
                }
            }
        }
    }

}
