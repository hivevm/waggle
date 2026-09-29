// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.analysis;

/**
 * A syntactic lookahead's {@code jj_2} routine. Its number is the one rule behind three things the
 * back ends used to derive separately: the routine name {@code _N}, the {@code jj_save} slot
 * {@code N - 1} (recovered with {@code Integer.parseInt(name.substring(1)) - 1}) and the
 * {@code jj_3_N} routine the rescan calls for that slot (ADR-0029).
 *
 * @param number the routine's number, counted from 1 in planning order
 */
public record Jj2Routine(int number) {

    /** The routine's name without its {@code jj_2}/{@code jj_3} prefix: {@code _N}. */
    public String name() {
        return "_" + this.number;
    }

    /** The {@code jj_2_rtns} slot the routine saves its position in. */
    public int saveSlot() {
        return this.number - 1;
    }
}
