// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.api;

import org.hivevm.source.OutputSink;
import org.hivevm.waggle.diag.Diagnostics;

/**
 * Everything one generation owns: what was asked for, the options it resolved, and what it has to
 * say about the grammar.
 *
 * <p>Every stage of the pipeline receives this context, so nothing reaches for process-global state
 * and two generations in the same JVM cannot see each other (ADR-0015).
 *
 * <p>The canonical constructor takes options the caller resolved itself. JJDoc reads its own
 * command line and keeps its own entry point ([SPECIFICATION §3]), so it has no
 * {@link GenerationRequest}.
 */
public record GenerationContext(WaggleOptions options, Diagnostics diagnostics) {

    /**
     * The context of one generation: what the caller asked for, resolved into options, writing
     * through {@code sink}. A {@code null} sink means the default: write files (ADR-0018).
     */
    public static GenerationContext of(GenerationRequest request, Diagnostics diagnostics,
            OutputSink sink) {
        var options = new WaggleOptions();
        options.apply(request);
        if (sink != null) {
            options.setOutputSink(sink);
        }
        return new GenerationContext(options, diagnostics);
    }
}
