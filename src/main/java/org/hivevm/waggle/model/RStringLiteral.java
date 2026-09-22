// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/jjtree/ASTBNF.java, org/javacc/parser/RStringLiteral.java

package org.hivevm.waggle.model;

/**
 * Describes string literals.
 */
public final class RStringLiteral extends RExpression {

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
