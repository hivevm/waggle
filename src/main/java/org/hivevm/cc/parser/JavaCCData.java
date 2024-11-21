// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hivevm.cc.ParserRequest;
import org.hivevm.cc.semantic.SemanticRequest;

/**
 * The {@link JavaCCData} class.
 */
public class JavaCCData implements SemanticRequest, ParserRequest {

  private final Options                    options;
  private final boolean                    isGenerated;
  private Action                           actForEof;
  private String                           nextStateForEof;

  /**
   * The name of the parser class (what appears in PARSER_BEGIN and PARSER_END).
   */
  private String                           cu_name;

  /**
   * The total number of distinct tokens. This is therefore one more than the largest assigned token
   * ordinal.
   */
  private int                              tokenCount;

  /**
   * A mapping of lexical state strings to their integer internal representation. Integers are
   * stored as java.lang.Integer's.
   */
  private final Hashtable<String, Integer> lexstate_S2I = new Hashtable<>();

  /**
   * A mapping of the internal integer representations of lexical states to their strings. Integers
   * are stored as java.lang.Integer's.
   */
  private final Hashtable<Integer, String> lexstate_I2S = new Hashtable<>();


  /**
   * A list of all grammar productions - normal and JAVACODE - in the order they appear in the input
   * file. Each entry here will be a subclass of "NormalProduction".
   */
  private final List<NormalProduction>                                                     bnfproductions       =
      new ArrayList<>();

  /**
   * Contains the same entries as "named_tokens_table", but this is an ordered list which is ordered
   * by the order of appearance in the input file.
   */
  private final List<RegularExpression>                                                    ordered_named_tokens =
      new ArrayList<>();

  /**
   * This is a three-level symbol table that contains all simple tokens (those that are defined
   * using a single string (with or without a label). The index to the first level table is a
   * lexical state which maps to a second level hashtable. The index to the second level hashtable
   * is the string of the simple token converted to upper case, and this maps to a third level
   * hashtable. This third level hashtable contains the actual string of the simple token and maps
   * it to its RegularExpression.
   */
  private final Hashtable<String, Hashtable<String, Hashtable<String, RegularExpression>>> simple_tokens_table  =
      new Hashtable<>();

  /**
   * A symbol table of all grammar productions - normal and JAVACODE. The symbol table is indexed by
   * the name of the left hand side non-terminal. Its contents are of type "NormalProduction".
   */
  private final Map<String, NormalProduction>                                              production_table     =
      new HashMap<>();

  /**
   * The list of all TokenProductions from the input file. This list includes implicit
   * TokenProductions that are created for uses of regular expressions within BNF productions.
   */
  private final List<TokenProduction>                                                      rexprlist            =
      new ArrayList<>();

  /**
   * A mapping of ordinal values (represented as objects of type "Integer") to the corresponding
   * labels (of type "String"). An entry exists for an ordinal value only if there is a labeled
   * token corresponding to this entry. If there are multiple labels representing the same ordinal
   * value, then only one label is stored.
   */
  private final Map<Integer, String>                                                       names_of_tokens      =
      new HashMap<>();

  /**
   * Constructs an instance of {@link JavaCCData}.
   */
  public JavaCCData(boolean isGenerated, Options options) {
    this.options = options;
    this.tokenCount = 0;
    this.isGenerated = isGenerated;
    this.lexstate_S2I.put("DEFAULT", 0);
    this.lexstate_I2S.put(0, "DEFAULT");
    this.simple_tokens_table.put("DEFAULT", new Hashtable<>());
  }

  @Override
  public final Options options() {
    return this.options;
  }

  final void setParser(String name) {
    this.cu_name = name;
  }

  final void setLexState(String name, int index) {
    this.lexstate_I2S.put(index, name);
    this.lexstate_S2I.put(name, index);
    this.simple_tokens_table.put(name, new Hashtable<>());
  }

  final void addTokenProduction(TokenProduction p) {
    this.rexprlist.add(p);
  }

  final void addNormalProduction(NormalProduction p) {
    this.bnfproductions.add(p);
  }

  final boolean hasLexState(String name) {
    return (this.lexstate_S2I.get(name) == null);
  }

  @Override
  public final String getParserName() {
    return this.cu_name;
  }

  @Override
  public final boolean isGenerated() {
    return this.isGenerated;
  }

  @Override
  public final boolean ignoreCase() {
    return options().getIgnoreCase();
  }

  @Override
  public final Set<String> getStateNames() {
    return this.lexstate_S2I.keySet();
  }

  @Override
  public final String getStateName(int index) {
    return this.lexstate_I2S.get(index);
  }

  @Override
  public final Integer getStateIndex(String name) {
    return this.lexstate_S2I.get(name);
  }

  @Override
  public final Iterable<NormalProduction> getNormalProductions() {
    return this.bnfproductions;
  }

  @Override
  public final Iterable<TokenProduction> getTokenProductions() {
    return this.rexprlist;
  }

  @Override
  public final NormalProduction getProductionTable(String name) {
    return this.production_table.get(name);
  }

  @Override
  public final NormalProduction setProductionTable(NormalProduction production) {
    return this.production_table.put(production.getLhs(), production);
  }

  @Override
  public final int getStateCount() {
    return this.lexstate_I2S.size();
  }

  @Override
  public final int getTokenCount() {
    return this.tokenCount;
  }

  @Override
  public final void unsetTokenCount() {
    this.tokenCount = 1;
  }

  @Override
  public final int addTokenCount() {
    return this.tokenCount++;
  }

  @Override
  public final Action getActionForEof() {
    return this.actForEof;
  }

  @Override
  public final void setActionForEof(Action action) {
    this.actForEof = action;
  }

  @Override
  public final String getNextStateForEof() {
    return this.nextStateForEof;
  }

  @Override
  public final void setNextStateForEof(String state) {
    this.nextStateForEof = state;
  }

  @Override
  public final Iterable<RegularExpression> getOrderedsTokens() {
    return this.ordered_named_tokens;
  }

  @Override
  public final void addOrderedNamedToken(RegularExpression token) {
    this.ordered_named_tokens.add(token);
  }

  @Override
  public final Hashtable<String, Hashtable<String, RegularExpression>> getSimpleTokenTable(String stateName) {
    return this.simple_tokens_table.get(stateName);
  }

  @Override
  public final String getNameOfToken(int ordinal) {
    return this.names_of_tokens.get(ordinal);
  }

  @Override
  public final void setNamesOfToken(RegularExpression expression) {
    this.names_of_tokens.put(expression.ordinal, expression.getLabel());
  }
}
