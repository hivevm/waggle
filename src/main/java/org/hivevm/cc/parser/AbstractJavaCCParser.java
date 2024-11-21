// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.parser;

import java.util.ArrayList;
import java.util.List;

import org.hivevm.cc.HiveCCOptions;
import org.hivevm.cc.jjtree.JJTreeParserConstants;

/**
 * Utilities.
 */
abstract class AbstractJavaCCParser implements JavaCCParserConstants {

  private JavaCCData data;
  private int        nextFreeLexState;

  /**
   * This int variable is incremented while parsing local lookaheads. Hence it keeps track of
   * *syntactic* lookahead nesting. This is used to provide warnings when actions and nested
   * lookaheads are used in syntactic lookahead productions. This is to prevent typos such as
   * leaving out the comma in LOOKAHEAD( foo(), {check()} ).
   */
  protected int      inLocalLA;

  /**
   * Constructs an instance of {@link AbstractJavaCCParser}.
   *
   */
  protected AbstractJavaCCParser() {
    this.nextFreeLexState = 1;
    this.inLocalLA = 0;
  }

  /**
   * Gets the options.
   */
  public HiveCCOptions getOptions() {
    throw new UnsupportedOperationException();
  }

  public void initialize(JavaCCData data) {
    this.data = data;
  }

  protected void setParserName(int index) {
    this.data.setParser(getToken(index).image);
  }

  protected void addproduction(NormalProduction p) {
    this.data.addNormalProduction(p);
  }

  protected void production_addexpansion(BNFProduction p, Expansion e) {
    e.parent = p;
    p.setExpansion(e);
  }

  protected void addregexpr(TokenProduction p) {
    this.data.addTokenProduction(p);
    if (p.getLexStates() == null) {
      return;
    }
    for (int i = 0; i < p.getLexStates().length; i++) {
      for (int j = 0; j < i; j++) {
        if (p.getLexStates()[i].equals(p.getLexStates()[j])) {
          JavaCCErrors.parse_error(p, "Multiple occurrence of \"" + p.getLexStates()[i] + "\" in lexical state list.");
        }
      }
      if (this.data.hasLexState(p.getLexStates()[i])) {
        this.data.setLexState(p.getLexStates()[i], this.nextFreeLexState++);
      }
    }
  }

  protected void add_inline_regexpr(RegularExpression r) {
    if (!(r instanceof REndOfFile)) {
      TokenProduction p = new TokenProduction();
      p.setExplicit(false);
      p.setLexStates(new String[] { "DEFAULT" });
      p.setKind(TokenProduction.Kind.TOKEN);
      RegExprSpec res = new RegExprSpec();
      res.rexp = r;
      res.rexp.setTpContext(p);
      res.act = new Action();
      res.nextState = null;
      res.nsTok = null;
      p.getRespecs().add(res);
      this.data.addTokenProduction(p);
    }
  }

  private static boolean hexchar(char ch) {
    if (((ch >= '0') && (ch <= '9')) || ((ch >= 'A') && (ch <= 'F'))) {
      return true;
    }
    if ((ch >= 'a') && (ch <= 'f')) {
      return true;
    }
    return false;
  }

  private static int hexval(char ch) {
    if ((ch >= '0') && (ch <= '9')) {
      return (ch) - ('0');
    }
    if ((ch >= 'A') && (ch <= 'F')) {
      return ((ch) - ('A')) + 10;
    }
    return ((ch) - ('a')) + 10;
  }

  protected String remove_escapes_and_quotes(Token t, String str) {
    String retval = "";
    int index = 1;
    char ch, ch1;
    int ordinal;
    while (index < (str.length() - 1)) {
      if (str.charAt(index) != '\\') {
        retval += str.charAt(index);
        index++;
        continue;
      }
      index++;
      ch = str.charAt(index);
      if (ch == 'b') {
        retval += '\b';
        index++;
        continue;
      }
      if (ch == 't') {
        retval += '\t';
        index++;
        continue;
      }
      if (ch == 'n') {
        retval += '\n';
        index++;
        continue;
      }
      if (ch == 'f') {
        retval += '\f';
        index++;
        continue;
      }
      if (ch == 'r') {
        retval += '\r';
        index++;
        continue;
      }
      if (ch == '"') {
        retval += '\"';
        index++;
        continue;
      }
      if (ch == '\'') {
        retval += '\'';
        index++;
        continue;
      }
      if (ch == '\\') {
        retval += '\\';
        index++;
        continue;
      }
      if ((ch >= '0') && (ch <= '7')) {
        ordinal = (ch) - ('0');
        index++;
        ch1 = str.charAt(index);
        if ((ch1 >= '0') && (ch1 <= '7')) {
          ordinal = ((ordinal * 8) + (ch1)) - ('0');
          index++;
          ch1 = str.charAt(index);
          if ((ch <= '3') && (ch1 >= '0') && (ch1 <= '7')) {
            ordinal = ((ordinal * 8) + (ch1)) - ('0');
            index++;
          }
        }
        retval += (char) ordinal;
        continue;
      }
      if (ch == 'u') {
        index++;
        ch = str.charAt(index);
        if (AbstractJavaCCParser.hexchar(ch)) {
          ordinal = AbstractJavaCCParser.hexval(ch);
          index++;
          ch = str.charAt(index);
          if (AbstractJavaCCParser.hexchar(ch)) {
            ordinal = (ordinal * 16) + AbstractJavaCCParser.hexval(ch);
            index++;
            ch = str.charAt(index);
            if (AbstractJavaCCParser.hexchar(ch)) {
              ordinal = (ordinal * 16) + AbstractJavaCCParser.hexval(ch);
              index++;
              ch = str.charAt(index);
              if (AbstractJavaCCParser.hexchar(ch)) {
                ordinal = (ordinal * 16) + AbstractJavaCCParser.hexval(ch);
                index++;
                continue;
              }
            }
          }
        }
        JavaCCErrors.parse_error(t, "Encountered non-hex character '" + ch + "' at position " + index + " of string "
            + "- Unicode escape must have 4 hex digits after it.");
        return retval;
      }
      JavaCCErrors.parse_error(t, "Illegal escape sequence '\\" + ch + "' at position " + index + " of string.");
      return retval;
    }
    return retval;
  }

  protected char character_descriptor_assign(Token t, String s) {
    if (s.length() != 1) {
      JavaCCErrors.parse_error(t, "String in character list may contain only one character.");
      return ' ';
    } else {
      return s.charAt(0);
    }
  }

  protected char character_descriptor_assign(Token t, String s, String left) {
    if (s.length() != 1) {
      JavaCCErrors.parse_error(t, "String in character list may contain only one character.");
      return ' ';
    } else if ((left.charAt(0)) > (s.charAt(0))) {
      JavaCCErrors.parse_error(t, "Right end of character range \'" + s
          + "\' has a lower ordinal value than the left end of character range \'" + left + "\'.");
      return left.charAt(0);
    } else {
      return s.charAt(0);
    }
  }

  /*
   * Returns true if the next token is not in the FOLLOW list of "expansion". It is used to decide
   * when the end of an "expansion" has been reached.
   */
  protected boolean notTailOfExpansionUnit() {
    Token t;
    t = getToken(1);
    if ((t.kind == JavaCCParserConstants.BIT_OR) || (t.kind == JavaCCParserConstants.COMMA)
        || (t.kind == JavaCCParserConstants.RPAREN) || (t.kind == JavaCCParserConstants.RBRACE)
        || (t.kind == JavaCCParserConstants.RBRACKET)) {
      return false;
    }
    return true;
  }

  protected abstract Token getNextToken();

  protected abstract Token getToken(int index);

  protected void Arguments() throws ParseException {
    Arguments(new ArrayList<>());
  }

  protected void Statement() throws ParseException {
    Statement(new ArrayList<>());
  }

  protected void Expression() throws ParseException {
    Expression(new ArrayList<>());
  }

  protected void Name() throws ParseException {
    Name(new ArrayList<>());
  }

  protected void TypeArguments() throws ParseException {
    TypeArguments(new ArrayList<>());
  }

  protected abstract void Arguments(List<Token> tokens) throws ParseException;

  protected abstract void Statement(List<Token> tokens) throws ParseException;

  protected abstract void Expression(List<Token> tokens) throws ParseException;

  protected abstract void FormalParameters(List<Token> tokens) throws ParseException;

  protected abstract void Name(List<Token> tokens) throws ParseException;

  protected abstract void ResultType(List<Token> tokens) throws ParseException;

  protected abstract void TypeArguments(List<Token> tokens) throws ParseException;

  protected boolean checkEmptyLA(boolean emptyLA, Token token) {
    return !emptyLA && (token.kind != JavaCCParserConstants.RPAREN);
  }

  protected boolean checkEmptyLAAndCommandEnd(boolean emptyLA, boolean commaAtEnd, Token token) {
    return !emptyLA && !commaAtEnd && (getToken(1).kind != JavaCCParserConstants.RPAREN);
  }

  protected boolean checkEmptyLAOrCommandEnd(boolean emptyLA, boolean commaAtEnd) {
    return emptyLA || commaAtEnd;
  }

  protected boolean checkEmpty(Token token) {
    return (token.kind != JavaCCParserConstants.RPAREN) && (token.kind != JavaCCParserConstants.LBRACE);
  }

  protected final void setInputOption(Token o, Token v) {
    switch (v.kind) {
      case JJTreeParserConstants.INTEGER_LITERAL:
        getOptions().setOption(o, v, o.image, Integer.valueOf(v.image));
        break;

      case JJTreeParserConstants.TRUE:
      case JJTreeParserConstants.FALSE:
        getOptions().setOption(o, v, o.image, Boolean.valueOf(v.image));
        break;

      default:
        getOptions().setOption(o, v, o.image, remove_escapes_and_quotes(v, v.image));
        break;
    }
  }
}
