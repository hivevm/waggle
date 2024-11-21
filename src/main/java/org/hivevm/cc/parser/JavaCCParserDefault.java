// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.parser;

import org.hivevm.cc.HiveCCOptions;

/**
 * The {@link JavaCCParserDefault} class.
 */
public class JavaCCParserDefault extends JavaCCParser {

  private final HiveCCOptions options;

  public JavaCCParserDefault(Provider stream, HiveCCOptions options) {
    super(stream);
    this.options = options;
  }

  /**
   * Gets the {@link #options}.
   */
  @Override
  public final HiveCCOptions getOptions() {
    return this.options;
  }
}
