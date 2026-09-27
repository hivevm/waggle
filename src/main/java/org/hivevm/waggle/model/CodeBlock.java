// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.model;

import java.util.Arrays;
import java.util.stream.Collectors;

/** The text of an embedded code block, as the grammar's {@code <? ... ?>} delimits it. */
public final class CodeBlock {

    private static final String BEGIN = "<?";

    private CodeBlock() {
    }

    public static String strip(String text) {
        if (text.startsWith(CodeBlock.BEGIN)) {
            text = text.substring(CodeBlock.BEGIN.length());
            if (text.contains("\n")) {
                var lines = Arrays.asList(text.split("\n"));
                var first = lines.get(0).trim().isEmpty() ? 1 : 0;
                var tab = lines.get(first).indexOf(lines.get(first).trim());
                text = lines.stream().skip(first).map(l -> CodeBlock.outdent(l, tab))
                        .collect(Collectors.joining("\n"));
            } else {
                text = text.trim();
            }
        }
        return text;
    }

    /**
     * Removes up to {@code tab} characters of leading whitespace. Cutting a fixed {@code tab}
     * characters instead turned a line indented less than the first one into garbage — with a
     * first line indented by 8, {@code "    bar();"} came out as {@code ");"}.
     */
    private static String outdent(String line, int tab) {
        int i = 0;
        while ((i < tab) && (i < line.length()) && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(i);
    }
}