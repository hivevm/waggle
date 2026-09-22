// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.api.ParserOptions;
import org.hivevm.waggle.api.TargetOptions;
import org.hivevm.waggle.api.Waggle;
import org.hivevm.waggle.api.WaggleOptions;
import org.hivevm.waggle.diag.DiagnosticSink;
import org.hivevm.waggle.diag.Diagnostics;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void theTargetViewReadsTheTargetsOwnSettings() {
        var options = TypedOptionsTest.options();
        var silent = new Diagnostics(DiagnosticSink.SILENT);
        options.setOption(silent, null, null, Waggle.JAVA_PACKAGE, "org.example");
        options.setOption(silent, null, null, Waggle.RUST_MODULE, "example");
        options.setOption(silent, null, null, Waggle.BASE_PARSER, "MyBase");

        var target = TargetOptions.from(options);

        assertEquals(Language.JAVA, target.language(), "java is the default target");
        assertEquals("org.example", target.javaPackage());
        assertEquals("example", target.rustModule());
        assertEquals("MyBase", target.baseParser());
        assertEquals("", target.cppNamespace(), "an unset option is empty, not null");
        assertEquals(List.of(), target.javaImports(), "JAVA_IMPORTS is a list, not a string");
    }
}
