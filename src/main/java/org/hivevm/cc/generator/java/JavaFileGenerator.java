// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

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
import org.hivevm.cc.utils.Encoding;
import org.hivevm.cc.utils.TemplateOptions;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the Constants file.
 */
public class JavaFileGenerator extends AbstractFileGenerator implements FileGenerator {

  /**
   * Gets the template by name.
   * 
   * @param name
   */
  protected final String getTemplate(String name) {
    return String.format("/templates/java/%s.template", name.substring(0, name.length() - 5));
  }

  /**
   * Creates a new {@link DigestWriter}.
   * 
   * @param file
   * @param options
   */
  protected final DigestWriter createDigestWriter(File file, DigestOptions options) throws FileNotFoundException {
    return DigestWriter.create(file, HiveCC.VERSION, options);
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

      if (expressions.indexOf(re) < expressions.size() - 1)
        buffer.append(",");
      return buffer.toString();
    });

    generateFile("Token.java", new DigestOptions(context.options()));
    generateFile("TokenException.java", new DigestOptions(context.options()));

    generateFile("Provider.java", new DigestOptions(context.options()));
    generateFile("StringProvider.java", new DigestOptions(context.options()));
    generateFile("StreamProvider.java", new DigestOptions(context.options()));
    generateFile("JavaCharStream.java", new DigestOptions(context.options()));

    generateFile("ParseException.java", new DigestOptions(context.options()));

    generateFile("ParserConstants.java", request.getParserName() + "Constants.java",
        new DigestOptions(context.options(), options));
  }
}
