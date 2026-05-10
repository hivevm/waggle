// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.semantic;

import org.hivevm.cc.Encoding;
import org.hivevm.cc.model.Choice;
import org.hivevm.cc.model.Expansion;
import org.hivevm.cc.model.Lookahead;
import org.hivevm.cc.model.NonTerminal;
import org.hivevm.cc.model.NormalProduction;
import org.hivevm.cc.model.OneOrMore;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.RStringLiteral;
import org.hivevm.cc.model.Sequence;
import org.hivevm.cc.model.ZeroOrMore;
import org.hivevm.cc.model.ZeroOrOne;

import java.util.ArrayList;
import java.util.List;

class LookaheadCalc {

    private static MatchInfo overlap(List<MatchInfo> v1, List<MatchInfo> v2) {
        MatchInfo m1, m2, m3;
        int size;
        boolean diff;
        for (MatchInfo element : v1) {
            m1 = element;
            for (MatchInfo element2 : v2) {
                m2 = element2;
                size = m1.firstFreeLoc();
                m3 = m1;
                if (size > m2.firstFreeLoc()) {
                    size = m2.firstFreeLoc();
                    m3 = m2;
                }
                if (size == 0)
                    return null;
                // we wish to ignore empty expansions and the JAVACODE stuff here.
                diff = false;
                for (int k = 0; k < size; k++) {
                    if (m1.match()[k] != m2.match()[k]) {
                        diff = true;
                        break;
                    }
                }
                if (!diff)
                    return m3;
            }
        }
        return null;
    }

    private static boolean javaCodeCheck(List<MatchInfo> v) {
        for (MatchInfo element : v) {
            if ((element).firstFreeLoc() == 0)
                return true;
        }
        return false;
    }

    private static String image(MatchInfo m, Semanticize semanticize) {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < m.firstFreeLoc(); i++) {
            if (m.match()[i] == 0)
                ret.append(" <EOF>");
            else {
                RExpression re = semanticize.getRegularExpression(m.match()[i]);
                if (re instanceof RStringLiteral)
                    ret.append(" \"").append(Encoding.escape(((RStringLiteral) re).getImage()))
                            .append("\"");
                else if ((re.getLabel() != null) && !re.getLabel().isEmpty())
                    ret.append(" <").append(re.getLabel()).append(">");
                else
                    ret.append(" <token of kind ").append(i).append(">");
            }
        }
        return (m.firstFreeLoc() == 0) ? "" : ret.substring(1);
    }

    static void choiceCalc(Choice ch, Semanticize data, SemanticContext context) {
        int first = LookaheadCalc.firstChoice(ch, context);
        // dbl[i] and dbr[i] are lists of size limited matches for choice i
        // of ch. dbl ignores matches with semantic lookaheads (when force_la_check
        // is false), while dbr ignores semantic lookahead.
        List<MatchInfo>[] dbl = new ArrayList[ch.getChoices().size()];
        List<MatchInfo>[] dbr = new ArrayList[ch.getChoices().size()];
        int[] minLA = new int[ch.getChoices().size() - 1];
        MatchInfo[] overlapInfo = new MatchInfo[ch.getChoices().size() - 1];
        int[] other = new int[ch.getChoices().size() - 1];
        MatchInfo m;
        List<MatchInfo> v;
        boolean overlapDetected;
        for (int la = 1; la <= context.getChoiceAmbiguityCheck(); la++) {
            data.setLaLimit(la);
            data.setConsiderSemanticLA(!context.isForceLaCheck());
            for (int i = first; i < (ch.getChoices().size() - 1); i++) {
                data.initSizeLimitedMatches();
                m = new MatchInfo(data.laLimit());
                v = new ArrayList<>();
                v.add(m);
                LookaheadCalc.genFirstSet(data, v, ch.getChoices().get(i));
                dbl[i] = data.getSizeLimitedMatches();
            }
            data.setConsiderSemanticLA(false);
            for (int i = first + 1; i < ch.getChoices().size(); i++) {
                data.initSizeLimitedMatches();
                m = new MatchInfo(data.laLimit());
                v = new ArrayList<>();
                v.add(m);
                LookaheadCalc.genFirstSet(data, v, ch.getChoices().get(i));
                dbr[i] = data.getSizeLimitedMatches();
            }
            if (la == 1) {
                for (int i = first; i < (ch.getChoices().size() - 1); i++) {
                    Expansion exp = ch.getChoices().get(i);
                    if (Semanticize.emptyExpansionExists(exp)) {
                        context.onWarning(exp, "This choice can expand to the empty token sequence "
                                + "and will therefore always be taken in favor of the choices appearing later.");
                        break;
                    } else if (LookaheadCalc.javaCodeCheck(dbl[i])) {
                        context.onWarning(exp,
                                "JAVACODE non-terminal will force this choice to be taken "
                                        + "in favor of the choices appearing later.");
                        break;
                    }
                }
            }
            overlapDetected = false;
            for (int i = first; i < (ch.getChoices().size() - 1); i++) {
                for (int j = i + 1; j < ch.getChoices().size(); j++) {
                    if ((m = LookaheadCalc.overlap(dbl[i], dbr[j])) != null) {
                        minLA[i] = la + 1;
                        overlapInfo[i] = m;
                        other[i] = j;
                        overlapDetected = true;
                        break;
                    }
                }
            }
            if (!overlapDetected)
                break;
        }
        for (int i = first; i < (ch.getChoices().size() - 1); i++) {
            if (LookaheadCalc.explicitLA(ch.getChoices().get(i)) && !context.isForceLaCheck())
                continue;
            if (minLA[i] > context.getChoiceAmbiguityCheck()) {
                context.onWarning("Choice conflict involving two expansions at");
                System.err.print("         line " + ch.getChoices().get(i).getLine());
                System.err.print(", column " + ch.getChoices().get(i).getColumn());
                System.err.print(" and line " + ch.getChoices().get(other[i]).getLine());
                System.err.print(", column " + ch.getChoices().get(other[i]).getColumn());
                System.err.println(" respectively.");
                System.err.println(
                        "         A common prefix is: " + LookaheadCalc.image(overlapInfo[i], data));
                System.err.println("         Consider using a lookahead of " + minLA[i]
                        + " or more for earlier expansion.");
            } else if (minLA[i] > 1) {
                context.onWarning("Choice conflict involving two expansions at");
                System.err.print("         line " + ch.getChoices().get(i).getLine());
                System.err.print(", column " + ch.getChoices().get(i).getColumn());
                System.err.print(" and line " + ch.getChoices().get(other[i]).getLine());
                System.err.print(", column " + ch.getChoices().get(other[i]).getColumn());
                System.err.println(" respectively.");
                System.err.println(
                        "         A common prefix is: " + LookaheadCalc.image(overlapInfo[i], data));
                System.err.println(
                        "         Consider using a lookahead of " + minLA[i]
                                + " for earlier expansion.");
            }
        }
    }

    private static boolean explicitLA(Expansion exp) {
        if (!(exp instanceof Sequence seq))
            return false;
        var obj = seq.getUnits().getFirst();
        if (!(obj instanceof Lookahead la))
            return false;
        return la.isExplicit();
    }

    private static int firstChoice(Choice ch, SemanticContext context) {
        if (context.isForceLaCheck())
            return 0;
        for (int i = 0; i < ch.getChoices().size(); i++) {
            if (!LookaheadCalc.explicitLA(ch.getChoices().get(i)))
                return i;
        }
        return ch.getChoices().size();
    }

    private static String image(Expansion exp) {
        if (exp instanceof OneOrMore)
            return "(...)+";
        else if (exp instanceof ZeroOrMore)
            return "(...)*";
        else /* if (exp instanceof ZeroOrOne) */
            return "[...]";
    }

    static void ebnfCalc(Expansion exp, Expansion nested, Semanticize data,
                         SemanticContext context) {
        // exp is one of OneOrMore, ZeroOrMore, ZeroOrOne
        MatchInfo m, m1 = null;
        List<MatchInfo> v;
        List<MatchInfo> first, follow;
        int la;
        for (la = 1; la <= context.getOtherAmbiguityCheck(); la++) {
            data.setLaLimit(la);
            data.initSizeLimitedMatches();
            m = new MatchInfo(data.laLimit());
            v = new ArrayList<>();
            v.add(m);
            data.setConsiderSemanticLA(!context.isForceLaCheck());
            LookaheadCalc.genFirstSet(data, v, nested);
            first = data.getSizeLimitedMatches();
            data.initSizeLimitedMatches();
            data.setConsiderSemanticLA(false);
            LookaheadCalc.genFollowSet(v, exp, data.nextGenerationIndex(), data);
            follow = data.getSizeLimitedMatches();
            if ((la == 1) && LookaheadCalc.javaCodeCheck(first)) {
                context.onWarning(nested,
                        "JAVACODE non-terminal within " + LookaheadCalc.image(exp)
                                + " construct will force this construct to be entered in favor of "
                                + "expansions occurring after construct.");
            }
            if ((m = LookaheadCalc.overlap(first, follow)) == null)
                break;
            m1 = m;
        }
        if (la > context.getOtherAmbiguityCheck()) {
            context.onWarning(
                    "Choice conflict in " + LookaheadCalc.image(exp) + " construct " + "at line "
                            + exp.getLine()
                            + ", column " + exp.getColumn() + ".");
            System.err.println(
                    "         Expansion nested within construct and expansion following construct");
            System.err.println(
                    "         have common prefixes, one of which is: " + LookaheadCalc.image(m1, data));
            System.err.println(
                    "         Consider using a lookahead of " + la + " or more for nested expansion.");
        } else if (la > 1) {
            context.onWarning(
                    "Choice conflict in " + LookaheadCalc.image(exp) + " construct " + "at line "
                            + exp.getLine()
                            + ", column " + exp.getColumn() + ".");
            System.err.println(
                    "         Expansion nested within construct and expansion following construct");
            System.err.println(
                    "         have common prefixes, one of which is: " + LookaheadCalc.image(m1, data));
            System.err.println(
                    "         Consider using a lookahead of " + la + " for nested expansion.");
        }
    }

    private static void listAppend(List<MatchInfo> vToAppendTo, List<MatchInfo> vToAppend) {
        vToAppendTo.addAll(vToAppend);
    }

    static List<MatchInfo> genFirstSet(Semanticize data, List<MatchInfo> partialMatches,
                                       Expansion exp) {
        if (exp instanceof RExpression re) {
            List<MatchInfo> retval = new ArrayList<>();
            for (MatchInfo m : partialMatches) {
                MatchInfo mnew = m.copyWith(re.getOrdinal());
                if (mnew.firstFreeLoc() == data.laLimit())
                    data.getSizeLimitedMatches().add(mnew);
                else
                    retval.add(mnew);
            }
            return retval;
        } else if (exp instanceof NonTerminal) {
            NormalProduction prod = ((NonTerminal) exp).getProd();
            return LookaheadCalc.genFirstSet(data, partialMatches, prod.getExpansion());
        } else if (exp instanceof Choice ch) {
            List<MatchInfo> retval = new ArrayList<>();
            for (var element : ch.getChoices()) {
                List<MatchInfo> v = LookaheadCalc.genFirstSet(data, partialMatches, element);
                LookaheadCalc.listAppend(retval, v);
            }
            return retval;
        } else if (exp instanceof Sequence seq) {
            List<MatchInfo> v = partialMatches;
            for (var element : seq.getUnits()) {
                v = LookaheadCalc.genFirstSet(data, v, element);
                if (v.isEmpty())
                    break;
            }
            return v;
        } else if (exp instanceof OneOrMore om) {
            List<MatchInfo> retval = new ArrayList<>();
            List<MatchInfo> v = partialMatches;
            while (true) {
                v = LookaheadCalc.genFirstSet(data, v, om.getExpansion());
                if (v.isEmpty())
                    break;
                LookaheadCalc.listAppend(retval, v);
            }
            return retval;
        } else if (exp instanceof ZeroOrMore zm) {
            List<MatchInfo> retval = new ArrayList<>();
            LookaheadCalc.listAppend(retval, partialMatches);
            List<MatchInfo> v = partialMatches;
            while (true) {
                v = LookaheadCalc.genFirstSet(data, v, zm.getExpansion());
                if (v.isEmpty())
                    break;
                LookaheadCalc.listAppend(retval, v);
            }
            return retval;
        } else if (exp instanceof ZeroOrOne) {
            List<MatchInfo> retval = new ArrayList<>();
            LookaheadCalc.listAppend(retval, partialMatches);
            LookaheadCalc.listAppend(retval,
                    LookaheadCalc.genFirstSet(data, partialMatches, ((ZeroOrOne) exp).getExpansion()));
            return retval;
        } else if (data.considerSemanticLA() && (exp instanceof Lookahead lookahead)
                && (!lookahead.getActionTokens().isEmpty())) {
            return new ArrayList<>();
        } else {
            List<MatchInfo> retval = new ArrayList<>();
            LookaheadCalc.listAppend(retval, partialMatches);
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
            LookaheadCalc.listAppend(retval, partialMatches);
            return retval;
        } else if (exp.parent() instanceof NormalProduction np) {
            List<Object> parents = np.getParents();
            List<MatchInfo> retval = new ArrayList<>();
            // System.out.println("1; gen: " + generation + "; exp: " + exp);
            for (Object parent : parents) {
                List<MatchInfo> v = LookaheadCalc.genFollowSet(partialMatches, (Expansion) parent,
                        generation, data);
                LookaheadCalc.listAppend(retval, v);
            }
            return retval;
        } else if (exp.parent() instanceof Sequence seq) {
            List<MatchInfo> v = partialMatches;
            for (int i = exp.parentOrdinal() + 1; i < seq.getUnits().size(); i++) {
                v = LookaheadCalc.genFirstSet(data, v, seq.getUnits().get(i));
                if (v.isEmpty())
                    return v;
            }
            List<MatchInfo> v1 = new ArrayList<>();
            List<MatchInfo> v2 = new ArrayList<>();
            LookaheadCalc.listSplit(v, partialMatches, v1, v2);
            if (!v1.isEmpty())
                // System.out.println("2; gen: " + generation + "; exp: " + exp);
                v1 = LookaheadCalc.genFollowSet(v1, seq, generation, data);
            if (!v2.isEmpty())
                // System.out.println("3; gen: " + generation + "; exp: " + exp);
                v2 = LookaheadCalc.genFollowSet(v2, seq, data.nextGenerationIndex(), data);
            LookaheadCalc.listAppend(v2, v1);
            return v2;
        } else if ((exp.parent() instanceof OneOrMore) || (exp.parent() instanceof ZeroOrMore)) {
            List<MatchInfo> moreMatches = new ArrayList<>();
            LookaheadCalc.listAppend(moreMatches, partialMatches);
            List<MatchInfo> v = partialMatches;
            while (true) {
                v = LookaheadCalc.genFirstSet(data, v, exp);
                if (v.isEmpty())
                    break;
                LookaheadCalc.listAppend(moreMatches, v);
            }
            List<MatchInfo> v1 = new ArrayList<>();
            List<MatchInfo> v2 = new ArrayList<>();
            LookaheadCalc.listSplit(moreMatches, partialMatches, v1, v2);
            if (!v1.isEmpty())
                // System.out.println("4; gen: " + generation + "; exp: " + exp);
                v1 = LookaheadCalc.genFollowSet(v1, (Expansion) exp.parent(), generation, data);
            if (!v2.isEmpty())
                // System.out.println("5; gen: " + generation + "; exp: " + exp);
                v2 = LookaheadCalc.genFollowSet(v2, (Expansion) exp.parent(),
                        data.nextGenerationIndex(),
                        data);
            LookaheadCalc.listAppend(v2, v1);
            return v2;
        } else
            // System.out.println("6; gen: " + generation + "; exp: " + exp);
            return LookaheadCalc.genFollowSet(partialMatches, (Expansion) exp.parent(), generation,
                    data);
    }
}