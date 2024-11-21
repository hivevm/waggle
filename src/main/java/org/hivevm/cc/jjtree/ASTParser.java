// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

import java.text.ParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.generator.TreeContext;

/**
 * The {@link ASTParser} class.
 */
abstract class ASTParser {

  private final Set<String>                jjtreeOptions;
  private final Map<String, ASTProduction> productions;

  protected ASTParser() {
    this.productions = new HashMap<>();
    this.jjtreeOptions = new HashSet<>();
    this.jjtreeOptions.add(HiveCC.JJTREE_MULTI);
    this.jjtreeOptions.add(HiveCC.JJTREE_NODE_PREFIX);
    this.jjtreeOptions.add(HiveCC.JJTREE_NODE_EXTENDS);
    this.jjtreeOptions.add(HiveCC.JJTREE_NODE_CUSTOM);
    this.jjtreeOptions.add(HiveCC.JJTREE_NODE_CLASS);
    this.jjtreeOptions.add(HiveCC.JJTREE_NODE_DEFAULT_VOID);
    this.jjtreeOptions.add(HiveCC.JJTREE_OUTPUT_FILE);
    this.jjtreeOptions.add(HiveCC.JJTREE_NODE_SCOPE_HOOK);
    this.jjtreeOptions.add(HiveCC.JJTREE_TRACK_TOKENS);
    this.jjtreeOptions.add(HiveCC.JJTREE_NODE_FACTORY);
    this.jjtreeOptions.add(HiveCC.JJTREE_BUILD_NODE_FILES);
    this.jjtreeOptions.add(HiveCC.JJTREE_VISITOR);
    this.jjtreeOptions.add(HiveCC.JJTREE_VISITOR_EXCEPTION);
    this.jjtreeOptions.add(HiveCC.JJTREE_VISITOR_DATA_TYPE);
    this.jjtreeOptions.add(HiveCC.JJTREE_VISITOR_RETURN_TYPE); // TODO Auto-generated
    // constructor stub
  }

  protected final void setParserName(int index) {
    getOptions().setParser(getToken(index).image);
  }

  protected final void addProduction(ASTProduction prod) {
    this.productions.put(prod.name, prod);
  }

  public final ASTProduction getProduction(String name) {
    return this.productions.get(name);
  }

  public boolean isOptionJJTreeOnly(String optionName) {
    return this.jjtreeOptions.contains(optionName.toUpperCase());
  }

  protected final String setInputOption(Token o, Token v) {
    String image = v.image;
    switch (v.kind) {
      case JJTreeParserConstants.INTEGER_LITERAL:
        getOptions().setOption(o, v, o.image, Integer.valueOf(image));
        break;

      case JJTreeParserConstants.TRUE:
      case JJTreeParserConstants.FALSE:
        getOptions().setOption(o, v, o.image, Boolean.valueOf(image));
        break;

      default:
        image = TokenUtils.remove_escapes_and_quotes(v, image);
        getOptions().setOption(o, v, o.image, image);
        break;
    }
    return image;
  }

  protected abstract Token getNextToken();

  protected abstract Token getToken(int index);

  protected void jjtreeOpenNodeScope(Node n) throws ParseException {}

  protected void jjtreeCloseNodeScope(Node n) throws ParseException {}

  protected TreeContext getOptions() {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns true if the next token is not in the FOLLOW list of "expansion". It is used to decide
   * when the end of an "expansion" has been reached.
   */
  protected boolean notTailOfExpansionUnit() {
    Token t;
    t = getToken(1);
    if ((t.kind == JJTreeParserConstants.BIT_OR) || (t.kind == JJTreeParserConstants.COMMA)
        || (t.kind == JJTreeParserConstants.RPAREN) || (t.kind == JJTreeParserConstants.RBRACE)
        || (t.kind == JJTreeParserConstants.RBRACKET)) {
      return false;
    }
    return true;
  }

  protected boolean checkEmptyLA(boolean emptyLA, Token token) {
    return !emptyLA && (token.kind != JJTreeParserConstants.RPAREN);
  }

  protected boolean checkEmptyLAAndCommandEnd(boolean emptyLA, boolean commaAtEnd, Token token) {
    return !emptyLA && !commaAtEnd && (getToken(1).kind != JJTreeParserConstants.RPAREN);
  }

  protected boolean checkEmptyLAOrCommandEnd(boolean emptyLA, boolean commaAtEnd) {
    return emptyLA || commaAtEnd;
  }

  protected boolean checkEmpty(Token token) {
    return (token.kind != JJTreeParserConstants.RPAREN) && (token.kind != JJTreeParserConstants.LBRACE);
  }
}
