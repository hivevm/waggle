// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.jjtree;

import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.api.WaggleOptions;
import org.hivevm.waggle.diag.Diagnostics;
import org.hivevm.waggle.tree.TreeEmitter;
import org.hivevm.waggle.tree.TreeModel;

import java.io.Writer;

/**
 * The entry point of the reference consumer: parse a grammar into an AST, and write that AST back
 * out with the tree code woven in.
 *
 * <p>Until now there was none. {@link JJTreeParserDefault}, {@link JJTreeVisitor} and
 * {@link TreeGenerator} were constructed nowhere, so the package was compiled and regenerated but
 * never run — the build proved that it builds, not that it works (ADR-0016).
 */
public final class JJTree {

    private JJTree() {
    }

    /**
     * Parses a grammar in Waggle's own syntax into its syntax tree.
     *
     * @param text        the grammar source
     * @param options     the settings the grammar's own {@code options { … }} block is merged into
     * @param diagnostics where the parse reports what it finds
     */
    public static ASTGrammar parse(String text, WaggleOptions options, Diagnostics diagnostics)
            throws ParseException {
        return new JJTreeParserDefault(text, options, diagnostics).parse();
    }

    /** Parses a grammar with default settings, collecting diagnostics of its own. */
    public static ASTGrammar parse(String text) throws ParseException {
        return JJTree.parse(text, new WaggleOptions(), new Diagnostics());
    }

    /**
     * Writes the AST back out for {@code language}, with the tree code {@code emitter} produces
     * woven into the node scopes.
     *
     * @return the nodes the grammar declares, as the walk discovered them
     */
    public static TreeModel write(ASTGrammar grammar, TreeEmitter emitter, Language language,
            Writer writer) {
        var generator = new TreeGenerator(emitter);
        try (var out = new ASTWriter(writer, language)) {
            grammar.jjtAccept(new JJTreeVisitor(generator), out);
        }
        return generator.getData();
    }
}
