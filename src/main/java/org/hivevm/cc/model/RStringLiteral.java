// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

/**
 * Describes string literals.
 */
public class RStringLiteral extends RExpression {

    private final String image;

    public RStringLiteral(String image) {
        this.image = image;
    }

    public final String getImage() {
        return this.image;
    }

    @Override
    public final <R, D> R accept(RegularExpressionVisitor<R, D> visitor, D data) {
        return visitor.visit(this, data);
    }

    @Override
    public String toString() {
        return super.toString() + " - " + this.image;
    }
}
