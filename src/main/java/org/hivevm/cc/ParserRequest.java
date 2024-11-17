// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;

import org.hivevm.cc.parser.Action;
import org.hivevm.cc.parser.NormalProduction;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.parser.RegularExpression;
import org.hivevm.cc.parser.TokenProduction;

/**
 * The {@link ParserRequest} class.
 */
public interface ParserRequest {

  Options options();

  boolean isGenerated();

  String getParserName();

  int getStateCount();

  int getTokenCount();

  Action getActionForEof();

  String getNextStateForEof();

  String getNameOfToken(int ordinal);

  Iterable<RegularExpression> getOrderedsTokens();

  Iterable<TokenProduction> getTokenProductions();

  Iterable<NormalProduction> getNormalProductions();

  NormalProduction getProductionTable(String name);

  default boolean ignoreCase() {
    return options().getIgnoreCase();
  }
}
