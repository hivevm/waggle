// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.analysis;

import org.hivevm.waggle.model.CodeText;
import org.hivevm.waggle.model.NodeScope;

import java.util.List;

/**
 * One piece of a production's body as the parser runs it, with every decision taken: the choice
 * points carry their {@link Decision}, the loops their label, and every piece of grammar code the
 * node scope its {@code $NODE} and {@code $BOOL} refer to. The generator used to walk the
 * expansions, look the decisions up, hand out loop labels from a counter as it printed and pass the
 * enclosing scope down (ADR-0029).
 *
 * <p>What a node keeps of the grammar is its verbatim code, as a {@link CodeText} the generator
 * lays out as the grammar wrote it -- not the expansion it was written in.
 */
public sealed interface PlanNode {

    /**
     * Consumes a token.
     *
     * @param lhs      what the grammar assigns the token to, empty when it assigns nothing
     * @param rhsField the field the grammar takes from the token ({@code t = <ID>.image}), or null
     * @param token    the token as the parser names it
     * @param scope    the node scope its code refers to, or {@code null}
     */
    record Consume(CodeText lhs, String rhsField, TokenRef token, NodeScope scope)
            implements PlanNode {
    }

    /**
     * Calls another production.
     *
     * @param name      the production it calls
     * @param lhs       what the grammar assigns the result to, empty when it assigns nothing
     * @param arguments the arguments it passes, empty when it passes none
     * @param scope     the node scope its code refers to, or {@code null}
     */
    record Call(String name, CodeText lhs, CodeText arguments, NodeScope scope)
            implements PlanNode {
    }

    /**
     * Runs grammar code.
     *
     * @param code  the code
     * @param scope the node scope it refers to, or {@code null}
     */
    record Code(CodeText code, NodeScope scope) implements PlanNode {
    }

    /** Runs its units in order. */
    record Seq(List<PlanNode> units) implements PlanNode {
    }

    /**
     * Picks an alternative: a choice, or an option {@code […]}.
     *
     * @param decision     how it picks
     * @param alternatives the alternatives the decision can reach, in order; alternative {@code i}
     *                     runs for step {@code i}, and the default runs when no step matched
     * @param mustMatch    whether reaching no alternative is an error (a choice) or nothing (an
     *                     option)
     * @param scope        the node scope the lookahead code refers to, or {@code null}
     */
    record Decide(Decision decision, List<PlanNode> alternatives, boolean mustMatch,
                  NodeScope scope) implements PlanNode {
    }

    /**
     * A loop: {@code (…)+} or {@code (…)*}. The decision's first alternative stays in the loop,
     * its second leaves it.
     *
     * @param label       the loop's label, numbered in the order the loops are written
     * @param atLeastOnce whether the body runs before the first decision
     * @param body        what the loop repeats
     * @param decision    whether it goes round again
     * @param scope       the node scope the lookahead code refers to, or {@code null}
     */
    record Repeat(int label, boolean atLeastOnce, PlanNode body, Decision decision,
                  NodeScope scope) implements PlanNode {
    }

    /** Builds a tree node around its body. */
    record Scoped(NodeScope scope, PlanNode body) implements PlanNode {
    }
}
