// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import java.io.PrintWriter;
import java.util.List;

import org.hivevm.cc.generator.FileGenerator;
import org.hivevm.cc.generator.LexerData;
import org.hivevm.cc.generator.TemplateProvider;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.ParseException;
import org.hivevm.cc.parser.RStringLiteral;
import org.hivevm.cc.parser.RegularExpression;
import org.hivevm.cc.parser.TokenProduction;

/**
 * Generates the Constants file.
 */
class CppFileGenerator implements FileGenerator {

  @Override
  public final void generate(LexerData context) throws ParseException {
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
  }

  static void getRegExp(PrintWriter writer, boolean isImage, int i, List<RegularExpression> expressions) {
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
