// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.diag;

import org.hivevm.waggle.model.Production;
import org.hivevm.waggle.parser.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What one generation has to say about its grammar.
 *
 * <p>This used to be three {@code static int} counters, so the answer to "were there errors?"
 * belonged to the JVM rather than to a generation: a second generation reset the first one's state
 * and a concurrent one corrupted both (ADR-0015). An instance is owned by exactly one generation and
 * carried to every stage by the {@code GenerationContext}.
 *
 * <p>Positions come from two types the generator already passes around as the "node" a diagnostic is
 * about: {@link Production}, the base of everything in the model that knows where it came from, and
 * the generated {@link Token} — the same carve-out the model makes for positions (ADR-0013).
 * Anything else simply has no position.
 */
public final class Diagnostics {

    private final DiagnosticSink sink;
    private final List<Diagnostic> diagnostics;

    private int errorCount;
    private int warningCount;

    /** Collects diagnostics and writes them to {@code System.err} as they are reported. */
    public Diagnostics() {
        this(DiagnosticSink.STDERR);
    }

    public Diagnostics(DiagnosticSink sink) {
        this.sink = sink;
        this.diagnostics = new ArrayList<>();
    }

    public void error(Object node, String message) {
        add(Severity.ERROR, node, message);
    }

    public void error(String message) {
        add(Severity.ERROR, null, message);
    }

    public void warning(Object node, String message) {
        add(Severity.WARNING, node, message);
    }

    public void warning(String message) {
        add(Severity.WARNING, null, message);
    }

    public boolean hasError() {
        return this.errorCount > 0;
    }

    public boolean hasWarning() {
        return this.warningCount > 0;
    }

    public int errorCount() {
        return this.errorCount;
    }

    public int warningCount() {
        return this.warningCount;
    }

    /** Everything reported so far, in the order it was reported. */
    public List<Diagnostic> collected() {
        return Collections.unmodifiableList(this.diagnostics);
    }

    private void add(Severity severity, Object node, String message) {
        var diagnostic = switch (node) {
            case Production p -> new Diagnostic(severity, message, p.getLine(), p.getColumn());
            case Token t -> new Diagnostic(severity, message, t.beginLine, t.beginColumn);
            case null, default ->
                    new Diagnostic(severity, message, Diagnostic.NO_POSITION, Diagnostic.NO_POSITION);
        };

        this.diagnostics.add(diagnostic);
        if (severity == Severity.ERROR) {
            this.errorCount++;
        } else {
            this.warningCount++;
        }
        this.sink.report(diagnostic);
    }
}
