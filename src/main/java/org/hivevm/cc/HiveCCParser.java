// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.text.ParseException;

import org.hivevm.cc.generator.ParserEngine;
import org.hivevm.cc.parser.JavaCCData;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.JavaCCParser;
import org.hivevm.cc.parser.JavaCCParserDefault;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.parser.StreamProvider;
import org.hivevm.cc.semantic.Semanticize;

/**
 * Entry point.
 */
public abstract class HiveCCParser {

  private HiveCCParser() {}

  /**
   * A main program that exercises the parser.
   */
  public static void main(String args[]) throws Exception {
    if ((args.length == 1) && args[args.length - 1].equalsIgnoreCase("-version")) {
      System.out.println(HiveCC.VERSION.toString());
      System.exit(0);
    }

    HiveCCTools.bannerLine("Parser Generator", "");

    JavaCCErrors.reInit();

    if (args.length == 0) {
      System.err.println("");
      System.err.println("Missing arguments");
      System.exit(1);
    }

    Options options = new Options();
    if (options.isOption(args[args.length - 1])) {
      System.out.println("Last argument \"" + args[args.length - 1] + "\" is not a filename.");
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
      InputStreamReader reader =
          new InputStreamReader(new FileInputStream(args[args.length - 1]), options.getGrammarEncoding());
      JavaCCParser parser = new JavaCCParserDefault(new StreamProvider(reader), options);

      try {
        String jjFile = args[args.length - 1];
        System.out.println("Reading from file " + jjFile + " . . .");

        JavaCCData request = new JavaCCData(HiveCCTree.isGenerated(jjFile), options);

        parser.initialize(request);
        parser.javacc_input();

        // Initialize the parser data
        ParserEngine engine = ParserEngine.create(Options.getOutputLanguage());
        HiveCCTools.createOutputDir(options.getOutputDirectory());
        Semanticize.semanticize(request, options);
        options.setStringOption(HiveCC.PARSER_NAME, request.getParserName());
        engine.generate(request);

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
      } catch (ParseException e) {
        System.out.println(e.toString());
        System.out.println("Detected " + (JavaCCErrors.get_error_count() + 1) + " errors and "
            + JavaCCErrors.get_warning_count() + " warnings.");
        System.exit(1);
      }
    } catch (SecurityException se) {
      System.out.println("Security violation while trying to open " + args[args.length - 1]);
      System.exit(1);
    } catch (FileNotFoundException e) {
      System.out.println("File " + args[args.length - 1] + " not found.");
      System.exit(1);
    }
  }
}
