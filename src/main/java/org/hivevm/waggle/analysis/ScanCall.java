// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.analysis;

/**
 * What a lookahead routine calls to scan an expansion: a routine of its own, or — when the
 * expansion comes down to a single token — the token scan directly.
 *
 * <p>The planner used to store the Java/C++ call text {@code jj_scan_token(…)} as the routine's
 * "internal name", and every reader recognised it with {@code startsWith("jj_scan_token")}: target
 * spelling inside the analysis, parsed back out of a string (ADR-0029).
 */
public sealed interface ScanCall {

    /** A single token, scanned in place; its label if it has one, its ordinal otherwise. */
    record Token(TokenRef token) implements ScanCall {
    }

    /** A {@code jj_3} routine, by the name the planner gave it (without the {@code jj_3} prefix). */
    record Routine(String name) implements ScanCall {
    }
}
