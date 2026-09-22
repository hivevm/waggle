// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/OneOrMore.java

package org.hivevm.waggle.model;

/**
 * Describes one-or-more expansions (e.g., foo+).
 */
public final class OneOrMore extends Expansion {

    private final Expansion expansion;

    public OneOrMore(Expansion e) {
        this.expansion = e;
        this.expansion.setParent(this);
    }

    public final Expansion getExpansion() {
        return this.expansion;
    }
}
