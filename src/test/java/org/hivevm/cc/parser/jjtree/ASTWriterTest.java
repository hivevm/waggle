// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.parser.jjtree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hivevm.cc.Language;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

/**
 * Regression test for {@link ASTWriter} indentation.
 *
 * <p>{@code println()} used to call {@code IntStream.of(indent)}, which yields a single-element
 * stream regardless of the depth, so every line was indented by exactly one level (and even at
 * depth 0). The emitted whitespace must instead track the current {@code indent}/{@code outdent}
 * depth.
 */
class ASTWriterTest {

    @Test
    void indentationTracksDepth() {
        var buffer = new StringWriter();
        try (var writer = new ASTWriter(buffer, Language.JAVA)) {
            writer.print("a");
            writer.indent();
            writer.println();
            writer.print("b");
            writer.indent();
            writer.println();
            writer.print("c");
        }
        // Depth 1 -> 4 spaces, depth 2 -> 8 spaces (buggy version printed 4 spaces for both).
        assertEquals("a\n    b\n        c", buffer.toString());
    }

    @Test
    void zeroIndentEmitsNoLeadingSpaces() {
        var buffer = new StringWriter();
        try (var writer = new ASTWriter(buffer, Language.JAVA)) {
            writer.print("x");
            writer.println();
            writer.print("y");
        }
        // At depth 0 no indentation may be emitted (buggy version printed 4 spaces).
        assertEquals("x\ny", buffer.toString());
    }
}
