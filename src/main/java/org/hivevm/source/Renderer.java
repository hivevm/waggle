// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import org.hivevm.core.Environment;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Represents a template rendering system that interprets a set of commands embedded within input
 * data, allowing conditional logic and repetitive constructs. A template is processed with an
 * underlying environment, and the output is written to a specified {@link LinePrinter}.
 * <p>
 * Commands such as "if", "elif", "else", "foreach", and their corresponding closing commands are
 * parsed and constructed into a tree structure, which is then rendered dynamically based on the
 * provided environment.
 *
 * <p>A node that can fail while rendering carries {@code where}, the template and line it was
 * written on, so that the failure names the line rather than only the name it could not find.
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
    record VarRenderer(String text, String where) implements Renderer {

        @Override
        public void render(LinePrinter printer, Environment environment) {
            if (!environment.has(text)) {
                // Rendering an unknown placeholder as "" produces source that does not compile —
                // "class ASTx extends  {}" or "jjtAccept(NodeVisitor v,  data)". Say so instead.
                throw new TemplateException(
                        where + ": unknown placeholder '" + text + "'");
            }

            Object value = environment.get(text);
            if (value instanceof TemplateContext.SourceSupplier supplier)
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
     * One branch of an {@code //@if}: the {@code //@if} itself, an {@code //@elif}, or, with no
     * condition, the {@code //@else}.
     */
    record Branch(String condition, ListRenderer body, String where) {
    }

    /**
     * An {@code //@if} chain: renders the body of the first branch, in source order, whose condition
     * holds, or the {@code //@else} branch, which the builder keeps last.
     */
    record MatchRenderer(List<Branch> branches) implements Renderer {

        public MatchRenderer() {
            this(new ArrayList<>());
        }

        @Override
        public void render(LinePrinter printer, Environment environment) {
            for (var branch : branches) {
                if ((branch.condition() == null)
                        || validate(branch.condition(), environment, branch.where())) {
                    branch.body().render(printer, environment);
                    return;
                }
            }
        }
    }

    /**
     * A record that implements the {@link Renderer} interface to render its body once per element
     * of a list of records: each pass reads the components of its element by name, and the
     * surrounding environment for everything else, as an applied template does.
     */
    record ForEachRenderer(String list, ListRenderer renderer, String where) implements Renderer {

        @Override
        public void render(LinePrinter printer, Environment environment) {
            if (!environment.has(list)) {
                throw new TemplateException(where + ": unknown //@foreach list '" + list + "'");
            }
            if (!(environment.get(list) instanceof Iterable<?> iterable)) {
                // Rendering nothing for a value that is not a list would drop the block as silently
                // as an unknown name did.
                var value = environment.get(list);
                throw new TemplateException(where + ": //@foreach list '" + list + "' is not a list: "
                        + (value == null ? "null" : value.getClass().getName()));
            }
            for (var element : iterable) {
                renderer.render(printer, RecordEnv.of(environment, element,
                        () -> where + ": //@foreach(" + list + ")"));
            }
        }
    }

    /**
     * A record that implements the {@link Renderer} interface to render a plan record through the
     * template named after its type: the fourth of the operations a template engine needs to
     * generate nested output, and the one this engine lacked (ADR-0031).
     *
     * <p>The attribute holds a record, or a list of them, or nothing. Each is rendered by the
     * template {@code base} names for its type, against a {@link RecordEnv} that answers the
     * record's components and falls back to the surrounding environment for everything else — so
     * an applied template still reads the options.
     *
     * @param attribute the name the record is bound to
     * @param base      the resource path of the applied template, with a {@code %s} for the type
     */
    record ApplyRenderer(String attribute, String base, String where) implements Renderer {

        @Override
        public void render(LinePrinter printer, Environment environment) {
            if (!environment.has(attribute)) {
                throw new TemplateException(where + ": unknown //@apply attribute '" + attribute + "'");
            }
            var value = environment.get(attribute);
            if (value instanceof Iterable<?> iterable) {
                for (var element : iterable) {
                    apply(printer, environment, element);
                }
            } else {
                apply(printer, environment, value);
            }
        }

        private void apply(LinePrinter printer, Environment environment, Object value) {
            if (value == null) {
                return;
            }
            var env = RecordEnv.of(environment, value, () -> where + ": //@apply(" + attribute + ")");
            var path = String.format(base, value.getClass().getSimpleName());
            var template = TemplateCache.find(path);
            if (template == null) {
                throw new TemplateException(where + ": //@apply(" + attribute + ") has no template "
                        + path + " for " + value.getClass().getName());
            }
            template.renderInto(printer, env);
        }
    }

    /**
     * The environment of one record: its components, and the surrounding environment for every
     * other name. It is what an applied template and a pass of a {@code //@foreach} read.
     */
    final class RecordEnv implements Environment {

        /** The accessors of a record type by component name, looked up once per type. */
        private static final ClassValue<Map<String, Method>> ACCESSORS = new ClassValue<>() {
            @Override
            protected Map<String, Method> computeValue(Class<?> type) {
                var accessors = new LinkedHashMap<String, Method>();
                for (var component : type.getRecordComponents()) {
                    accessors.put(component.getName(), component.getAccessor());
                }
                return Map.copyOf(accessors);
            }
        };

        private final Environment environment;
        private final Object record;
        private final Map<String, Method> accessors;

        private RecordEnv(Environment environment, Object record) {
            this.environment = environment;
            this.record = record;
            this.accessors = RecordEnv.ACCESSORS.get(record.getClass());
        }

        /**
         * The environment of {@code value}, which must be a record.
         *
         * @param what names the directive for the error, which is rare enough to be built lazily
         */
        static RecordEnv of(Environment environment, Object value,
                            java.util.function.Supplier<String> what) {
            if (value == null || !value.getClass().isRecord()) {
                // Something that has no components would render nothing of its own, and say why
                // only much later.
                throw new TemplateException(what.get() + " needs a record, but got "
                        + (value == null ? "null" : value.getClass().getName()));
            }
            return new RecordEnv(environment, value);
        }

        @Override
        public boolean has(String name) {
            return this.accessors.containsKey(name) || this.environment.has(name);
        }

        @Override
        public Object get(String name) {
            var accessor = this.accessors.get(name);
            if (accessor == null) {
                return this.environment.get(name);
            }
            try {
                return accessor.invoke(this.record);
            } catch (ReflectiveOperationException e) {
                throw new TemplateException("Cannot read '" + name + "' of "
                        + this.record.getClass().getName(), e);
            }
        }
    }

    private static boolean validate(String expression, Environment environment, String where) {
        if (expression.startsWith("!")) { // negative condition
            return !validate(expression.substring(1), environment, where);
        }

        // An unknown name used to be false: a misspelt or never-set key silently dropped its block
        // (or kept the //@if(!X) one), which yields source that does not compile far from the cause.
        if (!environment.has(expression)) {
            throw new TemplateException(where + ": unknown condition '" + expression + "'");
        }

        return switch (environment.get(expression)) {
            case String text when !text.isEmpty() -> true;
            case Number number when number.intValue() != 0 -> true;
            case Boolean bool when bool -> true;
            case null -> false;
            default -> false;
        };
    }
}
