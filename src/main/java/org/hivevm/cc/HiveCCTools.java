// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;

import org.hivevm.cc.parser.JavaCCErrors;

import java.io.File;

/**
 * The {@link HiveCCTools} class.
 */
public interface HiveCCTools {

  /**
   * This prints the banner line when the various tools are invoked. This takes as argument the
   * tool's full name and its version.
   */
  static void bannerLine(String fullName, String ver) {
    System.out.print("Java Compiler Compiler Version " + HiveCC.VERSION.toString() + " (" + fullName);
    if (!ver.equals("")) {
      System.out.print(" Version " + ver);
    }
    System.out.println(")");
  }

  static void createOutputDir(File outputDir) {
    if (!outputDir.exists()) {
      JavaCCErrors.warning("Output directory \"" + outputDir + "\" does not exist. Creating the directory.");

      if (!outputDir.mkdirs()) {
        JavaCCErrors.semantic_error("Cannot create the output directory : " + outputDir);
        return;
      }
    }

    if (!outputDir.isDirectory()) {
      JavaCCErrors.semantic_error("\"" + outputDir + " is not a valid output directory.");
      return;
    }

    if (!outputDir.canWrite()) {
      JavaCCErrors.semantic_error("Cannot write to the output output directory : \"" + outputDir + "\"");
    }
  }
}
