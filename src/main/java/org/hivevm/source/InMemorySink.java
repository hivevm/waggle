// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps rendered source in memory, so a generation can be asked what it emitted without a temporary
 * directory (ADR-0018).
 */
public final class InMemorySink implements OutputSink {

    private final Map<String, String> files = new LinkedHashMap<>();

    @Override
    public void write(File file, String content) {
        this.files.put(file.getPath(), content);
    }

    /** What was written, keyed by path, in the order it was written. */
    public Map<String, String> files() {
        return Collections.unmodifiableMap(this.files);
    }

    /** The file whose path ends in {@code suffix}, if exactly one does. */
    public Optional<String> endingWith(String suffix) {
        return this.files.entrySet().stream()
                .filter(e -> e.getKey().endsWith(suffix))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
