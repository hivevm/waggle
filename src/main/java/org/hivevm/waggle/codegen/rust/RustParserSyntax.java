// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.codegen.rust;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.codegen.ParserGenerator;
import org.hivevm.waggle.model.NonTerminal;

import java.util.Locale;
import java.util.regex.Pattern;
import org.hivevm.waggle.codegen.ParserSyntax;

import java.util.List;
import java.util.function.Consumer;

/**
 * How Rust spells a lookahead routine (ADR-0021). The parser is a struct, so every field and every
 * routine is reached through {@code self}, and a jj_3 routine ends in a bare expression rather than
 * a statement — which is what {@link #failure(String)} wraps.
 */
final class RustParserSyntax implements ParserSyntax {

    @Override
    public void declareScanPos(LinePrinter printer) {
        printer.println("    let mut xsp: Rc<RefCell<Token>>;");
    }

    @Override
    public void saveScanPos(LinePrinter printer) {
        printer.println("    xsp = self.jj_scanpos.as_mut().unwrap().clone();");
    }

    @Override
    public String tokenRef(String name, int ordinal) {
        return (name == null) ? Integer.toString(ordinal) : name;
    }

    @Override
    public String callRef(String call) {
        return "self." + call;
    }

    @Override
    public String failure(String value) {
        return "return " + value + ";";
    }

    @Override
    public void failIfScanToken(LinePrinter printer, String token, String failure) {
        printer.println("    if self.jj_scan_token(" + token + ") {");
        printer.println("        " + failure);
        printer.println("    }");
    }

    @Override
    public void failIfCall(LinePrinter printer, String call, String failure) {
        printer.println("    if " + callRef(call) + " {");
        printer.println("        " + failure);
        printer.println("    }");
    }

    @Override
    public void beginSemanticLookahead(LinePrinter printer) {
        printer.println("    self.jj_lookingAhead = true;");
        printer.print("    self.jj_semLA = ");
    }

    @Override
    public void endSemanticLookahead(LinePrinter printer) {
        printer.println(";");
        printer.println("    self.jj_lookingAhead = false;");
    }

    @Override
    public String semanticGuard() {
        return "!self.jj_semLA || ";
    }

    @Override
    public void choiceAlternative(LinePrinter printer, String call, boolean semanticLookahead,
            boolean isLast, String failure) {
        printer.print("    if ");
        if (semanticLookahead) {
            printer.print(semanticGuard());
        }
        printer.println(callRef(call) + " {");
        if (isLast) {
            printer.println("    " + failure);
            printer.println("}");
        } else {
            printer.println("    self.jj_scanpos = Some(xsp.clone());");
        }
    }

    @Override
    public void endChoice(LinePrinter printer, int count) {
        for (int i = 1; i < count; i++) {
            printer.println("}");
        }
    }

    @Override
    public void scanLoop(LinePrinter printer, String call) {
        printer.println("    loop {");
        printer.println("        xsp = self.jj_scanpos.as_mut().unwrap().clone();");
        printer.println("        if " + callRef(call) + " {");
        printer.println("            self.jj_scanpos = Some(xsp.clone());");
        printer.println("            break;");
        printer.println("        }");
        printer.println("    }");
    }

    @Override
    public void optionalScan(LinePrinter printer, String call) {
        saveScanPos(printer);
        printer.println("    if " + callRef(call) + " {");
        printer.println("        self.jj_scanpos = Some(xsp.clone());");
        printer.println("    }");
    }

    @Override
    public void openSemanticCondition(LinePrinter printer, ParserGenerator.LookaheadState state,
            int index) {
        openConditionArm(printer, state, index, "(");
    }

    @Override
    public void openLookaheadCondition(LinePrinter printer, ParserGenerator.LookaheadState state,
            int index) {
        // No parenthesis: a Rust condition needs none, and closeLookaheadCondition writes no ")".
        openConditionArm(printer, state, index, "");
    }

    private static void openConditionArm(LinePrinter printer,
            ParserGenerator.LookaheadState state, int index, String open) {
        switch (state) {
            case NOOPENSTM -> printer.print("\nif ");
            case OPENIF -> {
                printer.outdent();
                printer.print("\n} else if " + open);
            }
            case OPENSWITCH -> {
                printer.outdent();
                printer.print("\n_ => {");
                printer.indent();
                if (index >= 0) {
                    printer.print("\nself.jj_la1[" + index + "] = self.jj_gen;");
                }
                printer.print("\nif ");
            }
        }
    }

    @Override
    public void openFallback(LinePrinter printer, ParserGenerator.LookaheadState state, int index,
            Consumer<LinePrinter> action) {
        switch (state) {
            case NOOPENSTM -> action.accept(printer);
            case OPENIF -> {
                printer.outdent();
                printer.print("\n} else {");
                printer.indent();
                action.accept(printer);
            }
            case OPENSWITCH -> {
                printer.outdent();
                printer.print("\n_ => {");
                printer.indent();
                if (index >= 0) {
                    printer.print("\nself.jj_la1[" + index + "] = self.jj_gen;");
                }
                action.accept(printer);
            }
        }
    }

    @Override
    public void closeLookaheadCondition(LinePrinter printer) {
        printer.print(" {");
    }

    @Override
    public String lookaheadCall(String routine, String amount) {
        return "self.jj_2" + routine + "(" + amount + ")";
    }

    @Override
    public void openTokenSwitch(LinePrinter printer, ParserGenerator.LookaheadState state,
            boolean cacheTokens) {
        if (state == ParserGenerator.LookaheadState.OPENIF) {
            printer.outdent();
            printer.print("\n} else {");
            printer.indent();
        }
        if ((state == ParserGenerator.LookaheadState.OPENIF)
                || (state == ParserGenerator.LookaheadState.NOOPENSTM)) {
            printer.print("\nlet kind = if self.jj_nt.is_none() {");
            printer.print("\n    u32::MAX");
            printer.print("\n} else {");
            printer.print("\n    self.jj_nt.clone().unwrap().borrow().kind");
            printer.print("\n};");
            printer.print("\nmatch ");
            printer.print(cacheTokens ? "kind" : "(jj_ntk==-1)?jj_ntk_f():jj_ntk)");
            printer.print(" {");
            printer.indent();
        }
    }

    @Override
    public void caseLabels(LinePrinter printer, List<String> cases) {
        printer.outdent();
        printer.print("\n");
        printer.print(String.join(" | ", cases));
        printer.print(" =>");
        printer.indent();
        printer.print(" {");
    }

    @Override
    public void closeSwitchArm(LinePrinter printer) {
        printer.print("\n}");
    }

    @Override
    public void endBlocks(LinePrinter printer, int indents) {
        for (int i = 0; i < indents; i++) {
            printer.outdent();
            printer.print("\n}");
        }
    }

    @Override
    public void consumeToken(LinePrinter printer) {
        printer.print("try_catch = self.jj_consume_token(");
    }

    @Override
    public void noAlternativeMatched(LinePrinter printer) {
        printer.print("""
                
                    let _ = self.jj_consume_token(u32::MAX);
                    return Err(std::io::Error::new(
                        std::io::ErrorKind::Other,
                        "ParseException",
                    ));
                """);
    }

    @Override
    public void callProduction(NonTerminal non, LinePrinter printer) {
        printer.println("if try_catch.is_ok() {");
        printer.print("    try_catch = self." + RustParserSyntax.toSnakeCase(non.getName()) + "(");
    }

    @Override
    public void callProductionEnd(LinePrinter printer) {
        printer.println(");");
        printer.print("}");
    }

    @Override
    public void openRepetition(int labelIndex, LinePrinter printer) {
        printer.print("'label_" + labelIndex + ": loop {");
        printer.indent();
    }

    @Override
    public void breakRepetition(int labelIndex, LinePrinter printer, int offset) {
        if (offset == 0) {
            printer.print("\n;");
        } else {
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
