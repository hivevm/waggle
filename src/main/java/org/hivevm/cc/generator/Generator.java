// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import java.io.IOException;
import java.text.ParseException;

import org.hivevm.cc.ParserRequest;
import org.hivevm.cc.jjtree.ASTGrammar;
import org.hivevm.cc.jjtree.ASTWriter;

/**
 * The {@link Generator} class.
 */
public interface Generator {

  /**
   * Generates the parser.
   *
   * @param request
   */
  void generate(ParserRequest request) throws IOException, ParseException;

  /**
   * Generates the Abstract Syntax Tree.
   *
   * @param node
   * @param writer
   * @param context
   */
  void generateAST(ASTGrammar node, ASTWriter writer, TreeOptions context);
}
