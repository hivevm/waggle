// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

import java.util.ArrayList;
import java.util.List;
import org.hivevm.cc.parser.Token;

/**
 * Describes JavaCC productions.
 */
public class NormalProduction extends Expansion {

  // The NonTerminal nodes which refer to this production.
  private final List<Object> parents = new ArrayList<>();

  // The name of the non-terminal of this production.
  private String lhs;

  // The tokens that make up the return type of this production.
  private final List<Token> return_type_tokens = new ArrayList<>();

  // The tokens that make up the parameters of this production.
  private final List<Token> parameter_list_tokens = new ArrayList<>();

  /**
   * Each entry in this list is a list of tokens that represents an exception in the throws list of
   * this production. This list does not include ParseException which is always thrown.
   */
  private List<List<Token>> throws_list = new ArrayList<>();

  // The RHS of this production. Not used for JavaCodeProduction.
  private Expansion expansion;

  // This boolean flag is true if this production can expand to empty.
  private boolean emptyPossible = false;

  /**
   * A list of all non-terminals that this one can expand to without having to consume any tokens.
   * Also an index that shows how many pointers exist.
   */
  private NormalProduction[] leftExpansions = new NormalProduction[10];
  public  int                leIndex        = 0;

  /**
   * The following variable is used to maintain state information for the left-recursion
   * determination algorithm: It is initialized to 0, and set to -1 if this node has been visited in
   * a pre-order walk, and then it is set to 1 if the pre-order walk of the whole graph from this
   * node has been traversed. i.e., -1 indicates partially processed, and 1 indicates fully
   * processed.
   */
  private int walkStatus = 0;

  // The first and last tokens from the input stream that represent this production.
  private Token lastToken;
  private Token firstToken;

  protected NormalProduction() {}
  /**
   * @return the parents
   */
  public List<? super Object> getParents() {
    return this.parents;
  }

  /**
   * @param lhs the lhs to set
   */
  public void setLhs(String lhs) {
    this.lhs = lhs;
  }

  /**
   * @return the lhs
   */
  public String getLhs() {
    return this.lhs;
  }

  /**
   * @return the return_type_tokens
   */
  public List<Token> getReturnTypeTokens() {
    return this.return_type_tokens;
  }

  /**
   * @return the parameter_list_tokens
   */
  public List<Token> getParameterListTokens() {
    return this.parameter_list_tokens;
  }

  /**
   * @param throws_list the throws_list to set
   */
  public void setThrowsList(List<List<Token>> throws_list) {
    this.throws_list = throws_list;
  }

  /**
   * @return the throws_list
   */
  public List<List<Token>> getThrowsList() {
    return this.throws_list;
  }

  /**
   * @param expansion the expansion to set
   */
  public void setExpansion(Expansion expansion) {
    this.expansion = expansion;
  }

  /**
   * @return the expansion
   */
  public Expansion getExpansion() {
    return this.expansion;
  }

  /**
   * @param emptyPossible the emptyPossible to set
   */
  public boolean setEmptyPossible(boolean emptyPossible) {
    this.emptyPossible = emptyPossible;
    return emptyPossible;
  }

  /**
   * @return the emptyPossible
   */
  public boolean isEmptyPossible() {
    return this.emptyPossible;
  }

  /**
   * @param leftExpansions the leftExpansions to set
   */
  public void setLeftExpansions(NormalProduction[] leftExpansions) {
    this.leftExpansions = leftExpansions;
  }

  /**
   * @return the leftExpansions
   */
  public NormalProduction[] getLeftExpansions() {
    return this.leftExpansions;
  }

  /**
   * @param walkStatus the walkStatus to set
   */
  public void setWalkStatus(int walkStatus) {
    this.walkStatus = walkStatus;
  }

  /**
   * @return the walkStatus
   */
  public int getWalkStatus() {
    return this.walkStatus;
  }

  /**
   * @param firstToken the firstToken to set
   */
  public Token setFirstToken(Token firstToken) {
    this.firstToken = firstToken;
    return firstToken;
  }

  /**
   * @return the firstToken
   */
  public Token getFirstToken() {
    return this.firstToken;
  }

  /**
   * @param lastToken the lastToken to set
   */
  public void setLastToken(Token lastToken) {
    this.lastToken = lastToken;
  }

  /**
   * @return the lastToken
   */
  public Token getLastToken() {
    return this.lastToken;
  }
}
