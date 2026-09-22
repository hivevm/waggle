// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.jjtree;

import org.hivevm.waggle.api.WaggleOptions;
import org.hivevm.waggle.diag.Diagnostics;

import java.io.Reader;

/**
 * The {@link JJTreeParserDefault} implements a parser for JJTree grammars.
 */
class JJTreeParserDefault extends Parser {

    private final WaggleOptions options;
    private final Diagnostics diagnostics;

    JJTreeParserDefault(String text, WaggleOptions options, Diagnostics diagnostics) {
        super(new Lexer(new JavaCharStream(new StringProvider(text))));
        this.options = options;
        this.diagnostics = diagnostics;
    }

    /**
     * Parses the {@link Reader} and creates the abstract syntax tree.
     */
    final ASTGrammar parse() throws ParseException {
        grammar_input();
        return (ASTGrammar) rootNode();
    }

    @Override
    protected final WaggleOptions getOptions() {
        return this.options;
    }

    @Override
    protected final Diagnostics diagnostics() {
        return this.diagnostics;
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
