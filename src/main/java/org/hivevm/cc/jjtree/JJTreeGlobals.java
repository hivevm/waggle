// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.jjtree;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hivevm.cc.HiveCC;

public class JJTreeGlobals {

  public static void initialize() {
    JJTreeGlobals.parserName = null;
    JJTreeGlobals.productions = new HashMap<>();

    JJTreeGlobals.jjtreeOptions = new HashSet<>();
    JJTreeGlobals.jjtreeOptions.add(HiveCC.JJTREE_MULTI);
    JJTreeGlobals.jjtreeOptions.add(HiveCC.JJTREE_NODE_PREFIX);
    JJTreeGlobals.jjtreeOptions.add(HiveCC.JJTREE_NODE_PACKAGE);
    JJTreeGlobals.jjtreeOptions.add(HiveCC.JJTREE_NODE_EXTENDS);
    JJTreeGlobals.jjtreeOptions.add(HiveCC.JJTREE_NODE_EXCLUDES);
    JJTreeGlobals.jjtreeOptions.add(HiveCC.JJTREE_NODE_CLASS);
    JJTreeGlobals.jjtreeOptions.add(HiveCC.JJTREE_NODE_DEFAULT_VOID);
    JJTreeGlobals.jjtreeOptions.add(HiveCC.JJTREE_OUTPUT_FILE);
    JJTreeGlobals.jjtreeOptions.add(HiveCC.JJTREE_NODE_SCOPE_HOOK);
    JJTreeGlobals.jjtreeOptions.add(HiveCC.JJTREE_TRACK_TOKENS);
    JJTreeGlobals.jjtreeOptions.add(HiveCC.JJTREE_NODE_FACTORY);
    JJTreeGlobals.jjtreeOptions.add(HiveCC.JJTREE_BUILD_NODE_FILES);
    JJTreeGlobals.jjtreeOptions.add(HiveCC.JJTREE_VISITOR);
    JJTreeGlobals.jjtreeOptions.add(HiveCC.JJTREE_VISITOR_EXCEPTION);
    JJTreeGlobals.jjtreeOptions.add(HiveCC.JJTREE_VISITOR_DATA_TYPE);
    JJTreeGlobals.jjtreeOptions.add(HiveCC.JJTREE_VISITOR_RETURN_TYPE);
  }

  static {
    JJTreeGlobals.initialize();
  }

  /**
   * This set stores the JJTree-specific options that should not be passed down to JavaCC
   */
  private static Set<String> jjtreeOptions;

  static boolean isOptionJJTreeOnly(String optionName) {
    return JJTreeGlobals.jjtreeOptions.contains(optionName.toUpperCase());
  }

  /**
   * Use this like className.
   **/
  public static String                     parserName;

  /**
   * This is mapping from production names to ASTProduction objects.
   **/
  public static Map<String, ASTProduction> productions = new HashMap<>();

}
