// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.Language;
import org.hivevm.cc.ParserRequest;
import org.hivevm.cc.jjtree.ASTGrammar;
import org.hivevm.cc.jjtree.ASTWriter;

import java.io.IOException;
import java.text.ParseException;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.stream.Stream;

/**
 * The {@link GeneratorProvider} class.
 */
public abstract class GeneratorProvider implements Generator {

  protected abstract ASTGenerator newASTGenerator();

  protected abstract FileGenerator newFileGenerator();

  protected abstract LexerGenerator newLexerGenerator();

  protected abstract ParserGenerator newParserGenerator();

  /**
   * Generates the parser files.
   *
   * @param request
   */
  @Override
  public final void generate(ParserRequest request) throws IOException, ParseException {
    LexerData dataLexer = new LexerBuilder().build(request);
    ParserData dataParser = new ParserBuilder().build(request);

    newLexerGenerator().generate(dataLexer);
    newParserGenerator().generate(dataParser);
    newFileGenerator().generate(dataLexer);
  }

  /**
   * Generates the Abstract Syntax Tree.
   *
   * @param node
   * @param writer
   * @param context
   */
  @Override
  public final void generateAST(ASTGrammar node, ASTWriter writer, ASTGeneratorContext context) {
    ASTGenerator generator = newASTGenerator();
    node.jjtAccept(generator, writer);
    generator.generate(context);
  }

  /**
   * Lookups for a {@link Generator} for the provided language.
   *
   * @param language
   */
  public static Generator generatorFor(Language language) {
    ServiceLoader<Generator> loader = ServiceLoader.load(Generator.class);
    Stream<Provider<Generator>> provider = loader.stream();
    provider = provider.filter(p -> p.type().isAnnotationPresent(GeneratorName.class)
        && p.type().getAnnotation(GeneratorName.class).value().equalsIgnoreCase(language.name()));
    return provider.findFirst().orElseThrow().get();
  }
}
