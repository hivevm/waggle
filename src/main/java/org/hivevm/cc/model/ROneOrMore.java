// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

/**
 * Describes one-or-more regular expressions (<foo+>).
 */
public class ROneOrMore extends RExpression {

    private final RExpression regexpr;

    public ROneOrMore(RExpression regexpr) {
        this.regexpr = regexpr;
    }

    public final RExpression getRegexpr() {
        return this.regexpr;
    }

    @Override
    public final <R, D> R accept(RegularExpressionVisitor<R, D> visitor, D data) {
        return visitor.visit(this, data);
    }
}
