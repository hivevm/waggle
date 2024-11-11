// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import java.io.File;
import java.io.IOException;

import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.utils.DigestOptions;
import org.hivevm.cc.utils.DigestWriter;
import org.hivevm.cc.utils.Template;
import org.hivevm.cc.utils.TemplateProvider;

/**
 * The {@link AbstractFileGenerator} class.
 */
public abstract class AbstractFileGenerator {

  /**
   * Generates a {@link File} from a template.
   *
   * @param tpl
   * @param options
   */
  protected final void generateFile(TemplateProvider tpl, DigestOptions options) {
    generateFile(tpl, null, options);
  }

  /**
   * Generates a {@link File} from a template.
   *
   * @param tpl
   * @param name
   * @param options
   */
  protected final void generateFile(TemplateProvider tpl, String name, DigestOptions options) {
    String filename = tpl.getFilename(name);
    try (DigestWriter writer = tpl.createDigestWriter(name, options)) {
      Template template = Template.of(tpl, writer.options());
      template.render(writer);
    } catch (IOException e) {
      System.err.println("Failed to create file: " + filename + " " + e);
      JavaCCErrors.semantic_error("Could not open file: " + filename + " for writing.");
      throw new Error();
    }
  }
}
