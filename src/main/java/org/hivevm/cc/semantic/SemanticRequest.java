// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.semantic;

import java.util.Hashtable;
import java.util.Set;
import org.hivevm.cc.model.Action;
import org.hivevm.cc.model.NormalProduction;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.TokenProduction;

/**
 * The {@link SemanticRequest} class.
 */
public interface SemanticRequest {

  void unsetTokenCount();

  int addTokenCount();

  Set<String> getStateNames();

  Integer getStateIndex(String name);

  Action getActionForEof();

  void setActionForEof(Action action);

  String getNextStateForEof();

  void setNextStateForEof(String state);

  Iterable<TokenProduction> getTokenProductions();

  Iterable<NormalProduction> getNormalProductions();

  NormalProduction getProductionTable(String name);

  NormalProduction setProductionTable(NormalProduction production);

  void addOrderedNamedToken(RExpression token);

  Hashtable<String, Hashtable<String, RExpression>> getSimpleTokenTable(String stateName);

  void setNamesOfToken(RExpression expression);
}
