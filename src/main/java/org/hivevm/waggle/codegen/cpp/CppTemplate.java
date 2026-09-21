// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.cpp;

import org.hivevm.source.TemplateSet;
import org.hivevm.source.TemplateSet.Source;

import java.io.File;
import java.util.Set;

/**
 * The C++ templates: which resource each is read from, and which file it writes.
 *
 * <p>C++ is the only target that writes two files per component, a header and an implementation,
 * and the only one whose file names derive from the grammar. The extension is part of the name a
 * template declares, so the set needs no notion of a "file type" (ADR-0018).
 */
public interface CppTemplate {

    TemplateSet SET = new TemplateSet("cpp", (name, options) ->
            new File(options.getOutputDirectory(), name));

    Source WAGGLE = CppTemplate.SET.declare("runtime", "Waggle.h", "Waggle.h");
    Source READER = CppTemplate.SET.declare("runtime", "Reader.h", "Reader.h");
    Source STRINGREADER = CppTemplate.SET.declare("runtime", "StringReader.cc", "StringReader.cc");
    Source STRINGREADER_H = CppTemplate.SET.declare("runtime", "StringReader.h", "StringReader.h");
    Source TOKEN = CppTemplate.SET.declare("runtime", "Token.cc", "Token.cc");
    Source TOKEN_H = CppTemplate.SET.declare("runtime", "Token.h", "Token.h");

    Source LEXER = CppTemplate.SET.declare("lexer", "Lexer.cc", "%sTokenManager.cc");
    Source LEXER_H = CppTemplate.SET.declare("lexer", "Lexer.h", "%sTokenManager.h");
    Source TOKENMANAGER = CppTemplate.SET.declare("lexer", "TokenManager.h", "TokenManager.h");
    Source TOKENNANAGERERROR =
            CppTemplate.SET.declare("lexer", "TokenManagerError.cc", "TokenManagerError.cc");
    Source TOKENNANAGERERROR_H =
            CppTemplate.SET.declare("lexer", "TokenManagerError.h", "TokenManagerError.h");
    Source TOKENNANAGERHANDLER = CppTemplate.SET.declare("lexer",
            "TokenManagerErrorHandler.cc", "TokenManagerErrorHandler.cc");
    Source TOKENNANAGERHANDLER_H = CppTemplate.SET.declare("lexer",
            "TokenManagerErrorHandler.h", "TokenManagerErrorHandler.h");

    Source PARSER = CppTemplate.SET.declare("parser", "Parser.cc", "%s.cc");
    Source PARSER_H = CppTemplate.SET.declare("parser", "Parser.h", "%s.h");
    Source PARSER_CONSTANTS =
            CppTemplate.SET.declare("parser", "ParserConstants.h", "%sConstants.h");
    Source PARSEEXCEPTION = CppTemplate.SET.declare("parser", "ParseException.cc", "ParseException.cc");
    Source PARSEEXCEPTION_H = CppTemplate.SET.declare("parser", "ParseException.h", "ParseException.h");
    Source PARSERHANDLER =
            CppTemplate.SET.declare("parser", "ParserErrorHandler.cc", "ParserErrorHandler.cc");
    Source PARSERHANDLER_H =
            CppTemplate.SET.declare("parser", "ParserErrorHandler.h", "ParserErrorHandler.h");

    Source NODE = CppTemplate.SET.declare("tree", "Node.cc", "Node.cc");
    Source NODE_H = CppTemplate.SET.declare("tree", "Node.h", "Node.h");
    /** One file per AST node: the name is the node type, not the template name. */
    Source MULTINODE = CppTemplate.SET.declare("tree", "MultiNode.cc", "%s.cc");
    Source MULTINODE_H = CppTemplate.SET.declare("tree", "MultiNode.h", "%s.h");
    Source TREE = CppTemplate.SET.declare("tree", "Tree.h", "Tree.h");
    Source TREE_ONE = CppTemplate.SET.declare("tree", "TreeOne.h", "%sTree.h");
    Source TREESTATE = CppTemplate.SET.declare("tree", "TreeState.cc", "TreeState.cc");
    Source TREESTATE_H = CppTemplate.SET.declare("tree", "TreeState.h", "TreeState.h");
    Source TREE_CONSTANTS =
            CppTemplate.SET.declare("tree", "TreeConstants.h", "%sTreeConstants.h");
    Source VISITOR = CppTemplate.SET.declare("tree", "Visitor.h", "%sVisitor.h");

    /**
     * The names this back end writes under a name of its own. C++ names carry their extension, so a
     * reserved name is compared without it — a grammar called {@code Tree} would overwrite
     * {@code Tree.h}.
     */
    static Set<String> reservedNames() {
        return CppTemplate.SET.reservedNames().stream()
                .map(n -> n.substring(0, n.lastIndexOf('.')))
                .collect(java.util.stream.Collectors.toSet());
    }
}
