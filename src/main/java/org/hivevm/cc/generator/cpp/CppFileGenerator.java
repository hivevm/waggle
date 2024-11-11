// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.hivevm.cc.JavaCCRequest;
import org.hivevm.cc.generator.AbstractFileGenerator;
import org.hivevm.cc.generator.FileGenerator;
import org.hivevm.cc.generator.LexerData;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.ParseException;
import org.hivevm.cc.parser.RStringLiteral;
import org.hivevm.cc.parser.RegExprSpec;
import org.hivevm.cc.parser.RegularExpression;
import org.hivevm.cc.parser.TokenProduction;
import org.hivevm.cc.utils.DigestOptions;
import org.hivevm.cc.utils.TemplateOptions;

/**
 * Generates the Constants file.
 */
public class CppFileGenerator extends AbstractFileGenerator implements FileGenerator {

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


    generateFile(CppTemplate.JAVACC, new DigestOptions(context.options()));

    generateFile(CppTemplate.TOKEN, new DigestOptions(context.options()));
    generateFile(CppTemplate.TOKEN_H, new DigestOptions(context.options()));
    generateFile(CppTemplate.TOKENMANAGER, new DigestOptions(context.options()));
    generateFile(CppTemplate.TOKENNANAGERERROR, new DigestOptions(context.options()));
    generateFile(CppTemplate.TOKENNANAGERERROR_H, new DigestOptions(context.options()));
    generateFile(CppTemplate.TOKENNANAGERHANDLER, new DigestOptions(context.options()));
    generateFile(CppTemplate.TOKENNANAGERHANDLER_H, new DigestOptions(context.options()));

    generateFile(CppTemplate.READER, new DigestOptions(context.options()));
    generateFile(CppTemplate.STRINGREADER, new DigestOptions(context.options()));
    generateFile(CppTemplate.STRINGREADER_H, new DigestOptions(context.options()));

    generateFile(CppTemplate.PARSEEXCEPTION, new DigestOptions(context.options()));
    generateFile(CppTemplate.PARSEEXCEPTION_H, new DigestOptions(context.options()));
    generateFile(CppTemplate.PARSERHANDLER, new DigestOptions(context.options()));
    generateFile(CppTemplate.PARSERHANDLER_H, new DigestOptions(context.options()));

    generateFile(CppTemplate.PARSER_CONSTANTS, request.getParserName(), new DigestOptions(context.options(), options));
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
  protected static void printCharArray(PrintWriter writer, String s) {
    for (int i = 0; i < s.length(); i++) {
      writer.print("0x" + Integer.toHexString(s.charAt(i)) + ", ");
    }
  }
}
