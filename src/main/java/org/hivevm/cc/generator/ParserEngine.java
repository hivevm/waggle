// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.JavaCCRequest;
import org.hivevm.cc.Language;
import org.hivevm.cc.generator.cpp.CppFileGenerator;
import org.hivevm.cc.generator.cpp.CppLexerGenerator;
import org.hivevm.cc.generator.cpp.CppParserGenerator;
import org.hivevm.cc.generator.cpp.CppASTGenerator;
import org.hivevm.cc.generator.java.JavaFileGenerator;
import org.hivevm.cc.generator.java.JavaLexerGenerator;
import org.hivevm.cc.generator.java.JavaParserGenerator;
import org.hivevm.cc.generator.java.JavaASTGenerator;
import org.hivevm.cc.jjtree.ASTGrammar;
import org.hivevm.cc.jjtree.ASTWriter;
import org.hivevm.cc.jjtree.JJTreeOptions;

import java.io.IOException;
import java.text.ParseException;

/**
 * The {@link ParserEngine} class.
 */
public class ParserEngine {

  private final LexerGenerator   lexerGenerator;
  private final ParserGenerator  parserGenerator;
  private final FileGenerator    otherFilesGenerator;

  private final ASTGenerator treeGenerator;

  /**
   * Constructs an instance of {@link ParserEngine}.
   */
  private ParserEngine(LexerGenerator lexerGenerator, ParserGenerator parserGenerator, ASTGenerator treeGenerator,
      FileGenerator otherFilesGenerator) {
    this.lexerGenerator = lexerGenerator;
    this.parserGenerator = parserGenerator;
    this.treeGenerator = treeGenerator;
    this.otherFilesGenerator = otherFilesGenerator;
  }

  public final void generate(JavaCCRequest request) throws IOException, ParseException {
    LexerData dataLexer = new LexerBuilder().build(request);
    ParserData dataParser = new ParserBuilder().build(request);

    this.lexerGenerator.start(dataLexer);
    this.parserGenerator.start(dataParser);
    this.otherFilesGenerator.handleRequest(request, dataLexer);
  }

  /**
   * Generates the Abstract Syntax Tree.
   *
   * @param node
   * @param writer
   * @param options
   */
  public void generateAbstractSyntaxTree(ASTGrammar node, ASTWriter writer, JJTreeOptions options) throws IOException {
    node.jjtAccept(this.treeGenerator, writer);
    this.treeGenerator.generate(options);
  }

  /**
   * Create a new instance of {@link ParserEngine}.
   *
   * @param language
   */
  public static ParserEngine create(Language language) {
    switch (language) {
      case CPP:
        return new ParserEngine(new CppLexerGenerator(), new CppParserGenerator(), new CppASTGenerator(),
            new CppFileGenerator());

      case JAVA:
        return new ParserEngine(new JavaLexerGenerator(), new JavaParserGenerator(), new JavaASTGenerator(),
            new JavaFileGenerator());

      default:
        throw new RuntimeException("Language '" + language + "' type not supported!");
    }
  }
}
