// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.rust;

import org.hivevm.source.TemplateSet;
import org.hivevm.source.TemplateSet.Source;

import java.io.File;
import java.util.Locale;
import java.util.Set;

/**
 * The Rust templates: which resource each is read from, and which file it writes.
 *
 * <p>Rust writes everything into a directory named after the grammar, and the file name is the
 * template's own (ADR-0018).
 */
public interface RustTemplate {

    TemplateSet SET = new TemplateSet("rust", (name, options) ->
            new File(new File(options.getOutputDirectory(),
                    options.getParserName().toLowerCase(Locale.ROOT)), name + ".rs"));

    Source TOKEN = RustTemplate.SET.declare("runtime", "token.rs", "token");
    Source CHAR_STREAM = RustTemplate.SET.declare("runtime", "charstream.rs", "charstream");

    Source LEXER = RustTemplate.SET.declare("lexer", "lexer.rs", "lexer");

    Source PARSER = RustTemplate.SET.declare("parser", "parser.rs", "parser");
    Source PARSER_CONSTANTS =
            RustTemplate.SET.declare("parser", "parserconstants.rs", "parserconstants");

    Source NODE = RustTemplate.SET.declare("tree", "node.rs", "node");
    Source TREE_STATE = RustTemplate.SET.declare("tree", "treestate.rs", "treestate");
    Source TREE_CONSTANTS = RustTemplate.SET.declare("tree", "treeconstants.rs", "treeconstants");

    static Set<String> reservedNames() {
        return RustTemplate.SET.reservedNames();
    }
}
