// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import java.io.File;
import java.io.FileNotFoundException;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.utils.DigestOptions;
import org.hivevm.cc.utils.DigestWriter;
import org.hivevm.cc.utils.TemplateProvider;


/**
 * The {@link CppTemplate} class.
 */
public enum CppTemplate implements TemplateProvider {

  JAVACC("JavaCC", "h"),

  LEXER_H("Lexer", "h", "%s"),
  LEXER("Lexer", LEXER_H),

  PARSER_H("Parser", "h", "%s"),
  PARSER("Parser", PARSER_H),

  MULTINODE("MultiNode", "cc", "%s"),
  MULTINODE_H("MultiNode", "h"),
  NODE("Node"),
  NODE_H("Node", "h"),

  PARSEEXCEPTION("ParseException"),
  PARSEEXCEPTION_H("ParseException", "h"),

  PARSER_CONSTANTS("ParserConstants", "h", "%sConstants"),

  PARSERHANDLER("ParserErrorHandler"),
  PARSERHANDLER_H("ParserErrorHandler", "h"),

  READER("Reader", "h"),
  STRINGREADER("StringReader"),
  STRINGREADER_H("StringReader", "h"),

  TOKEN("Token"),
  TOKEN_H("Token", "h"),
  TOKENMANAGER("TokenManager", "h"),
  TOKENNANAGERERROR("TokenManagerError"),
  TOKENNANAGERERROR_H("TokenManagerError", "h"),
  TOKENNANAGERHANDLER("TokenManagerErrorHandler"),
  TOKENNANAGERHANDLER_H("TokenManagerErrorHandler", "h"),

  TREE("Tree", "h"),
  TREESTATE("TreeState"),
  TREESTATE_H("TreeState", "h");

  private final String      name;
  private final String      type;
  private final String      path;
  private final CppTemplate header;

  private CppTemplate(String name) {
    this(name, "cc");
  }

  private CppTemplate(String name, String type) {
    this.name = name;
    this.type = type;
    this.path = name;
    this.header = null;
  }

  private CppTemplate(String name, String type, String path) {
    this.name = name;
    this.type = type;
    this.path = path;
    this.header = null;
  }

  private CppTemplate(String name, CppTemplate header) {
    this.name = name;
    this.type = "cc";
    this.path = header.path;
    this.header = header;
  }

  public CppTemplate getHeader() {
    return this.header;
  }

  @Override
  public String getTemplate() {
    return String.format("cpp/%s.%s", this.name, this.type);
  }

  @Override
  public String getFilename(String name) {
    return String.format("%s.%s", (name == null) ? this.name : String.format(this.path, name), this.type);
  }

  @Override
  public final DigestWriter createDigestWriter(DigestOptions options) throws FileNotFoundException {
    return createDigestWriter(getFilename(null), options);
  }

  @Override
  public final DigestWriter createDigestWriter(String name, DigestOptions options) throws FileNotFoundException {
    File file = new File(options.getOptions().getOutputDirectory(), getFilename(name));
    return DigestWriter.createCpp(file, HiveCC.VERSION, options);
  }
}
