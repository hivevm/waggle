// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.parser;

import java.io.File;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.utils.Environment;

/**
 * A class with static state that stores all option information.
 */
public interface Options extends Environment {

  /**
   * Find the lookahead setting.
   */
  default String getParserName() {
    return stringValue(HiveCC.PARSER_NAME);
  }

  /**
   * Find the lookahead setting.
   */
  default int getLookahead() {
    return intValue(HiveCC.JJPARSER_LOOKAHEAD);
  }

  /**
   * Find the choice ambiguity check value.
   */
  default int getChoiceAmbiguityCheck() {
    return intValue(HiveCC.JJPARSER_CHOICE_AMBIGUITY_CHECK);
  }

  /**
   * Find the other ambiguity check value.
   */
  default int getOtherAmbiguityCheck() {
    return intValue(HiveCC.JJPARSER_OTHER_AMBIGUITY_CHECK);
  }

  /**
   * Find the no DFA value.
   */
  default boolean withoutNoDfa() {
    return booleanValue(HiveCC.JJPARSER_NO_DFA);
  }

  /**
   * Find the debug parser value.
   */
  default boolean getDebugParser() {
    return booleanValue(HiveCC.JJPARSER_DEBUG_PARSER);
  }

  /**
   * Find the debug lookahead value.
   */
  default boolean getDebugLookahead() {
    return booleanValue(HiveCC.JJPARSER_DEBUG_LOOKAHEAD);
  }

  /**
   * Find the debug tokenmanager value.
   */
  default boolean getDebugTokenManager() {
    return booleanValue(HiveCC.JJPARSER_DEBUG_TOKEN_MANAGER);
  }

  /**
   * Find the error reporting value.
   */
  default boolean getErrorReporting() {
    return booleanValue(HiveCC.JJPARSER_ERROR_REPORTING);
  }

  /**
   * Find the ignore case value.
   */
  default boolean getIgnoreCase() {
    return booleanValue(HiveCC.JJPARSER_IGNORE_CASE);
  }

  /**
   * Find the sanity check value.
   */
  default boolean getSanityCheck() {
    return booleanValue(HiveCC.JJPARSER_SANITY_CHECK);
  }

  /**
   * Find the force lookahead check value.
   */
  default boolean getForceLaCheck() {
    return booleanValue(HiveCC.JJPARSER_FORCE_LA_CHECK);
  }

  /**
   * Find the cache tokens value.
   */
  default boolean getCacheTokens() {
    return booleanValue(HiveCC.JJPARSER_CACHE_TOKENS);
  }

  /**
   * Find the keep line column value.
   */
  default boolean getKeepLineColumn() {
    return booleanValue(HiveCC.JJPARSER_KEEP_LINE_COLUMN);
  }

  /**
   * Get defined parser recursion depth limit.
   */
  default int getDepthLimit() {
    return intValue(HiveCC.JJPARSER_DEPTH_LIMIT);
  }

  /**
   * Get defined Java package name.
   */
  default String getJavaPackageName() {
    return stringValue(HiveCC.JJPARSER_JAVA_PACKAGE);
  }

  /**
   * Get defined the value for CPP to stop on first error.
   */
  default boolean stopOnFirstError() {
    return booleanValue(HiveCC.JJPARSER_CPP_STOP_ON_FIRST_ERROR);
  }

  /**
   * Find the output directory.
   */
  default File getOutputDirectory() {
    return new File(stringValue(HiveCC.JJPARSER_OUTPUT_DIRECTORY));
  }
}
