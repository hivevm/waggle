// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/JavaCCGlobals.java, org/javacc/jjtree/TokenMgrException.java

package org.hivevm.waggle.api;


/**
 * The {@link Encoding} class.
 */
public interface Encoding {

    /**
     * Escapes special ASCII characters.
     */
    static String escape(String text) {
        var retval = new StringBuilder();
        char ch;
        for (int i = 0; i < text.length(); i++) {
            ch = text.charAt(i);
            if (ch == '\b') {
                retval.append("\\b");
            } else if (ch == '\t') {
                retval.append("\\t");
            } else if (ch == '\n') {
                retval.append("\\n");
            } else if (ch == '\f') {
                retval.append("\\f");
            } else if (ch == '\r') {
                retval.append("\\r");
            } else if (ch == '\"') {
                retval.append("\\\"");
            } else if (ch == '\'') {
                retval.append("\\'");
            } else if (ch == '\\') {
                retval.append("\\\\");
            } else if ((ch < 0x20) || (ch > 0x7e)) {
                String s = "0000" + Integer.toString(ch, 16);
                retval.append("\\u").append(s.substring(s.length() - 4));
            } else {
                retval.append(ch);
            }
        }
        return retval.toString();
    }

    /**
     * Escapes special UNICODE characters.
     *
     * <p>The switch is exhaustive over {@link Language} and has no {@code default}: a fourth target
     * is a compile error here rather than a {@code RuntimeException} at generation time. That is
     * what the note this replaces asked for — "unification of output language specific processing"
     * — in the one form that adds nothing speculative (AGENTS.md §1, §5).
     */
    static String escapeUnicode(String text, Language language) {
        return switch (language) {
            case JAVA -> Encoding.escapeUnicodeForJava(text);
            // Neither spells a non-ASCII character as an escape, so the text stands as it is.
            case CPP, RUST -> text;
        };
    }

    /** Java writes any character outside printable ASCII as a {@code \\uXXXX} escape. */
    private static String escapeUnicodeForJava(String text) {
        var builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            var ch = text.charAt(i);
            if (((ch < 0x20) || (ch > 0x7e)) && (ch != '\t') && (ch != '\n') && (ch != '\r')
                    && (ch != '\f')) {
                var s = "0000" + Integer.toString(ch, 16);
                builder.append("\\u").append(s.substring(s.length() - 4));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }
}
