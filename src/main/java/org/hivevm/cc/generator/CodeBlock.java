// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

public enum CodeBlock {

  CODE,
  END;

  public final String image;

  CodeBlock() {
    this.image = "@" + name().toLowerCase();
  }

  public final String strip(String text) {
    return text.substring(name().length() + 1);
  }
}