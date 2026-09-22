// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.grammar;

import org.hivevm.waggle.api.WaggleOptions;

/**
 * The {@link GrammarParser} class.
 */
public class GrammarParser extends Parser {

    private final WaggleOptions options;

    public GrammarParser(Provider stream, WaggleOptions options) {
        super(stream);
        this.options = options;
    }

    /**
     * Gets the {@link #options}.
     */
    @Override
    public final WaggleOptions getOptions() {
        return this.options;
    }
}
