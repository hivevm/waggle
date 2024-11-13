// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.parser.Token;

public abstract class ParserGenerator extends CodeGenerator {

  // Constants used in the following method "buildLookaheadChecker".
  protected enum LookaheadState {
    NOOPENSTM,
    OPENIF,
    OPENSWITCH
  }

  private int labelIndex;

  /**
   * Constructs an instance of {@link ParserGenerator}.
   */
  protected ParserGenerator() {
    this.labelIndex = 0;
  }

  protected final int nextLabelIndex() {
    return ++this.labelIndex;
  }

  protected abstract void start(ParserData data);

  protected final String getTrailingComments(Token t) {
    return (t.next == null) ? "" : getLeadingComments(t.next);
  }
}
