// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/RChoice.java

package org.hivevm.waggle.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes regular expressions which are choices from from among included regular expressions.
 */
public final class RChoice extends RExpression {

    /**
     * The list of choices of this regular expression. Each list component will narrow to
     * RegularExpression.
     */
    private final List<RExpression> choices = new ArrayList<>();

    public final List<RExpression> getChoices() {
        return this.choices;
    }

    /**
     * Collapses a list of alternative regular expressions into a single node: the sole child is
     * returned unchanged for a single alternative, otherwise an {@link RChoice} located at the first
     * child and owning every child (parent wired) is created.
     */
    public static RExpression of(List<RExpression> choices) {
        if (choices.size() == 1) {
            return choices.get(0);
        }
        RChoice choice = new RChoice();
        choice.setLocation(choices.get(0));
        for (RExpression c : choices) {
            choice.getChoices().add(c);
            c.setParent(choice);
        }
        return choice;
    }

    @Override
    public final <R, D> R accept(RegularExpressionVisitor<R, D> visitor, D data) {
        return visitor.visit(this, data);
    }
}
