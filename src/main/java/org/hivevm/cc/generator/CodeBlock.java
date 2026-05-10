// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum CodeBlock {

    BEGIN("<?"),
    END("?>");

    private final String image;

    CodeBlock(String image) {
        this.image = image;
    }

    public static String begin() {
        return CodeBlock.BEGIN.image;
    }

    public static String end() {
        return CodeBlock.END.image;
    }

    static String strip(String text) {
        if (text.startsWith(CodeBlock.BEGIN.image)) {
            text = text.substring(CodeBlock.BEGIN.image.length());
            if (text.contains("\n")) {
                var lines = Arrays.asList(text.split("\n"));
                var first = lines.get(0).trim().isEmpty() ? 1 : 0;
                var tab = lines.get(first).indexOf(lines.get(first).trim());
                text = lines.stream().skip(first).map(l -> l.length() < tab ? l : l.substring(tab)).collect(Collectors.joining("\n"));
            } else {
                text = text.trim();
            }
        }
        return text;
    }
}