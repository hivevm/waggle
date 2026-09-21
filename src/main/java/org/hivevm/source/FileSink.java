// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Writes rendered source to disk, and leaves a file alone when it would not change.
 *
 * <p>The rendered text carries its own checksum, so "would not change" is an exact comparison of
 * the whole file. Rewriting an identical file only moves its timestamp, which makes every task
 * downstream of generation believe something happened (ADR-0018).
 */
public final class FileSink implements OutputSink {

    @Override
    public void write(File file, String content) {
        var path = file.toPath();
        try {
            if (Files.isRegularFile(path)
                    && content.equals(Files.readString(path, StandardCharsets.UTF_8))) {
                return;
            }
            var parent = file.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Was reported three times over — to stderr, to the error counter, and as a bare
            // "new Error()" with neither message nor cause.
            throw new TemplateException("Failed to write " + file, e);
        }
    }
}
