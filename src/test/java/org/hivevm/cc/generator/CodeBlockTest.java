// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Regression test for {@link CodeBlock#strip}: it takes the indentation of the first line off every
 * line of an action. It used to cut that many characters off unconditionally, so a line indented
 * less than the first one lost code, not whitespace.
 */
class CodeBlockTest {

    @Test
    void stripsTheIndentationOfTheFirstLine() {
        assertEquals("if (x) {\n    foo();\n}",
                CodeBlock.strip("<?\n        if (x) {\n            foo();\n        }"));
    }

    @Test
    void keepsTheCodeOfALessIndentedLine() {
        assertEquals("if (x) {\n    foo();\n}\nbar();",
                CodeBlock.strip("<?\n        if (x) {\n            foo();\n        }\n    bar();"));
    }

    @Test
    void keepsTheCodeOfALineIndentedWithATab() {
        assertEquals("a();\nreturn b;", CodeBlock.strip("<?\n        a();\n\treturn b;"));
    }

    @Test
    void trimsASingleLineBlock() {
        assertEquals("foo();", CodeBlock.strip("<?  foo();  "));
    }

    @Test
    void leavesTextWithoutABlockMarkerAlone() {
        assertEquals("  foo();", CodeBlock.strip("  foo();"));
    }
}
