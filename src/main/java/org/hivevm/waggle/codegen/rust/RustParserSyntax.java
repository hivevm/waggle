// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.rust;

import org.hivevm.waggle.analysis.TokenRef;

import java.util.Locale;
import java.util.regex.Pattern;
import org.hivevm.waggle.codegen.ParserSyntax;

/**
 * How Rust spells the parser (ADR-0021, ADR-0030). The parser is a struct, so every field and every
 * routine is reached through {@code self}; a production returns a {@code Result} and passes a
 * failure on with {@code ?}; and a lookahead routine ends in a bare expression rather than a
 * statement. The templates write all of that; what is left here are the names.
 *
 * <p>Java leaves a decided lookahead by throwing LookaheadSuccess through every routine. A Rust
 * routine returns true with {@code jj_ls} set instead, so wherever a routine's failure is not
 * simply passed on — trying the next alternative, ending a loop — it checks that flag first.
 */
final class RustParserSyntax implements ParserSyntax {

    /** A token constant that is a Rust keyword is a raw identifier. */
    @Override
    public String tokenName(String name) {
        return RustIdentifier.of(name);
    }

    @Override
    public String tokenRef(TokenRef token) {
        return (token.name() == null) ? Integer.toString(token.ordinal())
                : RustIdentifier.of(token.name());
    }

    /** A keyword label is a raw identifier here as everywhere else. */
    @Override
    public String scanTokenCall(TokenRef token) {
        return "jj_scan_token(" + ((token.name() == null) ? Integer.toString(token.ordinal())
                : RustIdentifier.of(token.name())) + ")";
    }

    /** How a call to another lookahead routine is written where it is tested. */
    @Override
    public String callRef(String call) {
        return "self." + call;
    }

    @Override
    public String productionName(String name) {
        return RustIdentifier.of(RustParserSyntax.toSnakeCase(name));
    }

    private static final Pattern SNAKE_ACRONYM = Pattern.compile("([A-Z])(?=[A-Z])");
    private static final Pattern SNAKE_BOUNDARY = Pattern.compile("([a-z])([A-Z])");

    /** Rust names are snake_case; this is the one rule that turns a grammar name into one. */
    static String toSnakeCase(String name) {
        var withAcronyms = RustParserSyntax.SNAKE_ACRONYM.matcher(name).replaceAll("$1_");
        return RustParserSyntax.SNAKE_BOUNDARY.matcher(withAcronyms).replaceAll("$1_$2")
                .toLowerCase(Locale.ROOT);
    }
}
