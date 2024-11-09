// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;

import org.hivevm.cc.generator.LexerBuilder;
import org.hivevm.cc.generator.LexerData;
import org.hivevm.cc.parser.JavaCCData;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.JavaCCParser;
import org.hivevm.cc.parser.JavaCCParserDefault;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.parser.StringProvider;
import org.hivevm.cc.semantic.Semanticize;

import java.text.ParseException;
import java.util.HashSet;
import java.util.Set;

public class HiveCCInterpreter {

  private final Options options;


  /**
   * @param options
   */
  public HiveCCInterpreter(Options options) {
    this.options = options;
  }

  public void runTokenizer(String grammar, String input) {
    JavaCCErrors.reInit();
    try {
      JavaCCData request = new JavaCCData(false, options);

      JavaCCParser parser = new JavaCCParserDefault(new StringProvider(grammar), options);
      parser.initialize(request);
      parser.javacc_input();

      Semanticize.semanticize(request, options);

      if (JavaCCErrors.get_error_count() == 0) {
        LexerData data = new LexerBuilder().build(request);
        tokenize(data, input, options);
      }
    } catch (ParseException e) {
      System.out.println("Detected " + JavaCCErrors.get_error_count() + " errors and "
          + JavaCCErrors.get_warning_count() + " warnings.");
      System.exit(1);
    } catch (Exception e) {
      System.out.println(e.toString());
      System.out.println("Detected " + (JavaCCErrors.get_error_count() + 1) + " errors and "
          + JavaCCErrors.get_warning_count() + " warnings.");
      System.exit(1);
    }
  }

  public static void tokenize(LexerData data, String input, Options options) {
    // First match the string literals.
    final int input_size = input.length();
    int curPos = 0;
    int curLexState = data.defaultLexState;
    Set<Integer> curStates = new HashSet<>();
    Set<Integer> newStates = new HashSet<>();
    while (curPos < input_size) {
      int beg = curPos;
      int matchedPos = beg;
      int matchedKind = Integer.MAX_VALUE;
//      int nfaStartState = data.initialStates.get(curLexState);

      char c = input.charAt(curPos);
      if (options.getIgnoreCase())
        c = Character.toLowerCase(c);
      int key = curLexState << 16 | (int) c;
//      List<String> literals = data.literalSequence.get(key);
//      if (literals != null) {
//        // We need to go in order so that the longest match works.
//        int litIndex = 0;
//        for (String s : literals) {
//          int index = 1;
//          // See which literal matches.
//          while (index < s.length() && curPos + index < input_size) {
//            c = input.charAt(curPos + index);
//            if (options.getIgnoreCase())
//              c = Character.toLowerCase(c);
//            if (c != s.charAt(index))
//              break;
//            index++;
//          }
//          if (index == s.length()) {
//            // Found a string literal match.
//            matchedKind = data.literalKinds.get(key).get(litIndex);
//            matchedPos = curPos + index - 1;
//            nfaStartState = data.kindToNfaStartState.get(matchedKind);
//            curPos += index;
//            break;
//          }
//          litIndex++;
//        }
//      }
//
//      if (nfaStartState != -1) {
//        // We need to add the composite states first.
//        int kind = Integer.MAX_VALUE;
//        curStates.add(nfaStartState);
//        curStates.addAll(data.nfa.get(nfaStartState).compositeStates);
//        do {
//          c = input.charAt(curPos);
//          if (options.getIgnoreCase())
//            c = Character.toLowerCase(c);
//          for (int state : curStates) {
//            NfaState nfaState = data.nfa.get(state);
//            if (nfaState.characters.contains(c)) {
//              if (kind > nfaState.kind) {
//                kind = nfaState.kind;
//              }
//              newStates.addAll(nfaState.nextStates);
//            }
//          }
//          Set<Integer> tmp = newStates;
//          newStates = curStates;
//          curStates = tmp;
//          newStates.clear();
//          if (kind != Integer.MAX_VALUE) {
//            matchedKind = kind;
//            matchedPos = curPos;
//            kind = Integer.MAX_VALUE;
//          }
//        } while (!curStates.isEmpty() && ++curPos < input_size);
//      }
//      if (matchedPos == beg && matchedKind > data.wildcardKind.get(curLexState)) {
//        matchedKind = data.wildcardKind.get(curLexState);
//      }
//      if (matchedKind != Integer.MAX_VALUE) {
//        TokenizerData.MatchInfo matchInfo = data.allMatches.get(matchedKind);
//        if (matchInfo.action != null) {
//          System.err.println("Actions not implemented (yet) in intererpreted mode");
//        }
//        if (matchInfo.matchType == TokenProduction.Kind.TOKEN) {
//          System.err.println("Token: " + matchedKind + "; image: \"" + input.substring(beg, matchedPos + 1) + "\"");
//        }
//        if (matchInfo.newLexState != -1) {
//          curLexState = matchInfo.newLexState;
//        }
//        curPos = matchedPos + 1;
//      } else {
//        System.err.println("Encountered token error at char: " + input.charAt(curPos));
//        System.exit(1);
//      }
    }
    System.err.println("Matched EOF");
  }
}
