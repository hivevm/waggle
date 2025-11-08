// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;


/**
 * The {@link RegularExpressionVisitor} class.
 */
public interface RegularExpressionVisitor<R, D> {

    R visit(RCharacterList expr, D data);

    R visit(RChoice expr, D data);

    R visit(REndOfFile expr, D data);

    R visit(RJustName expr, D data);

    R visit(ROneOrMore expr, D data);

    R visit(RRepetitionRange expr, D data);

    R visit(RSequence expr, D data);

    R visit(RStringLiteral expr, D data);

    R visit(RZeroOrMore expr, D data);

    R visit(RZeroOrOne expr, D data);
}
