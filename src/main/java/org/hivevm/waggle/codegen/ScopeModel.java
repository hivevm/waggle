// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;

/**
 * The code around one node scope as a target's templates write it (ADR-0031): the opening, which
 * declares the node and its flag and starts the guarded region, and the closing, which unwinds the
 * scope on failure and closes it on the way out. Every value is already spelled for the target.
 */
public final class ScopeModel {

    private ScopeModel() {
    }

    /**
     * Opens a node scope.
     *
     * @param production  whether it is a production's own scope, whose comment the production
     *                    writes on the line of its signature, rather than a nested expansion's,
     *                    whose comment is a line of its own
     * @param descriptor  the node descriptor as the grammar wrote it, for the comment
     * @param nodeClass   the class of the node
     * @param nodeVar     the node local
     * @param closedVar   the "scope is still open" local
     * @param nodeId      the node's constant
     * @param factory     the class whose jjtCreate makes the node, or empty when it is constructed
     * @param scopeHook   whether the parser's own hook is called as the scope opens and closes
     * @param trackTokens whether the node records its first and last token
     */
    public record ScopeOpen(boolean production, String descriptor, String nodeClass,
                            String nodeVar, String closedVar, String nodeId, String factory,
                            boolean scopeHook, boolean trackTokens) {
    }

    /**
     * Closes a node scope.
     *
     * @param nodeVar     the node local
     * @param closedVar   the "scope is still open" local
     * @param close       how the scope closes under its arity
     * @param scopeHook   whether the parser's own hook is called as the scope closes
     * @param trackTokens whether the node records its last token
     */
    public record ScopeClose(String nodeVar, String closedVar, Close close,
                             boolean scopeHook, boolean trackTokens) {
    }

    /**
     * How a scope closes under its arity; the call reads the node local from its
     * {@link ScopeClose}. {@code text} is the grammar's expression as written, {@code trimmed} the
     * same without the blanks around it.
     */
    public sealed interface Close {
    }

    /** The node is always built. */
    public record CloseAlways() implements Close {
    }

    /** The node is built when more than {@code text} children are on the stack. */
    public record CloseAbove(String text, String trimmed) implements Close {
    }

    /** The node takes {@code text} children. */
    public record CloseCount(String text, String trimmed) implements Close {
    }

    /** The node is built when the condition {@code text} holds. */
    public record CloseIf(String text) implements Close {
    }
}
