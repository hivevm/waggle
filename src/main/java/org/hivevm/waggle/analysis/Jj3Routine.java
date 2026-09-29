// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.analysis;

import java.util.List;

/**
 * A {@code jj_3} routine: what it scans, already cut to the tokens it is ever asked to scan.
 *
 * @param name             the routine's name without its {@code jj_3} prefix
 * @param maxTokens        the largest number of tokens it is asked to scan
 * @param tracedProduction the production whose own expansion it checks, which the
 *                         DEBUG_LOOKAHEAD trace names; {@code null} when it checks a part of one
 * @param body             its statements, in order
 */
public record Jj3Routine(String name, int maxTokens, String tracedProduction,
                         List<ScanStep> body) {
}
