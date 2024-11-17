// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import org.hivevm.cc.generator.ASTGenerator;
import org.hivevm.cc.generator.FileGenerator;
import org.hivevm.cc.generator.GeneratorName;
import org.hivevm.cc.generator.GeneratorProvider;
import org.hivevm.cc.generator.LexerGenerator;
import org.hivevm.cc.generator.ParserGenerator;

/**
 * The {@link CppGenerator} class.
 */
@GeneratorName("Cpp")
public class CppGenerator extends GeneratorProvider {

  @Override
  protected final ASTGenerator newASTGenerator() {
    return new CppASTGenerator();
  }

  @Override
  protected final FileGenerator newFileGenerator() {
    return new CppFileGenerator();
  }

  @Override
  protected final LexerGenerator newLexerGenerator() {
    return new CppLexerGenerator();
  }

  @Override
  protected final ParserGenerator newParserGenerator() {
    return new CppParserGenerator();
  }
}
