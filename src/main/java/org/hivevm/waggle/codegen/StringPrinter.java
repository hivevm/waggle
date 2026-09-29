// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

import org.hivevm.source.LinePrinter;

/**
 * Collects what a target's syntax object writes, for a piece of the output model that is a string.
 *
 * <p>A few spellings are a short run of statements rather than one — the labels of a switch arm,
 * the call that consumes a token — and the syntax objects write them through a
 * {@link LinePrinter}. This turns that into the string the model holds; it keeps no indentation,
 * because what it collects sits on one line or carries its own.
 */
final class StringPrinter implements LinePrinter {

    private final StringBuilder text = new StringBuilder();

    String text() {
        return this.text.toString();
    }

    @Override
    public void print(String line) {
        this.text.append(line);
    }

    @Override
    public void println() {
        this.text.append('\n');
    }

    @Override
    public LinePrinter indent() {
        return this;
    }

    @Override
    public LinePrinter outdent() {
        return this;
    }
}
