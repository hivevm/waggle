
package org.hivevm.cc;

import java.io.File;

import org.junit.jupiter.api.Test;


class CCParserTest {

  public static final File ROOT          = new File("/data/smartIO/fastcc");
  public static final File PARSER_SOURCE = new File(CCParserTest.ROOT, "src/main/resources");
  public static final File PARSER_TARGET = new File(CCParserTest.ROOT, "src/main/gen/it/smartio/fastcc");

  @Test
  void testJJParser() {
    ParserBuilder builder = ParserBuilder.of(Language.JAVA);
    builder.setTargetDir(new File(CCParserTest.PARSER_TARGET, "parser"));
    builder.setParserFile(CCParserTest.PARSER_SOURCE, "JavaCC.jj");
    builder.build();
  }

  @Test
  void testJJTree() {
    ParserBuilder builder = ParserBuilder.of(Language.JAVA);
    builder.setTargetDir(new File(CCParserTest.PARSER_TARGET, "jjtree"));
    builder.setTreeFile(CCParserTest.PARSER_SOURCE, "JJTree.jjt");
    builder.build();
  }
}
