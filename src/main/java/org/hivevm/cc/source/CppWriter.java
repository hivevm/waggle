// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.source;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.utils.DigestOptions;
import org.hivevm.cc.utils.DigestWriter;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

/**
 * The {@link CppWriter} class.
 */
public class CppWriter extends SourceWriter {

  private final StringWriter writer;
  private final StringWriter header = new StringWriter();


  /**
   * Constructs an instance of {@link CppWriter}.
   *
   * @param name
   * @param options
   */
  public CppWriter(String name, DigestOptions options) {
    super(name, options);
    this.writer = (StringWriter) this.out;
    this.header.append("#ifndef JAVACC_" + name.replace('.', '_').toUpperCase() + "_H\n");
    this.header.append("#define JAVACC_" + name.replace('.', '_').toUpperCase() + "_H\n");
  }

  public final void switchToHeader() {
    this.out = this.header;
  }

  @Override
  public final void saveOutput(File path) {
    // dump the statics into the main file with the code.
    StringBuffer buffer = new StringBuffer();
    buffer.append(this.writer.toString());

    File file = new File(path, getName() + ".h");
    saveOutput(file, this.header.getBuffer(), getOptions());

    file = new File(path, getName() + ".cc");
    saveOutput(file, buffer, getOptions());
  }

  private void saveOutput(File file, StringBuffer buffer, DigestOptions options) {
    CppWriter.fixupLongLiterals(buffer);

    try (DigestWriter writer = DigestWriter.createCpp(file, HiveCC.VERSION, options)) {
      writer.print(buffer.toString());
    } catch (IOException ioe) {
      JavaCCErrors.fatal("Could not create output file: " + file);
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
