// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.doc;

import org.hivevm.cc.HiveCCTools;
import org.hivevm.cc.parser.JavaCCData;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.JavaCCParser;
import org.hivevm.cc.parser.JavaCCParserDefault;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.parser.StreamProvider;

import java.io.FileInputStream;
import java.text.ParseException;

/**
 * Main class.
 */
public final class JJDocMain extends JJDocGlobals {

  private JJDocMain() {}

  private static void help_message() {
    JJDocGlobals.info("");
    JJDocGlobals.info("    jjdoc option-settings - (to read from standard input)");
    JJDocGlobals.info("OR");
    JJDocGlobals.info("    jjdoc option-settings inputfile (to read from a file)");
    JJDocGlobals.info("");
    JJDocGlobals.info("WHERE");
    JJDocGlobals.info("    \"option-settings\" is a sequence of settings separated by spaces.");
    JJDocGlobals.info("");

    JJDocGlobals.info("Each option setting must be of one of the following forms:");
    JJDocGlobals.info("");
    JJDocGlobals.info("    -optionname=value (e.g., -TEXT=false)");
    JJDocGlobals.info("    -optionname:value (e.g., -TEXT:false)");
    JJDocGlobals.info("    -optionname       (equivalent to -optionname=true.  e.g., -TEXT)");
    JJDocGlobals.info("    -NOoptionname     (equivalent to -optionname=false. e.g., -NOTEXT)");
    JJDocGlobals.info("");
    JJDocGlobals.info("Option settings are not case-sensitive, so one can say \"-nOtExT\" instead");
    JJDocGlobals.info("of \"-NOTEXT\".  Option values must be appropriate for the corresponding");
    JJDocGlobals.info("option, and must be either an integer, boolean or string value.");
    JJDocGlobals.info("");
    JJDocGlobals.info("The string valued options are:");
    JJDocGlobals.info("");
    JJDocGlobals.info("    OUTPUT_FILE");
    JJDocGlobals.info("    CSS");
    JJDocGlobals.info("");
    JJDocGlobals.info("The boolean valued options are:");
    JJDocGlobals.info("");
    JJDocGlobals.info("    ONE_TABLE              (default true)");
    JJDocGlobals.info("    TEXT                   (default false)");
    JJDocGlobals.info("    BNF                    (default false)");
    JJDocGlobals.info("");

    JJDocGlobals.info("");
    JJDocGlobals.info("EXAMPLES:");
    JJDocGlobals.info("    jjdoc -ONE_TABLE=false mygrammar.jj");
    JJDocGlobals.info("    jjdoc - < mygrammar.jj");
    JJDocGlobals.info("");
    JJDocGlobals.info("ABOUT JJDoc:");
    JJDocGlobals.info("    JJDoc generates JavaDoc documentation from JavaCC grammar files.");
    JJDocGlobals.info("");
    JJDocGlobals.info("    For more information, see the online JJDoc documentation at");
    JJDocGlobals.info("    https://javacc.dev.java.net/doc/JJDoc.html");
  }

  /**
   * A main program that exercises the parser.
   */
  public static void main(String args[]) throws Exception {
    JavaCCErrors.reInit();
    Options options = new JJDocOptions();

    HiveCCTools.bannerLine("Documentation Generator", "0.1.4");

    JavaCCParser parser = null;
    if (args.length == 0) {
      JJDocMain.help_message();
      System.exit(1);
    } else {
      JJDocGlobals.info("(type \"jjdoc\" with no arguments for help)");
    }

    if (options.isOption(args[args.length - 1])) {
      JJDocGlobals.error("Last argument \"" + args[args.length - 1] + "\" is not a filename or \"-\".  ");
      System.exit(1);
    }
    for (int arg = 0; arg < (args.length - 1); arg++) {
      if (!options.isOption(args[arg])) {
        JJDocGlobals.error("Argument \"" + args[arg] + "\" must be an option setting.  ");
        System.exit(1);
      }
      options.setCmdLineOption(args[arg]);
    }

    if (args[args.length - 1].equals("-")) {
      JJDocGlobals.info("Reading from standard input . . .");
      parser = new JavaCCParserDefault(new StreamProvider(new java.io.DataInputStream(System.in)), options);
      JJDocGlobals.input_file = "standard input";
      JJDocGlobals.output_file = "standard output";
    } else {
      JJDocGlobals.info("Reading from file " + args[args.length - 1] + " . . .");
      try {
        java.io.File fp = new java.io.File(args[args.length - 1]);
        if (!fp.exists()) {
          JJDocGlobals.error("File " + args[args.length - 1] + " not found.");
        }
        if (fp.isDirectory()) {
          JJDocGlobals.error(args[args.length - 1] + " is a directory. Please use a valid file name.");
        }
        JJDocGlobals.input_file = fp.getName();
        parser = new JavaCCParserDefault(
            new StreamProvider(new FileInputStream(args[args.length - 1]), options.getGrammarEncoding()), options);
      } catch (SecurityException se) {
        JJDocGlobals.error("Security violation while trying to open " + args[args.length - 1]);
      } catch (java.io.FileNotFoundException e) {
        JJDocGlobals.error("File " + args[args.length - 1] + " not found.");
      }
    }

    JavaCCData javacc = new JavaCCData(false, options);
    try {
      parser.initialize(javacc);
      parser.javacc_input();

      JJDoc.start(javacc);

      if (!JavaCCErrors.hasError()) {
        if (!JavaCCErrors.hasWarning()) {
          JJDocGlobals.info("Grammar documentation generated successfully in " + JJDocGlobals.output_file);
        } else {
          JJDocGlobals.info(
              "Grammar documentation generated with 0 errors and " + JavaCCErrors.get_warning_count() + " warnings.");
        }
        System.exit(0);
      } else {
        JJDocGlobals.error("Detected " + JavaCCErrors.get_error_count() + " errors and "
            + JavaCCErrors.get_warning_count() + " warnings.");
        System.exit((JavaCCErrors.get_error_count() == 0) ? 0 : 1);
      }
    } catch (ParseException e) {
      JJDocGlobals.error(e.toString());
      JJDocGlobals.error("Detected " + JavaCCErrors.get_error_count() + " errors and "
          + JavaCCErrors.get_warning_count() + " warnings.");
      System.exit(1);
    }
  }

}
