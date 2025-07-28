// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

/**
 * Describes zero-or-more expansions (e.g., foo*).
 */
public class ZeroOrMore extends Expansion {

  private final Expansion expansion;

  public ZeroOrMore(Expansion expansion) {
    this.expansion = expansion;
    this.expansion.setParent(this);
  }

  public final Expansion getExpansion() {
    return this.expansion;
  }
}
