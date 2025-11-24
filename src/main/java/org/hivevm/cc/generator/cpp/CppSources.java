// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

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
enum CppSources implements SourceProvider {

    JAVACC("JavaCC"),

    LEXER_H("Lexer", "%sTokenManager"),
    LEXER("Lexer", "%sTokenManager", false),

    PARSER_H("Parser", "%s"),
    PARSER("Parser", "%s", false),

    PARSER_CONSTANTS("ParserConstants", "%sConstants"),

    PARSEEXCEPTION("ParseException", false),
    PARSEEXCEPTION_H("ParseException"),
    PARSERHANDLER("ParserErrorHandler", false),
    PARSERHANDLER_H("ParserErrorHandler"),

    TOKEN("Token", false),
    TOKEN_H("Token"),
    TOKENMANAGER("TokenManager"),
    TOKENNANAGERERROR("TokenManagerError", false),
    TOKENNANAGERERROR_H("TokenManagerError"),
    TOKENNANAGERHANDLER("TokenManagerErrorHandler", false),
    TOKENNANAGERHANDLER_H("TokenManagerErrorHandler"),

    READER("Reader"),
    STRINGREADER("StringReader", false),
    STRINGREADER_H("StringReader"),

    NODE("Node", false),
    NODE_H("Node"),
    MULTINODE("MultiNode", false),
    MULTINODE_H("MultiNode"),

    TREE("Tree"),
    TREE_ONE("TreeOne", "%sTree"),
    TREESTATE("TreeState", false),
    TREESTATE_H("TreeState"),
    TREE_CONSTANTS("TreeConstants", "%sTreeConstants"),
    VISITOR("Visitor", "%sVisitor");

    private final String name;
    private final String path;
    private final String filetype;

    CppSources(String name) {
        this(name, name, true);
    }

    CppSources(String path, String name) {
        this(path, name, true);
    }

    CppSources(String name, boolean isHeader) {
        this(name, name, isHeader);
    }

    CppSources(String path, String name, boolean isHeader) {
        this.name = name;
        this.path = path;
        this.filetype = isHeader ? "h" : "cc";
    }

    @Override
    public final String getPath() {
        return this.path + "." + filetype;
    }

    @Override
    public final String getType() {
        return "cpp";
    }

    @Override
    public final File getTargetFile(String name, Options options) {
        var targetName = (name == null ? this.name : String.format(this.name, name)) + "." + filetype;
        return new File(options.getOutputDirectory(), targetName);
    }
}
