// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import org.hivevm.waggle.model.Action;
import org.hivevm.waggle.model.NormalProduction;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.TokenProduction;
import org.hivevm.waggle.parser.Options;

/**
 * The {@link ParserRequest} class.
 */
public interface ParserRequest {

    Options options();

    String getParserName();

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
