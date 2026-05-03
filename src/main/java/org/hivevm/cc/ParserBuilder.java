// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;


import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.hivevm.cc.jjtree.JJTreeParser;

/**
 * The {@link ParserBuilder} class.
 */
public class ParserBuilder {

    private Language     language;
    private File         targetDir;
    private List<String> customNodes;

    private File parserFile;

    /**
     * Set the code generator.
     */
    public final ParserBuilder setCodeGenerator(Language language) {
        this.language = language;
        return this;
    }

    /**
     * Set the output directory.
     */
    public final ParserBuilder setTargetDir(File targetDir, String... pathes) {
        this.targetDir = ParserBuilder.toFile(targetDir, pathes);
        return this;
    }

    /**
     * Set the nodes that should not be generated.
     */
    public final ParserBuilder setCustomNodes(List<String> excludes) {
        this.customNodes = excludes;
        return this;
    }

    /**
     * Set the jj file.
     */
    public final ParserBuilder setParserFile(File file, String... pathes) {
        this.parserFile = ParserBuilder.toFile(file, pathes);
        return this;
    }

    public static ParserBuilder of(Language language) {
        ParserBuilder builder = new ParserBuilder();
        builder.setCodeGenerator(language);
        return builder;
    }


    /**
     * Run the parser generator.
     */
    public final Parser build() {
        return new JJTreeParser(parserFile, language, targetDir, customNodes);
    }

    /**
     * Run the parser generator.
     */
    public final void interpret(String text) {
        var options = new HiveCCOptions();
        try {
            var interpreter = new ParserInterpreter(options);
            var grammar = new String(Files.readAllBytes(this.parserFile.toPath()));
            interpreter.runTokenizer(grammar, text);
        }
        catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static File toFile(File file, String... pathes) {
        return (pathes.length == 0) ? file : new File(file, String.join(File.separator, pathes));
    }
}
