// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.rust;

import org.hivevm.waggle.api.GenerationException;

import java.util.Set;

/**
 * A name from the grammar as a Rust identifier. A grammar named {@code Match} or a token named
 * {@code fn} wrote {@code mod match;} and {@code pub const fn: u32}, which do not compile; a keyword
 * is written as a raw identifier instead ({@code r#match}).
 */
final class RustIdentifier {

    /** The keywords of Rust 2024, strict and reserved. */
    private static final Set<String> KEYWORDS = Set.of(
            "as", "async", "await", "break", "const", "continue", "dyn", "else", "enum", "extern",
            "false", "fn", "for", "gen", "if", "impl", "in", "let", "loop", "match", "mod", "move",
            "mut", "pub", "ref", "return", "static", "struct", "trait", "true", "type", "unsafe",
            "use", "where", "while", "abstract", "become", "box", "do", "final", "macro", "override",
            "priv", "try", "typeof", "unsized", "virtual", "yield");

    /** The keywords that cannot be raw identifiers either. */
    private static final Set<String> NOT_RAW = Set.of("crate", "self", "super", "Self", "_");

    private RustIdentifier() {
    }

    static String of(String name) {
        if (RustIdentifier.NOT_RAW.contains(name)) {
            throw new GenerationException(
                    "'" + name + "' cannot be a name in the Rust target: it is a Rust keyword.");
        }
        return RustIdentifier.KEYWORDS.contains(name) ? "r#" + name : name;
    }
}
