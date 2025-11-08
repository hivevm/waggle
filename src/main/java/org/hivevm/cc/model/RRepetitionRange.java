// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

/**
 * Describes one-or-more regular expressions (<foo+>).
 */
public class RRepetitionRange extends RExpression {

    private final RExpression regexpr;
    private final int         min;
    private final int         max;
    private final boolean     hasMax;

    public RRepetitionRange(RExpression regexpr, int min, int max, boolean hasMax) {
        this.regexpr = regexpr;
        this.min = min;
        this.max = max;
        this.hasMax = hasMax;
    }

    public final int getMin() {
        return this.min;
    }

    public final int getMax() {
        return this.max;
    }

    public final boolean hasMax() {
        return this.hasMax;
    }

    public final RExpression getRegexpr() {
        return this.regexpr;
    }

    @Override
    public final <R, D> R accept(RegularExpressionVisitor<R, D> visitor, D data) {
        return visitor.visit(this, data);
    }
}
