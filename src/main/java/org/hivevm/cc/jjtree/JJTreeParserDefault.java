// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

import org.hivevm.cc.generator.ASTGeneratorContext;

import java.io.Reader;

/**
 * The {@link JJTreeParserDefault} implements a parser for the .jjt files.
 */
public class JJTreeParserDefault extends JJTreeParser {

  private final ASTGeneratorContext options;

  public JJTreeParserDefault(Reader reader, ASTGeneratorContext options) {
    super(new JJTreeParserTokenManager(new JavaCharStream(new StreamProvider(reader))));
    this.options = options;
  }

  /**
   * Parses the {@link Reader} and creates the abstract syntax tree.
   */
  public final ASTGrammar parse() throws ParseException {
    javacc_input();
    return (ASTGrammar) this.jjtree.rootNode();
  }

  @Override
  protected final void jjtreeOpenNodeScope(Node n) {
    ((ASTNode) n).setFirstToken(getToken(1));
  }

  @Override
  protected final void jjtreeCloseNodeScope(Node n) {
    ((ASTNode) n).setLastToken(getToken(0));
  }

  @Override
  protected final ASTGeneratorContext getOptions() {
    return this.options;
  }
}
