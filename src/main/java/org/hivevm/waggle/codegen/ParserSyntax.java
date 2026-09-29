// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// Copyright 2012 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/ParseEngine.java, org/javacc/parser/OtherFilesGenCPP.java

package org.hivevm.waggle.codegen;

import org.hivevm.waggle.analysis.TokenRef;

/**
 * How one target language spells the names a parser is written with: a token, a production, a
 * lookahead call. The statements around them are template text (ADR-0031 §4).
 *
 * <p>The statements used to be inlined into {@code buildPhase3RoutineRecursive}, which therefore
 * existed three times — 116 lines in Java, 102 in C++, 109 in Rust — walking the same expansions in
 * the same order and differing only in braces and indentation (ADR-0021). What is left is a
 * vocabulary, not behaviour, and an emitter takes one of these rather than inheriting it.
 *
 * <p>The default answers are Java's, which is why the Java back end needs none of its own — the
 * same arrangement {@link TargetSyntax} makes for the lexer (ADR-0017).
 */
public interface ParserSyntax {

    /** Java's spellings: every default below, unchanged. */
    ParserSyntax JAVA = new ParserSyntax() {
    };

    /** A token's name as the parser writes it: its constant, unless the target has to escape it. */
    default String tokenName(String name) {
        return name;
    }

    /** A token as a production or a switch arm names it: by its constant, or by its ordinal. */
    default String tokenName(TokenRef token) {
        return (token.name() == null) ? Integer.toString(token.ordinal()) : tokenName(token.name());
    }

    /**
     * How a token is named in a scan call: by its constant when it has a name, by its ordinal
     * otherwise.
     */
    default String tokenRef(TokenRef token) {
        return (token.name() == null) ? Integer.toString(token.ordinal())
                : "ParserConstants." + token.name();
    }

    /**
     * The call that scans a lookahead which comes down to a single token, as a routine would call
     * it: by the grammar's label, or by ordinal. It is written bare, unlike {@link #tokenRef}.
     */
    default String scanTokenCall(TokenRef token) {
        return "jj_scan_token(" + (token.name() == null ? Integer.toString(token.ordinal())
                : token.name()) + ")";
    }

    /** How a call is written where it is tested; Rust reaches it through the parser itself. */
    default String callRef(String call) {
        return call;
    }

    /** How deep a lookahead scans, as the target spells an unbounded one. */
    default String lookaheadAmount(int amount) {
        return Integer.toString(amount);
    }

    /** A production as a call names it. */
    default String productionName(String name) {
        return name;
    }
}
