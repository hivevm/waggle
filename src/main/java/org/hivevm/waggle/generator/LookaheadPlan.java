// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.generator;

import org.hivevm.waggle.model.Lookahead;

import java.util.List;

/**
 * How one choice point — a choice, or the loop condition of {@code (…)*}, {@code (…)+} and
 * {@code […]} — decides between its alternatives. {@link ParserBuilder} makes these decisions once;
 * the generators only render them.
 *
 * <p>The builder and the generator used to run the same state machine side by side — FIRST sets,
 * empty-expansion checks, cased tokens, token masks — and it only worked while both copies agreed.
 * They did not: the builder filed the default case's mask under the last alternative, the generator
 * looked it up under the one it had stopped at.
 *
 * @param steps       one step per alternative that is tested, in order; the alternative after the
 *                    last step is the default
 * @param defaultMask the {@code jj_la1} slot of the default case of a trailing switch, or -1
 */
record LookaheadPlan(List<Step> steps, int defaultMask) {

    /** How an alternative is tested. */
    enum Kind {
        /** Only a semantic lookahead: an {@code if} on the user's expression. */
        SEMANTIC,
        /** One token of lookahead: {@code case}s of a switch on the next token. */
        SWITCH,
        /** A syntactic lookahead: a call to its {@code jj_2} routine. */
        SYNTACTIC
    }

    /**
     * @param la     the lookahead of the alternative
     * @param tokens SWITCH only: the kinds the alternative claims, each at most once per switch
     * @param mask   the {@code jj_la1} slot of the switch this step closes, or -1
     */
    record Step(Kind kind, Lookahead la, int[] tokens, int mask) {
    }

    /** The alternative taken when no step matches. */
    int defaultAlternative() {
        return this.steps.size();
    }
}
