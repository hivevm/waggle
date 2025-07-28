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
   * The list of choices of this expansion unit. Each List component will narrow to ExpansionUnit.
   */
  private final List<Expansion> choices;

  public Choice() {
    this.choices = new ArrayList<>();
  }

  public List<Expansion> getChoices() {
    return this.choices;
  }
}
