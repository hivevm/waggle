// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The templates read from the classpath, each read and parsed once.
 *
 * <p>A template used to be read from the classpath and parsed on every render; the tree emitters
 * render one template once per node type. The resources cannot change while the JVM runs, so one
 * parsed copy per path is enough.
 */
final class TemplateCache {

    private static final Map<String, Template> TEMPLATES = new ConcurrentHashMap<>();

    private TemplateCache() {
    }

    static Template get(String path) {
        return TemplateCache.TEMPLATES.computeIfAbsent(path, TemplateCache::load);
    }

    private static Template load(String path) {
        try (var stream = TemplateCache.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Invalid template name: " + path);
            }
            return new Template(stream.readAllBytes());
        } catch (IOException e) {
            throw new TemplateException("Failed to render " + path, e);
        }
    }
}
