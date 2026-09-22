// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.analysis;

import org.hivevm.waggle.diag.Diagnostics;
import org.hivevm.waggle.api.ParserOptions;

/**
 * The settings and the diagnostics of the generation being analysed.
 *
 * <p>There is one error channel, shared with every other stage (ADR-0011): this class used to keep a
 * second, independent counter, so a semantic error raised here never reached the driver's verdict and
 * generation could report success while it had already failed.
 */
class SemanticContext {

    private final ParserOptions options;
    private final Diagnostics diagnostics;

    public SemanticContext(ParserOptions options, Diagnostics diagnostics) {
        this.options = options;
        this.diagnostics = diagnostics;
    }

    final boolean hasErrors() {
        return this.diagnostics.hasError();
    }

    final int errorCount() {
        return this.diagnostics.errorCount();
    }

    public final int getLookahead() {
        return this.options.lookahead();
    }

    public final boolean isForceLaCheck() {
        return this.options.forceLaCheck();
    }

    public final boolean isSanityCheck() {
        return this.options.sanityCheck();
    }

    public final int getChoiceAmbiguityCheck() {
        return this.options.choiceAmbiguityCheck();
    }

    public final int getOtherAmbiguityCheck() {
        return this.options.otherAmbiguityCheck();
    }

    final void onSemanticError(Object node, String message) {
        this.diagnostics.error(node, message);
    }

    final void onWarning(String message) {
        this.diagnostics.warning(message);
    }

    final void onWarning(Object node, String message) {
        this.diagnostics.warning(node, message);
    }
}
