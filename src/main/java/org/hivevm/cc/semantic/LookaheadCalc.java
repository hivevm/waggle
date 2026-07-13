// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.semantic;

import org.hivevm.cc.Encoding;
import org.hivevm.cc.model.Action;
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
        return switch (exp) {
            case RExpression regexp -> {
                List<MatchInfo> retval = new ArrayList<>();
                for (MatchInfo m : partialMatches) {
                    MatchInfo mnew = m.copyWith(regexp.getOrdinal());
                    if (mnew.firstFreeLoc() == data.laLimit()) {
                        data.getSizeLimitedMatches().add(mnew);
                    } else {
                        retval.add(mnew);
                    }
                }
                yield retval;
            }

            case NonTerminal nonTerminal -> LookaheadCalc.genFirstSet(data, partialMatches,
                    nonTerminal.getProd().getExpansion());

            case Choice choice -> {
                List<MatchInfo> retval = new ArrayList<>();
                for (var alternative : choice.getChoices()) {
                    LookaheadCalc.listAppend(retval,
                            LookaheadCalc.genFirstSet(data, partialMatches, alternative));
                }
                yield retval;
            }

            case Sequence sequence -> {
                List<MatchInfo> matches = partialMatches;
                for (var unit : sequence.getUnits()) {
                    matches = LookaheadCalc.genFirstSet(data, matches, unit);
                    if (matches.isEmpty()) {
                        break;
                    }
                }
                yield matches;
            }

            case OneOrMore oneOrMore -> LookaheadCalc.genRepeated(data, partialMatches,
                    oneOrMore.getExpansion(), false);

            // Zero repetitions is a match too, so the incoming matches are kept.
            case ZeroOrMore zeroOrMore -> LookaheadCalc.genRepeated(data, partialMatches,
                    zeroOrMore.getExpansion(), true);

            case ZeroOrOne zeroOrOne -> {
                List<MatchInfo> retval = new ArrayList<>();
                LookaheadCalc.listAppend(retval, partialMatches);
                LookaheadCalc.listAppend(retval,
                        LookaheadCalc.genFirstSet(data, partialMatches, zeroOrOne.getExpansion()));
                yield retval;
            }

            // A semantic lookahead cuts the first set: nothing is guaranteed to match.
            case Lookahead lookahead
                    when data.considerSemanticLA() && !lookahead.getActionTokens().isEmpty() ->
                    new ArrayList<>();

            case Lookahead lookahead -> LookaheadCalc.copyOf(partialMatches);
            case Action action -> LookaheadCalc.copyOf(partialMatches);
            case NormalProduction production -> LookaheadCalc.copyOf(partialMatches);
        };
    }

    /** The first set of an expansion repeated any number of times. */
    private static List<MatchInfo> genRepeated(Semanticize data, List<MatchInfo> partialMatches,
                                               Expansion body, boolean zeroAllowed) {
        List<MatchInfo> retval = new ArrayList<>();
        if (zeroAllowed) {
            LookaheadCalc.listAppend(retval, partialMatches);
        }

        List<MatchInfo> matches = partialMatches;
        while (true) {
            matches = LookaheadCalc.genFirstSet(data, matches, body);
            if (matches.isEmpty()) {
                break;
            }
            LookaheadCalc.listAppend(retval, matches);
        }
        return retval;
    }

    private static List<MatchInfo> copyOf(List<MatchInfo> matches) {
        List<MatchInfo> retval = new ArrayList<>();
        LookaheadCalc.listAppend(retval, matches);
        return retval;
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

        exp.setGeneration(generation);

        Expansion parent = exp.parent();
        if (parent == null) {
            return LookaheadCalc.copyOf(partialMatches);
        }

        return switch (parent) {
            // Follow the production to each place it is used.
            case NormalProduction production -> {
                List<MatchInfo> retval = new ArrayList<>();
                for (var user : production.getParents()) {
                    LookaheadCalc.listAppend(retval,
                            LookaheadCalc.genFollowSet(partialMatches, user, generation, data));
                }
                yield retval;
            }

            // What follows is the rest of the sequence, and then whatever follows the sequence.
            case Sequence sequence -> {
                List<MatchInfo> matches = partialMatches;
                for (int i = exp.parentOrdinal() + 1; i < sequence.getUnits().size(); i++) {
                    matches = LookaheadCalc.genFirstSet(data, matches, sequence.getUnits().get(i));
                    if (matches.isEmpty()) {
                        yield matches;
                    }
                }
                yield LookaheadCalc.followParent(matches, partialMatches, sequence, generation, data);
            }

            // A repetition may loop, so what follows it may also be itself.
            case OneOrMore oneOrMore ->
                    LookaheadCalc.followRepeated(partialMatches, exp, oneOrMore, generation, data);
            case ZeroOrMore zeroOrMore ->
                    LookaheadCalc.followRepeated(partialMatches, exp, zeroOrMore, generation, data);

            default -> LookaheadCalc.genFollowSet(partialMatches, parent, generation, data);
        };
    }

    /** What follows a repetition: the repetition itself, and then whatever follows it. */
    private static List<MatchInfo> followRepeated(List<MatchInfo> partialMatches, Expansion exp,
                                                  Expansion parent, long generation,
                                                  Semanticize data) {
        List<MatchInfo> moreMatches = new ArrayList<>();
        LookaheadCalc.listAppend(moreMatches, partialMatches);

        List<MatchInfo> matches = partialMatches;
        while (true) {
            matches = LookaheadCalc.genFirstSet(data, matches, exp);
            if (matches.isEmpty()) {
                break;
            }
            LookaheadCalc.listAppend(moreMatches, matches);
        }
        return LookaheadCalc.followParent(moreMatches, partialMatches, parent, generation, data);
    }

    /**
     * Continues into what follows "parent". Matches that were already present on the way in keep the
     * current generation; the ones added here get a fresh one, so a right-recursive loop terminates.
     */
    private static List<MatchInfo> followParent(List<MatchInfo> matches,
                                                List<MatchInfo> partialMatches, Expansion parent,
                                                long generation, Semanticize data) {
        List<MatchInfo> known = new ArrayList<>();
        List<MatchInfo> fresh = new ArrayList<>();
        LookaheadCalc.listSplit(matches, partialMatches, known, fresh);

        if (!known.isEmpty()) {
            known = LookaheadCalc.genFollowSet(known, parent, generation, data);
        }
        if (!fresh.isEmpty()) {
            fresh = LookaheadCalc.genFollowSet(fresh, parent, data.nextGenerationIndex(), data);
        }
        LookaheadCalc.listAppend(fresh, known);
        return fresh;
    }
}