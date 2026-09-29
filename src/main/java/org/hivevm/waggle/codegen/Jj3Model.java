// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;


import java.util.List;

/**
 * A lookahead routine as one target writes it: the plan's decisions with every value already
 * spelled for that target, which is what its templates render (ADR-0031).
 *
 * <p>It decides nothing. What a routine scans, in which order, and which alternatives it tries is
 * {@link org.hivevm.waggle.analysis.Jj3Routine}'s, and that record may not know how a target writes
 * a call to another routine (ADR-0029). This is where the two meet.
 *
 * <p>A template is named after the type of the record it renders, so every record here has a file
 * of its own under {@code templates/<target>/parser/apply/}.
 */
public final class Jj3Model {

    private Jj3Model() {
    }

    /**
     * One routine.
     *
     * @param name       the routine's name, without the {@code jj_3} its target prefixes
     * @param traced     whether it traces what it does (DEBUG_LOOKAHEAD)
     * @param production the production the trace names, escaped for the target
     * @param rescan     whether the trace stays quiet while the parser rescans
     * @param trace      the trace a routine opens with, when it is traced
     * @param failure    what a step writes when it gives up, which the steps read from here
     * @param result     what the routine yields when every step scanned
     * @param body       the steps, in the order the routine runs them
     */
    public record Routine(String name, boolean traced, String production, boolean rescan,
                          Trace trace, Failure failure, Success result, List<Step> body) {
    }

    /** Traces that the routine is entered. */
    public record Trace() {
    }

    /** Gives up: the routine scanned what it looks for not. */
    public record Failure() {
    }

    /** Succeeds: the routine scanned all it looks for. */
    public record Success() {
    }

    /** One statement of a routine's body. */
    public sealed interface Step {
    }

    /** Declares the scan position a routine backtracks to; at most once per routine. */
    public record DeclareScanPos() implements Step {
    }

    /** Gives up unless the token scans. */
    public record ScanToken(String token) implements Step {
    }

    /** Gives up unless the call scans. */
    public record FailIfCall(String call) implements Step {
    }

    /**
     * Tries the alternatives of a choice in turn, backtracking between them.
     *
     * @param saveScanPos whether the scan position is saved first: a choice of one alternative has
     *                    nothing to backtrack to
     * @param first       the first alternative; each one that is not the last holds the rest, so
     *                    the nesting of the generated blocks is the nesting of the records
     */
    public record Choice(boolean saveScanPos, Alternative first) implements Step {
    }

    /** One alternative of a {@link Choice}. */
    public sealed interface Alternative {
    }

    /**
     * An alternative that has another after it: it opens a block the rest is written in.
     *
     * @param guarded  whether a semantic condition guards it
     * @param semantic that condition as the grammar wrote it, ignored unless {@code guarded}
     * @param rest     the alternatives after it
     */
    public record TryAlternative(String call, boolean guarded,
                                 String semantic,
                                 Alternative rest) implements Alternative {
    }

    /** The last alternative: when it does not scan either, the choice gives up. */
    public record LastAlternative(String call, boolean guarded,
                                  String semantic) implements Alternative {
    }

    /** Scans the call as often as it matches: the tail of {@code (…)*} and {@code (…)+}. */
    public record ScanLoop(String call) implements Step {
    }

    /** Scans the call if it matches: {@code […]}. */
    public record OptionalScan(String call) implements Step {
    }

    /**
     * A {@code jj_2} routine: it runs the {@code jj_3} routine of the same name from the current
     * token, and records the position for the error report when the parser does.
     *
     * @param name     the routine's name, without the {@code jj_2}/{@code jj_3} its target
     *                 prefixes
     * @param saveSlot the {@code jj_2_rtns} slot it saves its position in
     */
    public record Jj2(String name, int saveSlot) {
    }
}
