
package org.hivevm.cc;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;


class CCParserTest {

  private static final File WORKING_DIR    = new File(".").getAbsoluteFile();
  private static final File PARSER_SOURCE  = new File(CCParserTest.WORKING_DIR, "src/main/resources");
  private static final File PARSER_TARGET  = new File(CCParserTest.WORKING_DIR, "src/main/generated2");
  private static final List<String> NODES  = Arrays.asList("BNF", "BNFAction", "BNFDeclaration", "BNFNodeScope",
          "ExpansionNodeScope", "NodeDescriptor", "OptionBinding");

  @Test
  void testJJParser() {
    ParserBuilder builder = ParserBuilder.of(Language.JAVA);
    builder.setTargetDir(CCParserTest.PARSER_TARGET);
    builder.setParserFile(CCParserTest.PARSER_SOURCE, "JavaCC.jj");
    builder.build();
  }

  @Test
  void testJJTree() {
    ParserBuilder builder = ParserBuilder.of(Language.JAVA);
    builder.setTargetDir(CCParserTest.PARSER_TARGET);
    builder.setTreeFile(CCParserTest.PARSER_SOURCE, "JJTree.jjt");
    builder.setCustomNodes(CCParserTest.NODES);
    builder.build();
  }
}
