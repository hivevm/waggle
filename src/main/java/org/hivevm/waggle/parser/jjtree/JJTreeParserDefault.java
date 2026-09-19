// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.parser.jjtree;

import org.hivevm.waggle.WaggleOptions;

import java.io.Reader;

/**
 * The {@link JJTreeParserDefault} implements a parser for JJTree grammars.
 */
class JJTreeParserDefault extends Parser {

    private final WaggleOptions options;

    JJTreeParserDefault(String text, WaggleOptions options) {
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
    protected final WaggleOptions getOptions() {
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
