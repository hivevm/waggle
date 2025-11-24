// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.rust;

import java.io.File;

import org.hivevm.cc.parser.Options;
import org.hivevm.source.SourceProvider;

/**
 * Represents a collection of predefined templates for generating Rust code.
 * Each enum constant corresponds to a specific type of template file with an
 * associated name and optional path format for filename generation.
 *
 * Implements the {@link SourceProvider} interface to provide mechanisms
 * for retrieving template resource paths, generating filenames, and creating
 * corresponding {@link File} objects based on user-defined options.
 */
enum RustSources implements SourceProvider {

    NODE("node"),
    TOKEN("token"),
    CHAR_STREAM("charstream"),

    LEXER("lexer"),
    PARSER("parser"),

    PARSER_CONSTANTS("parserconstants"),

    TREE_STATE("treestate"),
    TREE_CONSTANTS("treeconstants"),

    MULTI_NODE("MultiNode", "%s"),
    VISITOR("Visitor", "%sVisitor"),
    DEFAULT_VISITOR("DefaultVisitor", "%sDefaultVisitor");

    private final String name;
    private final String path;

    RustSources(String name) {
        this(name, name);
    }

    RustSources(String path, String name) {
        this.name = name;
        this.path = path + ".rs";
    }

    @Override
    public final String getPath() {
        return this.path;
    }

    @Override
    public final String getType() {
        return "rust";
    }

    @Override
    public final File getTargetFile(String name, Options options) {
        var targetDir = new File(options.getOutputDirectory(), options.getParserName().toLowerCase());
        var targetName = (name == null ? this.name : String.format(this.name, name)) + ".rs";
        return new File(targetDir, targetName);
    }
}
