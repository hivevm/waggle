// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.java;

import org.hivevm.source.TemplateSet;
import org.hivevm.waggle.api.Options;
import org.hivevm.source.TemplateSet.Source;

import java.io.File;
import java.util.Locale;
import java.util.Set;

/**
 * The Java templates: which resource each is read from, and which file it writes.
 *
 * <p>Java folds the package into the directory and writes fixed file names, so the grammar's own
 * name never becomes one (ADR-0018).
 */
public interface JavaTemplate {

    TemplateSet<Options> SET = new TemplateSet<>("java", (name, options) -> {
        var packagePath = options.getJavaPackageName().replace('.', File.separatorChar);
        var targetDir = new File(options.getOutputDirectory(), packagePath.toLowerCase(Locale.ROOT));
        return new File(targetDir, name + ".java");
    });

    Source<Options> PROVIDER = JavaTemplate.SET.declare("runtime", "Provider.java", "Provider");
    Source<Options> STREAM_PROVIDER =
            JavaTemplate.SET.declare("runtime", "StreamProvider.java", "StreamProvider");
    Source<Options> STRING_PROVIDER =
            JavaTemplate.SET.declare("runtime", "StringProvider.java", "StringProvider");
    Source<Options> CHAR_STREAM =
            JavaTemplate.SET.declare("runtime", "JavaCharStream.java", "JavaCharStream");
    Source<Options> TOKEN = JavaTemplate.SET.declare("runtime", "Token.java", "Token");
    Source<Options> TOKEN_EXCEPTION =
            JavaTemplate.SET.declare("runtime", "TokenException.java", "TokenException");
    Source<Options> PARSER_EXCEPTION =
            JavaTemplate.SET.declare("runtime", "ParseException.java", "ParseException");

    Source<Options> LEXER = JavaTemplate.SET.declare("lexer", "Lexer.java", "Lexer");

    Source<Options> PARSER = JavaTemplate.SET.declare("parser", "Parser.java", "Parser");
    Source<Options> PARSER_CONSTANTS =
            JavaTemplate.SET.declare("parser", "ParserConstants.java", "ParserConstants");

    Source<Options> NODE = JavaTemplate.SET.declare("tree", "Node.java", "Node");
    Source<Options> NODESTATE = JavaTemplate.SET.declare("tree", "NodeState.java", "NodeState");
    Source<Options> NODETYPE = JavaTemplate.SET.declare("tree", "NodeType.java", "NodeType");
    /** One file per AST node: the name is the node type, not the template name. */
    Source<Options> MULTI_NODE = JavaTemplate.SET.declare("tree", "MultiNode.java", "%s");
    Source<Options> MULTI_NODE_VISITOR =
            JavaTemplate.SET.declare("tree", "NodeVisitor.java", "NodeVisitor");
    Source<Options> MULTI_NODE_DEFAULT_VISITOR =
            JavaTemplate.SET.declare("tree", "NodeDefaultVisitor.java", "NodeDefaultVisitor");

    static Set<String> reservedNames() {
        return JavaTemplate.SET.reservedNames();
    }
}
