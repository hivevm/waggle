// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes BNF productions.
 */

public class BNFProduction extends NormalProduction {

  /**
   * The declarations of this production.
   */
  private final List<Token> declaration_tokens = new ArrayList<>();
  private final List<Token> end_tokens         = new ArrayList<>();

  /**
   * @return the declaration_tokens
   */
  public List<Token> getDeclarationTokens() {
    return this.declaration_tokens;
  }

  public List<Token> getDeclarationEndTokens() {
    return this.end_tokens;
  }
}
