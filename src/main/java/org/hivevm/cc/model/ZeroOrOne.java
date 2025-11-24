// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

/**
 * Describes zero-or-one expansions (e.g., [foo], foo?).
 */
public class ZeroOrOne extends Expansion {

    private final Expansion expansion;

    public ZeroOrOne(Expansion e) {
        this.expansion = e;
        e.setParent(this);
    }

    public final Expansion getExpansion() {
        return this.expansion;
    }
}
