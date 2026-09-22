// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import java.io.File;
import java.io.IOException;

/**
 * Represents a provider for retrieving and rendering templates.
 * <p>
 * This interface defines methods for obtaining a template's resource path, generating filenames,
 * and creating corresponding file objects based on user-defined options. It additionally provides
 * default methods for rendering templates, either with or without a specified name.
 */
public interface SourceProvider<C extends RenderContext> {

    String getPath();

    String getType();

    File getTargetFile(String name, C context);

    /**
     * Renders a template using the specified options.
     */
    default void render(C context) {
        render(context, (String) null);
    }

    /**
     * Renders a template using the specified options and name, handing the source to the sink the
     * generation chose (ADR-0018).
     */
    default void render(C context, String name) {
        context.outputSink().write(getTargetFile(name, context), renderToString(context));
    }

    /** The source this template produces, as text. */
    default String renderToString(C context) {
        var path = String.format("/templates/%s/%s", getType(), getPath());
        try (var stream = SourceProvider.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Invalid template name: " + path);
            }
            return new Template(stream.readAllBytes()).render(context.renderTitle(), context);
        } catch (IOException e) {
            throw new TemplateException("Failed to render " + path, e);
        }
    }
}
