// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.model;

/**
 * Describes expansions - entities that may occur on the right hand sides of productions. This is
 * the base class of a bunch of other more specific classes.
 */

public class Expansion extends Production {

  // An internal name for this expansion. This is used to generate parser routines.
  private String internal_name = "";

  /**
   * The parent of this expansion node. In case this is the top level expansion of the production it
   * is a reference to the production node otherwise it is a reference to another Expansion node. In
   * case this is the top level of a lookahead expansion,then the parent is null.
   */
  private Object parent;

  // The ordinal of this node with respect to its parent.
  private int ordinal;

  /**
   * To avoid right-recursive loops when calculating follow sets, we use a generation number which
   * indicates if this expansion was visited by LookaheadWalk.genFollowSet in the same generation.
   * New generations are obtained by incrementing the static counter below, and the current
   * generation is stored in the non-static variable below.
   */
  private long myGeneration = 0;

  // This flag is used for bookkeeping by the minimumSize method in class ParseEngine.
  private boolean inMinimumSize = false;

  public final Object parent() {
    return this.parent;
  }

  public final int parentOrdinal() {
    return this.ordinal;
  }

  public final void setParent(Object parent) {
    this.parent = parent;
  }

  public final void setParent(Object parent, int ordinal) {
    this.parent = parent;
    this.ordinal = ordinal;
  }

  public final long generation() {
    return this.myGeneration;
  }

  public final void setGeneration(long generation) {
    this.myGeneration = generation;
  }

  public final boolean inMinimumSize() {
    return this.inMinimumSize;
  }

  public final void setInMinimumSize(boolean inMinimumSize) {
    this.inMinimumSize = inMinimumSize;
  }

  public final String internalName() {
    return this.internal_name;
  }

  public final void setInternalName(String internal_name) {
    this.internal_name = internal_name;
  }

  /**
   * A reimplementing of Object.hashCode() to be deterministic. This uses the line and column fields
   * to generate an arbitrary number - we assume that this method is called only after line and
   * column are set to their actual values.
   */
  @Override
  public final int hashCode() {
    return getLine() + getColumn();
  }

  @Override
  public String toString() {
    var name = getClass().getName();
    name = name.substring(name.lastIndexOf(".") + 1); // strip the package name
    return "[" + getLine() + "," + getColumn() + " " + System.identityHashCode(this) + " " + name
        + "]";
  }
}
