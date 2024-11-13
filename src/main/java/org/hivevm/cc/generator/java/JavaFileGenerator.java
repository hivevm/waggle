// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

import org.hivevm.cc.JavaCCRequest;
import org.hivevm.cc.generator.FileGenerator;
import org.hivevm.cc.generator.LexerData;
import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.ParseException;
import org.hivevm.cc.parser.RStringLiteral;
import org.hivevm.cc.parser.RegExprSpec;
import org.hivevm.cc.parser.RegularExpression;
import org.hivevm.cc.parser.TokenProduction;
import org.hivevm.cc.utils.Encoding;
import org.hivevm.cc.utils.TemplateOptions;
import org.hivevm.cc.utils.TemplateProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates the Constants file.
 */
public class JavaFileGenerator implements FileGenerator {

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

    TemplateProvider.render(JavaTemplate.TOKEN, context.options());
    TemplateProvider.render(JavaTemplate.TOKEN_EXCEPTION, context.options());

    TemplateProvider.render(JavaTemplate.PROVIDER, context.options());
    TemplateProvider.render(JavaTemplate.STRING_PROVIDER, context.options());
    TemplateProvider.render(JavaTemplate.STREAM_PROVIDER, context.options());
    TemplateProvider.render(JavaTemplate.CHAR_STREAM, context.options());

    TemplateProvider.render(JavaTemplate.PARSER_EXCEPTION, context.options());
    TemplateProvider.render(JavaTemplate.PARSER_CONSTANTS, context.options(), options, request.getParserName());
  }
}
