// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

import java.io.File;

import org.hivevm.cc.generator.TemplateProvider;
import org.hivevm.cc.parser.Options;


/**
 * The {@link JavaTemplate} class.
 */
enum JavaTemplate implements TemplateProvider {

  LEXER("Lexer", "%sTokenManager"),
  PARSER("Parser", "%s"),

  PROVIDER("Provider"),
  STREAM_PROVIDER("StreamProvider"),
  STRING_PROVIDER("StringProvider"),
  CHAR_STREAM("JavaCharStream"),

  MULTI_NODE("MultiNode", "%s"),
  NODE("Node"),

  PARSER_EXCEPTION("ParseException"),
  PARSER_CONSTANTS("ParserConstants", "%sConstants"),
  TOKEN("Token"),
  TOKEN_EXCEPTION("TokenException"),

  TREE("Tree"),
  TREE_STATE("TreeState", "JJT%sState"),
  TREE_CONSTANTS("TreeConstants", "%sTreeConstants"),

  VISITOR("Visitor", "%sVisitor"),
  DEFAULT_VISITOR("DefaultVisitor", "%sDefaultVisitor");

  private final String name;
  private final String path;

  JavaTemplate(String name) {
    this.name = name;
    this.path = name;
  }

  JavaTemplate(String name, String path) {
    this.name = name;
    this.path = path;
  }

  @Override
  public String getTemplate() {
    return String.format("java/%s", this.name);
  }

  @Override
  public String getFilename(String name) {
    return String.format("%s.java", (name == null) ? this.name : String.format(this.path, name));
  }

  @Override
  public final File getFile(Options options) {
    return getFile(options, null);
  }

  @Override
  public final File getFile(Options options, String name) {
    return JavaTemplate.getFile(getFilename(name), options);
  }

  /**
   * Get the Java file to generate.
   *
   * @param filename
   * @param options
   */
  public static File getFile(String filename, Options options) {
    String packagePath = options.getJavaPackageName().replace('.', File.separatorChar);
    File outputDir = new File(options.getOutputDirectory(), packagePath);
    outputDir.mkdirs();
    return new File(outputDir, filename);
  }
}
