// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

/**
 * Describes regular expressions which are referred to just by their name. This means that a regular
 * expression with this name has been declared earlier.
 */

public class RJustName extends RExpression {

    /**
     * "regexpr" points to the regular expression denoted by the name.
     */
    private RExpression regexpr;

    public RJustName(String image) {
        setLabel(image);
    }

    public final RExpression getRegexpr() {
        return this.regexpr;
    }

    /**
     * Sets the {@link #regexpr}.
     */
    public final void setRegexpr(RExpression regexpr) {
        this.regexpr = regexpr;
    }

    @Override
    public final <R, D> R accept(RegularExpressionVisitor<R, D> visitor, D data) {
        return visitor.visit(this, data);
    }
}
