// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.hivevm.cc.utils.Version;

/**
 * This package contains data created as a result of parsing and semanticizing a JavaCC input file.
 * This data is what is used by the back-ends of JavaCC as well as any other back-end of JavaCC
 * related tools such as JJTree.
 */
public interface HiveCC {

  String PARSER_NAME                = "PARSER_NAME";

  String JJTREE_MULTI               = "MULTI";
  String JJTREE_NODE_TYPE           = "NODE_TYPE";
  String JJTREE_NODE_PREFIX         = "NODE_PREFIX";
  String JJTREE_NODE_PACKAGE        = "NODE_PACKAGE";
  String JJTREE_NODE_EXTENDS        = "NODE_EXTENDS";
  String JJTREE_NODE_EXCLUDES       = "NODE_EXCLUDES";
  String JJTREE_NODE_CLASS          = "NODE_CLASS";
  String JJTREE_NODE_DEFAULT_VOID   = "NODE_DEFAULT_VOID";
  String JJTREE_NODE_SCOPE_HOOK     = "NODE_SCOPE_HOOK";
  String JJTREE_OUTPUT_FILE         = "OUTPUT_FILE";
  String JJTREE_TRACK_TOKENS        = "TRACK_TOKENS";
  String JJTREE_NODE_FACTORY        = "NODE_FACTORY";
  String JJTREE_BUILD_NODE_FILES    = "BUILD_NODE_FILES";

  String JJTREE_VISITOR             = "VISITOR";
  String JJTREE_VISITOR_EXCEPTION   = "VISITOR_EXCEPTION";
  String JJTREE_VISITOR_DATA_TYPE   = "VISITOR_DATA_TYPE";
  String JJTREE_VISITOR_RETURN_TYPE = "VISITOR_RETURN_TYPE";
  String JJTREE_VISITOR_RETURN_VOID = "VISITOR_RETURN_TYPE_VOID";


  String JJPARSER_LEGACY                  = "LEGACY";
  String JJPARSER_NO_DFA                  = "NO_DFA";
  String JJPARSER_LOOKAHEAD               = "LOOKAHEAD";
  String JJPARSER_IGNORE_CASE             = "IGNORE_CASE";
  String JJPARSER_ERROR_REPORTING         = "ERROR_REPORTING";
  String JJPARSER_DEBUG_TOKEN_MANAGER     = "DEBUG_TOKEN_MANAGER";
  String JJPARSER_DEBUG_LOOKAHEAD         = "DEBUG_LOOKAHEAD";
  String JJPARSER_DEBUG_PARSER            = "DEBUG_PARSER";
  String JJPARSER_OTHER_AMBIGUITY_CHECK   = "OTHER_AMBIGUITY_CHECK";
  String JJPARSER_CHOICE_AMBIGUITY_CHECK  = "CHOICE_AMBIGUITY_CHECK";
  String JJPARSER_CACHE_TOKENS            = "CACHE_TOKENS";
  String JJPARSER_FORCE_LA_CHECK          = "FORCE_LA_CHECK";
  String JJPARSER_SANITY_CHECK            = "SANITY_CHECK";
  String JJPARSER_OUTPUT_DIRECTORY        = "OUTPUT_DIRECTORY";
  String JJPARSER_CODEGENERATOR           = "CODE_GENERATOR";
  String JJPARSER_KEEP_LINE_COLUMN        = "KEEP_LINE_COLUMN";
  String JJPARSER_DEPTH_LIMIT             = "DEPTH_LIMIT";

  String JJPARSER_JAVA_PACKAGE            = "PACKAGE";
  String JJPARSER_JAVA_IMPORTS            = "IMPORTS";
  String JJPARSER_JAVA_EXTENDS            = "EXTENDS";
  String JJPARSER_JAVA_LEXER              = "LEXER";

  String JJPARSER_CPP_NAMESPACE           = "NAMESPACE";
  String JJPARSER_CPP_STOP_ON_FIRST_ERROR = "STOP_ON_FIRST_ERROR";
  String JJPARSER_CPP_STACK_LIMIT         = "STACK_LIMIT";


  Version VERSION = HiveCC.load();

  static Version load() {
    String major = "??";
    String minor = "??";
    String patch = "??";

    try (InputStream is = HiveCC.class.getResourceAsStream("/version.properties")) {
      Properties properties = new Properties();
      properties.load(is);
      major = properties.getProperty("version.major", major);
      minor = properties.getProperty("version.minor", minor);
      patch = properties.getProperty("version.patch", patch);
    } catch (IOException e) {
      System.err.println("Could not read version.properties: " + e);
    }
    return Version.of(Integer.parseInt(major), Integer.parseInt(minor), patch.equals("") ? 0 : Integer.parseInt(patch));
  }
}
