// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.model;

import org.hivevm.waggle.grammar.Token;

import java.util.List;

/**
 * A run of grammar code a generator copies verbatim, as the grammar wrote it.
 *
 * <p>It is what a plan hands a generator in place of the expansion the code was written in: the
 * generators used to take the {@code RExpression}, {@code NonTerminal}, {@code Action} and
 * {@code Lookahead} themselves and read the tokens off them, which put the whole construction model
 * within reach of an emitter (ADR-0029).
 */
public record CodeText(List<Token> tokens) {

    /** No code at all: an expansion that assigns nothing, takes no arguments or has no condition. */
    public static final CodeText NONE = new CodeText(List.of());

    public CodeText {
        tokens = List.copyOf(tokens);
    }

    public boolean isEmpty() {
        return this.tokens.isEmpty();
    }

    /** The token the run starts at, which fixes the column the rest is laid out against. */
    public Token first() {
        return this.tokens.getFirst();
    }

    /** The token the run ends at, which the comments after it follow. */
    public Token last() {
        return this.tokens.getLast();
    }
}
