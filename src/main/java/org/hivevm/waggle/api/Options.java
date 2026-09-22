// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.api;

import org.hivevm.source.RenderContext;
import org.hivevm.core.Environment;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The resolved settings of one generation, read by name.
 *
 * <p>The name-keyed lookup is the template contract (ADR-0005): a template reads option keys by
 * name, so the settings stay an {@link Environment}. The accessors below put a type and a single
 * spelling on top of it. Stages that only consume settings should take {@code ParserOptions} or
 * {@code TreeOptions} instead, which turn a typo into a compile error (ADR-0019).
 *
 * <p>Nothing here is static: the settings belong to one generation and are carried to the stages by
 * the {@code GenerationContext} (ADR-0015).
 */
public interface Options extends RenderContext {

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

    /** The grammar's name, which also names the generated parser. */
    default String getParserName() {
        return stringValue(Waggle.PARSER_NAME);
    }

    /** The default lookahead depth. */
    default int getLookahead() {
        return intValue(Waggle.LOOKAHEAD);
    }

    /**
     * Find the choice ambiguity check value.
     */
    default int getChoiceAmbiguityCheck() {
        return intValue(Waggle.CHOICE_AMBIGUITY_CHECK);
    }

    /**
     * Find the other ambiguity check value.
     */
    default int getOtherAmbiguityCheck() {
        return intValue(Waggle.OTHER_AMBIGUITY_CHECK);
    }

    /** Whether the string-literal DFA is skipped and everything goes through the NFA. */
    default boolean getNoDfa() {
        return booleanValue(Waggle.NO_DFA);
    }

    /**
     * Find the debug parser value.
     */
    default boolean getDebugParser() {
        return booleanValue(Waggle.DEBUG_PARSER);
    }

    /**
     * Find the debug lookahead value.
     */
    default boolean getDebugLookahead() {
        return booleanValue(Waggle.DEBUG_LOOKAHEAD);
    }

    /**
     * Find the debug tokenmanager value.
     */
    default boolean getDebugTokenManager() {
        return booleanValue(Waggle.DEBUG_TOKEN_MANAGER);
    }

    /**
     * Find the error reporting value.
     */
    default boolean getErrorReporting() {
        return booleanValue(Waggle.ERROR_REPORTING);
    }

    /**
     * Find the ignore case value.
     */
    default boolean getIgnoreCase() {
        return booleanValue(Waggle.IGNORE_CASE);
    }

    /**
     * Find the sanity check value.
     */
    default boolean getSanityCheck() {
        return booleanValue(Waggle.SANITY_CHECK);
    }

    /**
     * Find the force lookahead check value.
     */
    default boolean getForceLaCheck() {
        return booleanValue(Waggle.FORCE_LA_CHECK);
    }

    /**
     * Find the cache tokens value.
     */
    default boolean getCacheTokens() {
        return booleanValue(Waggle.CACHE_TOKENS);
    }

    /**
     * Find the keep line column value.
     */
    default boolean getKeepLineColumn() {
        return booleanValue(Waggle.KEEP_LINE_COLUMN);
    }

    /**
     * Get defined parser recursion depth limit.
     */
    default int getDepthLimit() {
        return intValue(Waggle.DEPTH_LIMIT);
    }

    /**
     * Get defined Java package name.
     */
    default String getJavaPackageName() {
        return stringValue(Waggle.JAVA_PACKAGE);
    }

    /**
     * Find the output directory.
     */
    default File getOutputDirectory() {
        return new File(stringValue(Waggle.OUTPUT_DIRECTORY));
    }

    /**
     * The banner a generated file carries. The template engine asks for it instead of reading
     * {@link WaggleVersion} itself, which is what let it stay free of this package (ADR-0023).
     *
     * <p>{@code outputSink()} is inherited from {@link RenderContext}: it answers the same question
     * as the output directory and defaults to writing files (ADR-0018).
     */
    @Override
    default String renderTitle() {
        return "HiveVM Waggle v." + WaggleVersion.VERSION.toString("0.0");
    }

    // TreeOptions

    /**
     * Find the multi value.
     */
    default boolean getMulti() {
        return booleanValue(Waggle.NODE_MULTI);
    }

    /**
     * Find the node default void value.
     */
    default boolean getNodeDefaultVoid() {
        return booleanValue(Waggle.NODE_DEFAULT_VOID);
    }

    /**
     * Find the node scope hook value.
     */
    default boolean getNodeScopeHook() {
        return booleanValue(Waggle.NODE_SCOPE_HOOK);
    }

    /**
     * Find the node factory value.
     */
    default String getNodeFactory() {
        return stringValue(Waggle.NODE_FACTORY);
    }

    /**
     * Find the build node files value.
     */
    default boolean getBuildNodeFiles() {
        return booleanValue(Waggle.BUILD_NODE_FILES);
    }

    /**
     * The node classes the grammar author supplies ({@code NODE_CUSTOM}), so generation skips them.
     */
    default Set<String> getExcludeNodes() {
        String excludes = stringValue(Waggle.NODE_CUSTOM);
        List<String> list = (excludes == null) || excludes.isEmpty()
                ? Collections.emptyList()
                : Arrays.asList(excludes.split(","));
        return list.stream().map(n -> "AST" + n).collect(Collectors.toSet());
    }

    /**
     * Find the visitor value.
     */
    default boolean getVisitor() {
        return booleanValue(Waggle.VISITOR);
    }

    /**
     * Find the trackTokens value.
     */
    default boolean getTrackTokens() {
        return booleanValue(Waggle.TRACK_TOKENS);
    }

    /**
     * Find the node class name.
     */
    default String getNodeClass() {
        return stringValue(Waggle.NODE_CLASS);
    }

    /**
     * Find the output file value.
     */
    default String getOutputFile() {
        return stringValue(Waggle.OUTPUT_FILE);
    }

    /**
     * Find the visitor exception value
     */
    default String getVisitorException() {
        return stringValue(Waggle.VISITOR_EXCEPTION);
    }

    /**
     * Find the visitor data type value
     */
    default String getVisitorDataType() {
        return stringValue(Waggle.VISITOR_DATA_TYPE);
    }

    /**
     * Find the visitor return type value
     */
    default String getVisitorReturnType() {
        return stringValue(Waggle.VISITOR_RETURN_TYPE);
    }
}
