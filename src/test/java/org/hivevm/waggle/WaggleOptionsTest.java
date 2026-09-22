package org.hivevm.waggle;

import org.hivevm.waggle.api.Waggle;

import org.hivevm.waggle.api.WaggleOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hivevm.waggle.diag.DiagnosticSink;
import org.hivevm.waggle.diag.Diagnostics;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Tests for option handling.
 */
class WaggleOptionsTest {

    /**
     * A list-valued option must not blow up.
     *
     * <p>{@code setOption} unwrapped a list into its first element, but then cast the <em>original</em>
     * value to Integer, so a list whose first element is a number threw a ClassCastException.
     */
    @Test
    void listValuedOptionIsAccepted() {
        var options = new WaggleOptions();
        options.setOption(diagnostics(), null, null, Waggle.LOOKAHEAD, List.of(3));

        assertEquals(List.of(3), options.get(Waggle.LOOKAHEAD));
    }

    /** A non-positive integer is rejected, and the previous value survives. */
    @Test
    void nonPositiveIntegerIsIgnored() {
        var options = new WaggleOptions();
        options.setOption(diagnostics(), null, null, Waggle.LOOKAHEAD, 0);

        assertEquals(1, options.getLookahead(), "a lookahead of 0 must be ignored");
    }

    /** A positive integer is taken. */
    @Test
    void positiveIntegerIsTaken() {
        var options = new WaggleOptions();
        options.setOption(diagnostics(), null, null, Waggle.LOOKAHEAD, 5);

        assertEquals(5, options.getLookahead());
    }

    /** Option warnings belong to the caller's diagnostics; tests keep them off the console. */
    private static Diagnostics diagnostics() {
        return new Diagnostics(DiagnosticSink.SILENT);
    }
}
