// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.analysis;

/**
 * What a target can do, as far as planning is concerned. A back end states it before planning; it
 * used to override the decisions while emitting, and to refuse what it could not write only once
 * it had begun writing (ADR-0029).
 *
 * @param target                the target's name, as a refusal names it
 * @param recordsExpectedTokens whether the generated parser records the tokens it expected, for
 *                              its error report: the {@code jj_la1} slots of the choices and the
 *                              rescan of the lookahead routines. A C++ parser reports an error
 *                              through its ParserErrorHandler, which names the token it found and
 *                              not the ones it expected.
 * @param guardsVoidProductions whether a production without a result carries the DEPTH_LIMIT
 *                              guard. The C++ guard returns a value on error, which a void
 *                              production has not got.
 * @param depthLimit            whether the target writes the DEPTH_LIMIT guard at all
 * @param parserTraces          whether it writes the DEBUG_PARSER and DEBUG_LOOKAHEAD traces
 */
public record PlanningProfile(String target, boolean recordsExpectedTokens,
                              boolean guardsVoidProductions, boolean depthLimit,
                              boolean parserTraces) {

    /** A target that records what ERROR_REPORTING asks for and writes every option. */
    public static final PlanningProfile DEFAULT =
            new PlanningProfile("Java", true, true, true, true);
}
