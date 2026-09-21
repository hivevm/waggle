// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.diag;

/**
 * Where a {@link Diagnostic} goes as it is reported.
 *
 * <p>{@link Diagnostics} always keeps what it collected, so a caller can ask afterwards what a
 * generation had to say. The sink is the other half: it decides whether the user also sees the
 * diagnostic while the generation is still running, which is what a build log wants and what a test
 * does not.
 */
@FunctionalInterface
public interface DiagnosticSink {

    /** Writes each diagnostic to {@code System.err} as it is reported — the default. */
    DiagnosticSink STDERR = diagnostic -> System.err.println(diagnostic);

    /** Keeps the diagnostics to themselves; used by tests. */
    DiagnosticSink SILENT = diagnostic -> {
    };

    void report(Diagnostic diagnostic);
}
