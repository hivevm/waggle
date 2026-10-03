// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The templates read from the classpath, each read and parsed once.
 *
 * <p>A template used to be read from the classpath and parsed on every render; the tree emitters
 * render one template once per node type. The resources cannot change while the JVM runs, so one
 * parsed copy per path is enough, and so is one answer that a path has none.
 */
final class TemplateCache {

    private static final Map<String, Optional<Template>> TEMPLATES = new ConcurrentHashMap<>();

    private TemplateCache() {
    }

    /** The template at {@code path}; fails when there is none. */
    static Template get(String path) {
        var template = TemplateCache.find(path);
        if (template == null) {
            throw new TemplateException(path + ": no such template");
        }
        return template;
    }

    /** The template at {@code path}, or {@code null} when there is none. */
    static Template find(String path) {
        return TemplateCache.TEMPLATES.computeIfAbsent(path, TemplateCache::load).orElse(null);
    }

    private static Optional<Template> load(String path) {
        try (var stream = TemplateCache.class.getResourceAsStream(path)) {
            if (stream == null) {
                return Optional.empty();
            }
            return Optional.of(new Template(path, stream.readAllBytes()));
        } catch (IOException e) {
            throw new TemplateException(path + ": cannot be read", e);
        }
    }
}
