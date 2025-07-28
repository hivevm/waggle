// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;

import org.hivevm.cc.model.Action;
import org.hivevm.cc.model.NormalProduction;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.TokenProduction;
import org.hivevm.cc.parser.Options;

/**
 * The {@link ParserRequest} class.
 */
public interface ParserRequest {

  Options options();

  String getParserName();

  boolean isGenerated();

  boolean ignoreCase();

  int getStateCount();

  int getTokenCount();

  Action getActionForEof();

  String getNextStateForEof();

  String getNameOfToken(int ordinal);

  Iterable<RExpression> getOrderedsTokens();

  Iterable<TokenProduction> getTokenProductions();

  Iterable<NormalProduction> getNormalProductions();

  NormalProduction getProductionTable(String name);
}
