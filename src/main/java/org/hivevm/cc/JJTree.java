// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.ParseException;

import org.hivevm.cc.generator.ParserEngine;
import org.hivevm.cc.jjtree.ASTGrammar;
import org.hivevm.cc.jjtree.ASTWriter;
import org.hivevm.cc.jjtree.JJTreeGlobals;
import org.hivevm.cc.jjtree.JJTreeOptions;
import org.hivevm.cc.jjtree.JJTreeParserDefault;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.Options;

public class JJTree {

  private static void help_message() {
    System.out.println("Usage:");
    System.out.println("    jjtree option-settings inputfile");
    System.out.println("");
    System.out.println("\"option-settings\" is a sequence of settings separated by spaces.");
    System.out.println("Each option setting must be of one of the following forms:");
    System.out.println("");
    System.out.println("    -optionname=value (e.g., -STATIC=false)");
    System.out.println("    -optionname:value (e.g., -STATIC:false)");
    System.out.println("    -optionname       (equivalent to -optionname=true.  e.g., -STATIC)");
    System.out.println("    -NOoptionname     (equivalent to -optionname=false. e.g., -NOSTATIC)");
    System.out.println("");
    System.out.println("Option settings are not case-sensitive, so one can say \"-nOsTaTiC\" instead");
    System.out.println("of \"-NOSTATIC\".  Option values must be appropriate for the corresponding");
    System.out.println("option, and must be either an integer or a string value.");
    System.out.println("");

    System.out.println("The boolean valued options are:");
    System.out.println("");
    System.out.println("    STATIC                   (default true)");
    System.out.println("    MULTI                    (default false)");
    System.out.println("    NODE_DEFAULT_VOID        (default false)");
    System.out.println("    NODE_SCOPE_HOOK          (default false)");
    System.out.println("    TRACK_TOKENS             (default false)");
    System.out.println("    VISITOR                  (default false)");
    System.out.println("");
    System.out.println("The string valued options are:");
    System.out.println("");
    System.out.println("    JDK_VERSION              (default \"1.5\")");
    System.out.println("    NODE_CLASS               (default \"\")");
    System.out.println("    NODE_PREFIX              (default \"AST\")");
    System.out.println("    NODE_PACKAGE             (default \"\")");
    System.out.println("    NODE_EXTENDS             (default \"\")");
    System.out.println("    NODE_FACTORY             (default \"\")");
    System.out.println("    OUTPUT_FILE              (default remove input file suffix, add .jj)");
    System.out.println("    OUTPUT_DIRECTORY         (default \"\")");
    System.out.println("    VISITOR_DATA_TYPE        (default \"\")");
    System.out.println("    VISITOR_RETURN_TYPE      (default \"Object\")");
    System.out.println("    VISITOR_EXCEPTION        (default \"\")");
    System.out.println("");
    System.out.println("JJTree also accepts JavaCC options, which it inserts into the generated file.");
    System.out.println("");

    System.out.println("EXAMPLES:");
    System.out.println("    jjtree -STATIC=false mygrammar.jjt");
    System.out.println("");
    System.out.println("ABOUT JJTree:");
    System.out.println("    JJTree is a preprocessor for JavaCC that inserts actions into a");
    System.out.println("    JavaCC grammar to build parse trees for the input.");
    System.out.println("");
    System.out.println("    For more information, see the online JJTree documentation at ");
    System.out.println("    https://javacc.dev.java.net/doc/JJTree.html ");
    System.out.println("");
  }

  /**
   * A main program that exercises the parser.
   */
  public static void main(String args[]) {
    JJMain.bannerLine("Tree Builder", "");

    JavaCCErrors.reInit();
    JJTreeOptions options = new JJTreeOptions();
    JJTreeGlobals.initialize();

    if (args.length == 0) {
      System.out.println("");
      JJTree.help_message();
      System.exit(1);
    } else {
      System.out.println("(type \"jjtree\" with no arguments for help)");
    }
    String fn = args[args.length - 1];

    if (options.isOption(fn)) {
      System.out.println("Last argument \"" + fn + "\" is not a filename");
      System.exit(1);
    }
    for (int arg = 0; arg < (args.length - 1); arg++) {
      if (!options.isOption(args[arg])) {
        System.out.println("Argument \"" + args[arg] + "\" must be an option setting.");
        System.exit(1);
      }
      options.setCmdLineOption(args[arg]);
    }

    options.validate();

    JJMain.createOutputDir(options.getOutputDirectory());
    File file = new File(options.getOutputDirectory(), JJTree.create_output_file_name(fn, options));

    try {
      if (JJMain.isGeneratedBy("JJTree", fn)) {
        throw new IOException(fn + " was generated by jjtree.  Cannot run jjtree again.");
      }

      System.out.println("Reading from file " + fn + " ...");

      try (Reader reader = new InputStreamReader(new FileInputStream(fn), options.getGrammarEncoding())) {
        JJTreeParserDefault parser = new JJTreeParserDefault(reader, options);
        ASTGrammar root = parser.parse();
        if (Boolean.getBoolean("jjtree-dump")) {
          root.dump(" ");
        }

        System.out.println("opt:" + Options.getOutputLanguage());

        ParserEngine engine = ParserEngine.create(Options.getOutputLanguage());

        try (ASTWriter writer = new ASTWriter(file, Options.getOutputLanguage())) {
          JJMain.writeGenerated(writer);
          engine.generateJJTree(root, writer, options);
        } catch (IOException ioe) {
          System.out.println("Error setting input: " + ioe.getMessage());
          System.exit(1);
        }

        System.out.println("Annotated grammar generated successfully in " + file.toString());

      } catch (ParseException pe) {
        System.out.println("Error parsing input: " + pe.toString());
        System.exit(1);
      } catch (Exception e) {
        System.out.println("Error parsing input: " + e.toString());
        e.printStackTrace(System.out);
        System.exit(1);
      }

    } catch (IOException ioe) {
      System.out.println("Error setting input: " + ioe.getMessage());
      System.exit(1);
    }
  }

  private static String create_output_file_name(String i, JJTreeOptions options) {
    String o = options.getOutputFile();

    if (o.equals("")) {
      int s = i.lastIndexOf(File.separatorChar);
      if (s >= 0) {
        i = i.substring(s + 1);
      }

      int di = i.lastIndexOf('.');
      if (di == -1) {
        o = i + ".jj";
      } else {
        String suffix = i.substring(di);
        if (suffix.equals(".jj")) {
          o = i + ".jj";
        } else {
          o = i.substring(0, di) + ".jj";
        }
      }
    }

    return o;
  }
}
