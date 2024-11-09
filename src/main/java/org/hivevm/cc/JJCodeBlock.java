// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;


/**
 * The {@link JJCodeBlock} class.
 */
public enum JJCodeBlock {

  Code;

  public final String CODE;

  private JJCodeBlock() {
    this.CODE = "@" + name().toLowerCase();
  }

  public final String strip(String text) {
    return text.substring(name().length() + 1);
  }
}
