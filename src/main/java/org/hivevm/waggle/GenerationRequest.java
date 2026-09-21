// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import java.io.File;
import java.util.List;

/**
 * What the caller asks for: one grammar, one target language, one output directory.
 *
 * <p>These used to be assembled into {@code -CODE_GENERATOR=…} strings and parsed back by the
 * option parser, although the Gradle plugin already held them as typed values and no command line
 * exists to serve ([SPECIFICATION §3]). The round trip only moved the type errors to runtime
 * (ADR-0015).
 *
 * @param grammarFile     the {@code .waggle} grammar; its sibling {@code .lex} is appended when present
 * @param language        the target language
 * @param outputDirectory where the generated sources are written
 * @param customNodes     AST nodes the grammar author supplies, so the back end must not write them
 */
public record GenerationRequest(File grammarFile, Language language, File outputDirectory,
                                List<String> customNodes) {

    public GenerationRequest {
        customNodes = (customNodes == null) ? List.of() : List.copyOf(customNodes);
    }
}
