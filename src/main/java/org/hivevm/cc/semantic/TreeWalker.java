// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.semantic;

import org.hivevm.cc.model.Action;
import org.hivevm.cc.model.Choice;
import org.hivevm.cc.model.Expansion;
import org.hivevm.cc.model.Lookahead;
import org.hivevm.cc.model.NonTerminal;
import org.hivevm.cc.model.NormalProduction;
import org.hivevm.cc.model.OneOrMore;
import org.hivevm.cc.model.RCharacterList;
import org.hivevm.cc.model.RChoice;
import org.hivevm.cc.model.REndOfFile;
import org.hivevm.cc.model.RJustName;
import org.hivevm.cc.model.RStringLiteral;
import org.hivevm.cc.model.ROneOrMore;
import org.hivevm.cc.model.RRepetitionRange;
import org.hivevm.cc.model.RSequence;
import org.hivevm.cc.model.RZeroOrMore;
import org.hivevm.cc.model.RZeroOrOne;
import org.hivevm.cc.model.Sequence;
import org.hivevm.cc.model.ZeroOrMore;
import org.hivevm.cc.model.ZeroOrOne;

/**
 * Objects of this type are passed to the tree walker routines in ExpansionTreeWalker.
 */
interface TreeWalker {

    /**
     * When called at a particular node, this specifies to the tree walker if it should visit more
     * nodes under this node.
     */
    boolean goDeeper(Expansion e);

    /**
     * When a node is visited, this method is invoked with the node as parameter.
     */
    void action(Expansion e);

    /**
     * Visits the nodes of the tree rooted at "node" in pre/post-order. i.e., it executes
     * opObj.action first and then visits the children.
     */
    static void walk(Expansion node, TreeWalker walker, boolean post) {
        if (!post)
            walker.action(node);

        if (walker.goDeeper(node)) {
            switch (node) {
                case Choice choice ->
                        choice.getChoices().forEach(o -> TreeWalker.walk(o, walker, post));
                case Sequence sequence ->
                        sequence.getUnits().forEach(o -> TreeWalker.walk(o, walker, post));
                case OneOrMore oneOrMore -> TreeWalker.walk(oneOrMore.getExpansion(), walker, post);
                case ZeroOrMore zeroOrMore ->
                        TreeWalker.walk(zeroOrMore.getExpansion(), walker, post);
                case ZeroOrOne zeroOrOne -> TreeWalker.walk(zeroOrOne.getExpansion(), walker, post);

                case Lookahead lookahead -> {
                    // Skip the lookahead's own expansion when it is the sequence this node opens,
                    // which would walk in a circle.
                    Expansion nested = lookahead.getLaExpansion();
                    if (!((nested instanceof Sequence sequence)
                            && (sequence.getUnits().getFirst() == node))) {
                        TreeWalker.walk(nested, walker, post);
                    }
                }

                case RChoice choice ->
                        choice.getChoices().forEach(o -> TreeWalker.walk(o, walker, post));
                case RSequence sequence ->
                        sequence.getUnits().forEach(o -> TreeWalker.walk(o, walker, post));
                case ROneOrMore oneOrMore -> TreeWalker.walk(oneOrMore.getRegexpr(), walker, post);
                case RZeroOrMore zeroOrMore ->
                        TreeWalker.walk(zeroOrMore.getRegexpr(), walker, post);
                case RZeroOrOne zeroOrOne -> TreeWalker.walk(zeroOrOne.getRegexpr(), walker, post);
                case RRepetitionRange range -> TreeWalker.walk(range.getRegexpr(), walker, post);

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
            walker.action(node);
    }
}
