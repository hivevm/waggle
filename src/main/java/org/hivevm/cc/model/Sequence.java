// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

import java.util.ArrayList;
import java.util.List;
import org.hivevm.cc.parser.Token;

/**
 * Describes expansions that are sequences of expansion units. (c1 c2 ...)
 */
public class Sequence extends Expansion {

  // The list of units in this expansion sequence. Each List component will narrow to Expansion.
  private final List<? super Expansion> units;

  public Sequence() {
    this.units = new ArrayList<>();
  }

  public final List<? super Expansion> getUnits() {
    return this.units;
  }
}
