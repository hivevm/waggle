// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.parser;

/**
 * Describes expansions - entities that may occur on the right hand sides of productions. This is
 * the base class of a bunch of other more specific classes.
 */

public class Expansion {

  /**
   * The line and column number of the construct that corresponds most closely to this node.
   */
  private int   line;
  private int   column;

  /**
   * An internal name for this expansion. This is used to generate parser routines.
   */
  public String internal_name = "";


  /**
   * The parent of this expansion node. In case this is the top level expansion of the production it
   * is a reference to the production node otherwise it is a reference to another Expansion node. In
   * case this is the top level of a lookahead expansion,then the parent is null.
   */
  public Object  parent;

  /**
   * The ordinal of this node with respect to its parent.
   */
  public int     ordinal;

  /**
   * To avoid right-recursive loops when calculating follow sets, we use a generation number which
   * indicates if this expansion was visited by LookaheadWalk.genFollowSet in the same generation.
   * New generations are obtained by incrementing the static counter below, and the current
   * generation is stored in the non-static variable below.
   */
  public long    myGeneration  = 0;

  /**
   * This flag is used for bookkeeping by the minimumSize method in class ParseEngine.
   */
  public boolean inMinimumSize = false;

  /**
   * A reimplementing of Object.hashCode() to be deterministic. This uses the line and column fields
   * to generate an arbitrary number - we assume that this method is called only after line and
   * column are set to their actual values.
   */
  @Override
  public int hashCode() {
    return getLine() + getColumn();
  }

  private String getSimpleName() {
    String name = getClass().getName();
    return name.substring(name.lastIndexOf(".") + 1); // strip the package name
  }

  @Override
  public String toString() {
    return "[" + getLine() + "," + getColumn() + " " + System.identityHashCode(this) + " " + getSimpleName() + "]";
  }

  public final String getProductionName() {
    Object next = this;
    // Limit the number of iterations in case there's a cycle
    for (int i = 0; (i < 42) && (next != null); i++) {
      if (next instanceof BNFProduction) {
        return ((BNFProduction) next).getLhs();
      } else if (next instanceof Expansion) {
        next = ((Expansion) next).parent;
      } else {
        return null;
      }
    }
    return null;
  }

  /**
   * @return the line
   */
  public int getLine() {
    return this.line;
  }

  /**
   * @return the column
   */
  public int getColumn() {
    return this.column;
  }

  /**
   * Sets the position in the source.
   *
   * @param token
   * @param token
   */
  public void setLocation(Expansion expansion) {
    this.line = expansion.getLine();
    this.column = expansion.getColumn();
  }

  /**
   * Sets the position in the source.
   *
   * @param token
   * @param token
   */
  public void setLocation(Token token) {
    this.line = token.beginLine;
    this.column = token.beginColumn;
  }
}
