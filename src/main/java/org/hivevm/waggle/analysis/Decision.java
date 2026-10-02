// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.analysis;

import org.hivevm.waggle.model.CodeText;

import java.util.List;

/**
 * How one choice point — a choice, or the loop condition of {@code (…)*}, {@code (…)+} and
 * {@code […]} — decides between its alternatives, finished: every test in order with the shape the
 * chain is in when it opens, the {@code jj_la1} slot it records, the tokens a switch arm takes, and
 * how many blocks the arm that runs when nothing matched has to close. {@link ParserPlanner} makes
 * these decisions once; the generators only write them.
 *
 * <p>The builder and the generator used to run the same state machine side by side — FIRST sets,
 * empty-expansion checks, cased tokens, token masks — and it only worked while both copies agreed.
 * They did not: the builder filed the default case's mask under the last alternative, the generator
 * looked it up under the one it had stopped at. The generator then still advanced the chain's shape
 * and counted its open blocks while printing (ADR-0029).
 *
 * @param steps    one step per alternative that is tested, in order; step {@code i} tests
 *                 alternative {@code i}, and the alternative after the last step is the default
 * @param fallback the arm that runs when no step matched
 */
public record Decision(List<Step> steps, Fallback fallback) {

    /** What the chain has opened when a step or the fallback is written. */
    public enum Opening {
        /** Nothing: the step is the first of the chain. */
        NOTHING,
        /** An {@code if} whose block is still open. */
        IF,
        /** A switch on the next token, with its last arm closed. */
        SWITCH
    }

    /** How one alternative is tested. */
    public sealed interface Step {

        /** What the chain has opened before this step. */
        Opening opening();
    }

    /**
     * Only a semantic lookahead: an {@code if} on the user's expression.
     *
     * @param slot      the {@code jj_la1} slot the default arm of an open switch records, or -1
     * @param condition the code the lookahead tests
     */
    public record Semantic(Opening opening, int slot, CodeText condition) implements Step {
    }

    /**
     * One token of lookahead: an arm of a switch on the next token.
     *
     * @param cases the tokens the arm takes, each at most once per switch
     */
    public record Switch(Opening opening, List<TokenRef> cases) implements Step {
    }

    /**
     * A syntactic lookahead: a call to its {@code jj_2} routine.
     *
     * @param slot     the {@code jj_la1} slot the default arm of an open switch records, or -1
     * @param routine  the routine
     * @param amount   how many tokens it scans at most
     * @param semantic the code of the semantic condition tested as well, empty when there is none
     */
    public record Syntactic(Opening opening, int slot, Jj2Routine routine, int amount,
                            CodeText semantic) implements Step {
    }

    /**
     * The arm that runs when no step matched.
     *
     * @param slot the {@code jj_la1} slot it records when it is a switch's default, or -1
     */
    public record Fallback(Opening opening, int slot) {
    }

    /** The alternative taken when no step matches. */
    public int defaultAlternative() {
        return this.steps.size();
    }
}
