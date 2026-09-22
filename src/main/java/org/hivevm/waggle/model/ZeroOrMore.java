// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/NormalProduction.java, org/javacc/parser/ZeroOrMore.java

package org.hivevm.waggle.model;

/**
 * Describes zero-or-more expansions (e.g., foo*).
 */
public final class ZeroOrMore extends Expansion {

    private final Expansion expansion;

    public ZeroOrMore(Expansion expansion) {
        this.expansion = expansion;
        this.expansion.setParent(this);
    }

    public final Expansion getExpansion() {
        return this.expansion;
    }
}
