// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/JavaCCGlobals.java

package org.hivevm.waggle.api;

/**
 * The names of the options a grammar, a caller or a back end can set, as they are written in a
 * grammar's {@code options { … }} block and read by the templates.
 */
public interface Waggle {

    String PARSER_NAME = "PARSER_NAME";

    String USE_AST = "USE_AST";
    String NODE_MULTI = "NODE_MULTI";
    String NODE_TYPE = "NODE_TYPE";
    String NODE_EXTENDS = "NODE_EXTENDS";
    String NODE_FACTORY = "NODE_FACTORY";
    String NODE_CUSTOM = "NODE_CUSTOM";
    String NODE_CLASS = "NODE_CLASS";
    String NODE_DEFAULT_VOID = "NODE_DEFAULT_VOID";
    String NODE_SCOPE_HOOK = "NODE_SCOPE_HOOK";

    String OUTPUT_FILE = "OUTPUT_FILE";
    String TRACK_TOKENS = "TRACK_TOKENS";
    String BUILD_NODE_FILES = "BUILD_NODE_FILES";

    String VISITOR = "VISITOR";
    String VISITOR_EXCEPTION = "VISITOR_EXCEPTION";
    String VISITOR_DATA_TYPE = "VISITOR_DATA_TYPE";
    String VISITOR_RETURN_TYPE = "VISITOR_RETURN_TYPE";
    String VISITOR_RETURN_TYPE_VOID = "VISITOR_RETURN_TYPE_VOID";

    String NO_DFA = "NO_DFA";
    String LOOKAHEAD = "LOOKAHEAD";
    String IGNORE_CASE = "IGNORE_CASE";
    String ERROR_REPORTING = "ERROR_REPORTING";
    String DEBUG_TOKEN_MANAGER = "DEBUG_TOKEN_MANAGER";
    String DEBUG_LOOKAHEAD = "DEBUG_LOOKAHEAD";
    String DEBUG_PARSER = "DEBUG_PARSER";
    String OTHER_AMBIGUITY_CHECK = "OTHER_AMBIGUITY_CHECK";
    String CHOICE_AMBIGUITY_CHECK = "CHOICE_AMBIGUITY_CHECK";
    String CACHE_TOKENS = "CACHE_TOKENS";
    String FORCE_LA_CHECK = "FORCE_LA_CHECK";
    String SANITY_CHECK = "SANITY_CHECK";
    String OUTPUT_DIRECTORY = "OUTPUT_DIRECTORY";
    String CODE_GENERATOR = "CODE_GENERATOR";
    String KEEP_LINE_COLUMN = "KEEP_LINE_COLUMN";
    String DEPTH_LIMIT = "DEPTH_LIMIT";

    String BASE_LEXER = "BASE_LEXER";
    String BASE_PARSER = "BASE_PARSER";

    String JAVA_PACKAGE = "JAVA_PACKAGE";
    String JAVA_IMPORTS = "JAVA_IMPORTS";

    String RUST_MODULE = "RUST_MODULE";

    String CPP_DEFINE = "CPP_DEFINE";
    String CPP_NAMESPACE = "CPP_NAMESPACE";
    String CPP_STACK_LIMIT = "CPP_STACK_LIMIT";
}
