// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.parser.jjtree;

import org.hivevm.cc.HiveCCOptions;

import java.io.Reader;

/**
 * The {@link JJTreeParserDefault} implements a parser for the .jjt files.
 */
class JJTreeParserDefault extends Parser {

    private final HiveCCOptions options;

    JJTreeParserDefault(String text, HiveCCOptions options) {
        super(new Lexer(new JavaCharStream(new StringProvider(text))));
        this.options = options;
    }

    /**
     * Parses the {@link Reader} and creates the abstract syntax tree.
     */
    final ASTGrammar parse() throws ParseException {
        javacc_input();
        return (ASTGrammar) rootNode();
    }

    @Override
    protected final HiveCCOptions getOptions() {
        return this.options;
    }

    @Override
    protected final void jjtreeOpenNodeScope(Node n) {
        ((ASTNode) n).setFirstToken(getToken(1));
    }

    @Override
    protected final void jjtreeCloseNodeScope(Node n) {
        ((ASTNode) n).setLastToken(getToken(0));
    }
}
