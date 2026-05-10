// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes expansions where one of many choices is taken (c1|c2|...).
 */
public class Choice extends Expansion {

    /**
     * The list of choices of this expansion unit. Each List component will narrow to
     * ExpansionUnit.
     */
    private final List<Expansion> choices;

    public Choice() {
        this.choices = new ArrayList<>();
    }

    public List<Expansion> getChoices() {
        return this.choices;
    }

    /**
     * Collapses a list of alternative expansions into a single node: the sole child is returned
     * unchanged for a single alternative, otherwise a {@link Choice} located at the first child and
     * owning every child (parent wired) is created.
     */
    public static Expansion of(List<Expansion> choices) {
        if (choices.size() == 1) {
            return choices.get(0);
        }
        Choice choice = new Choice();
        choice.setLocation(choices.get(0));
        for (Expansion c : choices) {
            choice.getChoices().add(c);
            c.setParent(choice);
        }
        return choice;
    }
}
