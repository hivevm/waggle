// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.semantic;

import org.hivevm.cc.model.Production;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.parser.Token;

/**
 * The {@link SemanticContext} class.
 */
class SemanticContext {

    private final Options options;
    private int errorCount = 0;

    public SemanticContext(Options options) {
        this.options = options;
    }

    final boolean hasErrors() {
        return this.errorCount > 0;
    }

    public final int getLookahead() {
        return this.options.getLookahead();
    }

    public final boolean isForceLaCheck() {
        return this.options.getForceLaCheck();
    }

    public final boolean isSanityCheck() {
        return this.options.getSanityCheck();
    }

    public final int getChoiceAmbiguityCheck() {
        return this.options.getChoiceAmbiguityCheck();
    }

    public final int getOtherAmbiguityCheck() {
        return this.options.getOtherAmbiguityCheck();
    }

    final void onSemanticError(Object node, String message) {
        this.errorCount++;
        System.err.print("Error: ");
        printLocationInfo(node);
        System.err.println(message);
    }

    final void onWarning(String message) {
        System.err.print("Warning: ");
        System.err.println(message);
    }

    final void onWarning(Object node, String message) {
        System.err.print("Warning: ");
        printLocationInfo(node);
        System.err.println(message);
    }

    private static void printLocationInfo(Object node) {
        if (node instanceof Production p) {
            System.err.print("Line " + p.getLine() + ", Column " + p.getColumn() + ": ");
        } else if (node instanceof Token t) {
            System.err.print("Line " + t.beginLine + ", Column " + t.beginColumn + ": ");
        }
    }
}
