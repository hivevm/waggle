// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.cpp;

import java.nio.charset.StandardCharsets;

/** Text as the elements of a JJChar array. */
final class CppChars {

    private CppChars() {
    }

    /**
     * The elements of a JJChar array that holds {@code s}, each followed by ", ". The reader hands
     * the lexer UTF-8, so the text is UTF-8 too: a UTF-16 code unit does not fit a char beyond
     * ASCII and does not compile.
     */
    static String of(String s) {
        var elements = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            elements.append((b >= 0) ? "0x" + Integer.toHexString(b)
                    : String.format("'\\x%02x'", b & 0xff)).append(", ");
        }
        return elements.toString();
    }
}
