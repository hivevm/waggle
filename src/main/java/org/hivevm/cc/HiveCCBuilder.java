// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;


import org.hivevm.cc.parser.Options;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@link HiveCCBuilder} class.
 */
public class HiveCCBuilder {

  private Language     language;
  private File         targetDir;
  private List<String> excludes;

  private File         jj;
  private File         jjt;

  /**
   * Set the code generator.
   *
   * @param language
   */
  public final HiveCCBuilder setCodeGenerator(Language language) {
    this.language = language;
    return this;
  }

  /**
   * Set the output directory.
   *
   * @param targetDir
   */
  public final HiveCCBuilder setTargetDir(File targetDir, String... pathes) {
    this.targetDir = HiveCCBuilder.toFile(targetDir, pathes);
    return this;
  }

  /**
   * Set the nodes that should not be generated.
   *
   * @param excludes
   */
  public final HiveCCBuilder setExcludes(List<String> excludes) {
    this.excludes = excludes;
    return this;
  }

  /**
   * Set the jj file.
   *
   * @param file
   */
  public final HiveCCBuilder setJJFile(File file, String... pathes) {
    this.jj = HiveCCBuilder.toFile(file, pathes);
    return this;
  }

  /**
   * Set the jj file.
   *
   * @param file
   */
  public final HiveCCBuilder setJJTreeFile(File file, String... pathes) {
    this.jjt = HiveCCBuilder.toFile(file, pathes);
    return this;
  }

  public static HiveCCBuilder of(Language language) {
    HiveCCBuilder builder = new HiveCCBuilder();
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
      if (this.jj == null) {
        if ((this.excludes != null) && !this.excludes.isEmpty()) {
          arguments.add("-NODE_EXCLUDES=" + String.join(",", this.excludes));
        }
        arguments.add(this.jjt.getAbsolutePath());

        HiveCCTree.main(arguments.toArray(new String[arguments.size()]));

        String path = this.jjt.getAbsolutePath();
        int offset = path.lastIndexOf("/");
        int length = path.lastIndexOf(".");
        arguments.set(arguments.size() - 1, this.targetDir + path.substring(offset, length) + ".jj");
      } else {
        arguments.add(this.jj.getAbsolutePath());
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
      HiveCCInterpreter interpreter = new HiveCCInterpreter(options);
      File file = this.jj;
      if (file == null) {
        String name = this.jjt.getName();
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
