// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/Sequence.java

package org.hivevm.waggle.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes expansions that are sequences of expansion units. (c1 c2 ...)
 */
public final class Sequence extends Expansion {

    // The list of units in this expansion sequence. Each List component will narrow to Expansion.
    private final List<Expansion> units;

    public Sequence() {
        this.units = new ArrayList<>();
    }

    public final List<Expansion> getUnits() {
        return this.units;
    }
}
