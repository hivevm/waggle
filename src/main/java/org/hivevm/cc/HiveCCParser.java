// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;

import org.hivevm.cc.generator.Generator;
import org.hivevm.cc.generator.GeneratorProvider;
import org.hivevm.cc.parser.JavaCCData;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.JavaCCParser;
import org.hivevm.cc.parser.JavaCCParserDefault;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.parser.StreamProvider;
import org.hivevm.cc.semantic.Semanticize;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * Entry point.
 */
public abstract class HiveCCParser {

  private HiveCCParser() {}

  /**
   * A main program that exercises the parser.
   */
  public static void main(String args[]) throws Exception {
    HiveCCTools.bannerLine("Parser Generator", "");

    if (args.length == 0) {
      System.err.println("");
      System.err.println("Missing arguments");
      System.exit(1);
    }

    if ((args.length == 1) && args[args.length - 1].equalsIgnoreCase("-version")) {
      System.out.println(HiveCC.VERSION.toString());
      System.exit(0);
    }

    JavaCCErrors.reInit();

    String filename = args[args.length - 1];

    Options options = new Options();
    if (options.isOption(filename)) {
      System.out.println("Last argument \"" + filename + "\" is not a filename.");
      System.exit(1);
    }

    for (int arg = 0; arg < (args.length - 1); arg++) {
      if (!options.isOption(args[arg])) {
        System.out.println("Argument \"" + args[arg] + "\" must be an option setting.");
        System.exit(1);
      }
      options.setCmdLineOption(args[arg]);
    }

    try {
      System.out.println("Reading from file " + filename + " ...");

      Reader reader = new InputStreamReader(new FileInputStream(filename), options.getGrammarEncoding());
      JavaCCData request = new JavaCCData(HiveCCTree.isGenerated(filename), options);
      JavaCCParser parser = new JavaCCParserDefault(new StreamProvider(reader), options);
      parser.initialize(request);
      parser.javacc_input();

      // Initialize the parser data
      HiveCCTools.createOutputDir(options.getOutputDirectory());
      Semanticize.semanticize(request, options);
      options.setStringOption(HiveCC.PARSER_NAME, request.getParserName());

      Generator generator = GeneratorProvider.generatorFor(Options.getOutputLanguage());
      generator.generate(request);

      if (!JavaCCErrors.hasError()) {
        if (!JavaCCErrors.hasWarning()) {
          System.out.println("Parser generated successfully.");
        } else {
          System.out.println("Parser generated with 0 errors and " + JavaCCErrors.get_warning_count() + " warnings.");
        }
      } else {
        System.out.println("Detected " + JavaCCErrors.get_error_count() + " errors and "
            + JavaCCErrors.get_warning_count() + " warnings.");
        System.exit(JavaCCErrors.get_error_count() == 0 ? 0 : 1);
      }
    } catch (SecurityException se) {
      System.out.println("Security violation while trying to open " + filename);
      System.exit(1);
    } catch (FileNotFoundException e) {
      System.out.println("File " + filename + " not found.");
      System.exit(1);
    }
  }
}
