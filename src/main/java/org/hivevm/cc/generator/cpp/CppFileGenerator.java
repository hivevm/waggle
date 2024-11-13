// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import org.hivevm.cc.JavaCCRequest;
import org.hivevm.cc.generator.FileGenerator;
import org.hivevm.cc.generator.LexerData;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.ParseException;
import org.hivevm.cc.parser.RStringLiteral;
import org.hivevm.cc.parser.RegExprSpec;
import org.hivevm.cc.parser.RegularExpression;
import org.hivevm.cc.parser.TokenProduction;
import org.hivevm.cc.utils.TemplateOptions;
import org.hivevm.cc.utils.TemplateProvider;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the Constants file.
 */
public class CppFileGenerator implements FileGenerator {

  @Override
  public final void handleRequest(JavaCCRequest request, LexerData context) throws ParseException {
    List<RegularExpression> expressions = new ArrayList<>();
    for (TokenProduction tp : request.getTokenProductions()) {
      for (RegExprSpec res : tp.getRespecs()) {
        expressions.add(res.rexp);
      }
    }

    TemplateOptions options = new TemplateOptions();
    options.add("STATES", context.getStateCount()).set("name", i -> context.getStateName(i));
    options.add("TOKENS", request.getOrderedsTokens()).set("ordinal", r -> r.getOrdinal()).set("label",
        r -> r.getLabel());
    options.add("REGEXPS", expressions.size() + 1)
        .set("label", (i, w) -> CppFileGenerator.getRegExp(w, false, i, expressions))
        .set("image", (i, w) -> CppFileGenerator.getRegExp(w, true, i, expressions));


    TemplateProvider.render(CppTemplate.JAVACC, context.options());

    TemplateProvider.render(CppTemplate.TOKEN, context.options());
    TemplateProvider.render(CppTemplate.TOKEN_H, context.options());
    TemplateProvider.render(CppTemplate.TOKENMANAGER, context.options());
    TemplateProvider.render(CppTemplate.TOKENNANAGERERROR, context.options());
    TemplateProvider.render(CppTemplate.TOKENNANAGERERROR_H, context.options());
    TemplateProvider.render(CppTemplate.TOKENNANAGERHANDLER, context.options());
    TemplateProvider.render(CppTemplate.TOKENNANAGERHANDLER_H, context.options());

    TemplateProvider.render(CppTemplate.READER, context.options());
    TemplateProvider.render(CppTemplate.STRINGREADER, context.options());
    TemplateProvider.render(CppTemplate.STRINGREADER_H, context.options());

    TemplateProvider.render(CppTemplate.PARSEEXCEPTION, context.options());
    TemplateProvider.render(CppTemplate.PARSEEXCEPTION_H, context.options());
    TemplateProvider.render(CppTemplate.PARSERHANDLER, context.options());
    TemplateProvider.render(CppTemplate.PARSERHANDLER_H, context.options());

    TemplateProvider.render(CppTemplate.PARSER_CONSTANTS, context.options(), options, request.getParserName());
  }

  private static void getRegExp(PrintWriter writer, boolean isImage, int i, List<RegularExpression> expressions) {
    if (i == 0) {
      CppFileGenerator.printCharArray(writer, "<EOF>");
    } else {
      RegularExpression expr = expressions.get(i - 1);
      if (expr instanceof RStringLiteral) {
        if (isImage) {
          CppFileGenerator.printCharArray(writer, ((RStringLiteral) expr).getImage());
        } else {
          CppFileGenerator.printCharArray(writer, "<" + expr.getLabel() + ">");
        }
      } else if (expr.getLabel().isEmpty()) {
        if (expr.getTpContext().getKind() == TokenProduction.Kind.TOKEN) {
          JavaCCErrors.warning(expr, "Consider giving this non-string token a label for better error reporting.");
        }
        CppFileGenerator.printCharArray(writer, "<token of kind " + expr.getOrdinal() + ">");
      } else {
        CppFileGenerator.printCharArray(writer, "<" + expr.getLabel() + ">");
      }
    }
  }

  // Used by the CPP code generatror
  private static void printCharArray(PrintWriter writer, String s) {
    for (int i = 0; i < s.length(); i++) {
      writer.print("0x" + Integer.toHexString(s.charAt(i)) + ", ");
    }
  }

  // Used by the CPP code generatror
  protected static String toCharArray(String s) {
    String charArray = "";
    for (int i = 0; i < s.length(); i++) {
      charArray += "0x" + Integer.toHexString(s.charAt(i)) + ", ";
    }
    return charArray;
  }
}
