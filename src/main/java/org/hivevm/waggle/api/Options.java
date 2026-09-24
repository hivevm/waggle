// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright (c) 2005-2006, Kees Jan Koster. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/Options.java, org/javacc/jjtree/JJTreeOptions.java

package org.hivevm.waggle.api;

import org.hivevm.source.RenderContext;
import org.hivevm.core.Environment;

import java.io.File;

/**
 * The resolved settings of one generation, read by name.
 *
 * <p>The name-keyed lookup is the template contract (ADR-0005): a template reads option keys by
 * name, so the settings stay an {@link Environment}. Stages that consume settings take
 * {@code ParserOptions} or {@code TreeOptions}, which put a type and a single spelling on them and
 * turn a typo into a compile error (ADR-0019); only the two values every stage needs have accessors
 * here.
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

    /** The default lookahead depth; the grammar parser reads it for a bare {@code LOOKAHEAD}. */
    default int getLookahead() {
        return intValue(Waggle.LOOKAHEAD);
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
}
