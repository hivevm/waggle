// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/jjdoc/JJDocMain.java, org/javacc/parser/Main.java

package org.hivevm.waggle.doc;

import org.hivevm.waggle.api.WaggleCompiler;

import org.hivevm.waggle.api.GenerationContext;
import org.hivevm.waggle.api.WaggleOptions;
import org.hivevm.waggle.diag.Diagnostics;
import org.hivevm.waggle.grammar.GrammarData;
import org.hivevm.waggle.grammar.GrammarParser;
import org.hivevm.waggle.grammar.Parser;
import org.hivevm.waggle.grammar.StringProvider;
import org.hivevm.waggle.api.GenerationException;
import org.hivevm.waggle.grammar.ParseException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
        JJDocMain.info("    -optionname=value (e.g., -OUTPUT_FILE=grammar.bnf)");
        JJDocMain.info("    -optionname:value (e.g., -OUTPUT_FILE:grammar.bnf)");
        JJDocMain.info("");
        JJDocMain.info("Option names are not case-sensitive.");
        JJDocMain.info("");
        JJDocMain.info("The options are:");
        JJDocMain.info("");
        JJDocMain.info("    OUTPUT_FILE            (default: the grammar file with the extension .bnf)");
        JJDocMain.info("");
        JJDocMain.info("EXAMPLES:");
        JJDocMain.info("    jjdoc mygrammar.waggle");
        JJDocMain.info("    jjdoc - < mygrammar.waggle");
        JJDocMain.info("");
        JJDocMain.info("ABOUT JJDoc:");
        JJDocMain.info("    JJDoc writes the productions of a Waggle grammar as BNF.");
    }

    /**
     * The command line of JJDoc.
     */
    public static void main(String[] args) {
        System.exit(JJDocMain.run(args));
    }

    /**
     * Runs JJDoc on the command line {@code args} and returns the exit status: 0 on success, 1 on
     * failure. Split from {@link #main} so that it can be run without ending the JVM.
     */
    static int run(String[] args) {
        var diagnostics = new Diagnostics();
        WaggleOptions options = new WaggleOptions();
        String inputFile = "standard input";

        WaggleCompiler.bannerLine("Documentation Generator");

        if (args.length == 0) {
            JJDocMain.help_message();
            return 1;
        }
        JJDocMain.info("(type \"jjdoc\" with no arguments for help)");

        if (options.isOption(args[args.length - 1])) {
            JJDocMain.error(
                    "Last argument \"" + args[args.length - 1] + "\" is not a filename or \"-\".  ");
            return 1;
        }
        for (int arg = 0; arg < (args.length - 1); arg++) {
            if (!options.isOption(args[arg])) {
                JJDocMain.error("Argument \"" + args[arg] + "\" must be an option setting.  ");
                return 1;
            }
            options.setCmdLineOption(diagnostics, args[arg]);
        }

        // Read as the parser generator reads a grammar: UTF-8, with line ends normalised. The file
        // used to be read in the platform encoding, and a missing one was reported and then parsed
        // anyway, which ended in a NullPointerException.
        String text;
        String name = args[args.length - 1];
        try {
            if (name.equals("-")) {
                JJDocMain.info("Reading from standard input . . .");
                text = WaggleCompiler.normalizeLineEnds(
                        new String(System.in.readAllBytes(), StandardCharsets.UTF_8));
            } else {
                JJDocMain.info("Reading from file " + name + " . . .");
                var file = new File(name);
                if (!file.isFile()) {
                    JJDocMain.error(file.isDirectory()
                            ? name + " is a directory. Please use a valid file name."
                            : "File " + name + " not found.");
                    return 1;
                }
                inputFile = file.getName();
                text = WaggleCompiler.readGrammar(file);
            }
        } catch (IOException e) {
            JJDocMain.error("Cannot read " + name + ": " + e.getMessage());
            return 1;
        }

        GrammarData javacc = new GrammarData(new GenerationContext(options, diagnostics));
        try {
            Parser parser = new GrammarParser(new StringProvider(text), options);
            parser.initialize(javacc);
            parser.grammar_input();

            var generator = JJDoc.start(javacc, inputFile);

            if (!diagnostics.hasError()) {
                if (!diagnostics.hasWarning()) {
                    JJDocMain.info("Grammar documentation generated successfully in "
                            + generator.outputFile());
                } else {
                    JJDocMain.info("Grammar documentation generated with 0 errors and "
                            + diagnostics.warningCount() + " warnings.");
                }
                return 0;
            }
            JJDocMain.error("Detected " + diagnostics.errorCount() + " errors and "
                    + diagnostics.warningCount() + " warnings.");
            return 1;
        } catch (ParseException | GenerationException e) {
            // JJDoc reports a broken grammar as a GenerationException too (ADR-0022).
            JJDocMain.error(e.toString());
            JJDocMain.error("Detected " + diagnostics.errorCount() + " errors and "
                    + diagnostics.warningCount() + " warnings.");
            return 1;
        }
    }

}
