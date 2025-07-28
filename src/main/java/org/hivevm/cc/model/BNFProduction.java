// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

import java.util.ArrayList;
import java.util.List;
import org.hivevm.cc.parser.Token;

/**
 * Describes BNF productions.
 */

public class BNFProduction extends NormalProduction {

  private final List<Token> declaration_tokens;
  private final List<Token> end_tokens;

  public BNFProduction() {
    this.declaration_tokens = new ArrayList<>();
    this.end_tokens         = new ArrayList<>();
  }

  public BNFProduction addDeclaration(Token token) {
    this.declaration_tokens.add(token);
    return this;
  }

  public BNFProduction addDeclarationEnd(Token token) {
    this.end_tokens.add(token);
    return this;
  }

  public List<Token> getDeclarationTokens() {
    return this.declaration_tokens;
  }

  public List<Token> getDeclarationEndTokens() {
    return this.end_tokens;
  }
}
