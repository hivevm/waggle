// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;


/**
 * A production as one target writes it: its signature, the wrappers its options ask for, and its
 * body, which its templates render (ADR-0031).
 *
 * <p>The wrappers nest — a depth guard outside a parser trace outside the body — and each adds a
 * level of indentation. The generators used to write them as a run of {@code if} blocks around one
 * call, indenting and outdenting as they went, which is the same nesting expressed by counting.
 */
public final class ProductionModel {

    private ProductionModel() {
    }

    /**
     * One production.
     *
     * @param signature  writes its declaration, comments and parameters as the grammar wrote them
     * @param scoped     whether it opens a node scope of its own: it has a node descriptor and
     *                   the target builds the tree
     * @param descriptor the node descriptor as the grammar wrote it, for the comment on the line
     *                   of the signature; empty when the production opens no scope
     * @param scopeOpen  opens that scope
     * @param scopeClose closes it
     * @param inner      the body with the wrappers its options ask for around it
     * @param voidResult whether the production yields nothing, which some targets end differently
     */
    public record Production(Signature signature, boolean scoped, String descriptor,
                             ScopeModel.ScopeOpen scopeOpen,
                             ScopeModel.ScopeClose scopeClose, Wrapped inner,
                             boolean voidResult) {
    }

    /**
     * How a production is declared, every part spelled for the target.
     *
     * @param leading    the comments in front of the production, as the grammar wrote them
     * @param returnType what it returns, as the target writes it
     * @param trailing   the comments after its first token
     * @param name       its name, as the target writes a method's name
     * @param parameters its parameter list, as the target writes it
     */
    public record Signature(String leading, String returnType, String trailing, String name,
                            String parameters) {
    }

    /**
     * What a C++ header declares for a production.
     *
     * @param returnType what it returns
     * @param name       its name
     * @param parameters its parameter list, as the grammar wrote it
     */
    public record Prototype(String returnType, String name, String parameters) {
    }

    /** The body of a production, or a wrapper around it. */
    public sealed interface Wrapped {
    }

    /** Fails the production when the parser has recursed too deep. */
    public record DepthGuarded(int limit, String name, Wrapped inner) implements Wrapped {
    }

    /** Traces the production's call and return. */
    public record Traced(String name, Wrapped inner) implements Wrapped {
    }

    /**
     * The body itself.
     *
     * @param body       what the production runs
     * @param voidResult whether the production yields nothing, which some targets end differently
     */
    public record Body(java.util.List<BodyModel.Node> body, boolean voidResult)
            implements Wrapped {
    }
}
