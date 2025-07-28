// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

/**
 * Describes single character descriptors in a character list.
 */
public class SingleCharacter extends Production {

  private final char ch;

  public SingleCharacter(char c) {
    this.ch = c;
  }

  public char getChar() {
    return this.ch;
  }
}
