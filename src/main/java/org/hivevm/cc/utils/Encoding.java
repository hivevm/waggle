// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.utils;

import org.hivevm.cc.Language;

/**
 * The {@link Encoding} class.
 */
public interface Encoding {

    /**
     * Escapes special ASCII characters.
     */
    static String escape(String text) {
        StringBuilder retval = new StringBuilder();
        char ch;
        for (int i = 0; i < text.length(); i++) {
            ch = text.charAt(i);
            if (ch == '\b') {
                retval.append("\\b");
            }
            else if (ch == '\t') {
                retval.append("\\t");
            }
            else if (ch == '\n') {
                retval.append("\\n");
            }
            else if (ch == '\f') {
                retval.append("\\f");
            }
            else if (ch == '\r') {
                retval.append("\\r");
            }
            else if (ch == '\"') {
                retval.append("\\\"");
            }
            else if (ch == '\'') {
                retval.append("\\'");
            }
            else if (ch == '\\') {
                retval.append("\\\\");
            }
            else if ((ch < 0x20) || (ch > 0x7e)) {
                String s = "0000" + Integer.toString(ch, 16);
                retval.append("\\u").append(s.substring(s.length() - 4));
            }
            else {
                retval.append(ch);
            }
        }
        return retval.toString();
    }

    /**
     * Escapes special UNICODE characters.
     */
    static String escapeUnicode(String text, Language language) {
        switch (language) {
            case JAVA:
                StringBuilder builder = new StringBuilder(text.length());
                char ch;
                for (int i = 0; i < text.length(); i++) {
                    ch = text.charAt(i);
                    if (((ch < 0x20) || (ch > 0x7e)) && (ch != '\t') && (ch != '\n') && (ch != '\r') && (ch
                            != '\f')) {
                        String s = "0000" + Integer.toString(ch, 16);
                        builder.append("\\u").append(s.substring(s.length() - 4));
                    }
                    else {
                        builder.append(ch);
                    }
                }
                return builder.toString();
            case RUST:
            case CPP:
                return text;
            default:
                // TODO :: CBA -- Require Unification of output language specific processing into a single
                // Enum class
                throw new RuntimeException("Unhandled Output Language : " + language);
        }
    }
}
