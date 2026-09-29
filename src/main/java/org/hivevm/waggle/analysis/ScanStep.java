// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.analysis;

import org.hivevm.waggle.model.CodeText;

import java.util.List;

/**
 * One statement of a {@code jj_3} routine's body, in the order the routine runs it. The planner
 * cuts the body to the tokens the routine is asked to scan and places the declaration of the scan
 * position where it is first needed; the emitter writes the steps and decides nothing (ADR-0029).
 */
public sealed interface ScanStep {

    /** Declares the scan position a routine backtracks to; at most once per routine. */
    record DeclareScanPos() implements ScanStep {
    }

    /** Gives up unless the token scans. */
    record ScanToken(TokenRef token) implements ScanStep {
    }

    /** Gives up unless {@code call} scans. */
    record FailIfCall(ScanCall call) implements ScanStep {
    }

    /**
     * Tries the alternatives of a choice in turn, backtracking between them.
     *
     * @param saveScanPos  whether the scan position is saved first: a choice of one alternative
     *                     has nothing to backtrack to
     * @param alternatives the alternatives in order; the last one gives up when it fails
     */
    record Choice(boolean saveScanPos, List<Alternative> alternatives) implements ScanStep {
    }

    /**
     * One alternative of a {@link Choice}.
     *
     * @param call     what scans it
     * @param semantic the code of the semantic condition that guards it, empty when there is none
     * @param last     whether it is the last alternative
     */
    record Alternative(ScanCall call, CodeText semantic, boolean last) {
    }

    /** Scans {@code call} as often as it matches: the tail of {@code (…)*} and {@code (…)+}. */
    record ScanLoop(ScanCall call) implements ScanStep {
    }

    /** Scans {@code call} if it matches: {@code […]}. */
    record OptionalScan(ScanCall call) implements ScanStep {
    }
}
