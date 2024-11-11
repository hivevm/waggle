// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import java.io.IOException;
import java.text.ParseException;

import org.hivevm.cc.JavaCCRequest;
import org.hivevm.cc.Language;
import org.hivevm.cc.generator.cpp.CppFileGenerator;
import org.hivevm.cc.generator.cpp.CppLexerGenerator;
import org.hivevm.cc.generator.cpp.CppParserGenerator;
import org.hivevm.cc.generator.cpp.CppTreeGenerator;
import org.hivevm.cc.generator.java.JavaFileGenerator;
import org.hivevm.cc.generator.java.JavaLexerGenerator;
import org.hivevm.cc.generator.java.JavaParserGenerator;
import org.hivevm.cc.generator.java.JavaTreeGenerator;
import org.hivevm.cc.jjtree.ASTGrammar;
import org.hivevm.cc.jjtree.ASTWriter;
import org.hivevm.cc.jjtree.JJTreeOptions;

/**
 * The {@link ParserEngine} class.
 */
public class ParserEngine {

  private final LexerGenerator      lexerGenerator;
  private final ParserGenerator     parserGenerator;
  private final JJTreeCodeGenerator treeGenerator;
  private final FileGenerator       otherFilesGenerator;

  /**
   * Constructs an instance of {@link ParserEngine}.
   */
  private ParserEngine(LexerGenerator lexerGenerator, ParserGenerator parserGenerator,
      JJTreeCodeGenerator treeGenerator, FileGenerator otherFilesGenerator) {
    this.lexerGenerator = lexerGenerator;
    this.parserGenerator = parserGenerator;
    this.treeGenerator = treeGenerator;
    this.otherFilesGenerator = otherFilesGenerator;
  }

  public final void generate(JavaCCRequest request) throws IOException, ParseException {
    LexerData data = new LexerBuilder().build(request);
    this.lexerGenerator.start(data);
    this.parserGenerator.start(request);
    this.otherFilesGenerator.handleRequest(request, data);
  }

  /**
   * Create a new instance of {@link ParserEngine}.
   *
   * @param node
   * @param writer
   */
  public void generateJJTree(ASTGrammar node, ASTWriter writer, JJTreeOptions options) throws IOException {
    node.jjtAccept(this.treeGenerator, writer);
    this.treeGenerator.generateJJTree(options);
  }

  /**
   * Create a new instance of {@link ParserEngine}.
   *
   * @param language
   */
  public static ParserEngine create(Language language) {
    switch (language) {
      case CPP:
        return new ParserEngine(new CppLexerGenerator(), new CppParserGenerator(), new CppTreeGenerator(),
            new CppFileGenerator());

      case JAVA:
        return new ParserEngine(new JavaLexerGenerator(), new JavaParserGenerator(), new JavaTreeGenerator(),
            new JavaFileGenerator());

      default:
        throw new RuntimeException("Language '" + language + "' type not supported!");
    }
  }
}
