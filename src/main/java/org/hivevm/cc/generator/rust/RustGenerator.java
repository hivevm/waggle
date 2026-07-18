// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.rust;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.ParserRequest;
import org.hivevm.cc.generator.FileGenerator;
import org.hivevm.cc.generator.GeneratorName;
import org.hivevm.cc.generator.GeneratorProvider;
import org.hivevm.cc.generator.LexerGenerator;
import org.hivevm.cc.generator.NodeData;
import org.hivevm.cc.generator.NodeGenerator;
import org.hivevm.cc.generator.ParserGenerator;
import org.hivevm.cc.parser.Options;

import java.util.Locale;
import java.util.Set;

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

    /**
     * Every {@code use crate::…} in the templates goes through RUST_MODULE, and it defaulted to the
     * empty string — which rendered as {@code use crate::::charstream}, so nothing compiled unless
     * the option was set by hand. The generated files land in a directory named after the grammar,
     * so that directory is the module.
     */
    @Override
    protected final void prepare(ParserRequest request) {
        var options = request.options();
        if (options.stringValue(HiveCC.JJPARSER_RUST_MODULE).isEmpty()) {
            options.set(HiveCC.JJPARSER_RUST_MODULE,
                    request.getParserName().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * A Rust project that uses "#Name" nodes supplies node.rs, treestate.rs and treeconstants.rs
     * itself, and generation has always left them alone: they are written only for NODE_MULTI or
     * NODE_SCOPE_HOOK, as before. Writing them for every node would overwrite the project's own.
     */
    @Override
    protected final boolean generatesTreeRuntime(NodeData nodes, Options options) {
        return !nodes.getNodesToGenerate().isEmpty() || options.getNodeScopeHook();
    }

    @Override
    protected final FileGenerator newFileGenerator() {
        return context -> {
            RustTemplate.TOKEN.render(context.options());
            RustTemplate.CHAR_STREAM.render(context.options());
        };
    }

    @Override
    protected final Set<String> reservedNames() {
        return RustTemplate.reservedNames();
    }
}
