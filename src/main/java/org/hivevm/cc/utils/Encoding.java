// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.utils;

import org.hivevm.cc.parser.Options;

/**
 * The {@link Encoding} class.
 */
public abstract class Encoding {

  /**
   * Constructs an instance of {@link Encoding}.
   */
  private Encoding() {}

  public static String escape(String str) {
    String retval = "";
    char ch;
    for (int i = 0; i < str.length(); i++) {
      ch = str.charAt(i);
      if (ch == '\b') {
        retval += "\\b";
      } else if (ch == '\t') {
        retval += "\\t";
      } else if (ch == '\n') {
        retval += "\\n";
      } else if (ch == '\f') {
        retval += "\\f";
      } else if (ch == '\r') {
        retval += "\\r";
      } else if (ch == '\"') {
        retval += "\\\"";
      } else if (ch == '\'') {
        retval += "\\\'";
      } else if (ch == '\\') {
        retval += "\\\\";
      } else if ((ch < 0x20) || (ch > 0x7e)) {
        String s = "0000" + Integer.toString(ch, 16);
        retval += "\\u" + s.substring(s.length() - 4, s.length());
      } else {
        retval += ch;
      }
    }
    return retval;
  }

  public static String escapeUnicode(String str) {
    switch (Options.getOutputLanguage()) {
      case JAVA:
        StringBuilder builder = new StringBuilder(str.length());
        char ch;
        for (int i = 0; i < str.length(); i++) {
          ch = str.charAt(i);
          if (((ch < 0x20) || (ch > 0x7e)) && (ch != '\t') && (ch != '\n') && (ch != '\r') && (ch != '\f')) {
            String s = "0000" + Integer.toString(ch, 16);
            builder.append("\\u" + s.substring(s.length() - 4, s.length()));
          } else {
            builder.append(ch);
          }
        }
        return builder.toString();
      case CPP:
        return str;
      default:
        // TODO :: CBA -- Require Unification of output language specific processing into a single
        // Enum class
        throw new RuntimeException("Unhandled Output Language : " + Options.getOutputLanguage());
    }
  }
}
