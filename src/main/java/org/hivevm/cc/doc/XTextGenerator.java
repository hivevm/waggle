// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.doc;

import org.hivevm.cc.model.Expansion;
import org.hivevm.cc.model.NonTerminal;
import org.hivevm.cc.model.NormalProduction;
import org.hivevm.cc.model.TokenProduction;
import org.hivevm.cc.parser.RegExprSpec;

/**
 * Output BNF in HTML 3.2 format.
 */
class XTextGenerator extends TextGenerator {

  public XTextGenerator(JJDocOptions opts) {
    super(opts);
  }

  @Override
  public void handleTokenProduction(TokenProduction tp) {

    StringBuilder sb = new StringBuilder();

    for (RegExprSpec res : tp.getRespecs()) {
      String regularExpressionText = JJDoc.emitRE(res.rexp);
      sb.append(regularExpressionText);

      if (res.nsTok != null) {
        sb.append(" : ").append(res.nsTok.image);
      }

      sb.append("\n");
    }

    // text(sb.toString());
  }

  private void println(String s) {
    print(s + "\n");
  }


  @Override
  public void documentStart() {
    this.ostr = create_output_stream();
    println("grammar " + JJDocGlobals.input_file + " with org.eclipse.xtext.common.Terminals");
    println("import \"http://www.eclipse.org/emf/2002/Ecore\" as ecore");
    println("");
  }

  @Override
  public void documentEnd() {
    this.ostr.close();
  }

  /**
   * Prints out comments, used for tokens and non-terminals. {@inheritDoc}
   *
   * @see org.hivevm.cc.doc.TextGenerator#specialTokens(java.lang.String)
   */
  @Override
  public void specialTokens(String s) {
    print(s);
  }


  @Override
  public void nonterminalsStart() {
  }

  @Override
  public void tokensStart() {
  }

  @Override
  public void productionStart(NormalProduction np) {
  }

  @Override
  public void productionEnd(NormalProduction np) {
  }

  @Override
  public void expansionStart(Expansion e, boolean first) {
  }

  @Override
  public void expansionEnd(Expansion e, boolean first) {
    println(";");
  }

  @Override
  public void nonTerminalStart(NonTerminal nt) {
    print("terminal ");
  }

  @Override
  public void nonTerminalEnd(NonTerminal nt) {
    print(";");
  }

}
