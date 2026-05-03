// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.parser;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hivevm.cc.HiveCC;
import org.hivevm.core.Environment;

/**
 * A class with static state that stores all option information.
 */
public interface Options extends Environment {

    default int intValue(final String option) {
        return ((Integer) get(option));
    }

    default boolean booleanValue(final String option) {
        return ((Boolean) get(option));
    }

    default String stringValue(final String option) {
        return ((String) get(option));
    }

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

    // TreeOptions


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
        String excludes = stringValue(HiveCC.JJTREE_NODE_CUSTOM);
        List<String> list = (excludes == null) || excludes.isEmpty()
                ? Collections.emptyList()
                : Arrays.asList(excludes.split(","));
        return list.stream().map(n -> "AST" + n).collect(Collectors.toSet());
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
