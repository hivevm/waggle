// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.Options;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The JJTree-specific options.
 *
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
public class ASTGeneratorContext extends Options {

  /**
   * Limit subclassing to derived classes.
   */
  public ASTGeneratorContext() {
    Options.optionValues.put(HiveCC.JJTREE_MULTI, Boolean.FALSE);
    Options.optionValues.put(HiveCC.JJTREE_NODE_DEFAULT_VOID, Boolean.FALSE);
    Options.optionValues.put(HiveCC.JJTREE_NODE_SCOPE_HOOK, Boolean.FALSE);
    Options.optionValues.put(HiveCC.JJTREE_BUILD_NODE_FILES, Boolean.TRUE);
    Options.optionValues.put(HiveCC.JJTREE_VISITOR, Boolean.FALSE);
    Options.optionValues.put(HiveCC.JJTREE_TRACK_TOKENS, Boolean.FALSE);
    Options.optionValues.put(HiveCC.JJTREE_NODE_PREFIX, "AST");
    Options.optionValues.put(HiveCC.JJTREE_NODE_PACKAGE, "");
    Options.optionValues.put(HiveCC.JJTREE_NODE_EXTENDS, "");
    Options.optionValues.put(HiveCC.JJTREE_NODE_CLASS, "");
    Options.optionValues.put(HiveCC.JJTREE_NODE_FACTORY, "");
    Options.optionValues.put(HiveCC.JJTREE_NODE_EXCLUDES, "");
    Options.optionValues.put(HiveCC.JJTREE_OUTPUT_FILE, "");
    Options.optionValues.put(HiveCC.JJTREE_VISITOR_DATA_TYPE, "");
    Options.optionValues.put(HiveCC.JJTREE_VISITOR_RETURN_TYPE, "Object");
    Options.optionValues.put(HiveCC.JJTREE_VISITOR_EXCEPTION, "");

    // Also appears to be a duplicate
    Options.optionValues.put(HiveCC.JJPARSER_JAVA_PACKAGE, "");
    Options.optionValues.put(HiveCC.JJPARSER_JAVA_IMPORTS, "");
    Options.optionValues.put(HiveCC.JJPARSER_JAVA_EXTENDS, "");
    Options.optionValues.put(HiveCC.JJPARSER_JAVA_LEXER, "");
    Options.optionValues.put(HiveCC.JJPARSER_CPP_NAMESPACE, "");
  }

  private final Set<String> nodesToGenerate = new HashSet<>();

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

  /**
   * Find the multi value.
   */
  public final boolean getMulti() {
    return booleanValue(HiveCC.JJTREE_MULTI);
  }

  /**
   * Find the node default void value.
   */
  public final boolean getNodeDefaultVoid() {
    return booleanValue(HiveCC.JJTREE_NODE_DEFAULT_VOID);
  }

  /**
   * Find the node scope hook value.
   */
  public final boolean getNodeScopeHook() {
    return booleanValue(HiveCC.JJTREE_NODE_SCOPE_HOOK);
  }

  /**
   * Find the node factory value.
   */
  public final String getNodeFactory() {
    return stringValue(HiveCC.JJTREE_NODE_FACTORY);
  }

  /**
   * Find the build node files value.
   */
  public final boolean getBuildNodeFiles() {
    return booleanValue(HiveCC.JJTREE_BUILD_NODE_FILES);
  }

  /**
   * Find the build node files value.
   */
  public final Set<String> getExcudeNodes() {
    String prefix = getNodePrefix();
    String excludes = stringValue(HiveCC.JJTREE_NODE_EXCLUDES);
    List<String> list =
        (excludes == null) || excludes.isEmpty() ? Collections.emptyList() : Arrays.asList(excludes.split(","));
    return list.stream().map(n -> prefix + n).collect(Collectors.toSet());
  }

  /**
   * Find the visitor value.
   */
  public final boolean getVisitor() {
    return booleanValue(HiveCC.JJTREE_VISITOR);
  }

  /**
   * Find the trackTokens value.
   */
  public final boolean getTrackTokens() {
    return booleanValue(HiveCC.JJTREE_TRACK_TOKENS);
  }

  /**
   * Find the node prefix value.
   */
  public final String getNodePrefix() {
    return stringValue(HiveCC.JJTREE_NODE_PREFIX);
  }


  /**
   * Find the node class name.
   */
  public final String getNodeClass() {
    return stringValue(HiveCC.JJTREE_NODE_CLASS);
  }

  /**
   * Find the output file value.
   */
  public final String getOutputFile() {
    return stringValue(HiveCC.JJTREE_OUTPUT_FILE);
  }

  /**
   * Find the visitor exception value
   */
  public final String getVisitorException() {
    return stringValue(HiveCC.JJTREE_VISITOR_EXCEPTION);
  }

  /**
   * Find the visitor data type value
   */
  public final String getVisitorDataType() {
    return stringValue(HiveCC.JJTREE_VISITOR_DATA_TYPE);
  }

  /**
   * Find the visitor return type value
   */
  public final String getVisitorReturnType() {
    return stringValue(HiveCC.JJTREE_VISITOR_RETURN_TYPE);
  }

  public final void addType(String nodeType) {
    if (!nodeType.equals("Node")) {
      this.nodesToGenerate.add(nodeType);
    }
  }

  public final Iterable<String> nodesToGenerate() {
    return this.nodesToGenerate;
  }
}
