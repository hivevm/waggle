// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.rust;

import org.hivevm.cc.generator.FileGenerator;
import org.hivevm.cc.generator.GeneratorName;
import org.hivevm.cc.generator.GeneratorProvider;
import org.hivevm.cc.generator.LexerGenerator;
import org.hivevm.cc.generator.ParserGenerator;
import org.hivevm.cc.generator.TreeGenerator;

/**
 * RustGenerator is a concrete implementation of the abstract GeneratorProvider
 * for generating components specific to the Rust programming language.
 * It overrides the necessary methods to return Rust-specific implementations
 * for generating abstract syntax trees, lexer, parser, and files.
 *
 * Annotated with {@link GeneratorName} having the value "Rust" to indicate
 * that this generator is tailored for Rust.
 *
 * Responsibilities include:
 * - Generating Rust Abstract Syntax Trees using {@link RustASTGenerator}.
 * - Generating lexer files using {@link RustLexerGenerator}.
 * - Generating parser files using {@link RustParserGenerator}.
 * - Generating additional Rust-specific file templates through embedded render logic.
 */
@GeneratorName("Rust")
public class RustGenerator extends GeneratorProvider {

    @Override
    protected final TreeGenerator newASTGenerator() {
        return new RustASTGenerator();
    }

    @Override
    protected final FileGenerator newFileGenerator() {
        return context -> {
            RustSources.TOKEN.render(context.options());
            RustSources.CHAR_STREAM.render(context.options());
        };
    }

    @Override
    protected final LexerGenerator newLexerGenerator() {
        return new RustLexerGenerator();
    }

    @Override
    protected final ParserGenerator newParserGenerator() {
        return new RustParserGenerator();
    }
}
