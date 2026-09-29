// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.rust;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.analysis.TokenRef;

import java.util.Locale;
import java.util.regex.Pattern;
import org.hivevm.waggle.codegen.ParserSyntax;

import java.util.List;

/**
 * How Rust spells the parser (ADR-0021, ADR-0030). The parser is a struct, so every field and every
 * routine is reached through {@code self}; a production returns a {@code Result} and passes a
 * failure on with {@code ?}; and a lookahead routine ends in a bare expression rather than a
 * statement — which is what {@link #failure(String)} wraps.
 *
 * <p>Java leaves a decided lookahead by throwing LookaheadSuccess through every routine. A Rust
 * routine returns true with {@code jj_ls} set instead, so wherever a routine's failure is not
 * simply passed on — trying the next alternative, ending a loop — it checks that flag first.
 */
final class RustParserSyntax implements ParserSyntax {

    /** Leaves the routine when the lookahead is already decided. */
    private static final String UNWIND = "if self.jj_ls {\n    return true;\n}";

    @Override
    public String tokenName(String name) {
        return RustIdentifier.of(name);
    }

    @Override
    public void declareScanPos(LinePrinter printer) {
        printer.println("let mut xsp: usize;");
    }

    @Override
    public void saveScanPos(LinePrinter printer) {
        printer.println("xsp = self.jj_scanpos;");
    }

    @Override
    public String tokenRef(TokenRef token) {
        return (token.name() == null) ? Integer.toString(token.ordinal())
                : RustIdentifier.of(token.name());
    }

    /** A keyword label is a raw identifier here as everywhere else. */
    @Override
    public String scanTokenCall(TokenRef token) {
        return "jj_scan_token(" + ((token.name() == null) ? Integer.toString(token.ordinal())
                : RustIdentifier.of(token.name())) + ")";
    }

    /** How a call to another lookahead routine is written where it is tested. */
    private static String callRef(String call) {
        return "self." + call;
    }

    @Override
    public String failure(String value) {
        return "return " + value + ";";
    }

    @Override
    public void failIfScanToken(LinePrinter printer, String token, String failure) {
        printer.println("if self.jj_scan_token(" + token + ") {");
        printer.println("    " + failure);
        printer.println("}");
    }

    @Override
    public void failIfCall(LinePrinter printer, String call, String failure) {
        printer.println("if " + callRef(call) + " {");
        printer.println("    " + failure);
        printer.println("}");
    }

    @Override
    public void beginSemanticLookahead(LinePrinter printer) {
        printer.println("self.jj_looking_ahead = true;");
        printer.print("self.jj_sem_la = ");
    }

    @Override
    public void endSemanticLookahead(LinePrinter printer) {
        printer.println(";");
        printer.println("self.jj_looking_ahead = false;");
    }

    @Override
    public String semanticGuard() {
        return "!self.jj_sem_la || ";
    }

    @Override
    public void choiceAlternative(LinePrinter printer, String call, boolean semanticLookahead,
            boolean isLast, String failure) {
        printer.print("if ");
        if (semanticLookahead) {
            printer.print(semanticGuard());
        }
        printer.println(callRef(call) + " {");
        printer.indent();
        if (isLast) {
            printer.println(failure);
            printer.outdent();
            printer.println("}");
        } else {
            printer.println(RustParserSyntax.UNWIND);
            printer.println("self.jj_scanpos = xsp;");
        }
    }

    @Override
    public void scanLoop(LinePrinter printer, String call) {
        printer.println("loop {");
        printer.indent();
        saveScanPos(printer);
        printer.println("if " + callRef(call) + " {");
        printer.indent();
        printer.println(RustParserSyntax.UNWIND);
        printer.println("self.jj_scanpos = xsp;");
        printer.println("break;");
        printer.outdent();
        printer.println("}");
        printer.outdent();
        printer.println("}");
    }

    @Override
    public void optionalScan(LinePrinter printer, String call) {
        saveScanPos(printer);
        printer.println("if " + callRef(call) + " {");
        printer.indent();
        printer.println(RustParserSyntax.UNWIND);
        printer.println("self.jj_scanpos = xsp;");
        printer.outdent();
        printer.println("}");
    }

    /** A syntactic condition needs no parenthesis, and closeLookaheadCondition writes no ")". */
    @Override
    public String conditionOpen(boolean syntactic) {
        return syntactic ? "" : "(";
    }

    @Override
    public String defaultArm() {
        return "_ => {";
    }

    @Override
    public String recordChoice(int index) {
        return "self.jj_la1[" + index + "] = self.jj_gen;";
    }

    @Override
    public void closeLookaheadCondition(LinePrinter printer) {
        printer.print(" {");
    }

    /** A lookahead routine returns a Result: a lexical error met while scanning is the result. */
    @Override
    public String lookaheadCall(String routine, String amount) {
        return "self.jj_2" + routine + "(" + amount + ")?";
    }

    @Override
    public String tokenSwitch(boolean cacheTokens) {
        return "match self.jj_ntk()? {";
    }

    @Override
    public void caseLabels(LinePrinter printer, List<String> cases) {
        // No label: a choice conflict left this alternative no token (the grammar was warned
        // about it). Java writes a block no case reaches; Rust needs a pattern.
        printer.print(cases.isEmpty() ? "_ if false" : String.join(" | ", cases));
        printer.print(" => {");
        printer.indent();
    }

    /** A match arm does not fall through, so it needs no break. */
    @Override
    public void closeSwitchArm(LinePrinter printer) {
        printer.println();
        printer.outdent();
        printer.println("}");
    }

    @Override
    public void consumeToken(LinePrinter printer) {
        printer.print("self.jj_consume_token(");
    }

    /**
     * The token, or the field the grammar takes from it ({@code t = <ID>.image}). The parser lends
     * the token out of its buffer, so only what the grammar assigns is copied.
     */
    @Override
    public void consumeTokenEnd(boolean discarded, String rhsField, LinePrinter printer) {
        if (discarded) {
            printer.print(")?;");
        } else {
            printer.print((rhsField == null) ? ")?.clone();" : ")?." + rhsField + ".clone();");
        }
    }

    @Override
    public void noAlternativeMatched(LinePrinter printer) {
        printer.println();
        printer.print("return Err(self.jj_no_alternative());");
    }

    @Override
    public void callProduction(String name, LinePrinter printer) {
        printer.print("self." + RustIdentifier.of(RustParserSyntax.toSnakeCase(name)) + "(");
    }

    @Override
    public void callProductionEnd(LinePrinter printer) {
        printer.print(")?;");
    }

    @Override
    public void openRepetition(int labelIndex, LinePrinter printer) {
        printer.print("'label_" + labelIndex + ": loop {");
        printer.indent();
    }

    @Override
    public void breakRepetition(int labelIndex, LinePrinter printer, int offset) {
        if (offset == 1) {
            printer.print("\nbreak 'label_" + labelIndex + ";");
        }
    }

    private static final Pattern SNAKE_ACRONYM = Pattern.compile("([A-Z])(?=[A-Z])");
    private static final Pattern SNAKE_BOUNDARY = Pattern.compile("([a-z])([A-Z])");

    /** Rust names are snake_case; this is the one rule that turns a grammar name into one. */
    static String toSnakeCase(String name) {
        var withAcronyms = RustParserSyntax.SNAKE_ACRONYM.matcher(name).replaceAll("$1_");
        return RustParserSyntax.SNAKE_BOUNDARY.matcher(withAcronyms).replaceAll("$1_$2")
                .toLowerCase(Locale.ROOT);
    }
}
