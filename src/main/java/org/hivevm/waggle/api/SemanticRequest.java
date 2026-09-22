// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.api;

import org.hivevm.waggle.diag.Diagnostics;
import org.hivevm.waggle.model.Action;
import org.hivevm.waggle.model.NormalProduction;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.TokenProduction;

import java.util.Map;
import java.util.Set;

/**
 * The {@link SemanticRequest} class.
 */
public interface SemanticRequest {

    Diagnostics diagnostics();

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

    Map<String, Map<String, RExpression>> getSimpleTokenTable(String stateName);

    void setNamesOfToken(RExpression expression);
}
