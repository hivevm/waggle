// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;


import org.hivevm.waggle.diag.Diagnostics;
import org.hivevm.waggle.generator.GeneratorProvider;
import org.hivevm.waggle.parser.JavaCCData;
import org.hivevm.waggle.parser.JavaCCParserDefault;
import org.hivevm.waggle.parser.StringProvider;
import org.hivevm.waggle.semantic.Semanticize;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;

/**
 * Turns one grammar into one set of generated sources.
 *
 * <p>Each run owns its {@link GenerationContext} — its options and its {@link Diagnostics} — so two
 * compilations in the same JVM, in sequence or in parallel, cannot see each other (ADR-0015).
 */
public class WaggleCompiler {

    private final GenerationRequest request;
    private final Diagnostics diagnostics;

    public WaggleCompiler(GenerationRequest request) {
        this(request, new Diagnostics());
    }

    public WaggleCompiler(GenerationRequest request, Diagnostics diagnostics) {
        this.request = request;
        this.diagnostics = diagnostics;
    }

    /** What this compilation had to say about the grammar. */
    public final Diagnostics diagnostics() {
        return this.diagnostics;
    }

    /**
     * Reads a grammar or lexical file.
     *
     * <p>The charset is fixed to UTF-8 and line endings are normalised to {@code \n}. Both matter for
     * reproducibility: action text is copied verbatim into the generated source, so reading with the
     * platform default charset — or on a CRLF checkout — made the output, and therefore the checksum
     * of every generated file, depend on the machine it was generated on.
     */
    private static String readGrammar(File file) throws IOException {
        return Files.readString(file.toPath(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").replace('\r', '\n');
    }

    /**
     * Run the parser generator.
     */
    public final void parse() {
        var grammarFile = this.request.grammarFile();
        var filename = grammarFile.getName();
        // A grammar without an extension used to make lastIndexOf('.') return -1 and throw a bare
        // StringIndexOutOfBoundsException here.
        var dot = filename.lastIndexOf('.');
        var lexerFile = new File(grammarFile.getParentFile(),
                (dot < 0 ? filename : filename.substring(0, dot)) + ".lex"
        );

        try {
            var text = WaggleCompiler.readGrammar(grammarFile);

            if (lexerFile.exists()) {
                System.out.printf("Reading from file %s ...\n", lexerFile);
                text += WaggleCompiler.readGrammar(lexerFile);
            }

            WaggleCompiler.bannerLine("Parser Generator");

            var context = GenerationContext.of(this.request, this.diagnostics);
            var options = context.options();
            var data = new JavaCCData(context);
            var parser = new JavaCCParserDefault(new StringProvider(text), options);
            parser.initialize(data);
            parser.javacc_input();

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
            throw new GenerationException("Failed to generate a parser from " + grammarFile, e);
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
