// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.source;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.hivevm.cc.generator.cpp.CppTemplate;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.utils.DigestOptions;
import org.hivevm.cc.utils.DigestWriter;
import org.hivevm.cc.utils.Template;
import org.hivevm.cc.utils.TemplateProvider;

/**
 * The {@link CppWriter} class.
 */
public class CppWriter extends SourceWriter {

  private final TemplateProvider provider;
  private final StringWriter     writer;
  private final StringWriter     header = new StringWriter();


  /**
   * Constructs an instance of {@link CppWriter}.
   *
   * @param name
   * @param options
   */
  public CppWriter(String name, CppTemplate provider, DigestOptions options) {
    super(name, provider, options);
    this.provider = provider.getHeader();
    this.writer = (StringWriter) this.out;
    this.header.append("#ifndef JAVACC_" + name.replace('.', '_').toUpperCase() + "_H\n");
    this.header.append("#define JAVACC_" + name.replace('.', '_').toUpperCase() + "_H\n");
  }

  public final void switchToHeader() {
    this.out = this.header;
  }

  /**
   * Write the content using a template.
   *
   * @param path
   */
  public final void writeTemplateHeader() throws IOException {
    Template template = Template.of(this.provider, getOptions());
    template.render(new PrintWriter(this.out));
  }

  @Override
  public final void close() {
    // dump the statics into the main file with the code.
    StringBuffer buffer = new StringBuffer();
    buffer.append(this.writer.toString());

    saveOutput(this.provider, this.header.getBuffer(), getOptions());
    saveOutput(getProvider(), buffer, getOptions());
  }

  private void saveOutput(TemplateProvider provider, StringBuffer buffer, DigestOptions options) {
    CppWriter.fixupLongLiterals(buffer);

    try (DigestWriter writer = provider.createDigestWriter(getName(), options)) {
      writer.print(buffer.toString());
    } catch (IOException ioe) {
      JavaCCErrors.fatal("Could not create output file: " + provider.getFilename(getName()));
    }
  }


  private static boolean isHexDigit(char c) {
    return ((c >= '0') && (c <= '9')) || ((c >= 'a') && (c <= 'f')) || ((c >= 'A') && (c <= 'F'));
  }

  // HACK
  private static void fixupLongLiterals(StringBuffer buffer) {
    for (int i = 0; i < (buffer.length() - 1); i++) {
      char c1 = buffer.charAt(i);
      char c2 = buffer.charAt(i + 1);
      if (Character.isDigit(c1) || ((c1 == '0') && (c2 == 'x'))) {
        i += c1 == '0' ? 2 : 1;
        while (CppWriter.isHexDigit(buffer.charAt(i))) {
          i++;
        }
        if (buffer.charAt(i) == 'L') {
          buffer.insert(i, "UL");
        }
        i++;
      }
    }
  }
}
