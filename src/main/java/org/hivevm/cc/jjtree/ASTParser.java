// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

/**
 * The {@link ASTParser} class.
 */
abstract class ASTParser {

  protected final void setParserName(int index) {
    JJTreeGlobals.parserName = getToken(index).image;
  }

  protected final void addProduction(ASTProduction prod) {
    JJTreeGlobals.productions.put(prod.name, prod);
  }

  protected final void normalize() {
    getOptions().normalize();
  }

  protected final String setInputOption(Token o, Token v) {
    String image = v.image;
    switch (v.kind) {
      case JJTreeParserConstants.INTEGER_LITERAL:
        getOptions().setInputOption(o, v, o.image, Integer.valueOf(image));
        break;

      case JJTreeParserConstants.TRUE:
      case JJTreeParserConstants.FALSE:
        getOptions().setInputOption(o, v, o.image, Boolean.valueOf(image));
        break;

      default:
        image = TokenUtils.remove_escapes_and_quotes(v, image);
        getOptions().setInputOption(o, v, o.image, image);
        break;
    }
    return image;
  }

  protected abstract Token getNextToken();

  protected abstract Token getToken(int index);

  protected void jjtreeOpenNodeScope(Node n) {}

  protected void jjtreeCloseNodeScope(Node n) {}

  protected JJTreeOptions getOptions() {
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
