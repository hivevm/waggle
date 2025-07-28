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

  @Override
  public final <R, D> R accept(RegularExpressionVisitor<R, D> visitor, D data) {
    return visitor.visit(this, data);
  }
}
