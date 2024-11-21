// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.HiveCCOptions;
import org.hivevm.cc.parser.JavaCCErrors;

/**
 * The JJTree-specific options.
 *
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
public class TreeContext extends HiveCCOptions implements TreeOptions {

  /**
   * Limit subclassing to derived classes.
   */
  public TreeContext() {
    set(HiveCC.JJTREE_MULTI, Boolean.FALSE);
    set(HiveCC.JJTREE_NODE_DEFAULT_VOID, Boolean.FALSE);
    set(HiveCC.JJTREE_NODE_SCOPE_HOOK, Boolean.FALSE);
    set(HiveCC.JJTREE_BUILD_NODE_FILES, Boolean.TRUE);
    set(HiveCC.JJTREE_VISITOR, Boolean.FALSE);
    set(HiveCC.JJTREE_TRACK_TOKENS, Boolean.FALSE);
    set(HiveCC.JJTREE_NODE_PREFIX, "AST");
    set(HiveCC.JJTREE_NODE_EXTENDS, "");
    set(HiveCC.JJTREE_NODE_CLASS, "");
    set(HiveCC.JJTREE_NODE_FACTORY, "");
    set(HiveCC.JJTREE_NODE_CUSTOM, "");
    set(HiveCC.JJTREE_OUTPUT_FILE, "");
    set(HiveCC.JJTREE_VISITOR_DATA_TYPE, "");
    set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, "Object");
    set(HiveCC.JJTREE_VISITOR_EXCEPTION, "");

    // Also appears to be a duplicate
    set(HiveCC.JJPARSER_JAVA_PACKAGE, "");
    set(HiveCC.JJPARSER_JAVA_IMPORTS, "");
    set(HiveCC.JJPARSER_BASE_PARSER, "");
    set(HiveCC.JJPARSER_BASE_LEXER, "");
    set(HiveCC.JJPARSER_CPP_NAMESPACE, "");
  }

  /**
   * Check options for consistency
   */
  public void validate() {
    if (!getVisitor()) {
      if (getVisitorDataType().length() > 0) {
        JavaCCErrors.warning("VISITOR_DATA_TYPE option will be ignored since VISITOR is false");
      }
      if ((getVisitorReturnType().length() > 0) && !getVisitorReturnType().equals("Object")) {
        JavaCCErrors.warning("VISITOR_RETURN_TYPE option will be ignored since VISITOR is false");
      }
      if (getVisitorException().length() > 0) {
        JavaCCErrors.warning("VISITOR_EXCEPTION option will be ignored since VISITOR is false");
      }
    }
  }
}
