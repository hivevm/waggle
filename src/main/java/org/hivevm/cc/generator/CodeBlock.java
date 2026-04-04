// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

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

    public static String strip(String text) {
        if (text.startsWith(CodeBlock.BEGIN.image))
            return text.substring(CodeBlock.BEGIN.image.length());
        return text;
    }
}