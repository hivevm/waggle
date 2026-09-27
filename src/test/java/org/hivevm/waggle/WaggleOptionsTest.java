// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hivevm.waggle.api.ParserOptions;
import org.hivevm.waggle.api.Waggle;
import org.hivevm.waggle.api.WaggleOptions;
import org.hivevm.waggle.diag.DiagnosticSink;
import org.hivevm.waggle.diag.Diagnostics;
import org.hivevm.waggle.tree.TreeOptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The option map, and the typed views that read it (ADR-0019).
 *
 * <p>The views sit beside the map rather than replacing it: templates read option keys by name
 * (ADR-0005), so the name-keyed environment is the template contract.
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

    @Test
    void theDefaultsSurviveTheView() {
        var parser = ParserOptions.from(new WaggleOptions());

        assertEquals(1, parser.lookahead(), "LL(1) is the default");
        assertTrue(parser.errorReporting());
        assertTrue(parser.sanityCheck());
        assertFalse(parser.debugParser());
        assertFalse(parser.noDfa());
        assertEquals(0, parser.depthLimit());
    }

    @Test
    void aChangedOptionReachesTheView() {
        var options = new WaggleOptions();
        options.setOption(diagnostics(), null, null, Waggle.LOOKAHEAD, 3);
        options.setOption(diagnostics(), null, null, Waggle.DEBUG_PARSER, Boolean.TRUE);

        var parser = ParserOptions.from(options);

        assertEquals(3, parser.lookahead());
        assertTrue(parser.debugParser());
    }

    /** A list written with spaces after its commas names the same nodes as one without. */
    @Test
    void customNodeNamesAreTrimmed() {
        var options = new WaggleOptions();
        options.setOption(diagnostics(), null, null, Waggle.NODE_CUSTOM, "Foo, Bar ,");

        assertEquals(Set.of("ASTFoo", "ASTBar"), TreeOptions.from(options).customNodes());
    }

    /** Option names are case-insensitive in every locale: "error_reporting" is ERROR_REPORTING. */
    @Test
    void optionNamesDoNotDependOnTheDefaultLocale() {
        var saved = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            var options = new WaggleOptions();
            options.setOption(diagnostics(), null, null, "error_reporting", Boolean.FALSE);

            assertFalse(ParserOptions.from(options).errorReporting());
        } finally {
            Locale.setDefault(saved);
        }
    }

    /** Option warnings belong to the caller's diagnostics; tests keep them off the console. */
    private static Diagnostics diagnostics() {
        return new Diagnostics(DiagnosticSink.SILENT);
    }
}
