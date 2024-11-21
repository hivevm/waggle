// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.utils.DigestOptions;
import org.hivevm.cc.utils.DigestWriter;
import org.hivevm.cc.utils.Environment;
import org.hivevm.cc.utils.Template;

/**
 * The {@link TemplateProvider} class.
 */
public interface TemplateProvider {

  String getTemplate();

  String getFilename(String name);

  File getFile(Options options);

  File getFile(Options options, String name);

  default void render(Options options) {
    render(options, null);
  }

  default void render(Options options, String filename) {
    File file = (filename == null) ? getFile(options) : getFile(options, filename);
    TemplateProvider.generate(file, filename, getTemplate(), options);
  }

  /**
   * Renders a {@link TemplateProvider}.
   *
   * @param template
   * @param options
   */
  static void render(TemplateProvider provider, Options options) {
    File file = provider.getFile(options, null);
    String filename = provider.getFilename(null);
    TemplateProvider.generate(file, filename, provider.getTemplate(), options);
  }

  /**
   * Renders a {@link TemplateProvider}.
   *
   * @param provider
   * @param options
   * @param options2
   * @param name
   */
  static void render(TemplateProvider provider, Options options, String name) {
    File file = provider.getFile(options, name);
    String filename = provider.getFilename(name);
    TemplateProvider.generate(file, filename, provider.getTemplate(), options);
  }

  /**
   * Generates a {@link File} from a template.
   *
   * @param name
   * @param file
   * @param opt
   * @param env
   */
  static void generate(File file, String filename, String templateName, Environment opt) {
    DigestOptions options = new DigestOptions(opt);
    try (PrintWriter writer = DigestWriter.createDigestWriter(file, options)) {
      String path = String.format("/templates/%s.template", templateName);
      InputStream stream = Template.class.getResourceAsStream(path);
      if (stream == null) {
        throw new IOException("Invalid template name: " + path);
      }
      Template template = new Template(stream.readAllBytes(), options);
      template.render(writer);
    } catch (IOException e) {
      System.err.println("Failed to create file: " + filename + " " + e);
      JavaCCErrors.semantic_error("Could not open file: " + filename + " for writing.");
      throw new Error();
    }
  }
}
