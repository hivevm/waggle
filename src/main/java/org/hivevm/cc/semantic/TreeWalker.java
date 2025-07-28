// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.semantic;

import org.hivevm.cc.model.Choice;
import org.hivevm.cc.model.Expansion;
import org.hivevm.cc.model.Lookahead;
import org.hivevm.cc.model.OneOrMore;
import org.hivevm.cc.model.RChoice;
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
   * Visits the nodes of the tree rooted at "node" in pre/post-order. i.e., it executes opObj.action
   * first and then visits the children.
   */
  static void walk(Expansion node, TreeWalker opObj, boolean post) {
    if (!post)
      opObj.action(node);

    if (opObj.goDeeper(node)) {
      if (node instanceof Choice choice) {
        choice.getChoices().forEach(o -> TreeWalker.walk(o, opObj, post));
      }
      else if (node instanceof Sequence sequence) {
        sequence.getUnits().forEach(o -> TreeWalker.walk((Expansion) o, opObj, post));
      }
      else if (node instanceof OneOrMore oneOrMore) {
        TreeWalker.walk(oneOrMore.getExpansion(), opObj, post);
      }
      else if (node instanceof ZeroOrMore zeroOrMore) {
        TreeWalker.walk(zeroOrMore.getExpansion(), opObj, post);
      }
      else if (node instanceof ZeroOrOne zeroOrOne) {
        TreeWalker.walk(zeroOrOne.getExpansion(), opObj, post);
      }
      else if (node instanceof Lookahead lookahead) {
        Expansion nested_e = lookahead.getLaExpansion();
        if (!((nested_e instanceof Sequence sequence) && (sequence.getUnits().getFirst()) == node))
          TreeWalker.walk(nested_e, opObj, post);
      }
      else if (node instanceof RChoice rChoice) {
        rChoice.getChoices().forEach(o -> TreeWalker.walk(o, opObj, post));
      }
      else if (node instanceof RSequence rSequence) {
        rSequence.getUnits().forEach(o -> TreeWalker.walk(o, opObj, post));
      }
      else if (node instanceof ROneOrMore rOneOrMore) {
        TreeWalker.walk(rOneOrMore.getRegexpr(), opObj, post);
      }
      else if (node instanceof RZeroOrMore zeroOrMore) {
        TreeWalker.walk(zeroOrMore.getRegexpr(), opObj, post);
      }
      else if (node instanceof RZeroOrOne rZeroOrOne) {
        TreeWalker.walk(rZeroOrOne.getRegexpr(), opObj, post);
      }
      else if (node instanceof RRepetitionRange rRepetitionRange) {
        TreeWalker.walk(rRepetitionRange.getRegexpr(), opObj, post);
      }
    }

    if (post)
      opObj.action(node);
  }
}
