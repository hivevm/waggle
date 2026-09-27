// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import org.hivevm.core.Environment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Represents a template rendering system that interprets a set of commands embedded within input
 * data, allowing conditional logic and repetitive constructs. A template is processed with an
 * underlying environment, and the output is written to a specified {@link LinePrinter}.
 * <p>
 * Commands such as "if", "elif", "else", "foreach", and their corresponding closing commands are
 * parsed and constructed into a tree structure, which is then rendered dynamically based on the
 * provided environment.
 */
interface Renderer {

    /**
     * Renders content to the provided {@link LinePrinter} using the specified {@link Environment}.
     * The method processes the environment's contextual data to dynamically generate the output.
     */
    void render(LinePrinter printer, Environment environment);

    record IndentRenderer(int indent) implements Renderer {

        @Override
        public void render(LinePrinter printer, Environment environment) {
            if (indent < 0)
                IntStream.range(0, -indent).forEach(i -> printer.outdent());
            else
                IntStream.range(0, indent).forEach(i -> printer.indent());
        }
    }

    /**
     * A record that implements the {@link Renderer} interface for rendering plain text. This class
     * is responsible for directly outputting the provided text to the specified
     * {@link LinePrinter}, without any additional processing or evaluation of the environment.
     */
    record TextRenderer(String text) implements Renderer {

        @Override
        public void render(LinePrinter printer, Environment environment) {
            printer.print(text);
        }
    }

    /**
     * A record implementing the {@link Renderer} interface that renders the value of a variable
     * defined in the provided {@link Environment}. The variable name is specified as a string
     * during the record's instantiation.
     */
    record VarRenderer(String text) implements Renderer {

        @Override
        public void render(LinePrinter printer, Environment environment) {
            if (!environment.has(text)) {
                // Rendering an unknown placeholder as "" produces source that does not compile —
                // "class ASTx extends  {}" or "jjtAccept(NodeVisitor v,  data)". Say so instead.
                throw new TemplateException(
                        "Unknown placeholder or invoke target '" + text + "'");
            }

            Object value = environment.get(text);
            if (value instanceof TemplateContext.SourceConsumer consumer)
                consumer.apply(printer);
            else if (value instanceof TemplateContext.SourceSupplier supplier)
                printer.print(supplier.get());
            else if (value != null)
                printer.print(value.toString());
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
        public void render(LinePrinter printer, Environment environment) {
            nodes.forEach(n -> n.render(printer, environment));
        }
    }

    /**
     * A record that implements the {@link Renderer} interface to enable conditional rendering based
     * on environment variables. The {@code MatchRenderer} evaluates the conditions associated with
     * the provided map of {@link Renderer} nodes and renders the first node whose condition is
     * satisfied. If no condition is satisfied, a default renderer is used, if provided.
     */
    record MatchRenderer(Map<String, Renderer> nodes) implements Renderer {

        /** The key of the {@code //@else} branch. */
        static final String DEFAULT = "_";

        // A LinkedHashMap, so that an if/elif chain is evaluated in source order rather than in
        // hash order.
        public MatchRenderer() {
            this(new LinkedHashMap<>());
        }

        @Override
        public void render(LinePrinter printer, Environment environment) {
            // No environment::has pre-filter here: a negated condition ("!FLAG") is not a name in
            // the environment, so the pre-filter used to drop it before validate() ever saw it —
            // which silently disabled every //@if(!X) block. validate() does the lookup itself.
            var result = nodes.keySet().stream()
                    .filter(n -> !DEFAULT.equals(n))
                    .filter(n -> validate(n, environment))
                    .findFirst();
            if (result.isPresent()) {
                nodes.get(result.get()).render(printer, environment);
            } else if (nodes.containsKey(DEFAULT)) {
                nodes.get(DEFAULT).render(printer, environment);
            }
        }
    }

    /**
     * A record that implements the {@link Renderer} interface to render its body once per element
     * of a list: the environment value {@code list} names is either a count or an
     * {@link Iterable}, and each pass sees the current element through a {@link ListEnv}.
     */
    record ForEachRenderer(String list, ListRenderer renderer) implements Renderer {

        public ForEachRenderer(String list) {
            this(list, new ListRenderer());
        }

        @Override
        public void render(LinePrinter printer, Environment environment) {
            if (!environment.has(list)) {
                throw new TemplateException("Unknown //@foreach list '" + list + "'");
            }
            var result = environment.get(list);
            if (result instanceof Integer integer) {
                for (var i = 0; i < integer; i++) {
                    renderer.render(printer, new ListEnv(environment, i));
                }
            } else if (result instanceof Iterable<?> iterable) {
                for (var elem : iterable) {
                    renderer.render(printer, new ListEnv(environment, elem));
                }
            } else {
                // Rendering nothing for a value that is neither a count nor a list would drop the
                // block as silently as an unknown name did.
                throw new TemplateException("//@foreach list '" + list + "' is neither a count nor an iterable: "
                        + (result == null ? "null" : result.getClass().getName()));
            }
        }
    }

    private static boolean validate(String expression, Environment environment) {
        if (expression.startsWith("!")) { // negative condition
            return !validate(expression.substring(1), environment);
        }

        // An unknown name used to be false: a misspelt or never-set key silently dropped its block
        // (or kept the //@if(!X) one), which yields source that does not compile far from the cause.
        if (!environment.has(expression)) {
            throw new TemplateException("Unknown condition '" + expression + "'");
        }

        return switch (environment.get(expression)) {
            case String text when !text.isEmpty() -> true;
            case Number number when number.intValue() != 0 -> true;
            case Boolean bool when bool -> true;
            case null -> false;
            default -> false;
        };
    }

    /**
     * The environment of one pass of a {@code //@foreach}: it answers every name from the
     * underlying environment, but applies a bound mapper or source provider to the current
     * element.
     */
    class ListEnv implements Environment {

        private final Environment environment;
        private final Object value;

        /**
         * Constructs the environment of the pass over {@code value}.
         */
        private ListEnv(Environment environment, Object value) {
            this.environment = environment;
            this.value = value;
        }

        /**
         * Checks if the specified name exists in the underlying environment.
         */
        @Override
        public final boolean has(String name) {
            return environment.has(name);
        }

        /**
         * Retrieves the value of the specified name from the underlying environment. A
         * {@link TemplateContext.SourceProvider} or a {@link Function} bound there is applied to the
         * current element; any other value is returned as it is.
         *
         * <p>The environment is keyed by name and holds values of no common type (ADR-0005), so what
         * comes back is an {@code Object} whose type parameter erasure has already discarded. The
         * {@code instanceof} before each cast is the check; the compiler simply cannot see it.
         */
        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public final Object get(String name) {
            Object func = environment.get(name);
            if (func instanceof TemplateContext.SourceProvider provider)
                return (TemplateContext.SourceConsumer) printer -> provider.apply(value, printer);
            else if (func instanceof Function)
                return ((Function<Object, Object>) func).apply(value);
            return func;
        }
    }
}