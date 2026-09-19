// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.parser;

import org.hivevm.waggle.WaggleOptions;

/**
 * The {@link JavaCCParserDefault} class.
 */
public class JavaCCParserDefault extends Parser {

    private final WaggleOptions options;

    public JavaCCParserDefault(Provider stream, WaggleOptions options) {
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
