// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.analysis;

/**
 * What a target can do, as far as planning is concerned. A back end states it before planning; it
 * used to override the decisions while emitting (ADR-0029).
 *
 * @param recordsExpectedTokens whether the generated parser records the tokens it expected, for
 *                              its error report: the {@code jj_la1} slots of the choices and the
 *                              rescan of the lookahead routines. A C++ parser reports an error
 *                              through its ParserErrorHandler, which names the token it found and
 *                              not the ones it expected.
 */
public record PlanningProfile(boolean recordsExpectedTokens) {

    /** A target that records what ERROR_REPORTING asks for. */
    public static final PlanningProfile DEFAULT = new PlanningProfile(true);
}
