// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

import org.hivevm.cc.generator.FileGenerator;
import org.hivevm.cc.generator.GeneratorName;
import org.hivevm.cc.generator.GeneratorProvider;
import org.hivevm.cc.generator.LexerGenerator;
import org.hivevm.cc.generator.ParserGenerator;
import org.hivevm.cc.generator.TreeGenerator;

/**
 * The {@link JavaGenerator} class.
 */
@GeneratorName("Java")
public class JavaGenerator extends GeneratorProvider {

  @Override
  protected final TreeGenerator newASTGenerator() {
    return new JavaASTGenerator();
  }

  @Override
  protected final FileGenerator newFileGenerator() {
    return new JavaFileGenerator();
  }

  @Override
  protected final LexerGenerator newLexerGenerator() {
    return new JavaLexerGenerator();
  }

  @Override
  protected final ParserGenerator newParserGenerator() {
    return new JavaParserGenerator();
  }
}
