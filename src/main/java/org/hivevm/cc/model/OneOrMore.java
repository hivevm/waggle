// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

import org.hivevm.cc.parser.Token;

/**
 * Describes one-or-more expansions (e.g., foo+).
 */
public class OneOrMore extends Expansion {

    private final Expansion expansion;

    public OneOrMore(Expansion e) {
        this.expansion = e;
        this.expansion.setParent(this);
    }

    public final Expansion getExpansion() {
        return this.expansion;
    }
}
