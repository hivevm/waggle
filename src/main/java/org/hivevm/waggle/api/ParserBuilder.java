// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.api;

import java.io.File;
import java.util.List;

/**
 * The {@link ParserBuilder} class.
 */
public class ParserBuilder {

    private Language language;
    private File targetDir;
    private List<String> customNodes;

    private File parserFile;

    /**
     * Set the code generator.
     */
    public final ParserBuilder setLanguage(Language language) {
        this.language = language;
        return this;
    }

    /**
     * Set the jj file.
     */
    public final ParserBuilder setParserFile(File file, String... pathes) {
        this.parserFile = ParserBuilder.toFile(file, pathes);
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
     * Run the parser generator.
     */
    public final WaggleCompiler build() {
        return new WaggleCompiler(
                new GenerationRequest(this.parserFile, this.language, this.targetDir,
                        this.customNodes));
    }

    private static File toFile(File file, String... pathes) {
        return (pathes.length == 0) ? file : new File(file, String.join(File.separator, pathes));
    }
}
