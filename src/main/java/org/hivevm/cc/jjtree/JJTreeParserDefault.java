// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

import java.io.Reader;

import org.hivevm.cc.generator.TreeContext;

/**
 * The {@link JJTreeParserDefault} implements a parser for the .jjt files.
 */
public class JJTreeParserDefault extends JJTreeParser {

  private final TreeContext options;

  public JJTreeParserDefault(Reader reader, TreeContext options) {
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
  protected final TreeContext getOptions() {
    return this.options;
  }
}
