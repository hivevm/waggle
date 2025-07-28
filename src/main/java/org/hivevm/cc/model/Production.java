// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

import org.hivevm.cc.parser.Token;

public class Production {

  // The line and column number of the construct that corresponds most closely to this node.
  private int line;
  private int column;

  public final int getLine() {
    return this.line;
  }

  public final int getColumn() {
    return this.column;
  }

  /**
   * Sets the position in the source.
   */
  public final void setLocation(Expansion expansion) {
    this.line = expansion.getLine();
    this.column = expansion.getColumn();
  }

  /**
   * Sets the position in the source.
   */
  public final void setLocation(Token token) {
    this.line = token.beginLine;
    this.column = token.beginColumn;
  }
}
