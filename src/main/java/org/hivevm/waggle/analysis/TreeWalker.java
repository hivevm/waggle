// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/TreeWalkerOp.java, org/javacc/parser/ExpansionTreeWalker.java

package org.hivevm.waggle.analysis;

import org.hivevm.waggle.model.Action;
import org.hivevm.waggle.model.Choice;
import org.hivevm.waggle.model.Expansion;
import org.hivevm.waggle.model.Lookahead;
import org.hivevm.waggle.model.NonTerminal;
import org.hivevm.waggle.model.NormalProduction;
import org.hivevm.waggle.model.OneOrMore;
import org.hivevm.waggle.model.RCharacterList;
import org.hivevm.waggle.model.RChoice;
import org.hivevm.waggle.model.REndOfFile;
import org.hivevm.waggle.model.RJustName;
import org.hivevm.waggle.model.RStringLiteral;
import org.hivevm.waggle.model.ROneOrMore;
import org.hivevm.waggle.model.RRepetitionRange;
import org.hivevm.waggle.model.RSequence;
import org.hivevm.waggle.model.RZeroOrMore;
import org.hivevm.waggle.model.RZeroOrOne;
import org.hivevm.waggle.model.Sequence;
import org.hivevm.waggle.model.ZeroOrMore;
import org.hivevm.waggle.model.ZeroOrOne;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Walks an expansion tree, calling an action on every node it visits.
 */
final class TreeWalker {

    private TreeWalker() {
    }

    /**
     * Visits the nodes of the tree rooted at "node" in pre- or post-order: {@code action} runs on a
     * node before its children, or with {@code post} after them. The children of a node are
     * visited only when {@code goDeeper} holds for it.
     */
    static void walk(Expansion node, Predicate<Expansion> goDeeper, Consumer<Expansion> action,
            boolean post) {
        if (!post)
            action.accept(node);

        if (goDeeper.test(node)) {
            switch (node) {
                case Choice choice ->
                        choice.getChoices().forEach(o -> TreeWalker.walk(o, goDeeper, action, post));
                case Sequence sequence ->
                        sequence.getUnits().forEach(o -> TreeWalker.walk(o, goDeeper, action, post));
                case OneOrMore oneOrMore ->
                        TreeWalker.walk(oneOrMore.getExpansion(), goDeeper, action, post);
                case ZeroOrMore zeroOrMore ->
                        TreeWalker.walk(zeroOrMore.getExpansion(), goDeeper, action, post);
                case ZeroOrOne zeroOrOne ->
                        TreeWalker.walk(zeroOrOne.getExpansion(), goDeeper, action, post);

                case Lookahead lookahead -> {
                    // Skip the lookahead's own expansion when it is the sequence this node opens,
                    // which would walk in a circle.
                    Expansion nested = lookahead.getLaExpansion();
                    if (!((nested instanceof Sequence sequence)
                            && (sequence.getUnits().getFirst() == node))) {
                        TreeWalker.walk(nested, goDeeper, action, post);
                    }
                }

                case RChoice choice ->
                        choice.getChoices().forEach(o -> TreeWalker.walk(o, goDeeper, action, post));
                case RSequence sequence ->
                        sequence.getUnits().forEach(o -> TreeWalker.walk(o, goDeeper, action, post));
                case ROneOrMore oneOrMore ->
                        TreeWalker.walk(oneOrMore.getRegexpr(), goDeeper, action, post);
                case RZeroOrMore zeroOrMore ->
                        TreeWalker.walk(zeroOrMore.getRegexpr(), goDeeper, action, post);
                case RZeroOrOne zeroOrOne ->
                        TreeWalker.walk(zeroOrOne.getRegexpr(), goDeeper, action, post);
                case RRepetitionRange range ->
                        TreeWalker.walk(range.getRegexpr(), goDeeper, action, post);

                // Leaves: nothing to descend into.
                case Action a -> { }
                case NonTerminal nt -> { }
                case NormalProduction p -> { }
                case RCharacterList c -> { }
                case REndOfFile e -> { }
                case RJustName n -> { }
                case RStringLiteral s -> { }
            }
        }

        if (post)
            action.accept(node);
    }
}
