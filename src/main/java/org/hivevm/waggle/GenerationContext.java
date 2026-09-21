// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import org.hivevm.waggle.diag.Diagnostics;

/**
 * Everything one generation owns: what was asked for, the options it resolved, and what it has to
 * say about the grammar.
 *
 * <p>Every stage of the pipeline receives this context, so nothing reaches for process-global state
 * and two generations in the same JVM cannot see each other (ADR-0015).
 */
public final class GenerationContext {

    private final WaggleOptions options;
    private final Diagnostics diagnostics;

    /**
     * A context over options the caller resolved itself. JJDoc reads its own command line and keeps
     * its own entry point ([SPECIFICATION §3]), so it has no {@link GenerationRequest}.
     */
    public GenerationContext(WaggleOptions options, Diagnostics diagnostics) {
        this.options = options;
        this.diagnostics = diagnostics;
    }

    /** The context of one generation: what the caller asked for, resolved into options. */
    public static GenerationContext of(GenerationRequest request, Diagnostics diagnostics) {
        var options = new WaggleOptions();
        options.apply(request);
        options.validate(diagnostics);
        return new GenerationContext(options, diagnostics);
    }

    public WaggleOptions options() {
        return this.options;
    }

    public Diagnostics diagnostics() {
        return this.diagnostics;
    }
}
