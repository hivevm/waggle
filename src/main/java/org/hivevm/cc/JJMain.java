// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hivevm.cc.parser.JavaCCErrors;

/**
 * The {@link JJMain} class.
 */
public abstract class JJMain {

  private static final Pattern GENERATED = Pattern.compile("@generated\\(([^\\)]+)\\)");

  /**
   * Constructs an instance of {@link JJMain}.
   */
  private JJMain() {}


  /**
   * This prints the banner line when the various tools are invoked. This takes as argument the
   * tool's full name and its version.
   */
  public static void bannerLine(String fullName, String ver) {
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

  private static List<String> readToolNameList(String str) {
    Matcher matcher = JJMain.GENERATED.matcher(str);
    while (matcher.find()) {
      return Arrays.asList(matcher.group(1).split(","));
    }
    return Collections.emptyList();
  }

  /**
   * Returns true if tool name passed is one of the tool names returned by getToolNames(fileName).
   *
   * @throws IOException
   * @throws FileNotFoundException
   */
  static boolean isGeneratedBy(String toolName, String fileName) {
    try (InputStream stream = new FileInputStream(fileName)) {
      String data = new String(stream.readAllBytes());
      for (String element : JJMain.readToolNameList(data)) {
        if (toolName.equals(element)) {
          return true;
        }
      }
    } catch (IOException e) {}
    return false;
  }

  static final void writeGenerated(PrintWriter writer) {
    writer.println("/* @generated(JJTree) */");
  }
}
