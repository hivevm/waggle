// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.source;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.utils.DigestOptions;
import org.hivevm.cc.utils.DigestWriter;
import org.hivevm.cc.utils.Template;
import org.hivevm.cc.utils.TemplateProvider;

/**
 * The {@link SourceWriter} class.
 */
public class SourceWriter extends PrintWriter {

  private final String           name;
  private final TemplateProvider provider;
  private final DigestOptions    options;

  /**
   * Constructs an instance of {@link SourceWriter}.
   *
   * @param name
   * @param provider
   * @param options
   */
  public SourceWriter(String name, TemplateProvider provider, DigestOptions options) {
    super(new StringWriter());
    this.name = name;
    this.provider = provider;
    this.options = options;
  }

  /**
   * Gets the name.
   */
  public final String getName() {
    return this.name;
  }

  /**
   * Gets the {@link TemplateProvider}.
   */
  public final TemplateProvider getProvider() {
    return this.provider;
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
  public final void writeTemplate() throws IOException {
    Template template = Template.of(getProvider(), getOptions());
    template.render(new PrintWriter(this.out));
  }

  /**
   * Save {@link SourceWriter} to output path.
   *
   */
  @Override
  public void close() {
    try (DigestWriter writer = getProvider().createDigestWriter(getName(), this.options)) {
      writer.print(toString());
    } catch (IOException e) {
      JavaCCErrors.fatal("Could not create output file: " + getProvider().getFilename(name));
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
