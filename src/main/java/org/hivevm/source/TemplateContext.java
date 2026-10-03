// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;


import java.util.HashMap;
import java.util.Map;

/**
 * A render context that overlays key-value bindings on an underlying {@link RenderContext}. Values
 * are bound directly or as suppliers; a list that a template iterates is a list of records, whose
 * components the template reads by name.
 *
 * <p>A caller may extend it to add its own view of the same names (ADR-0023); its behaviour is
 * fixed, so every method is final.
 */
public class TemplateContext implements RenderContext {

    private final RenderContext environment;
    private final Map<String, Object> options = new HashMap<>();

    /** A fresh overlay over {@code environment}. */
    public TemplateContext(RenderContext environment) {
        this.environment = environment;
    }

    /**
     * Where the rendered source goes. A context wraps the caller's own context, so it must pass
     * the sink on: forgetting to made the lexer and the parser write files while the runtime
     * classes went to the sink (ADR-0018). It used to find the sink by downcasting to Waggle's
     * options; the wrapped context now declares it (ADR-0023).
     */
    @Override
    public final OutputSink outputSink() {
        return this.environment.outputSink();
    }

    @Override
    public final String renderTitle() {
        return this.environment.renderTitle();
    }

    /**
     * Checks whether the specified name exists either in the current options map or in the
     * underlying environment.
     */
    @Override
    public final boolean has(String name) {
        return this.options.containsKey(name) || this.environment.has(name);
    }

    /**
     * Retrieves the value associated with the specified name. If the name exists in the current
     * options map, its corresponding value is returned. Otherwise, the value is retrieved from the
     * underlying environment.
     */
    @Override
    public final Object get(String name) {
        return this.options.containsKey(name) ? this.options.get(name) : this.environment.get(name);
    }

    /**
     * Associates the specified value with the given name in the current options map. If the name
     * already exists in the map, its value is updated to the given value.
     */
    public final void set(String name, Object value) {
        this.options.put(name, value);
    }

    public final void set(String name, SourceSupplier supplier) {
        this.options.put(name, supplier);
    }

    /** A value rendered as the text it supplies. */
    @FunctionalInterface
    public interface SourceSupplier {

        String get();
    }
}
