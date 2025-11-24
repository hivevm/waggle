// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.hivevm.core.Environment;

/**
 * Represents a template rendering system that interprets a set of commands embedded within input
 * data, allowing conditional logic and repetitive constructs. A template is processed with an
 * underlying environment, and the output is written to a specified {@link PrintWriter}.
 * <p>
 * Commands such as "if", "elif", "else", "foreach", and their corresponding closing commands are
 * parsed and constructed into a tree structure, which is then rendered dynamically based on the
 * provided environment.
 */
interface Renderer {

    /**
     * Renders content to the provided {@link SourceWriter} using the specified {@link Environment}.
     * The method processes the environment's contextual data to dynamically generate the output.
     */
    void render(SourceWriter writer, Environment environment);

    record IndentRenderer(int indent) implements Renderer {

        @Override
        public void render(SourceWriter writer, Environment environment) {
            if (indent < 0)
                IntStream.range(0, -indent).forEach(i -> writer.outdent());
            else
                IntStream.range(0, indent).forEach(i -> writer.indent());
        }
    }

    /**
     * A record that implements the {@link Renderer} interface for rendering plain text. This class
     * is responsible for directly outputting the provided text to the specified
     * {@link SourceWriter}, without any additional processing or evaluation of the environment.
     */
    record TextRenderer(String text) implements Renderer {

        @Override
        public void render(SourceWriter writer, Environment environment) {
            writer.append(text);
        }
    }

    /**
     * A record implementing the {@link Renderer} interface that renders the value of a variable
     * defined in the provided {@link Environment}. The variable name is specified as a string
     * during the record's instantiation.
     */
    record VarRenderer(String text) implements Renderer {

        @Override
        public void render(SourceWriter writer, Environment environment) {
            if (environment.has(text)) {
                Object value = environment.get(text);
                if (value instanceof Context.SourceConsumer consumer)
                    consumer.apply(writer);
                else if (value instanceof Context.SourceSupplier supplier)
                    writer.append(supplier.get());
                else if (value != null)
                    writer.append(value.toString());
            }
        }
    }

    /**
     * A record implementing the {@link Renderer} interface, responsible for managing and rendering
     * a list of {@link Renderer} nodes. Each node in the list is rendered in sequence, allowing
     * complex render hierarchies to be constructed.
     */
    record ListRenderer(List<Renderer> nodes) implements Renderer {

        public ListRenderer() {
            this(new ArrayList<>());
        }

        @Override
        public void render(SourceWriter writer, Environment environment) {
            nodes.forEach(n -> n.render(writer, environment));
        }
    }

    /**
     * A record that implements the {@link Renderer} interface to enable conditional rendering based
     * on environment variables. The {@code MatchRenderer} evaluates the conditions associated with
     * the provided map of {@link Renderer} nodes and renders the first node whose condition is
     * satisfied. If no condition is satisfied, a default renderer is used, if provided.
     */
    record MatchRenderer(Map<String, Renderer> nodes) implements Renderer {

        private static final String DEFAULT = "_";

        public MatchRenderer() {
            this(new HashMap<>());
        }

        @Override
        public void render(SourceWriter writer, Environment environment) {
            var result = nodes.keySet().stream()
                .filter(environment::has)
                .filter(n -> validate(n, environment))
                .findFirst();
            if (result.isPresent()) {
                nodes.get(result.get()).render(writer, environment);
            }
            else if (nodes.containsKey(DEFAULT)) {
                nodes.get(DEFAULT).render(writer, environment);
            }
        }
    }

    /**
     * A record that implements the {@link Renderer} interface to enable conditional rendering based
     * on environment variables. The {@code MatchRenderer} evaluates the conditions associated with
     * the provided map of {@link Renderer} nodes and renders the first node whose condition is
     * satisfied. If no condition is satisfied, a default renderer is used, if provided.
     */
    record ForEachRenderer(String list, ListRenderer renderer) implements Renderer {

        public ForEachRenderer(String list) {
            this(list, new ListRenderer());
        }

        @Override
        public void render(SourceWriter writer, Environment environment) {
            var result = environment.get(list);
            if (result instanceof Integer integer) {
                for (var i = 0; i < integer; i++) {
                    renderer.render(writer, new ListEnv(environment, i));
                }
            }
            else if (result instanceof Iterable<?> iterable) {
                for (var elem : iterable) {
                    renderer.render(writer, new ListEnv(environment, elem));
                }
            }
        }
    }

    private static boolean validate(String expression, Environment environment) {
        if (expression.startsWith("!")) { // negative condition
            return !validate(expression.substring(1), environment);
        }

        if (!environment.has(expression))
            return false;

        return switch (environment.get(expression)) {
            case String text when !text.isEmpty() -> true;
            case Number number when number.intValue() != 0 -> true;
            case Boolean bool when bool -> true;
            case null -> false;
            default -> false;
        };
    }

    /**
     * Represents an environment that overlays another environment, allowing the addition of local
     * variables that override or supplement the variables in the underlying environment.
     * <p>
     * This class allows setting and retrieving variables while respecting the underlying
     * environment if a variable is not explicitly set in the current instance.
     */
    class ListEnv implements Environment {

        private final Environment environment;
        private final Object      value;

        /**
         * Constructs a TemplateEnv instance with the specified underlying environment.
         */
        private ListEnv(Environment environment, Object value) {
            this.environment = environment;
            this.value = value;
        }

        /**
         * Checks if the specified name exists in either the local environment variables or the
         * underlying environment.
         * <p>
         * The method first checks if the name exists in the local `options` map of the current
         * instance. If the name does not exist locally, it then delegates to the `has` method of
         * the underlying environment.
         */
        @Override
        public final boolean has(String name) {
            return environment.has(name);
        }

        /**
         * Retrieves the value associated with the specified name from the current environment.
         * <p>
         * This method first checks if the name exists in the local `options` map. If it exists, the
         * corresponding value is returned. If the name does not exist in the local map, the method
         * delegates the retrieval to the underlying environment using its `get` method.
         */
        @Override
        public final Object get(String name) {
            Object func = environment.get(name);
            if (func instanceof Context.SourceProvider provider)
                return (Context.SourceConsumer) writer -> provider.apply(value, writer);
            else if (func instanceof Function)
                return ((Function<Object, Object>) func).apply(value);
            return environment.get(name);
        }
    }
}