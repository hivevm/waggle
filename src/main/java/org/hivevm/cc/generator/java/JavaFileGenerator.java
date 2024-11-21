// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

import org.hivevm.cc.generator.FileGenerator;
import org.hivevm.cc.generator.LexerData;
import org.hivevm.cc.generator.TemplateProvider;
import org.hivevm.cc.parser.ParseException;

/**
 * Generates the Constants file.
 */
class JavaFileGenerator implements FileGenerator {

  @Override
  public final void generate(LexerData context) throws ParseException {
    TemplateProvider.render(JavaTemplate.TOKEN, context.options());
    TemplateProvider.render(JavaTemplate.TOKEN_EXCEPTION, context.options());

    TemplateProvider.render(JavaTemplate.PROVIDER, context.options());
    TemplateProvider.render(JavaTemplate.STRING_PROVIDER, context.options());
    TemplateProvider.render(JavaTemplate.STREAM_PROVIDER, context.options());
    TemplateProvider.render(JavaTemplate.CHAR_STREAM, context.options());

    TemplateProvider.render(JavaTemplate.PARSER_EXCEPTION, context.options());
  }
}
