// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

import java.util.ArrayList;
import java.util.List;
import org.hivevm.cc.parser.Token;

/**
 * Describes regular expressions.
 */
public abstract class RExpression extends RegularExpression {

  // The label of the regular expression (if any). If no label is present, this is set to "".
  private String label = "";

  /**
   * The ordinal value assigned to the regular expression. It is used for internal processing and
   * passing information between the parser and the lexical analyzer.
   */
  private int ordinal;

  /**
   * The LHS to which the token value of the regular expression is assigned. In case there is no
   * LHS, then the list remains empty.
   */
  private List<Token> lhsTokens = new ArrayList<>();

  /**
   * We now allow qualified access to token members. Store it here.
   */
  private Token rhsToken;

  /**
   * This flag is set if the regular expression has a label prefixed with the # symbol - this
   * indicates that the purpose of the regular expression is solely for defining other regular
   * expressions.
   */
  private boolean private_rexp = false;

  /**
   * If this is a top-level regular expression (nested directly within a TokenProduction), then this
   * field point to that TokenProduction object.
   */
  private TokenProduction tpContext = null;

  /**
   * The following variable is used to maintain state information for the loop determination
   * algorithm: It is initialized to 0, and set to -1 if this node has been visited in a pre-order
   * walk, and then it is set to 1 if the pre-order walk of the whole graph from this node has been
   * traversed. i.e., -1 indicates partially processed, and 1 indicates fully processed.
   */
  private int walkStatus = 0;

  public final String getLabel() {
    return this.label;
  }

  public final void setLabel(String label) {
    this.label = label;
  }

  public boolean CanMatchAnyChar() {
    return false;
  }

  public final Token getRhsToken() {
    return this.rhsToken;
  }

  public final void setRhsToken(Token token) {
    this.rhsToken = token;
  }

  public final List<Token> getLhsTokens() {
    return this.lhsTokens;
  }

  public final void setLhsTokens(List<Token> lhsTokens) {
    this.lhsTokens = lhsTokens;
  }

  public final boolean isPrivateExp() {
    return this.private_rexp;
  }

  public final void setPrivateRegExp(boolean private_rexp) {
    this.private_rexp = private_rexp;
  }

  public final int getOrdinal() {
    return this.ordinal;
  }

  public final void setOrdinal(int ordinal) {
    this.ordinal = ordinal;
  }

  public final int getWalkStatus() {
    return this.walkStatus;
  }

  public final void setWalkStatus(int walkStatus) {
    this.walkStatus = walkStatus;
  }

  public final TokenProduction getTpContext() {
    return this.tpContext;
  }

  public final void setTpContext(TokenProduction tpContext) {
    this.tpContext = tpContext;
  }

  public abstract <R, D> R accept(RegularExpressionVisitor<R, D> visitor, D data);
}
