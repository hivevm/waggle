// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.source;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.utils.DigestOptions;
import org.hivevm.cc.utils.DigestWriter;
import org.hivevm.cc.utils.Template;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * The {@link SourceWriter} class.
 */
public class SourceWriter extends PrintWriter {

  private final String        name;
  private final DigestOptions options;

  /**
   * Constructs an instance of {@link SourceWriter}.
   *
   * @param name
   * @param options
   */
  public SourceWriter(String name, DigestOptions options) {
    super(new StringWriter());
    this.name = name;
    this.options = options;
  }

  /**
   * Gets the name.
   */
  public final String getName() {
    return this.name;
  }

  /**
   * Gets the {@link DigestOptions}.
   */
  public final DigestOptions getOptions() {
    return this.options;
  }

  /**
   * Write the content using a template.
   *
   * @param path
   */
  public final void writeTemplate(String path) throws IOException {
    Template template = Template.of(path, getOptions());
    template.render(new PrintWriter(this.out));
  }

  /**
   * Save {@link SourceWriter} to output path.
   *
   * @param path
   */
  public void saveOutput(File path) {
    File file = new File(path, getName() + ".java");
    try (DigestWriter writer = DigestWriter.create(file, HiveCC.VERSION, options)) {
      writer.print(toString());
    } catch (IOException e) {
      JavaCCErrors.fatal("Could not create output file: " + file);
    }
  }

  /**
   * Converts the {@link StringWriter} to a {@link StringBuffer}.
   */
  @Override
  public final String toString() {
    return ((StringWriter) this.out).getBuffer().toString();
  }
}
