// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.parser.Token;

public abstract class ParserGenerator extends CodeGenerator<ParserData> {

  protected static final String LOOKAHEAD_NEEDED = "LOOKAHEAD_NEEDED";
  protected static final String JJ2_INDEX        = "JJ2_INDEX";
  protected static final String JJ2_OFFSET       = "JJ2_OFFSET";
  protected static final String MASK_INDEX       = "MASK_INDEX";
  protected static final String TOKEN_COUNT      = "TOKEN_COUNT";
  protected static final String TOKEN_MASKS      = "TOKEN_MASKS";
  protected static final String JJPARSER_USE_AST = "USE_AST";


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

  protected final String getTrailingComments(Token t) {
    return (t.next == null) ? "" : getLeadingComments(t.next);
  }
}
