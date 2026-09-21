// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.diag;

/**
 * One thing the generator has to say about a grammar: a severity, a message, and where in the
 * grammar it applies.
 *
 * @param severity how badly this affects the generation
 * @param message  the text shown to the user
 * @param line     the 1-based line, or {@link #NO_POSITION} when the diagnostic is about the
 *                 generation as a whole rather than a place in the grammar
 * @param column   the 1-based column, or {@link #NO_POSITION}
 */
public record Diagnostic(Severity severity, String message, int line, int column) {

    /** The line and column of a diagnostic that does not belong to a place in the grammar. */
    public static final int NO_POSITION = -1;

    public boolean hasPosition() {
        return this.line != Diagnostic.NO_POSITION;
    }

    /**
     * The one-line rendering the generator has always written to {@code System.err}.
     */
    @Override
    public String toString() {
        var prefix = (this.severity == Severity.ERROR) ? "Error: " : "Warning: ";
        return hasPosition()
                ? prefix + "Line " + this.line + ", Column " + this.column + ": " + this.message
                : prefix + this.message;
    }
}
