// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The templates of one target language, and the two things a target decides about them: which
 * resource a template is read from, and which file it writes.
 *
 * <p>Those two answers used to be re-implemented by {@code JavaTemplate}, {@code CppTemplate} and
 * {@code RustTemplate}, which disagreed about how the one relates to the other — C++ carried a
 * {@code .h}/{@code .cc} split, Java folded the package into the directory, Rust lower-cased
 * everything. Every cross-cutting change had to be made three times and could be made three
 * different ways (ADR-0018).
 *
 * <p>A template is addressed by its group — {@code runtime}, {@code lexer}, {@code parser} or
 * {@code tree} — so the directory says what a file is for.
 */
public final class TemplateSet<C extends RenderContext> {

    /** Turns a template's name into the file it writes, which is the target's own business. */
    @FunctionalInterface
    public interface Naming<C extends RenderContext> {

        File targetFile(String name, C context);
    }

    private final String type;
    private final Naming<C> naming;
    private final List<Source<C>> sources = new ArrayList<>();

    public TemplateSet(String type, Naming<C> naming) {
        this.type = type;
        this.naming = naming;
    }

    /**
     * Declares a template.
     *
     * @param group    {@code runtime}, {@code lexer}, {@code parser} or {@code tree}
     * @param resource the file below {@code templates/<type>/<group>/}
     * @param name     the name of the file it writes, or a {@code %s} pattern filled in per call
     */
    public Source<C> declare(String group, String resource, String name) {
        var source = new Source<>(this, group + "/" + resource, name);
        this.sources.add(source);
        return source;
    }

    /**
     * The names this target writes under a name of its own, i.e. one that does not derive from the
     * grammar. A generated parser or AST node may not be called any of these, or it would silently
     * overwrite the runtime class.
     */
    public Set<String> reservedNames() {
        return this.sources.stream().map(Source<C>::name).filter(n -> !n.contains("%s"))
                .collect(Collectors.toSet());
    }

    /** Everything this target can write, in declaration order. */
    public List<Source<C>> sources() {
        return Collections.unmodifiableList(this.sources);
    }

    /**
     * One template of this set. A static class holding its set, not an inner one, so that a
     * declaration reads {@code Source<Options>} rather than {@code TemplateSet<Options>.Source}.
     */
    public static final class Source<C extends RenderContext> implements SourceProvider<C> {

        private final TemplateSet<C> set;
        private final String path;
        private final String name;

        private Source(TemplateSet<C> set, String path, String name) {
            this.set = set;
            this.path = path;
            this.name = name;
        }

        String name() {
            return this.name;
        }

        @Override
        public String getPath() {
            return this.path;
        }

        @Override
        public String getType() {
            return this.set.type;
        }

        @Override
        public File getTargetFile(String name, C context) {
            return this.set.naming.targetFile(
                    name == null ? this.name : String.format(this.name, name), context);
        }
    }
}
