// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.ParserRequest;
import org.hivevm.cc.generator.ParserData.Phase3Data;
import org.hivevm.cc.model.Action;
import org.hivevm.cc.model.BNFProduction;
import org.hivevm.cc.model.Choice;
import org.hivevm.cc.model.Expansion;
import org.hivevm.cc.model.Lookahead;
import org.hivevm.cc.model.NonTerminal;
import org.hivevm.cc.model.NormalProduction;
import org.hivevm.cc.model.OneOrMore;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.RegularExpression;
import org.hivevm.cc.model.Sequence;
import org.hivevm.cc.model.ZeroOrMore;
import org.hivevm.cc.model.ZeroOrOne;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.ParseException;
import org.hivevm.cc.semantic.Semanticize;

public class ParserBuilder {

  // Constants used in the following method "buildLookaheadChecker".
  protected enum LookaheadState {
    NOOPENSTM,
    OPENIF,
    OPENSWITCH
  }

  private int rIndex;

  /**
   * Constructs an instance of {@link ParserBuilder}.
   */
  public ParserBuilder() {
    this.rIndex = 0;
  }

  private int nextRIndex() {
    return ++this.rIndex;
  }

  public final ParserData build(ParserRequest request) throws ParseException {
    if (JavaCCErrors.hasError()) {
      throw new ParseException();
    }

    ParserData data = new ParserData(request);
    for (NormalProduction p : data.getProductions()) {
      if (p instanceof BNFProduction) {
        buildPhase1(data, p.getExpansion());
      }
    }

    for (Lookahead la : data.getLoakaheads()) {
      data.addExpansion(la);
    }

    int phase3index = 0;
    while (phase3index < data.phase3list.size()) {
      for (; phase3index < data.phase3list.size(); phase3index++) {
        setupPhase3Builds(data, data.phase3list.get(phase3index));
      }
    }

    for (Expansion e : data.getExpansions()) {
      buildPhase3Routine(data, e, data.getCount(e));
    }

    return data;
  }

  private void buildPhase1(ParserData data, Expansion e) {
    if (e instanceof Choice e_nrw) {
      Lookahead[] conds = new Lookahead[e_nrw.getChoices().size()];
      // In previous line, the "throw" never throws an exception since the
      // evaluation of jj_consume_token(-1) causes ParseException to be
      // thrown first.
      for (int i = 0; i < e_nrw.getChoices().size(); i++) {
        Sequence nestedSeq = (Sequence) (e_nrw.getChoices().get(i));
        buildPhase1(data, nestedSeq);
        conds[i] = (Lookahead) (nestedSeq.getUnits().getFirst());
      }
      data.setLookupAhead(e, conds);

      buildLookahead(data, conds);
    }
    else if (e instanceof Sequence e_nrw) {
      // We skip the first element in the following iteration since it is the
      // Lookahead object.
      for (int i = 1; i < e_nrw.getUnits().size(); i++) {
        // For C++, since we are not using exceptions, we will protect all the
        // expansion choices with if (!error)
        buildPhase1(data, (Expansion) (e_nrw.getUnits().get(i)));
      }
    }
    else if (e instanceof OneOrMore e_nrw) {
      Expansion nested_e = e_nrw.getExpansion();
      Lookahead la;
      if (nested_e instanceof Sequence) {
        la = (Lookahead) (((Sequence) nested_e).getUnits().getFirst());
      }
      else {
        la = new Lookahead();
        la.setAmount(data.getLookahead());
        la.setLaExpansion(nested_e);
      }

      Lookahead[] conds = {la};
      data.setLookupAhead(e, conds);

      buildPhase1(data, nested_e);
      buildLookahead(data, conds);
    }
    else if (e instanceof ZeroOrMore e_nrw) {
      Expansion nested_e = e_nrw.getExpansion();
      Lookahead la;
      if (nested_e instanceof Sequence) {
        la = (Lookahead) (((Sequence) nested_e).getUnits().getFirst());
      }
      else {
        la = new Lookahead();
        la.setAmount(data.getLookahead());
        la.setLaExpansion(nested_e);
      }

      Lookahead[] conds = {la};
      data.setLookupAhead(e, conds);

      buildLookahead(data, conds);
      buildPhase1(data, nested_e);
    }
    else if (e instanceof ZeroOrOne e_nrw) {
      Expansion nested_e = e_nrw.getExpansion();

      Lookahead la;
      if (nested_e instanceof Sequence) {
        la = (Lookahead) (((Sequence) nested_e).getUnits().getFirst());
      }
      else {
        la = new Lookahead();
        la.setAmount(data.getLookahead());
        la.setLaExpansion(nested_e);
      }

      Lookahead[] conds = {la};
      data.setLookupAhead(e, conds);

      buildPhase1(data, nested_e);
      buildLookahead(data, conds);
    }
  }

  /**
   * This method takes two parameters - an array of Lookahead's "conds", and an array of String's
   * "actions". "actions" contains exactly one element more than "conds". "actions" are Java source
   * code, and "conds" translate to conditions - so lets say "f(conds[i])" is true if the lookahead
   * required by "conds[i]" is indeed the case. This method returns a string corresponding to the
   * Java code for:
   * <p>
   * if (f(conds[0]) actions[0] else if (f(conds[1]) actions[1] . . . else actions[action.length-1]
   * <p>
   * A particular action entry ("actions[i]") can be null, in which case, a noop is generated for
   * that action.
   */
  private void buildLookahead(ParserData data, Lookahead[] conds) {
    LookaheadState state = LookaheadState.NOOPENSTM;
    boolean jj2LA;

    int[] tokenMask = null;
    int tokenMaskSize = ((data.getTokenCount() - 1) / 32) + 1;
    boolean[] casedValues = new boolean[data.getTokenCount()];

    for (Lookahead la : conds) {
      jj2LA = false;

      if ((la.getAmount() == 0) || Semanticize.emptyExpansionExists(la.getLaExpansion())) {
        // This handles the following cases:
        // . If syntactic lookahead is not wanted (and hence explicitly specified
        // as 0).
        // . If it is possible for the lookahead expansion to recognize the empty
        // string - in which case the lookahead trivially passes.
        // . If the lookahead expansion has a JAVACODE production that it directly
        // expands to - in which case the lookahead trivially passes.
        if (la.getActionTokens().isEmpty()) {
          // In addition, if there is no semantic lookahead, then the
          // lookahead trivially succeeds. So break the main loop and
          // treat this case as the default last action.
          break;
        }
        else {
          // This case is when there is only semantic lookahead
          // (without any preceding syntactic lookahead). In this
          // case, an "if" statement is generated.
          switch (state) {
            case OPENSWITCH:
              data.addMask(tokenMask, la);
            case OPENIF:
            case NOOPENSTM:
          }
          state = LookaheadState.OPENIF;
        }

      }
      else if ((la.getAmount() == 1) && (la.getActionTokens().isEmpty())) {
        // Special optimal processing when the lookahead is exactly 1, and there
        // is no semantic lookahead.
        boolean[] firstSet = new boolean[data.getTokenCount()];
        for (int i = 0; i < data.getTokenCount(); i++) {
          firstSet[i] = false;
        }

        // jj2LA is set to false at the beginning of the containing "if" statement.
        // It is checked immediately after the end of the same statement to determine
        // if lookaheads are to be performed using calls to the jj2 methods.
        jj2LA = data.genFirstSet(la.getLaExpansion(), firstSet, jj2LA);
        // genFirstSet may find that semantic attributes are appropriate for the next
        // token. In which case, it sets jj2LA to true.
        if (!jj2LA) {
          // This case is if there is no applicable semantic lookahead and the lookahead
          // is one (excluding the earlier cases such as JAVACODE, etc.).
          switch (state) {
            case OPENIF:
            case NOOPENSTM:
              for (int i = 0; i < data.getTokenCount(); i++) {
                casedValues[i] = false;
              }
              tokenMask = new int[tokenMaskSize];
              for (int i = 0; i < tokenMaskSize; i++) {
                tokenMask[i] = 0;
              }
              // Don't need to do anything if state is OPENSWITCH.
            default:
          }
          for (int i = 0; i < data.getTokenCount(); i++) {
            if (firstSet[i] && !casedValues[i]) {
              casedValues[i] = true;
              int j1 = i / 32;
              int j2 = i % 32;
              tokenMask[j1] |= 1 << j2;
            }
          }
          state = LookaheadState.OPENSWITCH;
        }
      }
      else {
        // This is the case when lookahead is determined through calls to
        // jj2 methods. The other case is when lookahead is 1, but semantic
        // attributes need to be evaluated. Hence this crazy control structure.
        jj2LA = true;
      }

      if (jj2LA) {
        // In this case lookahead is determined by the jj2 methods.
        switch (state) {
          case OPENSWITCH:
            data.addMask(tokenMask, la);
          case OPENIF:
          case NOOPENSTM:
        }

        // At this point, la.la_expansion.internal_name must be "".
        la.getLaExpansion().setInternalName("_" + data.addLookupAhead(la));
        state = LookaheadState.OPENIF;
      }
    }

    switch (state) {
      case OPENSWITCH:
        data.addMask(tokenMask, conds[conds.length - 1]);
      case OPENIF:
      case NOOPENSTM:
    }
  }

  private void setupPhase3Builds(ParserData data, Phase3Data p3d) {
    Expansion e = p3d.exp;
    if (e instanceof RegularExpression) {
      // nothing to here
    }
    else if (e instanceof NonTerminal e_nrw) {
      // All expansions of non-terminals have the "name" fields set. So
      // there's no need to check it below for "e_nrw" and "ntexp". In
      // fact, we rely here on the fact that the "name" fields of both these
      // variables are the same.
      NormalProduction ntprod = data.getProduction(e_nrw.getName());
      generate3R(data, ntprod.getExpansion(), p3d);
    }
    else if (e instanceof Choice e_nrw) {
      for (Expansion element : e_nrw.getChoices()) {
        generate3R(data, element, p3d);
      }
    }
    else if (e instanceof Sequence e_nrw) {
      // We skip the first element in the following iteration since it is the
      // Lookahead object.
      int cnt = p3d.count;
      for (int i = 1; i < e_nrw.getUnits().size(); i++) {
        Expansion eseq = (Expansion) (e_nrw.getUnits().get(i));
        setupPhase3Builds(data, new Phase3Data(eseq, cnt));
        cnt -= ParserBuilder.minimumSize(data, eseq);
        if (cnt <= 0) {
          break;
        }
      }
    }
    else if (e instanceof OneOrMore e_nrw) {
      generate3R(data, e_nrw.getExpansion(), p3d);
    }
    else if (e instanceof ZeroOrMore e_nrw) {
      generate3R(data, e_nrw.getExpansion(), p3d);
    }
    else if (e instanceof ZeroOrOne e_nrw) {
      generate3R(data, e_nrw.getExpansion(), p3d);
    }
  }


  private void generate3R(ParserData data, Expansion e, Phase3Data inf) {
    Expansion seq = e;
    if (e.internalName().isEmpty()) {
      while (true) {
        if ((seq instanceof Sequence) && (((Sequence) seq).getUnits().size() == 2)) {
          seq = (Expansion) ((Sequence) seq).getUnits().get(1);
        }
        else if (seq instanceof NonTerminal e_nrw) {
          NormalProduction ntprod = data.getProduction(e_nrw.getName());
          seq = ntprod.getExpansion();
        }
        else {
          break;
        }
      }

      if (seq instanceof RExpression re) {
        e.setInternalName("jj_scan_token("
            + ((re.getLabel() == null) || re.getLabel().isEmpty() ? "" + re.getOrdinal()
            : re.getLabel()) + ")");
        return;
      }

      e.setInternalName("R_" + ParserBuilder.getProductionName(e) + "_" + nextRIndex());
    }

    Integer count = data.phase3table.get(e);
    if ((count == null) || (count < inf.count)) {
      data.phase3list.add(new Phase3Data(e, inf.count));
      data.phase3table.put(e, inf.count);
    }
  }

  private static String getProductionName(Expansion e) {
    Object next = e;
    // Limit the number of iterations in case there's a cycle
    for (int i = 0; (i < 42) && (next != null); i++) {
      if (next instanceof BNFProduction bnf)
        return bnf.getLhs();
      else if (next instanceof Expansion exp)
        next = exp.parent();
      else
        return null;
    }
    return null;
  }

  private void buildPhase3Routine(ParserData data, Expansion e, int count) {
    if (e.internalName().startsWith("jj_scan_token")) {
      return;
    }

    if (e instanceof Choice e_nrw) {
      Sequence nested_seq;
      for (Expansion element : e_nrw.getChoices()) {
        nested_seq = (Sequence) (element);
        Lookahead la = (Lookahead) (nested_seq.getUnits().getFirst());
        if (!la.getActionTokens().isEmpty()) {
          // We have semantic lookahead that must be evaluated.
          data.setLookAheadNeeded(true);
        }
      }
    }
    else if (e instanceof Sequence e_nrw) {
      // We skip the first element in the following iteration since it is the
      // Lookahead object.
      int cnt = count;
      for (int i = 1; i < e_nrw.getUnits().size(); i++) {
        Expansion eseq = (Expansion) (e_nrw.getUnits().get(i));
        buildPhase3Routine(data, eseq, cnt);
        cnt -= ParserBuilder.minimumSize(data, eseq);
        if (cnt <= 0) {
          break;
        }
      }
    }
  }

  protected static int minimumSize(ParserData data, Expansion e) {
    return ParserBuilder.minimumSize(data, e, Integer.MAX_VALUE);
  }

  /*
   * Returns the minimum number of tokens that can parse to this expansion.
   */
  private static int minimumSize(ParserData data, Expansion e, int oldMin) {
    if (e.inMinimumSize())
      // recursive search for minimum size unnecessary.
      return Integer.MAX_VALUE;

    e.setInMinimumSize(true);
    try {
      return switch (e) {
        case RegularExpression regularExpression -> 1;
        case NonTerminal e_nrw -> {
          NormalProduction ntprod = data.getProduction(e_nrw.getName());
          Expansion ntexp = ntprod.getExpansion();
          yield ParserBuilder.minimumSize(data, ntexp);
        }
        case Choice e_nrw -> {
          int min = oldMin;
          Expansion nested_e;
          for (int i = 0; (min > 1) && (i < e_nrw.getChoices().size()); i++) {
            nested_e = (e_nrw.getChoices().get(i));
            int min1 = ParserBuilder.minimumSize(data, nested_e, min);
            if (min > min1) {
              min = min1;
            }
          }
          yield min;
        }
        case Sequence e_nrw -> {
          int min = 0;
          // We skip the first element in the following iteration since it is the
          // Lookahead object.
          for (int i = 1; i < e_nrw.getUnits().size(); i++) {
            Expansion eseq = (Expansion) (e_nrw.getUnits().get(i));
            int mineseq = ParserBuilder.minimumSize(data, eseq);
            if ((min == Integer.MAX_VALUE) || (mineseq == Integer.MAX_VALUE)) {
              min = Integer.MAX_VALUE; // Adding infinity to something results in infinity.
            }
            else {
              min += mineseq;
              if (min > oldMin) {
                break;
              }
            }
          }
          yield min;
        }
        case OneOrMore e_nrw -> ParserBuilder.minimumSize(data, e_nrw.getExpansion());
        case ZeroOrMore zeroOrMore -> 0;
        case ZeroOrOne zeroOrOne -> 0;
        case Lookahead lookahead -> 0;
        case Action action -> 0;
        default -> 0;
      };
    } finally {
      e.setInMinimumSize(false);
    }
  }
}
