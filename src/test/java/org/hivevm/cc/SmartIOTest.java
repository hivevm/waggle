
package org.hivevm.cc;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

class SmartIOTest {

  public static final File ROOT        = new File("/data/smartIO/develop");
  public static final File PARSER_JJT  = new File(ROOT, "parser/parser/src/main/resources/it/smartio/text/parser");
  public static final File PARSER_CPP  = new File(ROOT, "core-cpp/text/parser");
  public static final File PARSER_JAVA = new File(ROOT, "parser/parser/src/main/java/it/smartio/text/parser");

  @Test
  void testCpp() {
    HiveCCBuilder builder = HiveCCBuilder.of(Language.CPP);
    builder.setTargetDir(SmartIOTest.PARSER_CPP);
    builder.setJJTreeFile(SmartIOTest.PARSER_JJT, "OQL-Cpp.jjt");
    builder.build();
  }

  @Test
  void testJava() {
    HiveCCBuilder builder = HiveCCBuilder.of(Language.JAVA);
    builder.setTargetDir(SmartIOTest.PARSER_JAVA);
    builder.setJJTreeFile(SmartIOTest.PARSER_JJT, "OQL.jjt");
    builder.build();
  }

  public static void main(String... args) throws Exception {
    File inputFile = new File("/data/smartIO/fastcc/JavaGrammars/Test.java");
    String input = new String(Files.readAllBytes(inputFile.toPath()));

    HiveCCBuilder builder = HiveCCBuilder.of(Language.JAVA);
    builder.setTargetDir(SmartIOTest.PARSER_JAVA);
    builder.setJJFile(new File("/data/smartIO/fastcc/JavaGrammars"), "Java1.1.jj");
    builder.interpret(input);
  }
}
