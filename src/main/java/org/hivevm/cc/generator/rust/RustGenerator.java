// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.rust;

import org.hivevm.cc.generator.FileGenerator;
import org.hivevm.cc.generator.GeneratorName;
import org.hivevm.cc.generator.GeneratorProvider;
import org.hivevm.cc.generator.LexerGenerator;
import org.hivevm.cc.generator.NodeGenerator;
import org.hivevm.cc.generator.ParserGenerator;

/**
 * The {@link RustGenerator} class.
 */
@GeneratorName("Rust")
public class RustGenerator extends GeneratorProvider {

    @Override
    public final NodeGenerator newNodeGenerator() {
        return new RustNodeGenerator();
    }

    @Override
    public final LexerGenerator newLexerGenerator() {
        return new RustLexerGenerator();
    }

    @Override
    public final ParserGenerator newParserGenerator() {
        return new RustParserGenerator();
    }

    @Override
    protected final FileGenerator newFileGenerator() {
        return context -> {
            RustTemplate.TOKEN.render(context.options());
            RustTemplate.CHAR_STREAM.render(context.options());
        };
    }
}
