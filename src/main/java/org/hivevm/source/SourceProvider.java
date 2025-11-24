// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.hivevm.cc.HiveCCVersion;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.Options;

/**
 * Represents a provider for retrieving and rendering templates.
 * <p>
 * This interface defines methods for obtaining a template's resource path, generating filenames,
 * and creating corresponding file objects based on user-defined options. It additionally provides
 * default methods for rendering templates, either with or without a specified name.
 */
public interface SourceProvider {

    String getPath();

    String getType();

    File getTargetFile(String name, Options options);

    /**
     * Renders a template using the specified options.
     */
    default void render(Options options) {
        render(options, null);
    }

    /**
     * Renders a template using the specified options and name, generating an output file.
     */
    default void render(Options options, String name) {
        var path = String.format("/templates/%s/%s", getType(), getPath());
        var file = getTargetFile(name, options);
        file.getParentFile().mkdirs();
        try (var stream = SourceProvider.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Invalid template name: " + path);
            }
            var title = "HiveVM CC v." + HiveCCVersion.VERSION.toString("0.0");
            var template = new Template(stream.readAllBytes());
            try (var ostream = new FileOutputStream(file)) {
                template.render(title, ostream, options);
            }
        } catch (IOException e) {
            System.err.println("Failed to create file: " + file.getName() + " " + e);
            JavaCCErrors.semantic_error("Could not open file: " + file.getName() + " for writing.");
            throw new Error();
        }
    }
}
