// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.semantic;

import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.Options;

/**
 * The {@link SemanticContext} class.
 */
class SemanticContext {

    private final Options options;

    public SemanticContext(Options options) {
        this.options = options;
    }

    final boolean hasErrors() {
        return JavaCCErrors.hasError();
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
        JavaCCErrors.semantic_error(node, message);
    }

    final void onWarning(String message) {
        JavaCCErrors.warning(message);
    }

    final void onWarning(Object node, String message) {
        JavaCCErrors.warning(node, message);
    }
}
