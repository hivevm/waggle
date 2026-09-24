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
import org.junit.jupiter.api.Test;

/**
 * The typed views read the option map, and read it right (ADR-0019).
 *
 * <p>They sit beside the map rather than replacing it: templates read option keys by name
 * (ADR-0005), so the name-keyed environment is the template contract.
 */
class TypedOptionsTest {

    private static WaggleOptions options() {
        return new WaggleOptions();
    }

    @Test
    void theDefaultsSurviveTheView() {
        var parser = ParserOptions.from(TypedOptionsTest.options());

        assertEquals(1, parser.lookahead(), "LL(1) is the default");
        assertTrue(parser.errorReporting());
        assertTrue(parser.sanityCheck());
        assertTrue(parser.keepLineColumn());
        assertFalse(parser.debugParser());
        assertFalse(parser.noDfa());
        assertEquals(0, parser.depthLimit());
    }

    @Test
    void aChangedOptionReachesTheView() {
        var options = TypedOptionsTest.options();
        options.setOption(new Diagnostics(DiagnosticSink.SILENT), null, null,
                Waggle.LOOKAHEAD, 3);
        options.setOption(new Diagnostics(DiagnosticSink.SILENT), null, null,
                Waggle.DEBUG_PARSER, Boolean.TRUE);

        var parser = ParserOptions.from(options);

        assertEquals(3, parser.lookahead());
        assertTrue(parser.debugParser());
    }
}
