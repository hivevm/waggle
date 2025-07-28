// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

import java.util.ArrayList;
import java.util.List;
import org.hivevm.cc.parser.RegExprSpec;
import org.hivevm.cc.parser.Token;

/**
 * Describes the various regular expression productions.
 */

public class TokenProduction extends Production {

  /**
   * Definitions of constants that identify the kind of regular expression production this is.
   */
  public enum Kind {
    TOKEN,
    SKIP,
    MORE,
    SPECIAL
  }

  /**
   * The states in which this regular expression production exists. If this array is null, then
   * "<*>" has been specified and this regular expression exists in all states. However, this null
   * value is replaced by a String array that includes all lexical state names during the
   * semanticization phase.
   */
  private String[] lexStates;

  /**
   * The kind of this token production - TOKEN, SKIP, MORE, or SPECIAL.
   */
  private Kind kind;

  /**
   * The list of regular expression specifications that comprise this production. Each entry is a
   * "RegExprSpec".
   */
  private final List<RegExprSpec> respecs = new ArrayList<>();

  /**
   * This is true if this corresponds to a production that actually appears in the input grammar.
   * Otherwise (if this is created to describe a regular expression that is part of the BNF) this is
   * set to false.
   */
  private boolean isExplicit = true;

  /**
   * This is true if case is to be ignored within the regular expressions of this token production.
   */
  private boolean ignoreCase = false;

  /**
   * The first and last tokens from the input stream that represent this production.
   */
  private Token firstToken;

  public TokenProduction() {
  }

  public final Kind getKind() {
    return this.kind;
  }

  public final void setKind(Kind kind) {
    this.kind = kind;
  }

  public final List<RegExprSpec> getRespecs() {
    return this.respecs;
  }

  public final boolean isExplicit() {
    return this.isExplicit;
  }

  public final void setExplicit(boolean isExplicit) {
    this.isExplicit = isExplicit;
  }

  public final boolean isIgnoreCase() {
    return this.ignoreCase;
  }

  public final void setIgnoreCase(boolean ignoreCase) {
    this.ignoreCase = ignoreCase;
  }

  public final Token getFirstToken() {
    return this.firstToken;
  }

  public final void setFirstToken(Token firstToken) {
    this.firstToken = firstToken;
  }

  public final String[] getLexStates() {
    return this.lexStates;
  }

  public final void setLexStates(String[] lexstates) {
    this.lexStates = lexstates;
  }

  public final void setLexState(String lexstate, int index) {
    this.lexStates[index] = lexstate;
  }
}
