// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.doc;

import java.util.Iterator;
import org.hivevm.cc.model.Action;
import org.hivevm.cc.model.BNFProduction;
import org.hivevm.cc.model.CharacterRange;
import org.hivevm.cc.model.Choice;
import org.hivevm.cc.model.Expansion;
import org.hivevm.cc.model.Lookahead;
import org.hivevm.cc.model.NonTerminal;
import org.hivevm.cc.model.NormalProduction;
import org.hivevm.cc.model.OneOrMore;
import org.hivevm.cc.model.RCharacterList;
import org.hivevm.cc.model.RChoice;
import org.hivevm.cc.model.REndOfFile;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.RJustName;
import org.hivevm.cc.model.ROneOrMore;
import org.hivevm.cc.model.RRepetitionRange;
import org.hivevm.cc.model.RSequence;
import org.hivevm.cc.model.RStringLiteral;
import org.hivevm.cc.model.RZeroOrMore;
import org.hivevm.cc.model.RZeroOrOne;
import org.hivevm.cc.model.RegularExpression;
import org.hivevm.cc.model.Sequence;
import org.hivevm.cc.model.SingleCharacter;
import org.hivevm.cc.model.TokenProduction;
import org.hivevm.cc.model.ZeroOrMore;
import org.hivevm.cc.model.ZeroOrOne;
import org.hivevm.cc.parser.JavaCCData;
import org.hivevm.cc.parser.RegExprSpec;
import org.hivevm.cc.parser.Token;
import org.hivevm.cc.utils.Encoding;

/**
 * The main entry point for JJDoc.
 */
class JJDoc extends JJDocGlobals {

  static void start(JavaCCData javacc) {
    JJDocGlobals.generator = JJDocGlobals.getGenerator((JJDocOptions) javacc.options());
    JJDocGlobals.generator.documentStart();
    JJDoc.emitTokenProductions(JJDocGlobals.generator, javacc.getTokenProductions());
    JJDoc.emitNormalProductions(JJDocGlobals.generator, javacc.getNormalProductions());
    JJDocGlobals.generator.documentEnd();
  }

  private static Token getPrecedingSpecialToken(Token tok) {
    Token t = tok;
    while (t.specialToken != null) {
      t = t.specialToken;
    }
    return (t != tok) ? t : null;
  }

  private static void emitTopLevelSpecialTokens(Token tok) {
    if (tok == null)
      // Strange ...
      return;
    JJDoc.getPrecedingSpecialToken(tok);
  }

  /*
   * private static boolean toplevelExpansion(Expansion exp) { return exp.parent != null && (
   * (exp.parent instanceof NormalProduction) || (exp.parent instanceof TokenProduction) ); }
   */

  private static void emitTokenProductions(Generator gen, Iterable<TokenProduction> prods) {
    gen.tokensStart();
    // FIXME there are many empty productions here
    for (TokenProduction tp : prods) {
      JJDoc.emitTopLevelSpecialTokens(tp.getFirstToken());

      gen.handleTokenProduction(tp);

    }
    gen.tokensEnd();
  }

  static String getStandardTokenProductionText(TokenProduction tp) {
    String token = "";
    if (tp.isExplicit()) {
      if (tp.getLexStates() == null) {
        token += "<*> ";
      }
      else {
        token += "<";
        for (int i = 0; i < tp.getLexStates().length; ++i) {
          token += tp.getLexStates()[i];
          if (i < (tp.getLexStates().length - 1)) {
            token += ",";
          }
        }
        token += "> ";
      }
      token += tp.getKind().name();
      if (tp.isIgnoreCase()) {
        token += " [IGNORE_CASE]";
      }
      token += " : {\n";
      for (Iterator<RegExprSpec> it2 = tp.getRespecs().iterator(); it2.hasNext(); ) {
        RegExprSpec res = it2.next();

        token += JJDoc.emitRE(res.rexp);

        if (res.nsTok != null) {
          token += " : " + res.nsTok.image;
        }

        token += "\n";
        if (it2.hasNext()) {
          token += "| ";
        }
      }
      token += "}\n\n";
    }
    return token;
  }

  private static void emitNormalProductions(Generator gen, Iterable<NormalProduction> prods) {
    gen.nonterminalsStart();
    for (NormalProduction np : prods) {
      JJDoc.emitTopLevelSpecialTokens(np.getFirstToken());
      if (np instanceof BNFProduction) {
        gen.productionStart(np);
        if (np.getExpansion() instanceof Choice c) {
          boolean first = true;
          for (Expansion element : c.getChoices()) {
            gen.expansionStart(element, first);
            JJDoc.emitExpansionTree(element, gen);
            gen.expansionEnd(element, first);
            first = false;
          }
        }
        else {
          gen.expansionStart(np.getExpansion(), true);
          JJDoc.emitExpansionTree(np.getExpansion(), gen);
          gen.expansionEnd(np.getExpansion(), true);
        }
        gen.productionEnd(np);
      }
    }
    gen.nonterminalsEnd();
  }

  private static void emitExpansionTree(Expansion exp, Generator gen) {
    // gen.text("[->" + exp.getClass().getName() + "]");
    switch (exp) {
      case Action action -> JJDoc.emitExpansionAction();
      case Choice choice -> JJDoc.emitExpansionChoice(choice, gen);
      case Lookahead lookahead -> JJDoc.emitExpansionLookahead();
      case NonTerminal nonTerminal -> JJDoc.emitExpansionNonTerminal(nonTerminal, gen);
      case OneOrMore oneOrMore -> JJDoc.emitExpansionOneOrMore(oneOrMore, gen);
      case RegularExpression regularExpression ->
          JJDoc.emitExpansionRegularExpression((RExpression)regularExpression, gen);
      case Sequence sequence -> JJDoc.emitExpansionSequence(sequence, gen);
      case ZeroOrMore zeroOrMore -> JJDoc.emitExpansionZeroOrMore(zeroOrMore, gen);
      case ZeroOrOne zeroOrOne -> JJDoc.emitExpansionZeroOrOne(zeroOrOne, gen);
      case null, default -> JJDocGlobals.error("Oops: Unknown expansion type.");
    }
    // gen.text("[<-" + exp.getClass().getName() + "]");
  }

  private static void emitExpansionAction() {
  }

  private static void emitExpansionChoice(Choice c, Generator gen) {
    for (Iterator<Expansion> it = c.getChoices().iterator(); it.hasNext(); ) {
      Expansion e = it.next();
      JJDoc.emitExpansionTree(e, gen);
      if (it.hasNext()) {
        gen.text(" | ");
      }
    }
  }

  private static void emitExpansionLookahead() {
  }

  private static void emitExpansionNonTerminal(NonTerminal nt, Generator gen) {
    gen.nonTerminalStart(nt);
    gen.text(nt.getName());
    gen.nonTerminalEnd(nt);
  }

  private static void emitExpansionOneOrMore(OneOrMore o, Generator gen) {
    gen.text("( ");
    JJDoc.emitExpansionTree(o.getExpansion(), gen);
    gen.text(" )+");
  }

  private static void emitExpansionRegularExpression(RExpression r, Generator gen) {
    String reRendered = JJDoc.emitRE(r);
    if (!reRendered.isEmpty()) {
      gen.reStart(r);
      gen.text(reRendered);
      gen.reEnd(r);
    }
  }

  private static void emitExpansionSequence(Sequence s, Generator gen) {
    boolean firstUnit = true;
    for (Object unit : s.getUnits()) {
      Expansion e = (Expansion) unit;
      if ((e instanceof Lookahead) || (e instanceof Action)) {
        continue;
      }
      if (!firstUnit) {
        gen.text(" ");
      }
      boolean needParens = (e instanceof Choice) || (e instanceof Sequence);
      if (needParens) {
        gen.text("( ");
      }
      JJDoc.emitExpansionTree(e, gen);
      if (needParens) {
        gen.text(" )");
      }
      firstUnit = false;
    }
  }

  private static void emitExpansionZeroOrMore(ZeroOrMore z, Generator gen) {
    gen.text("( ");
    JJDoc.emitExpansionTree(z.getExpansion(), gen);
    gen.text(" )*");
  }

  private static void emitExpansionZeroOrOne(ZeroOrOne z, Generator gen) {
    gen.text("( ");
    JJDoc.emitExpansionTree(z.getExpansion(), gen);
    gen.text(" )?");
  }

  static String emitRE(RExpression re) {
    String returnString = "";
    boolean hasLabel = !re.getLabel().isEmpty();
    boolean justName = re instanceof RJustName;
    boolean eof = re instanceof REndOfFile;
    boolean isString = re instanceof RStringLiteral;
    boolean toplevelRE = (re.getTpContext() != null);
    boolean needBrackets = justName || eof || hasLabel || (!isString && toplevelRE);
    if (needBrackets) {
      returnString += "<";
      if (!justName) {
        if (re.isPrivateExp()) {
          returnString += "#";
        }
        if (hasLabel) {
          returnString += re.getLabel();
          returnString += ": ";
        }
      }
    }
    switch (re) {
      case RCharacterList cl -> {
        if (cl.isNegated_list()) {
          returnString += "~";
        }
        returnString += "[";
        for (Iterator<Object> it = cl.getDescriptors().iterator(); it.hasNext(); ) {
          Object o = it.next();
          if (o instanceof SingleCharacter c) {
            returnString += "\"";
            char[] s = {c.getChar()};
            returnString += Encoding.escape(new String(s));
            returnString += "\"";
          }
          else if (o instanceof CharacterRange range) {
            returnString += "\"";
            char[] s = {range.getLeft()};
            returnString += Encoding.escape(new String(s));
            returnString += "\"-\"";
            s[0] = range.getRight();
            returnString += Encoding.escape(new String(s));
            returnString += "\"";
          }
          else {
            JJDocGlobals.error("Oops: unknown character list element type.");
          }
          if (it.hasNext())
            returnString += ",";
        }
        returnString += "]";
      }
      case RChoice c -> {
        for (Iterator<RExpression> it = c.getChoices().iterator(); it.hasNext(); ) {
          RExpression sub = it.next();
          returnString += JJDoc.emitRE(sub);
          if (it.hasNext())
            returnString += " | ";
        }
      }
      case REndOfFile rEndOfFile -> returnString += "EOF";
      case RJustName jn -> returnString += jn.getLabel();
      case ROneOrMore om -> {
        returnString += "(";
        returnString += JJDoc.emitRE(om.getRegexpr());
        returnString += ")+";
      }
      case RSequence s -> {
        for (Iterator<RExpression> it = s.getUnits().iterator(); it.hasNext(); ) {
          RExpression sub = it.next();
          boolean needParens = sub instanceof RChoice;
          if (needParens) {
            returnString += "(";
          }
          returnString += JJDoc.emitRE(sub);
          if (needParens) {
            returnString += ")";
          }
          if (it.hasNext()) {
            returnString += " ";
          }
        }
      }
      case RStringLiteral sl -> returnString += ("\"" + Encoding.escape(sl.getImage()) + "\"");
      case RZeroOrMore zm -> {
        returnString += "(";
        returnString += JJDoc.emitRE(zm.getRegexpr());
        returnString += ")*";
      }
      case RZeroOrOne zo -> {
        returnString += "(";
        returnString += JJDoc.emitRE(zo.getRegexpr());
        returnString += ")?";
      }
      case RRepetitionRange zo -> {
        returnString += "(";
        returnString += JJDoc.emitRE(zo.getRegexpr());
        returnString += ")";
        returnString += "{";
        if (zo.hasMax()) {
          returnString += zo.getMin();
          returnString += ",";
          returnString += zo.getMax();
        }
        else {
          returnString += zo.getMin();
        }
        returnString += "}";
      }
      default -> JJDocGlobals.error("Oops: Unknown regular expression type.");
    }
    if (needBrackets) {
      returnString += ">";
    }
    return returnString;
  }
}
