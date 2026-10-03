// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hivevm.source.Template;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Every applied template is checked against the record it renders, without generating a grammar
 * (ADR-0031).
 *
 * <p>A template under {@code apply/} renders the record named like the file, and reads its
 * components by name. Those names used to be checked only when a grammar happened to reach the
 * template: a renamed component, a condition on a record (which is never true), or a record type
 * with no template of its own in one target surfaced as a failed generation, if at all. Here every
 * template is held against the types instead.
 *
 * <p>A name in lower case is a component; one in upper case is an option or a value the back end
 * binds for the whole file, which only a generation can check.
 */
class TemplateRecordsTest {

    private static final Path TEMPLATES = Path.of("src", "main", "resources", "templates");
    private static final Path CLASSES = Path.of("build", "classes", "java", "main");

    /** The package of the output model, which wins where a plan record has the same name. */
    private static final String OUTPUT_MODEL = "org.hivevm.waggle.codegen.";

    /** What a condition can test: anything else is never true. */
    private static final Set<Class<?>> TRUTHY = Set.of(boolean.class, Boolean.class, String.class,
            int.class, Integer.class, long.class, Long.class);

    /**
     * The record types a target leaves without a template, because it refuses the option that
     * produces them before anything is written: Rust has no DEPTH_LIMIT guard and no DEBUG_PARSER
     * trace yet (ADR-0030).
     */
    private static final Map<String, Set<String>> REFUSED = Map.of(
            "rust", Set.of("DepthGuarded", "Traced"));

    /** One applied template: the directory it is applied in, its record and what it refers to. */
    private record Applied(Path file, Class<?> record, List<Template.Reference> references) {
    }

    /** The records of the main classes by simple name. */
    private Map<String, List<Class<?>>> records;
    /** The applied templates by directory and record. */
    private final Map<Path, Map<Class<?>, Applied>> applied = new HashMap<>();
    /** The records whose templates apply a record, by directory and record. */
    private final Map<Path, Map<Class<?>, Set<Class<?>>>> appliers = new HashMap<>();

    @Test
    void everyAppliedTemplateMatchesItsRecord() throws IOException {
        this.records = TemplateRecordsTest.records();
        var problems = new ArrayList<String>();

        for (var file : TemplateRecordsTest.appliedTemplates()) {
            var name = file.getFileName().toString().replaceFirst("\\..*$", "");
            var record = resolve(name);
            if (record == null) {
                problems.add(file + ": no single record named " + name + " among "
                        + this.records.getOrDefault(name, List.of()));
                continue;
            }
            var template = new Template(file.toString(), Files.readString(file));
            this.applied.computeIfAbsent(file.getParent(), d -> new HashMap<>())
                    .put(record, new Applied(file, record, template.references()));
        }

        // Who applies whom: an applied template also reads the components of the records that
        // apply it, as the engine looks a name up through every record around it. What it applies
        // can itself be such a component, so the graph grows until nothing is added.
        for (var grown = true; grown; ) {
            grown = false;
            for (var directory : this.applied.entrySet()) {
                for (var entry : directory.getValue().values()) {
                    for (var reference : entry.references()) {
                        var component = reference.directive().equals("apply")
                                ? find(directory.getKey(), entry.record(), reference.name(),
                                new LinkedHashSet<>()) : null;
                        if (component != null) {
                            for (var child : TemplateRecordsTest.records(component.getGenericType())) {
                                grown |= this.appliers
                                        .computeIfAbsent(directory.getKey(), d -> new HashMap<>())
                                        .computeIfAbsent(child, c -> new LinkedHashSet<>())
                                        .add(entry.record());
                            }
                        }
                    }
                }
            }
        }

        var checked = 0;
        for (var directory : this.applied.values()) {
            for (var entry : directory.values()) {
                for (var reference : entry.references()) {
                    check(entry, reference, problems);
                }
                checked++;
            }
        }

        assertTrue(checked > 100, "only " + checked + " applied templates were checked");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** Checks one name the template of {@code entry} refers to. */
    private void check(Applied entry, Template.Reference reference, List<String> problems) {
        var name = reference.name();
        if (!Character.isLowerCase(name.charAt(0))) {
            return; // an option, or a value bound for the whole file
        }
        var file = entry.file();
        var record = entry.record();
        var where = file + ":" + reference.line() + ": ";

        // Inside a //@foreach over a component, the element answers first.
        RecordComponent component = null;
        if (reference.list() != null) {
            var list = find(file.getParent(), record, reference.list(), new LinkedHashSet<>());
            if (list == null) {
                return; // a list bound for the whole file: its element is not known here
            }
            for (var element : TemplateRecordsTest.records(list.getGenericType())) {
                component = (component != null) ? component : TemplateRecordsTest.component(element, name);
            }
        }
        if (component == null) {
            component = find(file.getParent(), record, name, new LinkedHashSet<>());
        }
        if (component == null) {
            problems.add(where + "neither " + record.getName()
                    + " nor every record that applies it has a component '" + name + "'");
            return;
        }

        var type = component.getType();
        switch (reference.directive()) {
            case "if", "elif" -> {
                if (!TemplateRecordsTest.TRUTHY.contains(type)) {
                    problems.add(where + "//@if(" + name + ") tests a " + type.getSimpleName()
                            + ", which is never true");
                }
            }
            case "placeholder" -> {
                if (type.isRecord() || Iterable.class.isAssignableFrom(type)) {
                    problems.add(where + "__" + name + "__ writes a " + type.getSimpleName()
                            + " as its toString()");
                }
            }
            case "apply" -> {
                for (var applied : TemplateRecordsTest.records(component.getGenericType())) {
                    checkApplied(file, where, name, applied, problems);
                }
            }
            case "foreach" -> {
                if (!Iterable.class.isAssignableFrom(type)) {
                    problems.add(where + "//@foreach(" + name + ") walks a " + type.getSimpleName());
                }
            }
            default -> problems.add(where + "unknown directive " + reference.directive());
        }
    }

    /**
     * The component {@code name} as a template of {@code record} sees it: the record's own, or else
     * one that every record applying it sees. {@code null} when a record that applies it does not
     * see it either, or when nothing applies it — then the name can only come from the file.
     */
    private RecordComponent find(Path directory, Class<?> record, String name, Set<Class<?>> seen) {
        // seen holds the records on the way up from the template being checked.
        var own = TemplateRecordsTest.component(record, name);
        if (own != null) {
            return own;
        }
        var parents = this.appliers.getOrDefault(directory, Map.of()).getOrDefault(record, Set.of());
        seen.add(record);
        RecordComponent found = null;
        for (var parent : parents) {
            if (seen.contains(parent)) {
                continue; // a record that applies itself adds nothing on the way round
            }
            var component = find(directory, parent, name, seen);
            if (component == null) {
                found = null;
                break;
            }
            found = (found != null) ? found : component;
        }
        seen.remove(record);
        return found;
    }

    /**
     * A record that {@code //@apply(name)} renders needs a template next to this one, and that
     * template must be the one for this type: a record of the output model and one of the plan can
     * share a name, and the engine picks a template by name alone.
     */
    private void checkApplied(Path file, String where, String name, Class<?> applied,
                              List<String> problems) {
        var extension = file.getFileName().toString().replaceFirst("^[^.]*", "");
        var target = file.resolveSibling(applied.getSimpleName() + extension);
        if (!Files.exists(target)) {
            var group = file.getParent().getParent().getParent().getFileName().toString();
            if (REFUSED.getOrDefault(group, Set.of()).contains(applied.getSimpleName())) {
                return;
            }
            problems.add(where + "//@apply(" + name + ") renders " + applied.getName()
                    + ", which has no template " + target);
        } else if (resolve(applied.getSimpleName()) != applied) {
            problems.add(where + "//@apply(" + name + ") renders " + applied.getName()
                    + " through " + target + ", which is written for "
                    + resolve(applied.getSimpleName()));
        }
    }

    /** The component {@code name} of {@code record}, or {@code null}. */
    private static RecordComponent component(Class<?> record, String name) {
        return Arrays.stream(record.getRecordComponents()).filter(c -> c.getName().equals(name))
                .findFirst().orElse(null);
    }

    /**
     * The record types a value of {@code type} can be: the record itself, the element of a list,
     * every record a sealed interface permits. Nothing for a type that is not known to be a
     * record, such as {@code Object}.
     */
    private static Set<Class<?>> records(Type type) {
        if (type instanceof ParameterizedType parameterized
                && parameterized.getRawType() instanceof Class<?> raw
                && Iterable.class.isAssignableFrom(raw)) {
            return TemplateRecordsTest.records(parameterized.getActualTypeArguments()[0]);
        }
        var result = new LinkedHashSet<Class<?>>();
        if (type instanceof Class<?> c) {
            if (c.isRecord()) {
                result.add(c);
            } else if (c.isSealed()) {
                for (var permitted : c.getPermittedSubclasses()) {
                    result.addAll(TemplateRecordsTest.records(permitted));
                }
            }
        }
        return result;
    }

    /** The record a template of that name renders: the output model's, if it has one. */
    private Class<?> resolve(String name) {
        var candidates = this.records.getOrDefault(name, List.of());
        var model = candidates.stream().filter(c -> c.getName().startsWith(OUTPUT_MODEL)).toList();
        var chosen = model.isEmpty() ? candidates : model;
        return (chosen.size() == 1) ? chosen.getFirst() : null;
    }

    /** Every file below an {@code apply} directory of a target. */
    private static List<Path> appliedTemplates() throws IOException {
        try (Stream<Path> files = Files.walk(TemplateRecordsTest.TEMPLATES)) {
            return files.filter(Files::isRegularFile)
                    .filter(f -> f.getParent().getFileName().toString().equals("apply"))
                    .sorted().toList();
        }
    }

    /** The records of the main classes by simple name. */
    private static Map<String, List<Class<?>>> records() throws IOException {
        assertTrue(Files.isDirectory(CLASSES),
                "compiled classes not found at " + CLASSES.toAbsolutePath() + " (run ./gradlew test)");
        var records = new HashMap<String, List<Class<?>>>();
        try (Stream<Path> files = Files.walk(CLASSES)) {
            for (var file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                var name = CLASSES.relativize(file).toString().replace(file.getFileSystem()
                        .getSeparator(), ".").replaceFirst("\\.class$", "");
                Class<?> type;
                try {
                    type = Class.forName(name, false, TemplateRecordsTest.class.getClassLoader());
                } catch (ClassNotFoundException | LinkageError e) {
                    continue;
                }
                if (type.isRecord()) {
                    records.computeIfAbsent(type.getSimpleName(), k -> new ArrayList<>()).add(type);
                }
            }
        }
        return records;
    }
}
