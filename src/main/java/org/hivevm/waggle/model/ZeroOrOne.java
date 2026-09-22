// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/ZeroOrOne.java

package org.hivevm.waggle.model;

/**
 * Describes zero-or-one expansions (e.g., [foo], foo?).
 */
public final class ZeroOrOne extends Expansion {

    private final Expansion expansion;

    public ZeroOrOne(Expansion e) {
        this.expansion = e;
        e.setParent(this);
    }

    public final Expansion getExpansion() {
        return this.expansion;
    }
}
