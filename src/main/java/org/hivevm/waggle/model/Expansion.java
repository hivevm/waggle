// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/Expansion.java

package org.hivevm.waggle.model;

/**
 * Describes expansions - entities that may occur on the right hand sides of productions. This is
 * the base class of a bunch of other more specific classes.
 *
 * <p>It is the grammar, and nothing a walk over it left behind. The visit marker of the follow-set
 * computation, the re-entry guard of {@code minimumSize} and the name the parser generator invents
 * for a lookahead routine used to live here; they belong to the stage that writes them (ADR-0019).
 * {@code hashCode} is identity again — it was {@code line + column}, which collided for every two
 * expansions on a diagonal and quietly required positions to be set before use.
 */

public abstract sealed class Expansion extends Production
        permits Action, Choice, Lookahead, NonTerminal, OneOrMore, Sequence, ZeroOrMore, ZeroOrOne,
        NormalProduction, RegularExpression {

    /**
     * The parent of this expansion node. In case this is the top level expansion of the production
     * it is a reference to the production node otherwise it is a reference to another Expansion
     * node. In case this is the top level of a lookahead expansion,then the parent is null.
     */
    private Expansion parent;

    // The ordinal of this node with respect to its parent.
    private int ordinal;

    private NodeScope node_scope;

    public final Expansion parent() {
        return this.parent;
    }

    public final int parentOrdinal() {
        return this.ordinal;
    }

    public final void setParent(Expansion parent) {
        this.parent = parent;
    }

    public final void setParent(Expansion parent, int ordinal) {
        setParent(parent);
        this.ordinal = ordinal;
    }

    public void setNodeScope(NodeScope node_scope) {
        this.node_scope = node_scope;
    }

    public NodeScope getNodeScope() {
        return this.node_scope;
    }

    @Override
    public String toString() {
        var name = getClass().getName();
        name = name.substring(name.lastIndexOf(".") + 1); // strip the package name
        return "[" + getLine() + "," + getColumn() + " " + System.identityHashCode(this) + " "
                + name
                + "]";
    }
}
