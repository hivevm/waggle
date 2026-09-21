// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.api;

import org.hivevm.source.FileSink;
import org.hivevm.source.OutputSink;
import org.hivevm.waggle.api.Waggle;
import org.hivevm.core.Environment;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A class with static state that stores all option information.
 */
public interface Options extends Environment {

    /** Sets an option, e.g. a back end filling in a default only it can know. */
    void set(String name, Object value);

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
        return stringValue(Waggle.PARSER_NAME);
    }

    /**
     * Find the lookahead setting.
     */
    default int getLookahead() {
        return intValue(Waggle.JJPARSER_LOOKAHEAD);
    }

    /**
     * Find the choice ambiguity check value.
     */
    default int getChoiceAmbiguityCheck() {
        return intValue(Waggle.JJPARSER_CHOICE_AMBIGUITY_CHECK);
    }

    /**
     * Find the other ambiguity check value.
     */
    default int getOtherAmbiguityCheck() {
        return intValue(Waggle.JJPARSER_OTHER_AMBIGUITY_CHECK);
    }

    /**
     * Find the no DFA value.
     */
    default boolean withoutNoDfa() {
        return booleanValue(Waggle.JJPARSER_NO_DFA);
    }

    /**
     * Find the debug parser value.
     */
    default boolean getDebugParser() {
        return booleanValue(Waggle.JJPARSER_DEBUG_PARSER);
    }

    /**
     * Find the debug lookahead value.
     */
    default boolean getDebugLookahead() {
        return booleanValue(Waggle.JJPARSER_DEBUG_LOOKAHEAD);
    }

    /**
     * Find the debug tokenmanager value.
     */
    default boolean getDebugTokenManager() {
        return booleanValue(Waggle.JJPARSER_DEBUG_TOKEN_MANAGER);
    }

    /**
     * Find the error reporting value.
     */
    default boolean getErrorReporting() {
        return booleanValue(Waggle.JJPARSER_ERROR_REPORTING);
    }

    /**
     * Find the ignore case value.
     */
    default boolean getIgnoreCase() {
        return booleanValue(Waggle.JJPARSER_IGNORE_CASE);
    }

    /**
     * Find the sanity check value.
     */
    default boolean getSanityCheck() {
        return booleanValue(Waggle.JJPARSER_SANITY_CHECK);
    }

    /**
     * Find the force lookahead check value.
     */
    default boolean getForceLaCheck() {
        return booleanValue(Waggle.JJPARSER_FORCE_LA_CHECK);
    }

    /**
     * Find the cache tokens value.
     */
    default boolean getCacheTokens() {
        return booleanValue(Waggle.JJPARSER_CACHE_TOKENS);
    }

    /**
     * Find the keep line column value.
     */
    default boolean getKeepLineColumn() {
        return booleanValue(Waggle.JJPARSER_KEEP_LINE_COLUMN);
    }

    /**
     * Get defined parser recursion depth limit.
     */
    default int getDepthLimit() {
        return intValue(Waggle.JJPARSER_DEPTH_LIMIT);
    }

    /**
     * Get defined Java package name.
     */
    default String getJavaPackageName() {
        return stringValue(Waggle.JJPARSER_JAVA_PACKAGE);
    }

    /**
     * Find the output directory.
     */
    default File getOutputDirectory() {
        return new File(stringValue(Waggle.JJPARSER_OUTPUT_DIRECTORY));
    }

    /**
     * Where the rendered source goes. It sits beside the output directory because it answers the
     * same question, and a caller that did not choose one writes files (ADR-0018).
     */
    default OutputSink outputSink() {
        return new FileSink();
    }

    // TreeOptions

    /**
     * Find the multi value.
     */
    default boolean getMulti() {
        return booleanValue(Waggle.JJTREE_MULTI);
    }

    /**
     * Find the node default void value.
     */
    default boolean getNodeDefaultVoid() {
        return booleanValue(Waggle.JJTREE_NODE_DEFAULT_VOID);
    }

    /**
     * Find the node scope hook value.
     */
    default boolean getNodeScopeHook() {
        return booleanValue(Waggle.JJTREE_NODE_SCOPE_HOOK);
    }

    /**
     * Find the node factory value.
     */
    default String getNodeFactory() {
        return stringValue(Waggle.JJTREE_NODE_FACTORY);
    }

    /**
     * Find the build node files value.
     */
    default boolean getBuildNodeFiles() {
        return booleanValue(Waggle.JJTREE_BUILD_NODE_FILES);
    }

    /**
     * Find the build node files value.
     */
    default Set<String> getExcudeNodes() {
        String excludes = stringValue(Waggle.JJTREE_NODE_CUSTOM);
        List<String> list = (excludes == null) || excludes.isEmpty()
                ? Collections.emptyList()
                : Arrays.asList(excludes.split(","));
        return list.stream().map(n -> "AST" + n).collect(Collectors.toSet());
    }

    /**
     * Find the visitor value.
     */
    default boolean getVisitor() {
        return booleanValue(Waggle.JJTREE_VISITOR);
    }

    /**
     * Find the trackTokens value.
     */
    default boolean getTrackTokens() {
        return booleanValue(Waggle.JJTREE_TRACK_TOKENS);
    }

    /**
     * Find the node class name.
     */
    default String getNodeClass() {
        return stringValue(Waggle.JJTREE_NODE_CLASS);
    }

    /**
     * Find the output file value.
     */
    default String getOutputFile() {
        return stringValue(Waggle.JJTREE_OUTPUT_FILE);
    }

    /**
     * Find the visitor exception value
     */
    default String getVisitorException() {
        return stringValue(Waggle.JJTREE_VISITOR_EXCEPTION);
    }

    /**
     * Find the visitor data type value
     */
    default String getVisitorDataType() {
        return stringValue(Waggle.JJTREE_VISITOR_DATA_TYPE);
    }

    /**
     * Find the visitor return type value
     */
    default String getVisitorReturnType() {
        return stringValue(Waggle.JJTREE_VISITOR_RETURN_TYPE);
    }
}
