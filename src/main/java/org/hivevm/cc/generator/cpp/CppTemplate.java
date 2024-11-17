// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import org.hivevm.cc.parser.Options;
import org.hivevm.cc.utils.TemplateProvider;

import java.io.File;


/**
 * The {@link CppTemplate} class.
 */
enum CppTemplate implements TemplateProvider {

  JAVACC("JavaCC.h"),

  LEXER_H("Lexer.h", "%sTokenManager.h"),
  LEXER("Lexer.cc", "%sTokenManager.cc"),

  PARSER_H("Parser.h", "%s.h"),
  PARSER("Parser.cc", "%s.cc"),

  PARSER_CONSTANTS("ParserConstants.h", "%sConstants.h"),

  PARSEEXCEPTION("ParseException.cc"),
  PARSEEXCEPTION_H("ParseException.h"),
  PARSERHANDLER("ParserErrorHandler.cc"),
  PARSERHANDLER_H("ParserErrorHandler.h"),

  TOKEN("Token.cc"),
  TOKEN_H("Token.h"),
  TOKENMANAGER("TokenManager.h"),
  TOKENNANAGERERROR("TokenManagerError.cc"),
  TOKENNANAGERERROR_H("TokenManagerError.h"),
  TOKENNANAGERHANDLER("TokenManagerErrorHandler.cc"),
  TOKENNANAGERHANDLER_H("TokenManagerErrorHandler.h"),

  READER("Reader.h"),
  STRINGREADER("StringReader.cc"),
  STRINGREADER_H("StringReader.h"),

  NODE("Node.cc"),
  NODE_H("Node.h"),
  MULTINODE("MultiNode.cc", "%s.cc"),
  MULTINODE_H("MultiNode.h"),

  TREE("Tree.h"),
  TREE_ONE("TreeOne.h", "%sTree.h"),
  TREESTATE("TreeState.cc"),
  TREESTATE_H("TreeState.h"),
  TREE_CONSTANTS("TreeConstants.h", "%sTreeConstants.h"),
  VISITOR("Visitor.h", "%sVisitor.h");

  private final String name;
  private final String path;

  CppTemplate(String name) {
    this(name, name);
  }

  CppTemplate(String name, String path) {
    this.name = name;
    this.path = path;
  }

  @Override
  public String getTemplate() {
    return String.format("cpp/%s", this.name);
  }

  @Override
  public String getFilename(String name) {
    return (name == null) ? this.path : String.format(this.path, name);
  }

  @Override
  public final File getFile(Options options) {
    return getFile(options, getFilename(null));
  }

  @Override
  public final File getFile(Options options, String name) {
    return new File(options.getOutputDirectory(), getFilename(name));
  }
}
