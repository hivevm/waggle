// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.doc;

import java.io.PrintWriter;

import org.hivevm.cc.parser.Expansion;
import org.hivevm.cc.parser.NonTerminal;
import org.hivevm.cc.parser.NormalProduction;
import org.hivevm.cc.parser.RCharacterList;
import org.hivevm.cc.parser.RJustName;
import org.hivevm.cc.parser.RegularExpression;
import org.hivevm.cc.parser.TokenProduction;

class BNFGenerator implements Generator {

  private PrintWriter        ostr;
  private final JJDocOptions opts;
  private boolean            printing = true;

  /**
   * Constructs an instance of {@link BNFGenerator}.
   *
   * @param ostr
   * @param opts
   * @param printing
   */
  public BNFGenerator(JJDocOptions opts) {
    this.opts = opts;
  }

  private PrintWriter create_output_stream() {

    if (this.opts.getOutputFile().equals("")) {
      if (JJDocGlobals.input_file.equals("standard input")) {
        return new java.io.PrintWriter(new java.io.OutputStreamWriter(System.out));
      } else {
        String ext = ".bnf";
        int i = JJDocGlobals.input_file.lastIndexOf('.');
        if (i == -1) {
          JJDocGlobals.output_file = JJDocGlobals.input_file + ext;
        } else {
          String suffix = JJDocGlobals.input_file.substring(i);
          if (suffix.equals(ext)) {
            JJDocGlobals.output_file = JJDocGlobals.input_file + ext;
          } else {
            JJDocGlobals.output_file = JJDocGlobals.input_file.substring(0, i) + ext;
          }
        }
      }
    } else {
      JJDocGlobals.output_file = this.opts.getOutputFile();
    }
    try {
      this.ostr = new java.io.PrintWriter(new java.io.FileWriter(JJDocGlobals.output_file));
    } catch (java.io.IOException e) {
      JJDocGlobals
          .error("JJDoc: can't open output stream on file " + JJDocGlobals.output_file + ".  Using standard output.");
      this.ostr = new java.io.PrintWriter(new java.io.OutputStreamWriter(System.out));
    }

    return this.ostr;
  }

  private void println(String s) {
    print(s + "\n");
  }

  @Override
  public void text(String s) {
    if (this.printing && !((s.length() == 1) && ((s.charAt(0) == '\n') || (s.charAt(0) == '\r')))) {
      print(s);
    }
  }

  @Override
  public void print(String s) {
    this.ostr.print(s);
  }

  @Override
  public void documentStart() {
    this.ostr = create_output_stream();
  }

  @Override
  public void documentEnd() {
    this.ostr.close();
  }

  @Override
  public void specialTokens(String s) {}


  @Override
  public void nonterminalsStart() {}

  @Override
  public void nonterminalsEnd() {}

  @Override
  public void tokensStart() {}

  @Override
  public void tokensEnd() {}

  @Override
  public void expansionEnd(Expansion e, boolean first) {}

  @Override
  public void nonTerminalStart(NonTerminal nt) {}

  @Override
  public void nonTerminalEnd(NonTerminal nt) {}

  @Override
  public void productionStart(NormalProduction np) {
    println("");
    print(np.getLhs() + " ::= ");
  }

  @Override
  public void productionEnd(NormalProduction np) {
    println("");
  }

  @Override
  public void expansionStart(Expansion e, boolean first) {
    if (!first) {
      print(" | ");
    }
  }

  @Override
  public void reStart(RegularExpression r) {
    if (r.getClass().equals(RJustName.class) || r.getClass().equals(RCharacterList.class)) {
      this.printing = false;
    }
  }

  @Override
  public void reEnd(RegularExpression r) {
    this.printing = true;
  }

  @Override
  public void handleTokenProduction(TokenProduction tp) {
    this.printing = false;
    String text = JJDoc.getStandardTokenProductionText(tp);
    text(text);
    this.printing = true;
  }
}
