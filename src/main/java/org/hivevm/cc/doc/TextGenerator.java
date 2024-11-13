// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.doc;

import org.hivevm.cc.parser.Expansion;
import org.hivevm.cc.parser.NonTerminal;
import org.hivevm.cc.parser.NormalProduction;
import org.hivevm.cc.parser.RegularExpression;
import org.hivevm.cc.parser.TokenProduction;

import java.io.PrintWriter;

/**
 * Output BNF in text format.
 */
class TextGenerator implements Generator {

  protected PrintWriter  ostr;
  protected JJDocOptions opts;

  public TextGenerator(JJDocOptions opts) {
    this.opts = opts;
  }

  /**
   * {@inheritDoc}
   *
   * @see org.hivevm.cc.doc.Generator#text(java.lang.String)
   */
  @Override
  public void text(String s) {
    print(s);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.hivevm.cc.doc.Generator#print(java.lang.String)
   */
  @Override
  public void print(String s) {
    this.ostr.print(s);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.hivevm.cc.doc.Generator#documentStart()
   */
  @Override
  public void documentStart() {
    this.ostr = create_output_stream();
    this.ostr.print("\nDOCUMENT START\n");
  }

  /**
   * {@inheritDoc}
   *
   * @see org.hivevm.cc.doc.Generator#documentEnd()
   */
  @Override
  public void documentEnd() {
    this.ostr.print("\nDOCUMENT END\n");
    this.ostr.close();
  }

  /**
   * {@inheritDoc}
   *
   * @see org.hivevm.cc.doc.Generator#specialTokens(java.lang.String)
   */
  @Override
  public void specialTokens(String s) {
    this.ostr.print(s);
  }


  /**
   * {@inheritDoc}
   *
   * @see org.hivevm.cc.doc.Generator#nonterminalsStart()
   */
  @Override
  public void nonterminalsStart() {
    text("NON-TERMINALS\n");
  }

  /**
   * {@inheritDoc}
   *
   * @see org.hivevm.cc.doc.Generator#nonterminalsEnd()
   */
  @Override
  public void nonterminalsEnd() {}

  /**
   * {@inheritDoc}
   *
   * @see org.hivevm.cc.doc.Generator#tokensStart()
   */
  @Override
  public void tokensStart() {
    text("TOKENS\n");
  }

  @Override
  public void handleTokenProduction(TokenProduction tp) {
    String text = JJDoc.getStandardTokenProductionText(tp);
    text(text);
  }


  /**
   * {@inheritDoc}
   *
   * @see org.hivevm.cc.doc.Generator#tokensEnd()
   */
  @Override
  public void tokensEnd() {}

  /**
   * {@inheritDoc}
   *
   * @see org.hivevm.cc.doc.Generator#productionStart(org.hivevm.cc.parser.NormalProduction)
   */
  @Override
  public void productionStart(NormalProduction np) {
    this.ostr.print("\t" + np.getLhs() + "\t:=\t");
  }

  /**
   * {@inheritDoc}
   *
   * @see org.hivevm.cc.doc.Generator#productionEnd(org.hivevm.cc.parser.NormalProduction)
   */
  @Override
  public void productionEnd(NormalProduction np) {
    this.ostr.print("\n");
  }

  /**
   * {@inheritDoc}
   *
   * @see org.hivevm.cc.doc.Generator#expansionStart(org.hivevm.cc.parser.Expansion, boolean)
   */
  @Override
  public void expansionStart(Expansion e, boolean first) {
    if (!first) {
      this.ostr.print("\n\t\t|\t");
    }
  }

  /**
   * {@inheritDoc}
   *
   * @see org.hivevm.cc.doc.Generator#expansionEnd(org.hivevm.cc.parser.Expansion, boolean)
   */
  @Override
  public void expansionEnd(Expansion e, boolean first) {}

  /**
   * {@inheritDoc}
   *
   * @see org.hivevm.cc.doc.Generator#nonTerminalStart(org.hivevm.cc.parser.NonTerminal)
   */
  @Override
  public void nonTerminalStart(NonTerminal nt) {}

  /**
   * {@inheritDoc}
   *
   * @see org.hivevm.cc.doc.Generator#nonTerminalEnd(org.hivevm.cc.parser.NonTerminal)
   */
  @Override
  public void nonTerminalEnd(NonTerminal nt) {}

  /**
   * {@inheritDoc}
   *
   * @see org.hivevm.cc.doc.Generator#reStart(org.hivevm.cc.parser.RegularExpression)
   */
  @Override
  public void reStart(RegularExpression r) {}

  /**
   * {@inheritDoc}
   *
   * @see org.hivevm.cc.doc.Generator#reEnd(org.hivevm.cc.parser.RegularExpression)
   */
  @Override
  public void reEnd(RegularExpression r) {}

  /**
   * Create an output stream for the generated Jack code. Try to open a file based on the name of
   * the parser, but if that fails use the standard output stream.
   */
  protected PrintWriter create_output_stream() {

    if (this.opts.getOutputFile().equals("")) {
      if (JJDocGlobals.input_file.equals("standard input")) {
        return new java.io.PrintWriter(new java.io.OutputStreamWriter(System.out));
      } else {
        String ext = ".html";

        if (this.opts.getText()) {
          ext = ".txt";
        } else if (this.opts.getXText()) {
          ext = ".xtext";
        }

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
}
