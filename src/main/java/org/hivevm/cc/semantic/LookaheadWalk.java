// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.semantic;

import java.util.ArrayList;
import java.util.List;

import org.hivevm.cc.model.Choice;
import org.hivevm.cc.model.Expansion;
import org.hivevm.cc.model.Lookahead;
import org.hivevm.cc.model.NonTerminal;
import org.hivevm.cc.model.NormalProduction;
import org.hivevm.cc.model.OneOrMore;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.Sequence;
import org.hivevm.cc.model.ZeroOrMore;
import org.hivevm.cc.model.ZeroOrOne;

abstract class LookaheadWalk {

    private LookaheadWalk() {
    }

    private static void listAppend(List<MatchInfo> vToAppendTo, List<MatchInfo> vToAppend) {
        vToAppendTo.addAll(vToAppend);
    }

    static List<MatchInfo> genFirstSet(Semanticize data, List<MatchInfo> partialMatches,
                                       Expansion exp) {
        if (exp instanceof RExpression re) {
            List<MatchInfo> retval = new ArrayList<>();
            for (MatchInfo m : partialMatches) {
                MatchInfo mnew = new MatchInfo(data.laLimit());
                if (m.firstFreeLoc >= 0)
                    System.arraycopy(m.match, 0, mnew.match, 0, m.firstFreeLoc);
                mnew.firstFreeLoc = m.firstFreeLoc;
                mnew.match[mnew.firstFreeLoc++] = re.getOrdinal();
                if (mnew.firstFreeLoc == data.laLimit())
                    data.getSizeLimitedMatches().add(mnew);
                else
                    retval.add(mnew);
            }
            return retval;
        }
        else if (exp instanceof NonTerminal) {
            NormalProduction prod = ((NonTerminal) exp).getProd();
            return LookaheadWalk.genFirstSet(data, partialMatches, prod.getExpansion());
        }
        else if (exp instanceof Choice ch) {
            List<MatchInfo> retval = new ArrayList<>();
            for (var element : ch.getChoices()) {
                List<MatchInfo> v = LookaheadWalk.genFirstSet(data, partialMatches, element);
                LookaheadWalk.listAppend(retval, v);
            }
            return retval;
        }
        else if (exp instanceof Sequence seq) {
            List<MatchInfo> v = partialMatches;
            for (var element : seq.getUnits()) {
                v = LookaheadWalk.genFirstSet(data, v, (Expansion) element);
                if (v.isEmpty())
                    break;
            }
            return v;
        }
        else if (exp instanceof OneOrMore om) {
            List<MatchInfo> retval = new ArrayList<>();
            List<MatchInfo> v = partialMatches;
            while (true) {
                v = LookaheadWalk.genFirstSet(data, v, om.getExpansion());
                if (v.isEmpty())
                    break;
                LookaheadWalk.listAppend(retval, v);
            }
            return retval;
        }
        else if (exp instanceof ZeroOrMore zm) {
            List<MatchInfo> retval = new ArrayList<>();
            LookaheadWalk.listAppend(retval, partialMatches);
            List<MatchInfo> v = partialMatches;
            while (true) {
                v = LookaheadWalk.genFirstSet(data, v, zm.getExpansion());
                if (v.isEmpty())
                    break;
                LookaheadWalk.listAppend(retval, v);
            }
            return retval;
        }
        else if (exp instanceof ZeroOrOne) {
            List<MatchInfo> retval = new ArrayList<>();
            LookaheadWalk.listAppend(retval, partialMatches);
            LookaheadWalk.listAppend(retval,
                    LookaheadWalk.genFirstSet(data, partialMatches, ((ZeroOrOne) exp).getExpansion()));
            return retval;
        }
        else if (data.considerSemanticLA() && (exp instanceof Lookahead lookahead)
                && (!lookahead.getActionTokens().isEmpty())) {
            return new ArrayList<>();
        }
        else {
            List<MatchInfo> retval = new ArrayList<>();
            LookaheadWalk.listAppend(retval, partialMatches);
            return retval;
        }
    }

    private static void listSplit(List<MatchInfo> toSplit, List<MatchInfo> mask,
                                  List<MatchInfo> partInMask,
                                  List<MatchInfo> rest) {
        OuterLoop:
        for (MatchInfo info : toSplit) {
            for (MatchInfo matchInfo : mask) {
                if (info == matchInfo) {
                    partInMask.add(info);
                    continue OuterLoop;
                }
            }
            rest.add(info);
        }
    }

    static List<MatchInfo> genFollowSet(List<MatchInfo> partialMatches, Expansion exp,
                                        long generation,
                                        Semanticize data) {
        if (exp.generation() == generation)
            return new ArrayList<>();

        // System.out.println("*** Parent: " + exp.parent);
        exp.setGeneration(generation);
        if (exp.parent() == null) {
            List<MatchInfo> retval = new ArrayList<>();
            LookaheadWalk.listAppend(retval, partialMatches);
            return retval;
        }
        else if (exp.parent() instanceof NormalProduction np) {
            List<Object> parents = np.getParents();
            List<MatchInfo> retval = new ArrayList<>();
            // System.out.println("1; gen: " + generation + "; exp: " + exp);
            for (Object parent : parents) {
                List<MatchInfo> v = LookaheadWalk.genFollowSet(partialMatches, (Expansion) parent,
                        generation, data);
                LookaheadWalk.listAppend(retval, v);
            }
            return retval;
        }
        else if (exp.parent() instanceof Sequence seq) {
            List<MatchInfo> v = partialMatches;
            for (int i = exp.parentOrdinal() + 1; i < seq.getUnits().size(); i++) {
                v = LookaheadWalk.genFirstSet(data, v, (Expansion) seq.getUnits().get(i));
                if (v.isEmpty())
                    return v;
            }
            List<MatchInfo> v1 = new ArrayList<>();
            List<MatchInfo> v2 = new ArrayList<>();
            LookaheadWalk.listSplit(v, partialMatches, v1, v2);
            if (!v1.isEmpty())
                // System.out.println("2; gen: " + generation + "; exp: " + exp);
                v1 = LookaheadWalk.genFollowSet(v1, seq, generation, data);
            if (!v2.isEmpty())
                // System.out.println("3; gen: " + generation + "; exp: " + exp);
                v2 = LookaheadWalk.genFollowSet(v2, seq, data.nextGenerationIndex(), data);
            LookaheadWalk.listAppend(v2, v1);
            return v2;
        }
        else if ((exp.parent() instanceof OneOrMore) || (exp.parent() instanceof ZeroOrMore)) {
            List<MatchInfo> moreMatches = new ArrayList<>();
            LookaheadWalk.listAppend(moreMatches, partialMatches);
            List<MatchInfo> v = partialMatches;
            while (true) {
                v = LookaheadWalk.genFirstSet(data, v, exp);
                if (v.isEmpty())
                    break;
                LookaheadWalk.listAppend(moreMatches, v);
            }
            List<MatchInfo> v1 = new ArrayList<>();
            List<MatchInfo> v2 = new ArrayList<>();
            LookaheadWalk.listSplit(moreMatches, partialMatches, v1, v2);
            if (!v1.isEmpty())
                // System.out.println("4; gen: " + generation + "; exp: " + exp);
                v1 = LookaheadWalk.genFollowSet(v1, (Expansion) exp.parent(), generation, data);
            if (!v2.isEmpty())
                // System.out.println("5; gen: " + generation + "; exp: " + exp);
                v2 = LookaheadWalk.genFollowSet(v2, (Expansion) exp.parent(), data.nextGenerationIndex(),
                        data);
            LookaheadWalk.listAppend(v2, v1);
            return v2;
        }
        else
            // System.out.println("6; gen: " + generation + "; exp: " + exp);
            return LookaheadWalk.genFollowSet(partialMatches, (Expansion) exp.parent(), generation, data);
    }
}
