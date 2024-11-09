// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import org.hivevm.cc.HiveCC;
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
import org.hivevm.cc.utils.DigestWriter;
import org.hivevm.cc.utils.TemplateOptions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the Constants file.
 */
public class CppFileGenerator extends AbstractFileGenerator implements FileGenerator {

  /**
   * Gets the template by name.
   * 
   * @param name
   */
  protected final String getTemplate(String name) {
    return String.format("/templates/cpp/%s.template", name);
  }

  /**
   * Creates a new {@link DigestWriter}.
   * 
   * @param file
   * @param options
   */
  protected final DigestWriter createDigestWriter(File file, DigestOptions options) throws FileNotFoundException {
    return DigestWriter.createCpp(file, HiveCC.VERSION, options);
  }

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
    options.add("REGEXPS", expressions.size() + 1).set("label", (i, w) -> getRegExp(w, false, i, expressions))
        .set("image", (i, w) -> getRegExp(w, true, i, expressions));


    generateFile("JavaCC.h", new DigestOptions(context.options()));

    generateFile("Token.h", new DigestOptions(context.options()));
    generateFile("Token.cc", new DigestOptions(context.options()));
    generateFile("TokenManager.h", new DigestOptions(context.options()));
    generateFile("TokenManagerError.h", new DigestOptions(context.options()));
    generateFile("TokenManagerError.cc", new DigestOptions(context.options()));
    generateFile("TokenManagerErrorHandler.h", new DigestOptions(context.options()));
    generateFile("TokenManagerErrorHandler.cc", new DigestOptions(context.options()));

    generateFile("Reader.h", new DigestOptions(context.options()));
    generateFile("StringReader.h", new DigestOptions(context.options()));
    generateFile("StringReader.cc", new DigestOptions(context.options()));

    generateFile("ParseException.h", new DigestOptions(context.options()));
    generateFile("ParseException.cc", new DigestOptions(context.options()));
    generateFile("ParserErrorHandler.h", new DigestOptions(context.options()));
    generateFile("ParserErrorHandler.cc", new DigestOptions(context.options()));

    generateFile("ParserConstants.h", request.getParserName() + "Constants.h",
        new DigestOptions(context.options(), options));
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
