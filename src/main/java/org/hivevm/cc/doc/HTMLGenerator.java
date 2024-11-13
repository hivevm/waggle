// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.doc;

import org.hivevm.cc.parser.Expansion;
import org.hivevm.cc.parser.NonTerminal;
import org.hivevm.cc.parser.NormalProduction;
import org.hivevm.cc.parser.RegularExpression;
import org.hivevm.cc.parser.TokenProduction;

import java.util.Hashtable;

/**
 * Output BNF in HTML 3.2 format.
 */
class HTMLGenerator extends TextGenerator {

  private final Hashtable<String, String> id_map = new Hashtable<>();
  private int                             id     = 1;

  public HTMLGenerator(JJDocOptions opts) {
    super(opts);
  }

  private String get_id(String nt) {
    String i = this.id_map.get(nt);
    if (i == null) {
      i = "prod" + this.id++;
      this.id_map.put(nt, i);
    }
    return i;
  }

  private void println(String s) {
    print(s + "\n");
  }

  @Override
  public void text(String s) {
    String ss = "";
    for (int i = 0; i < s.length(); ++i) {
      if (s.charAt(i) == '<') {
        ss += "&lt;";
      } else if (s.charAt(i) == '>') {
        ss += "&gt;";
      } else if (s.charAt(i) == '&') {
        ss += "&amp;";
      } else {
        ss += s.charAt(i);
      }
    }
    print(ss);
  }

  @Override
  public void print(String s) {
    this.ostr.print(s);
  }

  @Override
  public void documentStart() {
    this.ostr = create_output_stream();
    println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2//EN\">");
    println("<HTML>");
    println("<HEAD>");
    if (!"".equals(this.opts.getCSS())) {
      println("<LINK REL=\"stylesheet\" type=\"text/css\" href=\"" + this.opts.getCSS() + "\"/>");
    }
    if (JJDocGlobals.input_file != null) {
      println("<TITLE>BNF for " + JJDocGlobals.input_file + "</TITLE>");
    } else {
      println("<TITLE>A BNF grammar by JJDoc</TITLE>");
    }
    println("</HEAD>");
    println("<BODY>");
    println("<H1 ALIGN=CENTER>BNF for " + JJDocGlobals.input_file + "</H1>");
  }

  @Override
  public void documentEnd() {
    println("</BODY>");
    println("</HTML>");
    this.ostr.close();
  }

  /**
   * Prints out comments, used for tokens and non-terminals. {@inheritDoc}
   *
   * @see org.hivevm.cc.doc.TextGenerator#specialTokens(java.lang.String)
   */
  @Override
  public void specialTokens(String s) {
    println(" <!-- Special token -->");
    println(" <TR>");
    println("  <TD>");
    println("<PRE>");
    print(s);
    println("</PRE>");
    println("  </TD>");
    println(" </TR>");
  }


  @Override
  public void handleTokenProduction(TokenProduction tp) {
    println(" <!-- Token -->");
    println(" <TR>");
    println("  <TD>");
    println("   <PRE>");
    String text = JJDoc.getStandardTokenProductionText(tp);
    text(text);
    println("   </PRE>");
    println("  </TD>");
    println(" </TR>");
  }


  @Override
  public void nonterminalsStart() {
    println("<H2 ALIGN=CENTER>NON-TERMINALS</H2>");
    if (this.opts.getOneTable()) {
      println("<TABLE>");
    }
  }

  @Override
  public void nonterminalsEnd() {
    if (this.opts.getOneTable()) {
      println("</TABLE>");
    }
  }

  @Override
  public void tokensStart() {
    println("<H2 ALIGN=CENTER>TOKENS</H2>");
    println("<TABLE>");
  }

  @Override
  public void tokensEnd() {
    println("</TABLE>");
  }

  @Override
  public void productionStart(NormalProduction np) {
    if (!this.opts.getOneTable()) {
      println("");
      println("<TABLE ALIGN=CENTER>");
      println("<CAPTION><STRONG>" + np.getLhs() + "</STRONG></CAPTION>");
    }
    println("<TR>");
    println("<TD ALIGN=RIGHT VALIGN=BASELINE><A NAME=\"" + get_id(np.getLhs()) + "\">" + np.getLhs() + "</A></TD>");
    println("<TD ALIGN=CENTER VALIGN=BASELINE>::=</TD>");
    print("<TD ALIGN=LEFT VALIGN=BASELINE>");
  }

  @Override
  public void productionEnd(NormalProduction np) {
    if (!this.opts.getOneTable()) {
      println("</TABLE>");
      println("<HR>");
    }
  }

  @Override
  public void expansionStart(Expansion e, boolean first) {
    if (!first) {
      println("<TR>");
      println("<TD ALIGN=RIGHT VALIGN=BASELINE></TD>");
      println("<TD ALIGN=CENTER VALIGN=BASELINE>|</TD>");
      print("<TD ALIGN=LEFT VALIGN=BASELINE>");
    }
  }

  @Override
  public void expansionEnd(Expansion e, boolean first) {
    println("</TD>");
    println("</TR>");
  }

  @Override
  public void nonTerminalStart(NonTerminal nt) {
    print("<A HREF=\"#" + get_id(nt.getName()) + "\">");
  }

  @Override
  public void nonTerminalEnd(NonTerminal nt) {
    print("</A>");
  }

  @Override
  public void reStart(RegularExpression r) {}

  @Override
  public void reEnd(RegularExpression r) {}
}
