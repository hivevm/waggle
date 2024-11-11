// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

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
import org.hivevm.cc.utils.Encoding;
import org.hivevm.cc.utils.TemplateOptions;

/**
 * Generates the Constants file.
 */
public class JavaFileGenerator extends AbstractFileGenerator implements FileGenerator {

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
    options.add("PRODUCTIONS", expressions).set("label", re -> {
      StringBuffer buffer = new StringBuffer();

      if (re instanceof RStringLiteral) {
        buffer.append("\"\\\"" + Encoding.escape(Encoding.escape(((RStringLiteral) re).getImage())) + "\\\"\"");
      } else if (!re.getLabel().equals("")) {
        buffer.append("\"<" + re.getLabel() + ">\"");
      } else if (re.getTpContext().getKind() == TokenProduction.Kind.TOKEN) {
        JavaCCErrors.warning(re, "Consider giving this non-string token a label for better error reporting.");
      } else {
        buffer.append("\"<token of kind " + re.getOrdinal() + ">\"");
      }

      if (expressions.indexOf(re) < (expressions.size() - 1)) {
        buffer.append(",");
      }
      return buffer.toString();
    });

    generateFile(JavaTemplate.TOKEN, new DigestOptions(context.options()));
    generateFile(JavaTemplate.TOKEN_EXCEPTION, new DigestOptions(context.options()));

    generateFile(JavaTemplate.PROVIDER, new DigestOptions(context.options()));
    generateFile(JavaTemplate.STRING_PROVIDER, new DigestOptions(context.options()));
    generateFile(JavaTemplate.STREAM_PROVIDER, new DigestOptions(context.options()));
    generateFile(JavaTemplate.CHAR_STREAM, new DigestOptions(context.options()));

    generateFile(JavaTemplate.PARSER_EXCEPTION, new DigestOptions(context.options()));
    generateFile(JavaTemplate.PARSER_CONSTANTS, request.getParserName(), new DigestOptions(context.options(), options));
  }
}
