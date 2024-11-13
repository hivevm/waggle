// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.semantic;

import org.hivevm.cc.parser.Action;
import org.hivevm.cc.parser.NormalProduction;
import org.hivevm.cc.parser.RegularExpression;
import org.hivevm.cc.parser.TokenProduction;

import java.util.Hashtable;
import java.util.Set;

/**
 * The {@link SemanticRequest} class.
 */
public interface SemanticRequest {

  void unsetTokenCount();

  int addTokenCount();

  Set<String> getStateNames();

  String getStateName(int index);

  Integer getStateIndex(String name);

  Action getActionForEof();

  void setActionForEof(Action action);

  String getNextStateForEof();

  void setNextStateForEof(String state);

  Iterable<TokenProduction> getTokenProductions();

  Iterable<NormalProduction> getNormalProductions();

  NormalProduction getProductionTable(String name);

  NormalProduction setProductionTable(NormalProduction production);

  void addOrderedNamedToken(RegularExpression token);

  Hashtable<String, Hashtable<String, RegularExpression>> getSimpleTokenTable(String stateName);

  void setNamesOfToken(RegularExpression expression);
}
