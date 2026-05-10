// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes regular expressions which are sequences of other regular expressions.
 */

public class RSequence extends RExpression {

    private final List<RExpression> units;

    public RSequence() {
        this.units = new ArrayList<>();
    }

    public final List<RExpression> getUnits() {
        return this.units;
    }

    /**
     * Collapses a list of regular expressions into a single node: the sole child is returned
     * unchanged for a single unit, otherwise an {@link RSequence} located at the first unit and
     * owning every unit (parent and ordinal wired) is created.
     */
    public static RExpression of(List<RExpression> units) {
        if (units.size() == 1) {
            return units.get(0);
        }
        RSequence seq = new RSequence();
        seq.setLocation(units.get(0));
        for (int i = 0; i < units.size(); i++) {
            RExpression u = units.get(i);
            seq.getUnits().add(u);
            u.setParent(seq, i);
        }
        return seq;
    }

    @Override
    public final <R, D> R accept(RegularExpressionVisitor<R, D> visitor, D data) {
        return visitor.visit(this, data);
    }
}
