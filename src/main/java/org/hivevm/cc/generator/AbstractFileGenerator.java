// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.utils.DigestOptions;
import org.hivevm.cc.utils.DigestWriter;
import org.hivevm.cc.utils.Template;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * The {@link AbstractFileGenerator} class.
 */
public abstract class AbstractFileGenerator {

  /**
   * Gets the template by name.
   * 
   * @param name
   */
  protected abstract String getTemplate(String name);

  /**
   * Creates a new {@link DigestWriter}.
   * 
   * @param file
   * @param options
   */
  protected abstract DigestWriter createDigestWriter(File file, DigestOptions options) throws FileNotFoundException;

  /**
   * Generates a {@link File} from a template.
   *
   * @param filename
   * @param options
   */
  protected final void generateFile(String filename, DigestOptions options) {
    generateFile(filename, filename, options);
  }

  /**
   * Generates a {@link File} from a template.
   *
   * @param name
   * @param filename
   * @param options
   */
  protected final void generateFile(String name, String filename, DigestOptions options) {
    File file = new File(options.getOptions().getOutputDirectory(), filename);

    try (DigestWriter writer = createDigestWriter(file, options)) {
      Template template = Template.of(getTemplate(name), writer.options());
      template.render(writer);
    } catch (IOException e) {
      System.err.println("Failed to create file: " + filename + " " + e);
      JavaCCErrors.semantic_error("Could not open file: " + filename + " for writing.");
      throw new Error();
    }
  }
}
