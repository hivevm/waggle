// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.api;

import org.hivevm.source.TemplateContext;

/**
 * A render context that is both a template overlay and this generation's options.
 *
 * <p>A back end builds one of these per file it renders, binds the template's own variables on it,
 * and hands it to a {@code TemplateSet.Source}. That source needs both halves: the overlay to look
 * names up, and the options to decide which file to write ({@code JAVA_PACKAGE},
 * {@code OUTPUT_DIRECTORY}).
 *
 * <p>The template engine used to supply both, because {@code org.hivevm.source.Context} extended
 * {@code Options} — the cycle ADR-0023 removes. The join belongs on this side: every accessor of
 * {@link Options} is a default method over {@code get(String)}, so an object that can answer names
 * is an option set, and all this class adds is the declaration that says so.
 */
public final class OptionsContext extends TemplateContext implements Options {

    private OptionsContext(Options options) {
        super(options);
    }

    /** A fresh overlay over {@code options}, carrying its sink and its banner. */
    public static OptionsContext of(Options options) {
        return new OptionsContext(options);
    }
}
