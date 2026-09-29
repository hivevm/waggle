// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.cpp;

import org.hivevm.waggle.analysis.TokenRef;
import org.hivevm.waggle.codegen.ParserSyntax;

/**
 * How C++ spells the parser's names (ADR-0021): a token by its bare constant, an unbounded lookahead
 * as {@code INT_MAX}.
 */
final class CppParserSyntax implements ParserSyntax {

    @Override
    public String tokenRef(TokenRef token) {
        return (token.name() == null) ? Integer.toString(token.ordinal()) : token.name();
    }

    @Override
    public String lookaheadAmount(int amount) {
        return (amount == Integer.MAX_VALUE) ? "INT_MAX" : Integer.toString(amount);
    }
}
