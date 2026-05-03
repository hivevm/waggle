// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

import java.io.File;

import org.hivevm.cc.parser.Options;
import org.hivevm.source.SourceProvider;

/**
 * Represents a collection of predefined templates for generating Rust code. Each enum constant
 * corresponds to a specific type of template file with an associated name and optional path format
 * for filename generation.
 * <p>
 * Implements the {@link SourceProvider} interface to provide mechanisms for retrieving template
 * resource paths, generating filenames, and creating corresponding {@link File} objects based on
 * user-defined options.
 */
enum JavaSources implements SourceProvider {

    LEXER("Lexer"),
    PARSER("Parser"),
    PARSER_CONSTANTS("ParserConstants"),

    PROVIDER("Provider"),
    STREAM_PROVIDER("StreamProvider"),
    STRING_PROVIDER("StringProvider"),
    CHAR_STREAM("JavaCharStream"),

    NODE("Node"),
    NODESTATE("NodeState"),
    NODETYPE("NodeType"),

    MULTI_NODE("MultiNode", "%s"),
    MULTI_NODE_VISITOR("NodeVisitor"),
    MULTI_NODE_DEFAULT_VISITOR("NodeDefaultVisitor"),

    PARSER_EXCEPTION("ParseException"),
    TOKEN("Token"),
    TOKEN_EXCEPTION("TokenException");

    private final String name;
    private final String path;

    JavaSources(String name) {
        this(name, name);
    }

    JavaSources(String path, String name) {
        this.name = name;
        this.path = path + ".java";
    }

    @Override
    public final String getPath() {
        return this.path;
    }

    @Override
    public final String getType() {
        return "java";
    }

    @Override
    public final File getTargetFile(String name, Options options) {
        var packagePath = options.getJavaPackageName().replace('.', File.separatorChar);
        var targetDir = new File(options.getOutputDirectory(), packagePath.toLowerCase());
        var targetName = (name == null ? this.name : String.format(this.name, name)) + ".java";
        return new File(targetDir, targetName);
    }
}
