// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;


import org.hivevm.cc.parser.Options;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@link ParserBuilder} class.
 */
public class ParserBuilder {

  private Language     language;
  private File         targetDir;
  private List<String> excludes;

  private File         treeFile;
  private File         parserFile;

  /**
   * Set the code generator.
   *
   * @param language
   */
  public final ParserBuilder setCodeGenerator(Language language) {
    this.language = language;
    return this;
  }

  /**
   * Set the output directory.
   *
   * @param targetDir
   */
  public final ParserBuilder setTargetDir(File targetDir, String... pathes) {
    this.targetDir = ParserBuilder.toFile(targetDir, pathes);
    return this;
  }

  /**
   * Set the nodes that should not be generated.
   *
   * @param excludes
   */
  public final ParserBuilder setExcludes(List<String> excludes) {
    this.excludes = excludes;
    return this;
  }

  /**
   * Set the jj file.
   *
   * @param file
   */
  public final ParserBuilder setParserFile(File file, String... pathes) {
    this.parserFile = ParserBuilder.toFile(file, pathes);
    return this;
  }

  /**
   * Set the jj file.
   *
   * @param file
   */
  public final ParserBuilder setTreeFile(File file, String... pathes) {
    this.treeFile = ParserBuilder.toFile(file, pathes);
    return this;
  }

  public static ParserBuilder of(Language language) {
    ParserBuilder builder = new ParserBuilder();
    builder.setCodeGenerator(language);
    return builder;
  }

  /**
   * Run the parser generator.
   */
  public final void build() {
    try {
      List<String> arguments = new ArrayList<>();
      arguments.add("-CODE_GENERATOR=" + this.language.name());
      arguments.add("-OUTPUT_DIRECTORY=" + this.targetDir.getAbsolutePath());
      if (this.parserFile == null) {
        if ((this.excludes != null) && !this.excludes.isEmpty()) {
          arguments.add("-NODE_EXCLUDES=" + String.join(",", this.excludes));
        }
        arguments.add(this.treeFile.getAbsolutePath());

        HiveCCTree.main(arguments.toArray(new String[arguments.size()]));

        String path = this.treeFile.getAbsolutePath();
        int offset = path.lastIndexOf("/");
        int length = path.lastIndexOf(".");
        arguments.set(arguments.size() - 1, this.targetDir + path.substring(offset, length) + ".jj");
      } else {
        arguments.add(this.parserFile.getAbsolutePath());
      }

      HiveCCParser.main(arguments.toArray(new String[arguments.size()]));
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  /**
   * Run the parser generator.
   */
  public final void interpret(String text) {
    Options options = new Options();
    try {
      ParserInterpreter interpreter = new ParserInterpreter(options);
      File file = this.parserFile;
      if (file == null) {
        String name = this.treeFile.getName();
        String jjName = name.substring(0, name.length() - 1);
        file = new File(this.targetDir, jjName);
      }
      String grammar = new String(Files.readAllBytes(file.toPath()));
      interpreter.runTokenizer(grammar, text);
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  private static File toFile(File file, String... pathes) {
    return (pathes.length == 0) ? file : new File(file, String.join(File.separator, pathes));
  }
}
