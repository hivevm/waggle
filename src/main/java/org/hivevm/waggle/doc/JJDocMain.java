// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.doc;

import org.hivevm.waggle.api.WaggleCompiler;

import org.hivevm.waggle.api.GenerationContext;
import org.hivevm.waggle.api.WaggleOptions;
import org.hivevm.waggle.diag.Diagnostics;
import org.hivevm.waggle.grammar.GrammarData;
import org.hivevm.waggle.grammar.GrammarParser;
import org.hivevm.waggle.grammar.Parser;
import org.hivevm.waggle.grammar.StreamProvider;

import java.io.FileInputStream;
import org.hivevm.waggle.grammar.ParseException;

/**
 * The command line of JJDoc: it reads the arguments, runs one documentation pass and reports the
 * verdict.
 *
 * <p>The two printing methods below were {@code JJDocGlobals}, a class this one and {@link JJDoc}
 * both extended purely to reach its statics. What belongs on a command line — the help text, the
 * progress notes, the closing verdict — lives here now; what is about the grammar goes to
 * {@link Diagnostics} like every other stage's does (ADR-0015).
 */
public final class JJDocMain {

    private JJDocMain() {
    }

    /** Writes a note about the run itself to standard output. */
    private static void info(String message) {
        System.out.println(message);
    }

    /** Writes a failure of the run itself to standard error. */
    private static void error(String message) {
        System.err.println(message);
    }

    private static void help_message() {
        JJDocMain.info("");
        JJDocMain.info("    jjdoc option-settings - (to read from standard input)");
        JJDocMain.info("OR");
        JJDocMain.info("    jjdoc option-settings inputfile (to read from a file)");
        JJDocMain.info("");
        JJDocMain.info("WHERE");
        JJDocMain.info("    \"option-settings\" is a sequence of settings separated by spaces.");
        JJDocMain.info("");

        JJDocMain.info("Each option setting must be of one of the following forms:");
        JJDocMain.info("");
        JJDocMain.info("    -optionname=value (e.g., -TEXT=false)");
        JJDocMain.info("    -optionname:value (e.g., -TEXT:false)");
        JJDocMain.info("    -optionname       (equivalent to -optionname=true.  e.g., -TEXT)");
        JJDocMain.info("    -NOoptionname     (equivalent to -optionname=false. e.g., -NOTEXT)");
        JJDocMain.info("");
        JJDocMain.info(
                "Option settings are not case-sensitive, so one can say \"-nOtExT\" instead");
        JJDocMain.info(
                "of \"-NOTEXT\".  Option values must be appropriate for the corresponding");
        JJDocMain.info("option, and must be either an integer, boolean or string value.");
        JJDocMain.info("");
        JJDocMain.info("The string valued options are:");
        JJDocMain.info("");
        JJDocMain.info("    OUTPUT_FILE");
        JJDocMain.info("    CSS");
        JJDocMain.info("");
        JJDocMain.info("The boolean valued options are:");
        JJDocMain.info("");
        JJDocMain.info("    ONE_TABLE              (default true)");
        JJDocMain.info("    TEXT                   (default false)");
        JJDocMain.info("    BNF                    (default false)");
        JJDocMain.info("");

        JJDocMain.info("");
        JJDocMain.info("EXAMPLES:");
        JJDocMain.info("    jjdoc -ONE_TABLE=false mygrammar.waggle");
        JJDocMain.info("    jjdoc - < mygrammar.waggle");
        JJDocMain.info("");
        JJDocMain.info("ABOUT JJDoc:");
        JJDocMain.info("    JJDoc generates JavaDoc documentation from JavaCC grammar files.");
        JJDocMain.info("");
        JJDocMain.info("    For more information, see the online JJDoc documentation at");
        JJDocMain.info("    https://javacc.dev.java.net/doc/JJDoc.html");
    }

    /**
     * A main program that exercises the parser.
     */
    public static void main(String[] args) throws Exception {
        var diagnostics = new Diagnostics();
        WaggleOptions options = new WaggleOptions();
        String inputFile = "standard input";

        WaggleCompiler.bannerLine("Documentation Generator");

        Parser parser = null;
        if (args.length == 0) {
            JJDocMain.help_message();
            System.exit(1);
        } else {
            JJDocMain.info("(type \"jjdoc\" with no arguments for help)");
        }

        if (options.isOption(args[args.length - 1])) {
            JJDocMain.error(
                    "Last argument \"" + args[args.length - 1] + "\" is not a filename or \"-\".  ");
            System.exit(1);
        }
        for (int arg = 0; arg < (args.length - 1); arg++) {
            if (!options.isOption(args[arg])) {
                JJDocMain.error("Argument \"" + args[arg] + "\" must be an option setting.  ");
                System.exit(1);
            }
            options.setCmdLineOption(diagnostics, args[arg]);
        }

        if (args[args.length - 1].equals("-")) {
            JJDocMain.info("Reading from standard input . . .");
            parser = new GrammarParser(
                    new StreamProvider(new java.io.DataInputStream(System.in)),
                    options);
        } else {
            JJDocMain.info("Reading from file " + args[args.length - 1] + " . . .");
            try {
                java.io.File fp = new java.io.File(args[args.length - 1]);
                if (!fp.exists()) {
                    JJDocMain.error("File " + args[args.length - 1] + " not found.");
                }
                if (fp.isDirectory()) {
                    JJDocMain.error(
                            args[args.length - 1] + " is a directory. Please use a valid file name.");
                }
                inputFile = fp.getName();
                parser = new GrammarParser(
                        new StreamProvider(new FileInputStream(args[args.length - 1]),
                                WaggleOptions.getFileEncoding()), options);
            } catch (SecurityException se) {
                JJDocMain.error(
                        "Security violation while trying to open " + args[args.length - 1]);
            } catch (java.io.FileNotFoundException e) {
                JJDocMain.error("File " + args[args.length - 1] + " not found.");
            }
        }

        GrammarData javacc = new GrammarData(new GenerationContext(options, diagnostics));
        try {
            parser.initialize(javacc);
            parser.grammar_input();

            var generator = JJDoc.start(javacc, inputFile);

            if (!diagnostics.hasError()) {
                if (!diagnostics.hasWarning()) {
                    JJDocMain.info(
                            "Grammar documentation generated successfully in "
                                    + generator.outputFile());
                } else {
                    JJDocMain.info(
                            "Grammar documentation generated with 0 errors and "
                                    + diagnostics.warningCount() + " warnings.");
                }
                System.exit(0);
            } else {
                JJDocMain.error("Detected " + diagnostics.errorCount() + " errors and "
                        + diagnostics.warningCount() + " warnings.");
                System.exit((diagnostics.errorCount() == 0) ? 0 : 1);
            }
        } catch (ParseException e) {
            JJDocMain.error(e.toString());
            JJDocMain.error("Detected " + diagnostics.errorCount() + " errors and "
                    + diagnostics.warningCount() + " warnings.");
            System.exit(1);
        }
    }

}
