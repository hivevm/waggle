// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

import java.io.File;
import java.io.FileNotFoundException;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.utils.DigestOptions;
import org.hivevm.cc.utils.DigestWriter;
import org.hivevm.cc.utils.TemplateProvider;


/**
 * The {@link JavaTemplate} class.
 */
public enum JavaTemplate implements TemplateProvider {

  LEXER("Lexer", "%s"),
  PARSER("Parser", "%s"),

  PROVIDER("Provider"),
  STREAM_PROVIDER("StreamProvider"),
  STRING_PROVIDER("StringProvider"),
  CHAR_STREAM("JavaCharStream"),

  MULTI_NODE("MultiNode"),
  NODE("Node"),

  PARSER_EXCEPTION("ParseException"),
  PARSER_CONSTANTS("ParserConstants", "%sConstants"),
  TOKEN("Token"),
  TOKEN_EXCEPTION("TokenException"),
  TREE("Tree"),
  TREE_STATE("TreeState");

  private final String name;
  private final String path;

  private JavaTemplate(String name) {
    this.name = name;
    this.path = name;
  }

  private JavaTemplate(String name, String path) {
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
  public final DigestWriter createDigestWriter(DigestOptions options) throws FileNotFoundException {
    return createDigestWriter(null, options);
  }

  @Override
  public final DigestWriter createDigestWriter(String name, DigestOptions options) throws FileNotFoundException {
    File file = new File(options.getOptions().getOutputDirectory(), getFilename(name));
    return DigestWriter.create(file, HiveCC.VERSION, options);
  }
}
