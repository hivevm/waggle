// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen;


import java.util.List;

/**
 * What a production runs, as one target writes it (ADR-0031): the planner's nodes and choice points
 * with every name, token and code already spelled, which is what its templates render.
 *
 * <p>A choice point reaches the generator as a flat list of tests, each marked with what the chain
 * has open when it is reached, plus a count of the blocks the last one closes. The generator walked
 * that list and indented and outdented its way through it. Here it is a tree: a test holds what
 * follows when it does not hold, a switch holds what follows in its default arm, and every level
 * closes the block it opened.
 */
public final class BodyModel {

    private BodyModel() {
    }

    /** One piece of a production's body. */
    public sealed interface Node {
    }

    /**
     * Consumes a token.
     *
     * @param lhs       the assignment the grammar wrote, or nothing
     * @param token     the token, as its target names it
     * @param field     the field of the token the grammar assigns, or nothing
     * @param discarded whether nothing is assigned the token at all
     */
    public record Consume(String lhs, String token, String field,
                          boolean discarded) implements Node {
    }

    /**
     * Calls another production.
     *
     * @param lhs       the assignment the grammar wrote, or nothing
     * @param name      the production, as its target names it
     * @param arguments the arguments the grammar wrote, or nothing
     */
    public record Call(String lhs, String name, String arguments) implements Node {
    }

    /** Runs grammar code. */
    public record Code(String code) implements Node {
    }

    /** Runs its units in order. */
    public record Seq(List<Node> units) implements Node {
    }

    /** Picks an alternative. */
    public record Decide(Chain chain) implements Node {
    }

    /**
     * A loop.
     *
     * @param label the number of its label
     * @param first the body, when it runs before the first test
     * @param chain whether it goes round again
     * @param then  the body, when it runs after that test
     */
    public record Repeat(int label, List<Node> first, Chain chain,
                         List<Node> then) implements Node {
    }

    /**
     * Builds a tree node around its body.
     *
     * @param built whether a node is built at all: without tree support the scope wraps nothing
     */
    public record Scoped(boolean built, ScopeModel.ScopeOpen open, List<Node> body,
                         ScopeModel.ScopeClose close) implements Node {
    }

    /** Nothing at all: the alternative of a loop's test that stays in the loop. */
    public record Nothing() implements Node {
    }

    /**
     * What a loop's test picks.
     *
     * @param label  the number of the loop's label
     * @param leaves whether it leaves the loop rather than going round again
     */
    public record Break(int label, boolean leaves) implements Node {
    }

    /** Reports that a choice matched none of its alternatives. */
    public record NoAlternative() implements Node {
    }

    /** One link of the chain a choice point compiles to. */
    public sealed interface Chain {
    }

    /** What a test checks, from its opening parenthesis to the brace that opens its block. */
    public sealed interface Test {
    }

    /** Grammar code that decides. */
    public record SemanticTest(String code) implements Test {
    }

    /**
     * A lookahead routine that decides, and the grammar code that has to hold as well.
     *
     * @param routine  the routine's name, without the {@code jj_2} its target prefixes
     * @param amount   how deep it scans
     * @param semantic the grammar code, or nothing
     */
    public record LookaheadTest(String routine, String amount, String semantic) implements Test {
    }

    /**
     * The test the chain opens with.
     *
     * @param leadingBlank whether it starts on a line of its own, as a syntactic lookahead does
     * @param condition    what it checks
     * @param action       the alternative it picks
     * @param rest         what is tried when it does not hold
     */
    public record If(boolean leadingBlank, Test condition, Node action,
                     Chain rest) implements Chain {
    }

    /** A further test, reached when the one before it did not hold. */
    public record ElseIf(Test condition, Node action, Chain rest) implements Chain {
    }

    /**
     * A test in the default arm of a switch.
     *
     * @param recorded whether the choice is noted for the error message
     * @param slot     where it is noted
     */
    public record DefaultIf(boolean recorded, int slot, Test condition, Node action,
                            Chain rest) implements Chain {
    }

    /**
     * A switch on the next token, where the chain opens.
     *
     * @param cached whether the next token is cached rather than looked up
     * @param arms   one per alternative a single token decides
     * @param rest   what its default arm holds
     */
    public record Switch(boolean cached, List<Arm> arms, Chain rest) implements Chain {
    }

    /** The same, opened after a test that did not hold. */
    public record ElseSwitch(boolean cached, List<Arm> arms, Chain rest) implements Chain {
    }

    /**
     * One arm of a switch.
     *
     * @param labelled whether it has a label at all: a choice conflict can leave it none
     * @param cases    its labels
     */
    public record Arm(boolean labelled, List<CaseLabel> cases, Node action) {
    }

    /** One label of an arm; {@code first} tells it from those that follow it. */
    public record CaseLabel(boolean first, String token) {
    }

    /** The alternative taken when the chain never opened a block. */
    public record Plain(Node action) implements Chain {
    }

    /** The alternative taken when no test of an open chain held. */
    public record Else(Node action) implements Chain {
    }

    /** The same, in the default arm of a switch. */
    public record DefaultElse(boolean recorded, int slot, Node action) implements Chain {
    }
}
