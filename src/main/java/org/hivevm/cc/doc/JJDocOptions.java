// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.doc;

import org.hivevm.cc.HiveCCOptions;

/**
 * The options, specific to JJDoc.
 *
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
class JJDocOptions extends HiveCCOptions {

  /**
   * Limit subclassing to derived classes.
   */
  public JJDocOptions() {
    set("ONE_TABLE", Boolean.TRUE);
    set("TEXT", Boolean.FALSE);
    set("XTEXT", Boolean.FALSE);
    set("BNF", Boolean.FALSE);
    set("OUTPUT_FILE", "");
    set("CSS", "");
  }

  /**
   * Find the one table value.
   *
   * @return The requested one table value.
   */
  boolean getOneTable() {
    return booleanValue("ONE_TABLE");
  }

  /**
   * Find the CSS value.
   *
   * @return The requested CSS value.
   */
  String getCSS() {
    return stringValue("CSS");
  }

  /**
   * Find the text value.
   *
   * @return The requested text value.
   */
  boolean getText() {
    return booleanValue("TEXT");
  }

  boolean getXText() {
    return booleanValue("XTEXT");
  }

  /**
   * Find the BNF value.
   *
   * @return The requested text value.
   */
  boolean getBNF() {
    return booleanValue("BNF");
  }

  /**
   * Find the output file value.
   *
   * @return The requested output value.
   */
  String getOutputFile() {
    return stringValue("OUTPUT_FILE");
  }
}
