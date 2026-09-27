// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/LookaheadCalc.java

package org.hivevm.waggle.analysis;

import org.hivevm.waggle.api.Encoding;
import org.hivevm.waggle.api.ParserOptions;
import org.hivevm.waggle.diag.Diagnostics;
import org.hivevm.waggle.model.Action;
import org.hivevm.waggle.model.Choice;
import org.hivevm.waggle.model.Expansion;
import org.hivevm.waggle.model.Lookahead;
import org.hivevm.waggle.model.NonTerminal;
import org.hivevm.waggle.model.NormalProduction;
import org.hivevm.waggle.model.OneOrMore;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.RStringLiteral;
import org.hivevm.waggle.model.Sequence;
import org.hivevm.waggle.model.ZeroOrMore;
import org.hivevm.waggle.model.ZeroOrOne;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The lookahead adequacy check of one generation: it warns where a choice or a repetition cannot be
 * decided with the lookahead it has. One instance serves every check of a run, and holds the
 * scratch state of the first- and follow-set walks.
 */
class LookaheadCalc {

    private final ParserOptions options;
    private final Diagnostics diagnostics;

    /** The regular expression of each token ordinal, to name the tokens of a common prefix. */
    private final Map<Integer, RExpression> tokens;

    private long generationIndex = 1;
    private int laLimit;
    private boolean considerSemanticLA;
    private List<MatchInfo> sizeLimitedMatches;

    /**
     * The generation in which each expansion was last visited by the follow-set walk, so a
     * right-recursive grammar does not loop. It used to be a field of the expansion, which put this
     * walk's scratch space into the model — and made it per-JVM rather than per-generation
     * (ADR-0019).
     */
    private final Map<Expansion, Long> visited = new IdentityHashMap<>();

    LookaheadCalc(ParserOptions options, Diagnostics diagnostics, Map<Integer, RExpression> tokens) {
        this.options = options;
        this.diagnostics = diagnostics;
        this.tokens = tokens;
    }

    private long nextGenerationIndex() {
        return this.generationIndex++;
    }

    /** Whether {@code exp} has not yet been seen in {@code generation}. */
    private boolean visit(Expansion exp, long generation) {
        return !Long.valueOf(generation).equals(this.visited.put(exp, generation));
    }

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

    private String image(MatchInfo m) {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < m.firstFreeLoc(); i++) {
            if (m.match()[i] == 0)
                ret.append(" <EOF>");
            else {
                RExpression re = this.tokens.get(m.match()[i]);
                if (re instanceof RStringLiteral)
                    ret.append(" \"").append(Encoding.escape(((RStringLiteral) re).getImage()))
                            .append("\"");
                else if ((re.getLabel() != null) && !re.getLabel().isEmpty())
                    ret.append(" <").append(re.getLabel()).append(">");
                else
                    ret.append(" <token of kind ").append(m.match()[i]).append(">");
            }
        }
        return (m.firstFreeLoc() == 0) ? "" : ret.substring(1);
    }

    /** The size-limited matches of {@code exp}, from an empty prefix. */
    private List<MatchInfo> sizeLimitedMatches(Expansion exp) {
        this.sizeLimitedMatches = new ArrayList<>();
        List<MatchInfo> v = new ArrayList<>();
        v.add(new MatchInfo(this.laLimit));
        genFirstSet(v, exp);
        return this.sizeLimitedMatches;
    }

    void choiceCalc(Choice ch) {
        int first = firstChoice(ch);
        // dbl[i] and dbr[i] are lists of size limited matches for choice i
        // of ch. dbl ignores matches with semantic lookaheads (when force_la_check
        // is false), while dbr ignores semantic lookahead.
        List<MatchInfo>[] dbl = LookaheadCalc.newMatchLists(ch.getChoices().size());
        List<MatchInfo>[] dbr = LookaheadCalc.newMatchLists(ch.getChoices().size());
        int[] minLA = new int[ch.getChoices().size() - 1];
        MatchInfo[] overlapInfo = new MatchInfo[ch.getChoices().size() - 1];
        int[] other = new int[ch.getChoices().size() - 1];
        MatchInfo m;
        boolean overlapDetected;
        for (int la = 1; la <= this.options.choiceAmbiguityCheck(); la++) {
            this.laLimit = la;
            this.considerSemanticLA = !this.options.forceLaCheck();
            for (int i = first; i < (ch.getChoices().size() - 1); i++) {
                dbl[i] = sizeLimitedMatches(ch.getChoices().get(i));
            }
            this.considerSemanticLA = false;
            for (int i = first + 1; i < ch.getChoices().size(); i++) {
                dbr[i] = sizeLimitedMatches(ch.getChoices().get(i));
            }
            if (la == 1) {
                for (int i = first; i < (ch.getChoices().size() - 1); i++) {
                    Expansion exp = ch.getChoices().get(i);
                    if (Semanticize.emptyExpansionExists(exp)) {
                        this.diagnostics.warning(exp, "This choice can expand to the empty token sequence "
                                + "and will therefore always be taken in favor of the choices appearing later.");
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
            if (LookaheadCalc.explicitLA(ch.getChoices().get(i)) && !this.options.forceLaCheck())
                continue;
            if (minLA[i] > this.options.choiceAmbiguityCheck()) {
                warnChoiceConflict(ch, i, other[i], overlapInfo[i], minLA[i], " or more");
            } else if (minLA[i] > 1) {
                warnChoiceConflict(ch, i, other[i], overlapInfo[i], minLA[i], "");
            }
        }
    }

    /**
     * Emits the "choice conflict" warning for choice {@code i} overlapping choice {@code other}. The
     * two call sites differed only in the trailing "{@code or more}" hint, passed as {@code amount}.
     *
     * <p>The detail lines used to go straight to {@code System.err} while only the first line was
     * reported as a diagnostic (ADR-0015). A silent sink therefore still printed them, and a caller
     * reading {@code Diagnostics} afterwards got a warning that named neither the two expansions nor
     * the prefix they share. The whole warning is one diagnostic now; the rendered text is the same.
     */
    private void warnChoiceConflict(Choice ch, int i, int other, MatchInfo overlapInfo, int minLA,
                                    String amount) {
        this.diagnostics.warning("Choice conflict involving two expansions at"
                + "\n         line " + ch.getChoices().get(i).getLine()
                + ", column " + ch.getChoices().get(i).getColumn()
                + " and line " + ch.getChoices().get(other).getLine()
                + ", column " + ch.getChoices().get(other).getColumn()
                + " respectively."
                + "\n         A common prefix is: " + image(overlapInfo)
                + "\n         Consider using a lookahead of " + minLA + amount
                + " for earlier expansion.");
    }

    /** An array of match lists, one per choice. Generic array creation needs the cast. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<MatchInfo>[] newMatchLists(int length) {
        return new List[length];
    }

    private static boolean explicitLA(Expansion exp) {
        if (!(exp instanceof Sequence seq))
            return false;
        var obj = seq.getUnits().getFirst();
        if (!(obj instanceof Lookahead la))
            return false;
        return la.isExplicit();
    }

    private int firstChoice(Choice ch) {
        if (this.options.forceLaCheck())
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

    void ebnfCalc(Expansion exp, Expansion nested) {
        // exp is one of OneOrMore, ZeroOrMore, ZeroOrOne
        MatchInfo m, m1 = null;
        int la;
        for (la = 1; la <= this.options.otherAmbiguityCheck(); la++) {
            this.laLimit = la;
            this.considerSemanticLA = !this.options.forceLaCheck();
            List<MatchInfo> first = sizeLimitedMatches(nested);
            this.sizeLimitedMatches = new ArrayList<>();
            this.considerSemanticLA = false;
            List<MatchInfo> v = new ArrayList<>();
            v.add(new MatchInfo(this.laLimit));
            genFollowSet(v, exp, nextGenerationIndex());
            List<MatchInfo> follow = this.sizeLimitedMatches;
            if ((m = LookaheadCalc.overlap(first, follow)) == null)
                break;
            m1 = m;
        }
        if (la > this.options.otherAmbiguityCheck()) {
            warnEbnfConflict(exp, m1, la, " or more");
        } else if (la > 1) {
            warnEbnfConflict(exp, m1, la, "");
        }
    }

    /**
     * Emits the "choice conflict" warning for a repetition whose body and continuation overlap. As
     * with {@link #warnChoiceConflict}, the two branches differed only in the "{@code or more}"
     * hint, and the detail lines bypassed the diagnostics channel (ADR-0015).
     */
    private void warnEbnfConflict(Expansion exp, MatchInfo m1, int la, String amount) {
        this.diagnostics.warning("Choice conflict in " + LookaheadCalc.image(exp) + " construct " + "at line "
                + exp.getLine()
                + ", column " + exp.getColumn() + "."
                + "\n         Expansion nested within construct and expansion following construct"
                + "\n         have common prefixes, one of which is: " + image(m1)
                + "\n         Consider using a lookahead of " + la + amount
                + " for nested expansion.");
    }

    private List<MatchInfo> genFirstSet(List<MatchInfo> partialMatches, Expansion exp) {
        return switch (exp) {
            case RExpression regexp -> {
                List<MatchInfo> retval = new ArrayList<>();
                for (MatchInfo m : partialMatches) {
                    MatchInfo mnew = m.copyWith(regexp.getOrdinal());
                    if (mnew.firstFreeLoc() == this.laLimit) {
                        this.sizeLimitedMatches.add(mnew);
                    } else {
                        retval.add(mnew);
                    }
                }
                yield retval;
            }

            case NonTerminal nonTerminal -> genFirstSet(partialMatches,
                    nonTerminal.getProd().getExpansion());

            case Choice choice -> {
                List<MatchInfo> retval = new ArrayList<>();
                for (var alternative : choice.getChoices()) {
                    retval.addAll(genFirstSet(partialMatches, alternative));
                }
                yield retval;
            }

            case Sequence sequence -> {
                List<MatchInfo> matches = partialMatches;
                for (var unit : sequence.getUnits()) {
                    matches = genFirstSet(matches, unit);
                    if (matches.isEmpty()) {
                        break;
                    }
                }
                yield matches;
            }

            case OneOrMore oneOrMore -> genRepeated(partialMatches, oneOrMore.getExpansion(), false);

            // Zero repetitions is a match too, so the incoming matches are kept.
            case ZeroOrMore zeroOrMore -> genRepeated(partialMatches, zeroOrMore.getExpansion(), true);

            case ZeroOrOne zeroOrOne -> {
                List<MatchInfo> retval = new ArrayList<>(partialMatches);
                retval.addAll(genFirstSet(partialMatches, zeroOrOne.getExpansion()));
                yield retval;
            }

            // A semantic lookahead cuts the first set: nothing is guaranteed to match.
            case Lookahead lookahead
                    when this.considerSemanticLA && !lookahead.getActionTokens().isEmpty() ->
                    new ArrayList<>();

            case Lookahead lookahead -> new ArrayList<>(partialMatches);
            case Action action -> new ArrayList<>(partialMatches);
            case NormalProduction production -> new ArrayList<>(partialMatches);
        };
    }

    /** The first set of an expansion repeated any number of times. */
    private List<MatchInfo> genRepeated(List<MatchInfo> partialMatches, Expansion body,
                                        boolean zeroAllowed) {
        List<MatchInfo> retval = new ArrayList<>();
        if (zeroAllowed) {
            retval.addAll(partialMatches);
        }

        List<MatchInfo> matches = partialMatches;
        while (true) {
            matches = genFirstSet(matches, body);
            if (matches.isEmpty()) {
                break;
            }
            retval.addAll(matches);
        }
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

    private List<MatchInfo> genFollowSet(List<MatchInfo> partialMatches, Expansion exp,
                                         long generation) {
        if (!visit(exp, generation))
            return new ArrayList<>();

        Expansion parent = exp.parent();
        if (parent == null) {
            return new ArrayList<>(partialMatches);
        }

        return switch (parent) {
            // Follow the production to each place it is used.
            case NormalProduction production -> {
                List<MatchInfo> retval = new ArrayList<>();
                for (var user : production.getParents()) {
                    retval.addAll(genFollowSet(partialMatches, user, generation));
                }
                yield retval;
            }

            // What follows is the rest of the sequence, and then whatever follows the sequence.
            case Sequence sequence -> {
                List<MatchInfo> matches = partialMatches;
                for (int i = exp.parentOrdinal() + 1; i < sequence.getUnits().size(); i++) {
                    matches = genFirstSet(matches, sequence.getUnits().get(i));
                    if (matches.isEmpty()) {
                        yield matches;
                    }
                }
                yield followParent(matches, partialMatches, sequence, generation);
            }

            // A repetition may loop, so what follows it may also be itself.
            case OneOrMore oneOrMore -> followRepeated(partialMatches, exp, oneOrMore, generation);
            case ZeroOrMore zeroOrMore -> followRepeated(partialMatches, exp, zeroOrMore, generation);

            default -> genFollowSet(partialMatches, parent, generation);
        };
    }

    /** What follows a repetition: the repetition itself, and then whatever follows it. */
    private List<MatchInfo> followRepeated(List<MatchInfo> partialMatches, Expansion exp,
                                           Expansion parent, long generation) {
        List<MatchInfo> moreMatches = new ArrayList<>(partialMatches);

        List<MatchInfo> matches = partialMatches;
        while (true) {
            matches = genFirstSet(matches, exp);
            if (matches.isEmpty()) {
                break;
            }
            moreMatches.addAll(matches);
        }
        return followParent(moreMatches, partialMatches, parent, generation);
    }

    /**
     * Continues into what follows "parent". Matches that were already present on the way in keep the
     * current generation; the ones added here get a fresh one, so a right-recursive loop terminates.
     */
    private List<MatchInfo> followParent(List<MatchInfo> matches, List<MatchInfo> partialMatches,
                                         Expansion parent, long generation) {
        List<MatchInfo> known = new ArrayList<>();
        List<MatchInfo> fresh = new ArrayList<>();
        LookaheadCalc.listSplit(matches, partialMatches, known, fresh);

        if (!known.isEmpty()) {
            known = genFollowSet(known, parent, generation);
        }
        if (!fresh.isEmpty()) {
            fresh = genFollowSet(fresh, parent, nextGenerationIndex());
        }
        fresh.addAll(known);
        return fresh;
    }
}
