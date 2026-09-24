// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/JavaCCGlobals.java

package org.hivevm.waggle.api;



import org.hivevm.source.OutputSink;
import org.hivevm.waggle.diag.Diagnostics;
import org.hivevm.waggle.codegen.GeneratorProvider;
import org.hivevm.waggle.grammar.GrammarData;
import org.hivevm.waggle.grammar.GrammarParser;
import org.hivevm.waggle.grammar.StringProvider;
import org.hivevm.waggle.analysis.Semanticize;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.hivevm.waggle.grammar.ParseException;

/**
 * Turns one grammar into one set of generated sources.
 *
 * <p>Each run owns its {@link GenerationContext} — its options and its {@link Diagnostics} — so two
 * compilations in the same JVM, in sequence or in parallel, cannot see each other (ADR-0015).
 */
public class WaggleCompiler {

    private final GenerationRequest request;
    private final Diagnostics diagnostics;
    private final OutputSink sink;

    public WaggleCompiler(GenerationRequest request) {
        this(request, new Diagnostics());
    }

    public WaggleCompiler(GenerationRequest request, Diagnostics diagnostics) {
        this(request, diagnostics, null);
    }

    /** Compiles into {@code sink}; a {@code null} sink writes files (ADR-0018). */
    public WaggleCompiler(GenerationRequest request, Diagnostics diagnostics, OutputSink sink) {
        this.request = request;
        this.diagnostics = diagnostics;
        this.sink = sink;
    }

    /** What this compilation had to say about the grammar. */
    public final Diagnostics diagnostics() {
        return this.diagnostics;
    }

    /**
     * Reads a grammar file; JJDoc reads it the same way.
     *
     * <p>The charset is fixed to UTF-8 and line endings are normalised to {@code \n}. Both matter for
     * reproducibility: action text is copied verbatim into the generated source, so reading with the
     * platform default charset — or on a CRLF checkout — made the output, and therefore the checksum
     * of every generated file, depend on the machine it was generated on.
     */
    public static String readGrammar(File file) throws IOException {
        return WaggleCompiler.normalizeLineEnds(Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }

    /** The text with every CR LF and every lone CR turned into LF. */
    public static String normalizeLineEnds(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /**
     * Run the parser generator.
     */
    public final void parse() {
        var grammarFile = this.request.grammarFile();

        try {
            var text = WaggleCompiler.readGrammar(grammarFile);

            WaggleCompiler.bannerLine("Parser Generator");

            var context = GenerationContext.of(this.request, this.diagnostics, this.sink);
            var options = context.options();
            var data = new GrammarData(context);
            var parser = new GrammarParser(new StringProvider(text), options);
            parser.initialize(data);
            parser.grammar_input();

            // The lookahead trace is written with the parser trace's methods, so it implies it, as
            // it did in JavaCC (Options.normalize).
            if (options.booleanValue(Waggle.DEBUG_LOOKAHEAD)) {
                options.set(Waggle.DEBUG_PARSER, Boolean.TRUE);
            }

            // Initialize the parser data
            createOutputDir(options.getOutputDirectory());
            Semanticize.semanticize(data, options);
            options.set(Waggle.PARSER_NAME, data.getParserName());
            var generator = GeneratorProvider.generatorFor(options.getOutputLanguage());
            generator.generate(data);
        } catch (ParseException | IOException e) {
            // Swallowing this used to let the code fall through to the verdict below, which only
            // consults the diagnostics — untouched by an I/O failure. A missing grammar therefore
            // printed "Parser generated successfully." and produced nothing.
            //
            // ParseException is now only the generated parser's own: the grammar did not parse
            // (ADR-0022). Neither type carries a message of this pipeline's, so the wrapper says
            // everything, exactly as before.
            throw new GenerationException("Failed to generate a parser from " + grammarFile, e);
        } catch (GenerationException e) {
            // A stage refused, and said why. It is already this pipeline's failure type, so the
            // wrapper only adds which grammar it was about — burying the stage's own message in a
            // cause would lose the one sentence a caller wants (ADR-0022).
            throw new GenerationException(
                    "Failed to generate a parser from " + grammarFile + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            // Anything else that escapes a stage -- a template that does not render, a sink that
            // cannot write, an option of the wrong type -- is still a failed generation, and the
            // caller catches one type for that (ADR-0011, ADR-0022).
            throw new GenerationException(
                    "Failed to generate a parser from " + grammarFile + ": " + e, e);
        }

        if (this.diagnostics.hasError()) {
            throw new GenerationException(
                    "Failed to generate a parser from " + grammarFile + ": detected "
                            + this.diagnostics.errorCount() + " error(s) and "
                            + this.diagnostics.warningCount() + " warning(s)");
        }

        if (this.diagnostics.hasWarning()) {
            System.out.printf("Parser generated with 0 errors and %s warnings.\n",
                    this.diagnostics.warningCount());
        } else {
            System.out.println("Parser generated successfully.");
        }
    }

    private void createOutputDir(File outputDir) {
        if (!outputDir.exists()) {
            this.diagnostics.warning(
                    "Output directory \"" + outputDir + "\" does not exist. Creating the directory.");

            if (!outputDir.mkdirs()) {
                this.diagnostics.error("Cannot create the output directory : " + outputDir);
                return;
            }
        }

        if (!outputDir.isDirectory()) {
            this.diagnostics.error("\"" + outputDir + " is not a valid output directory.");
            return;
        }

        if (!outputDir.canWrite()) {
            this.diagnostics.error(
                    "Cannot write to the output output directory : \"" + outputDir + "\"");
        }
    }

    /**
     * This prints the banner line when the various tools are invoked. This takes as argument the
     * tool's full name and its version.
     */
    public static void bannerLine(String fullName) {
        System.out.printf("Waggle Version %s (%s)\n",
                WaggleVersion.VERSION.toString(), fullName);
    }
}
