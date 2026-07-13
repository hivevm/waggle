package org.hivevm.cc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Tests for option handling.
 */
class HiveCCOptionsTest {

    /**
     * A list-valued option must not blow up.
     *
     * <p>{@code setOption} unwrapped a list into its first element, but then cast the <em>original</em>
     * value to Integer, so a list whose first element is a number threw a ClassCastException.
     */
    @Test
    void listValuedOptionIsAccepted() {
        var options = new HiveCCOptions();
        options.setOption(null, null, HiveCC.JJPARSER_LOOKAHEAD, List.of(3));

        assertEquals(List.of(3), options.get(HiveCC.JJPARSER_LOOKAHEAD));
    }

    /** A non-positive integer is rejected, and the previous value survives. */
    @Test
    void nonPositiveIntegerIsIgnored() {
        var options = new HiveCCOptions();
        options.setOption(null, null, HiveCC.JJPARSER_LOOKAHEAD, 0);

        assertEquals(1, options.getLookahead(), "a lookahead of 0 must be ignored");
    }

    /** A positive integer is taken. */
    @Test
    void positiveIntegerIsTaken() {
        var options = new HiveCCOptions();
        options.setOption(null, null, HiveCC.JJPARSER_LOOKAHEAD, 5);

        assertEquals(5, options.getLookahead());
    }
}
