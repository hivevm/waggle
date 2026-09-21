// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.generator.java;

import org.hivevm.waggle.parser.Options;
import org.hivevm.source.SourceProvider;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a collection of predefined templates for generating Java code. Each enum constant
 * corresponds to a specific type of template file with an associated name and optional path format
 * for filename generation.
 * <p>
 * Implements the {@link SourceProvider} interface to provide mechanisms for retrieving template
 * resource paths, generating filenames, and creating corresponding {@link File} objects based on
 * user-defined options.
 */
public enum JavaTemplate implements SourceProvider {

    LEXER("Lexer"),
    PARSER("Parser"),
    PARSER_CONSTANTS("ParserConstants"),

    PROVIDER("Provider"),
    STREAM_PROVIDER("StreamProvider"),
    STRING_PROVIDER("StringProvider"),
    CHAR_STREAM("JavaCharStream"),

    // Tree templates live under templates/java/tree/ and are rendered only for a grammar that
    // builds a tree (ADR-0016). The name -- and therefore the generated file -- is unchanged.
    NODE("tree/Node", "Node"),
    NODESTATE("tree/NodeState", "NodeState"),
    NODETYPE("tree/NodeType", "NodeType"),

    MULTI_NODE("tree/MultiNode", "%s"),
    MULTI_NODE_VISITOR("tree/NodeVisitor", "NodeVisitor"),
    MULTI_NODE_DEFAULT_VISITOR("tree/NodeDefaultVisitor", "NodeDefaultVisitor"),

    PARSER_EXCEPTION("ParseException"),
    TOKEN("Token"),
    TOKEN_EXCEPTION("TokenException");

    private final String name;
    private final String path;

    JavaTemplate(String name) {
        this(name, name);
    }

    JavaTemplate(String path, String name) {
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
        var targetDir = new File(options.getOutputDirectory(), packagePath.toLowerCase(Locale.ROOT));
        var targetName = (name == null ? this.name : String.format(this.name, name)) + ".java";
        return new File(targetDir, targetName);
    }

    /**
     * The files this back end writes under a name of its own, i.e. one that does not derive from the
     * grammar. A generated parser or AST node may not be called any of these, or it would silently
     * overwrite the runtime class.
     */
    public static Set<String> reservedNames() {
        return Arrays.stream(values()).filter(t -> !t.name.contains("%s")).map(t -> t.name)
                .collect(Collectors.toSet());
    }
}
