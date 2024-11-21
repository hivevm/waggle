// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.parser.Options;

/**
 * The JJTree-specific options.
 *
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
public interface TreeOptions extends Options {

  /**
   * Find the multi value.
   */
  default boolean getMulti() {
    return booleanValue(HiveCC.JJTREE_MULTI);
  }

  /**
   * Find the node default void value.
   */
  default boolean getNodeDefaultVoid() {
    return booleanValue(HiveCC.JJTREE_NODE_DEFAULT_VOID);
  }

  /**
   * Find the node scope hook value.
   */
  default boolean getNodeScopeHook() {
    return booleanValue(HiveCC.JJTREE_NODE_SCOPE_HOOK);
  }

  /**
   * Find the node factory value.
   */
  default String getNodeFactory() {
    return stringValue(HiveCC.JJTREE_NODE_FACTORY);
  }

  /**
   * Find the build node files value.
   */
  default boolean getBuildNodeFiles() {
    return booleanValue(HiveCC.JJTREE_BUILD_NODE_FILES);
  }

  /**
   * Find the build node files value.
   */
  default Set<String> getExcudeNodes() {
    String prefix = getNodePrefix();
    String excludes = stringValue(HiveCC.JJTREE_NODE_CUSTOM);
    List<String> list =
        (excludes == null) || excludes.isEmpty() ? Collections.emptyList() : Arrays.asList(excludes.split(","));
    return list.stream().map(n -> prefix + n).collect(Collectors.toSet());
  }

  /**
   * Find the visitor value.
   */
  default boolean getVisitor() {
    return booleanValue(HiveCC.JJTREE_VISITOR);
  }

  /**
   * Find the trackTokens value.
   */
  default boolean getTrackTokens() {
    return booleanValue(HiveCC.JJTREE_TRACK_TOKENS);
  }

  /**
   * Find the node prefix value.
   */
  default String getNodePrefix() {
    return stringValue(HiveCC.JJTREE_NODE_PREFIX);
  }


  /**
   * Find the node class name.
   */
  default String getNodeClass() {
    return stringValue(HiveCC.JJTREE_NODE_CLASS);
  }

  /**
   * Find the output file value.
   */
  default String getOutputFile() {
    return stringValue(HiveCC.JJTREE_OUTPUT_FILE);
  }

  /**
   * Find the visitor exception value
   */
  default String getVisitorException() {
    return stringValue(HiveCC.JJTREE_VISITOR_EXCEPTION);
  }

  /**
   * Find the visitor data type value
   */
  default String getVisitorDataType() {
    return stringValue(HiveCC.JJTREE_VISITOR_DATA_TYPE);
  }

  /**
   * Find the visitor return type value
   */
  default String getVisitorReturnType() {
    return stringValue(HiveCC.JJTREE_VISITOR_RETURN_TYPE);
  }
}
