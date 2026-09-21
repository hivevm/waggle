// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import org.hivevm.waggle.parser.Options;

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
public final class TemplateSet {

    /** Turns a template's name into the file it writes, which is the target's own business. */
    @FunctionalInterface
    public interface Naming {

        File targetFile(String name, Options options);
    }

    private final String type;
    private final Naming naming;
    private final List<Source> sources = new ArrayList<>();

    public TemplateSet(String type, Naming naming) {
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
    public Source declare(String group, String resource, String name) {
        var source = new Source(group + "/" + resource, name);
        this.sources.add(source);
        return source;
    }

    /**
     * The names this target writes under a name of its own, i.e. one that does not derive from the
     * grammar. A generated parser or AST node may not be called any of these, or it would silently
     * overwrite the runtime class.
     */
    public Set<String> reservedNames() {
        return this.sources.stream().map(Source::name).filter(n -> !n.contains("%s"))
                .collect(Collectors.toSet());
    }

    /** Everything this target can write, in declaration order. */
    public List<Source> sources() {
        return Collections.unmodifiableList(this.sources);
    }

    /** One template of this set. */
    public final class Source implements SourceProvider {

        private final String path;
        private final String name;

        private Source(String path, String name) {
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
            return TemplateSet.this.type;
        }

        @Override
        public File getTargetFile(String name, Options options) {
            return TemplateSet.this.naming.targetFile(
                    name == null ? this.name : String.format(this.name, name), options);
        }
    }
}
