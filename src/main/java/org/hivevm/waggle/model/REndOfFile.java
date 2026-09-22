// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/jjtree/ASTCompilationUnit.java, org/javacc/jjtree/ASTBNF.java

package org.hivevm.waggle.model;

/**
 * Describes the EOF regular expression
 */

public final class REndOfFile extends RExpression {

    @Override
    public final <R, D> R accept(RegularExpressionVisitor<R, D> visitor, D data) {
        return visitor.visit(this, data);
    }
}
