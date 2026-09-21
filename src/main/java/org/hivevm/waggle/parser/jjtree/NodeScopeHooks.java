// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.parser.jjtree;

/**
 * The contract a {@code NODE_SCOPE_HOOK} parser has to satisfy.
 *
 * <p>With that option on, the generated parser calls {@code jjtreeOpenNodeScope(node)} right after
 * it opens a node scope and {@code jjtreeCloseNodeScope(node)} right after it closes one — by name,
 * on itself. The call sites come from the back end's {@code TreeEmitter}; what they expect to find
 * is written down here, so that a grammar author reading {@code BASE_PARSER} sees the two methods
 * they may override, and so that renaming either one breaks compilation instead of the generated
 * parser.
 *
 * <p>This states what the generated code already calls; nothing new is emitted and the hooks keep
 * their JavaCC names. It is a class rather than an interface because the generated parser declares
 * both hooks {@code protected}, and raising that to {@code public} would be a change to emitted
 * code — which only takes effect one release later (ADR-0009, ADR-0013).
 */
abstract class NodeScopeHooks {

    /** Called immediately after the parser opened the scope of {@code node}. */
    protected void jjtreeOpenNodeScope(Node node) throws ParseException {
    }

    /** Called immediately after the parser closed the scope of {@code node}. */
    protected void jjtreeCloseNodeScope(Node node) throws ParseException {
    }
}
