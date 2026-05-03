// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;


import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hivevm.cc.generator.GeneratorProvider;
import org.hivevm.cc.parser.JavaCCData;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.JavaCCParserDefault;
import org.hivevm.cc.parser.StringProvider;
import org.hivevm.cc.semantic.Semanticize;

/**
 * The {@link Parser} class.
 */
public class Parser {

    private static final Pattern GENERATED = Pattern.compile("@generated\\(([^)]+)\\)");

    private final File file;

    private final Language     language;
    private final File         targetDir;
    private final List<String> customNodes;

    public Parser(File file, Language language, File targetDir, List<String> customNodes) {
        this.file = file;
        this.language = language;
        this.targetDir = targetDir;
        this.customNodes = customNodes;
    }

    protected String parseJJTree(String filename, List<String> arguments)
            throws IOException, ParseException {
        Parser.bannerLine("Tree Builder");
        JavaCCErrors.reInit();
        System.out.printf("Reading from file '%s' ...\n", file);

        return new String(Files.readAllBytes(file.toPath()));
    }

    /**
     * Run the parser generator.
     */
    public final void parse() {
        var arguments = new ArrayList<String>();
        arguments.add("-CODE_GENERATOR=" + this.language.name());
        arguments.add("-OUTPUT_DIRECTORY=" + this.targetDir.getAbsolutePath());
        if ((this.customNodes != null) && !this.customNodes.isEmpty()) {
            arguments.add("-NODE_CUSTOM=" + String.join(",", this.customNodes));
        }

        var filename = this.file.getName();
        var lexerFile = new File(this.file.getParentFile(),
                filename.substring(0, filename.lastIndexOf('.')) + ".lex"
        );

        try {
            var text = filename.endsWith(".jjt")
                    ? parseJJTree(filename, arguments)
                    : new String(Files.readAllBytes(file.toPath()));

            if (lexerFile.exists()) {
                System.out.printf("Reading from file %s ...\n", file);
                text += new String(Files.readAllBytes(lexerFile.toPath()));
            }

            Parser.bannerLine("Parser Generator");
            JavaCCErrors.reInit();

            var options = parseContext(arguments);
            var request = new JavaCCData(Parser.isGenerated(text), options);
            var parser = new JavaCCParserDefault(new StringProvider(text), options);
            parser.initialize(request);
            parser.javacc_input();

            // Initialize the parser data
            Parser.createOutputDir(options.getOutputDirectory());
            Semanticize.semanticize(request, options);
            options.set(HiveCC.PARSER_NAME, request.getParserName());
            var generator = GeneratorProvider.generatorFor(options.getOutputLanguage());
            generator.generate(request);
        }
        catch (ParseException | IOException e) {
            e.printStackTrace();
        }

        if (JavaCCErrors.hasError()) {
            System.out.printf("Detected %s errors and %s warnings.\n",
                    JavaCCErrors.get_error_count(), JavaCCErrors.get_warning_count());
            System.exit(JavaCCErrors.get_error_count() == 0 ? 0 : 1);
        }
        else if (JavaCCErrors.hasWarning()) {
            System.out.printf("Parser generated with 0 errors and %s warnings.\n",
                    JavaCCErrors.get_warning_count());
        }
        else {
            System.out.println("Parser generated successfully.");
        }
    }

    /**
     * Writes the generated string.
     */
    protected static void writeGenerated(PrintWriter writer) {
        writer.println("/* @generated(JJTree) */");
    }

    /**
     * Parses the tool list from the generated string.
     */
    private static List<String> readToolNameList(String text) {
        Matcher matcher = Parser.GENERATED.matcher(text);
        if (matcher.find())
            return Arrays.asList(matcher.group(1).split(","));
        return Collections.emptyList();
    }

    /**
     * Returns true if tool name passed is one of the tool names returned by
     * getToolNames(fileName).
     */
    private static boolean isGenerated(String data) {
        for (var element : Parser.readToolNameList(data)) {
            if ("JJTree".equals(element)) {
                return true;
            }
        }
        return false;
    }

    protected static HiveCCOptions parseContext(List<String> args) {
        var options = new HiveCCOptions();
        for (var arg : args) {
            if (!options.isOption(arg)) {
                System.out.printf("Argument '%s' must be an option setting.\n", arg);
                System.exit(1);
            }
            options.setCmdLineOption(arg);
        }
        options.validate();
        return options;
    }

    protected static void createOutputDir(File outputDir) {
        if (!outputDir.exists()) {
            JavaCCErrors.warning(
                    "Output directory \"" + outputDir + "\" does not exist. Creating the directory.");

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
            JavaCCErrors.semantic_error(
                    "Cannot write to the output output directory : \"" + outputDir + "\"");
        }
    }

    /**
     * This prints the banner line when the various tools are invoked. This takes as argument the
     * tool's full name and its version.
     */
    public static void bannerLine(String fullName) {
        System.out.printf("Java Compiler Compiler Version %s (%s)\n",
                HiveCCVersion.VERSION.toString(), fullName);
    }
}
