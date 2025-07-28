
package org.hivevm.cc;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

class H3QLTest {

  public static final File WORKING_DIR = new File("../hivevm").getAbsoluteFile();
  public static final File MAIN_DIR = new File(H3QLTest.WORKING_DIR, "core/criteria/src/main");

  public static final File PARSER_JJT  = new File(H3QLTest.MAIN_DIR, "resources/org/hivevm/criteria/parser");
  public static final File PARSER_CPP  = new File(H3QLTest.MAIN_DIR, "cpp/parser");
  public static final File PARSER_JAVA = new File(H3QLTest.MAIN_DIR, "java");

  //@Test
  void testCpp() {
    ParserBuilder builder = ParserBuilder.of(Language.CPP);
    builder.setTargetDir(H3QLTest.PARSER_CPP);
    builder.setTreeFile(H3QLTest.PARSER_JJT, "OQL.jjt");
    builder.build();
  }

  //@Test
  void testJava() {
    ParserBuilder builder = ParserBuilder.of(Language.JAVA);
    builder.setTargetDir(H3QLTest.PARSER_JAVA);
    builder.setTreeFile(H3QLTest.PARSER_JJT, "OQL.jjt");
    builder.build();
  }

  public static void main(String... args) throws Exception {
    File inputFile = new File("/data/smartIO/fastcc/JavaGrammars/Test.java");
    String input = new String(Files.readAllBytes(inputFile.toPath()));

    ParserBuilder builder = ParserBuilder.of(Language.JAVA);
    builder.setTargetDir(H3QLTest.PARSER_JAVA);
    builder.setParserFile(new File("/data/smartIO/fastcc/JavaGrammars"), "Java1.1.jj");
    builder.interpret(input);
  }
}
