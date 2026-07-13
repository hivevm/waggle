package org.hivevm.cc;

import java.io.File;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * A local scratch harness for the external H3QL/OQL grammar. It reads from {@code ../model} and
 * writes into {@code /data/hivevm/hql/src} — paths that exist on one developer's machine and on no
 * CI runner.
 *
 * <p>Disabled, and it has to be: it used to pass everywhere, because the missing grammar threw a
 * NoSuchFileException that generation swallowed while still reporting success. Since generation now
 * throws (ADR-0011), the test fails honestly — which is the whole point. Re-enable it locally when
 * the sibling checkout is present.
 */
@Disabled("Depends on external paths (../model, /data/hivevm/hql/src); enable locally")
class H3QLTest {

    public static final File WORKING_DIR = new File("../h3vm").getAbsoluteFile();
    public static final File MAIN_DIR = new File(H3QLTest.WORKING_DIR, "criteria/src/main");

    public static final File PARSER_JJT = new File(H3QLTest.MAIN_DIR,
            "resources/org/hivevm/criteria/parser");
    public static final File PARSER_CPP = new File(H3QLTest.MAIN_DIR, "cpp/parser");
    public static final File PARSER_JAVA = new File(H3QLTest.MAIN_DIR, "java");
    public static final File PARSER_RUST = new File("/data/hivevm/hql/src");

    @Test
    void testCpp() {
        var builder = new ParserBuilder();
        builder.setLanguage(Language.CPP);
        builder.setTargetDir(H3QLTest.PARSER_CPP);
        builder.setParserFile(H3QLTest.PARSER_JJT, "OQL.jj");
        builder.build().parse();
    }

    @Test
    void testJava() {
        var builder = new ParserBuilder();
        builder.setLanguage(Language.JAVA);
        builder.setTargetDir(H3QLTest.PARSER_JAVA);
        builder.setParserFile(H3QLTest.PARSER_JJT, "OQL.jj");
        builder.build().parse();
    }

    @Test
    void testRust() {
        var builder = new ParserBuilder();
        builder.setLanguage(Language.RUST);
        builder.setTargetDir(H3QLTest.PARSER_RUST);
        builder.setParserFile(H3QLTest.PARSER_JJT, "OQL.jj");
        builder.build().parse();
    }
}
