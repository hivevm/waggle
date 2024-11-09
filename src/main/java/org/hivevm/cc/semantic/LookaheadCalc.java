// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.semantic;

import org.hivevm.cc.parser.Choice;
import org.hivevm.cc.parser.Expansion;
import org.hivevm.cc.parser.Lookahead;
import org.hivevm.cc.parser.OneOrMore;
import org.hivevm.cc.parser.RStringLiteral;
import org.hivevm.cc.parser.RegularExpression;
import org.hivevm.cc.parser.Sequence;
import org.hivevm.cc.parser.ZeroOrMore;
import org.hivevm.cc.utils.Encoding;

import java.util.ArrayList;
import java.util.List;

class LookaheadCalc {

  private static MatchInfo overlap(List<MatchInfo> v1, List<MatchInfo> v2) {
    MatchInfo m1, m2, m3;
    int size;
    boolean diff;
    for (MatchInfo element : v1) {
      m1 = element;
      for (MatchInfo element2 : v2) {
        m2 = element2;
        size = m1.firstFreeLoc;
        m3 = m1;
        if (size > m2.firstFreeLoc) {
          size = m2.firstFreeLoc;
          m3 = m2;
        }
        if (size == 0) {
          return null;
        }
        // we wish to ignore empty expansions and the JAVACODE stuff here.
        diff = false;
        for (int k = 0; k < size; k++) {
          if (m1.match[k] != m2.match[k]) {
            diff = true;
            break;
          }
        }
        if (!diff) {
          return m3;
        }
      }
    }
    return null;
  }

  private static boolean javaCodeCheck(List<MatchInfo> v) {
    for (MatchInfo element : v) {
      if ((element).firstFreeLoc == 0) {
        return true;
      }
    }
    return false;
  }

  private static String image(MatchInfo m, Semanticize semanticize) {
    String ret = "";
    for (int i = 0; i < m.firstFreeLoc; i++) {
      if (m.match[i] == 0) {
        ret += " <EOF>";
      } else {
        RegularExpression re = semanticize.getRegularExpression(m.match[i]);
        if (re instanceof RStringLiteral) {
          ret += " \"" + Encoding.escape(((RStringLiteral) re).getImage()) + "\"";
        } else if ((re.getLabel() != null) && !re.getLabel().equals("")) {
          ret += " <" + re.getLabel() + ">";
        } else {
          ret += " <token of kind " + i + ">";
        }
      }
    }
    if (m.firstFreeLoc == 0) {
      return "";
    } else {
      return ret.substring(1);
    }
  }

  static void choiceCalc(Choice ch, Semanticize data, SemanticContext context) {
    int first = LookaheadCalc.firstChoice(ch, context);
    // dbl[i] and dbr[i] are lists of size limited matches for choice i
    // of ch. dbl ignores matches with semantic lookaheads (when force_la_check
    // is false), while dbr ignores semantic lookahead.
    List<MatchInfo>[] dbl = new ArrayList[ch.getChoices().size()];
    List<MatchInfo>[] dbr = new ArrayList[ch.getChoices().size()];
    int[] minLA = new int[ch.getChoices().size() - 1];
    MatchInfo[] overlapInfo = new MatchInfo[ch.getChoices().size() - 1];
    int[] other = new int[ch.getChoices().size() - 1];
    MatchInfo m;
    List<MatchInfo> v;
    boolean overlapDetected;
    for (int la = 1; la <= context.getChoiceAmbiguityCheck(); la++) {
      data.setLaLimit(la);
      data.setConsiderSemanticLA(!context.isForceLaCheck());
      for (int i = first; i < (ch.getChoices().size() - 1); i++) {
        data.initSizeLimitedMatches();
        m = new MatchInfo(data.laLimit());
        v = new ArrayList<>();
        v.add(m);
        LookaheadWalk.genFirstSet(data, v, ch.getChoices().get(i));
        dbl[i] = data.getSizeLimitedMatches();
      }
      data.setConsiderSemanticLA(false);
      for (int i = first + 1; i < ch.getChoices().size(); i++) {
        data.initSizeLimitedMatches();
        m = new MatchInfo(data.laLimit());
        v = new ArrayList<>();
        v.add(m);
        LookaheadWalk.genFirstSet(data, v, ch.getChoices().get(i));
        dbr[i] = data.getSizeLimitedMatches();
      }
      if (la == 1) {
        for (int i = first; i < (ch.getChoices().size() - 1); i++) {
          Expansion exp = ch.getChoices().get(i);
          if (Semanticize.emptyExpansionExists(exp)) {
            context.onWarning(exp, "This choice can expand to the empty token sequence "
                + "and will therefore always be taken in favor of the choices appearing later.");
            break;
          } else if (LookaheadCalc.javaCodeCheck(dbl[i])) {
            context.onWarning(exp, "JAVACODE non-terminal will force this choice to be taken "
                + "in favor of the choices appearing later.");
            break;
          }
        }
      }
      overlapDetected = false;
      for (int i = first; i < (ch.getChoices().size() - 1); i++) {
        for (int j = i + 1; j < ch.getChoices().size(); j++) {
          if ((m = LookaheadCalc.overlap(dbl[i], dbr[j])) != null) {
            minLA[i] = la + 1;
            overlapInfo[i] = m;
            other[i] = j;
            overlapDetected = true;
            break;
          }
        }
      }
      if (!overlapDetected) {
        break;
      }
    }
    for (int i = first; i < (ch.getChoices().size() - 1); i++) {
      if (LookaheadCalc.explicitLA(ch.getChoices().get(i)) && !context.isForceLaCheck()) {
        continue;
      }
      if (minLA[i] > context.getChoiceAmbiguityCheck()) {
        context.onWarning("Choice conflict involving two expansions at");
        System.err.print("         line " + ch.getChoices().get(i).getLine());
        System.err.print(", column " + ch.getChoices().get(i).getColumn());
        System.err.print(" and line " + ch.getChoices().get(other[i]).getLine());
        System.err.print(", column " + ch.getChoices().get(other[i]).getColumn());
        System.err.println(" respectively.");
        System.err.println("         A common prefix is: " + LookaheadCalc.image(overlapInfo[i], data));
        System.err.println("         Consider using a lookahead of " + minLA[i] + " or more for earlier expansion.");
      } else if (minLA[i] > 1) {
        context.onWarning("Choice conflict involving two expansions at");
        System.err.print("         line " + ch.getChoices().get(i).getLine());
        System.err.print(", column " + ch.getChoices().get(i).getColumn());
        System.err.print(" and line " + ch.getChoices().get(other[i]).getLine());
        System.err.print(", column " + ch.getChoices().get(other[i]).getColumn());
        System.err.println(" respectively.");
        System.err.println("         A common prefix is: " + LookaheadCalc.image(overlapInfo[i], data));
        System.err.println("         Consider using a lookahead of " + minLA[i] + " for earlier expansion.");
      }
    }
  }

  private static boolean explicitLA(Expansion exp) {
    if (!(exp instanceof Sequence)) {
      return false;
    }
    Sequence seq = (Sequence) exp;
    Object obj = seq.getUnits().get(0);
    if (!(obj instanceof Lookahead)) {
      return false;
    }
    Lookahead la = (Lookahead) obj;
    return la.isExplicit();
  }

  private static int firstChoice(Choice ch, SemanticContext context) {
    if (context.isForceLaCheck()) {
      return 0;
    }
    for (int i = 0; i < ch.getChoices().size(); i++) {
      if (!LookaheadCalc.explicitLA(ch.getChoices().get(i))) {
        return i;
      }
    }
    return ch.getChoices().size();
  }

  private static String image(Expansion exp) {
    if (exp instanceof OneOrMore) {
      return "(...)+";
    } else if (exp instanceof ZeroOrMore) {
      return "(...)*";
    } else /* if (exp instanceof ZeroOrOne) */ {
      return "[...]";
    }
  }

  static void ebnfCalc(Expansion exp, Expansion nested, Semanticize data, SemanticContext context) {
    // exp is one of OneOrMore, ZeroOrMore, ZeroOrOne
    MatchInfo m, m1 = null;
    List<MatchInfo> v;
    List<MatchInfo> first, follow;
    int la;
    for (la = 1; la <= context.getOtherAmbiguityCheck(); la++) {
      data.setLaLimit(la);
      data.initSizeLimitedMatches();
      m = new MatchInfo(data.laLimit());
      v = new ArrayList<>();
      v.add(m);
      data.setConsiderSemanticLA(!context.isForceLaCheck());
      LookaheadWalk.genFirstSet(data, v, nested);
      first = data.getSizeLimitedMatches();
      data.initSizeLimitedMatches();
      data.setConsiderSemanticLA(false);
      LookaheadWalk.genFollowSet(v, exp, data.nextGenerationIndex(), data);
      follow = data.getSizeLimitedMatches();
      if (la == 1) {
        if (LookaheadCalc.javaCodeCheck(first)) {
          context.onWarning(nested,
              "JAVACODE non-terminal within " + LookaheadCalc.image(exp)
                  + " construct will force this construct to be entered in favor of "
                  + "expansions occurring after construct.");
        }
      }
      if ((m = LookaheadCalc.overlap(first, follow)) == null) {
        break;
      }
      m1 = m;
    }
    if (la > context.getOtherAmbiguityCheck()) {
      context.onWarning("Choice conflict in " + LookaheadCalc.image(exp) + " construct " + "at line " + exp.getLine()
          + ", column " + exp.getColumn() + ".");
      System.err.println("         Expansion nested within construct and expansion following construct");
      System.err.println("         have common prefixes, one of which is: " + LookaheadCalc.image(m1, data));
      System.err.println("         Consider using a lookahead of " + la + " or more for nested expansion.");
    } else if (la > 1) {
      context.onWarning("Choice conflict in " + LookaheadCalc.image(exp) + " construct " + "at line " + exp.getLine()
          + ", column " + exp.getColumn() + ".");
      System.err.println("         Expansion nested within construct and expansion following construct");
      System.err.println("         have common prefixes, one of which is: " + LookaheadCalc.image(m1, data));
      System.err.println("         Consider using a lookahead of " + la + " for nested expansion.");
    }
  }
}
