// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator;

import org.hivevm.cc.Language;
import org.hivevm.cc.model.Action;
import org.hivevm.cc.model.Choice;
import org.hivevm.cc.model.Expansion;
import org.hivevm.cc.model.Lookahead;
import org.hivevm.cc.model.NodeDescriptor;
import org.hivevm.cc.model.NodeScope;
import org.hivevm.cc.model.NonTerminal;
import org.hivevm.cc.model.NormalProduction;
import org.hivevm.cc.model.OneOrMore;
import org.hivevm.cc.model.RExpression;
import org.hivevm.cc.model.Sequence;
import org.hivevm.cc.model.ZeroOrMore;
import org.hivevm.cc.model.ZeroOrOne;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.parser.Token;
import org.hivevm.cc.semantic.Semanticize;
import org.hivevm.source.Context;
import org.hivevm.source.LinePrinter;
import org.hivevm.source.Template;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class ParserGenerator extends CodeGenerator<ParserData> {

    protected static final String LOOKAHEAD_NEEDED = "LOOKAHEAD_NEEDED";
    protected static final String JJ2_INDEX = "JJ2_INDEX";
    protected static final String JJ2_OFFSET = "JJ2_OFFSET";
    protected static final String MASK_INDEX = "MASK_INDEX";
    protected static final String TOKEN_COUNT = "TOKEN_COUNT";
    protected static final String TOKEN_MASKS = "TOKEN_MASKS";
    protected static final String JJPARSER_USE_AST = "USE_AST";

    // Constants used in the following method "buildLookaheadChecker".
    protected enum LookaheadState {
        NOOPENSTM,
        OPENIF,
        OPENSWITCH
    }

    private int labelIndex;

    /**
     * Constructs an instance of {@link ParserGenerator}.
     */
    protected ParserGenerator(Language language) {
        super(language);
        this.labelIndex = 0;
    }

    @Override
    public final void generate(ParserData data) {
        var options = Template.newContext(data.options());

        options.set(ParserGenerator.JJPARSER_USE_AST, data.isGenerated());
        options.set(ParserGenerator.LOOKAHEAD_NEEDED, data.isLookAheadNeeded());
        options.set(ParserGenerator.JJ2_INDEX, data.jj2Index());
        options.set(ParserGenerator.MASK_INDEX, data.maskIndex());
        options.set(ParserGenerator.TOKEN_COUNT, data.getTokenCount());

        options.add(ParserGenerator.JJ2_OFFSET, data.jj2Index())
                .set("JJ2_OFFSET_INDEX", i -> i)
                .set("JJ2_OFFSET_VALUE", i -> getStringIndex(i + 1));
        options.add(ParserGenerator.TOKEN_MASKS, ((data.getTokenCount() - 1) / 32) + 1)
                .set("TOKEN_MASKS_INDEX", this::getStringIndex)
                .set("TOKEN_MASKS_VALUE", i -> data.maskVals().stream().map(v ->
                        "0x" + Integer.toHexString(v[i])).collect(Collectors.joining(", ")));

        options.set("DUMP_NORMALPRODUCTIONS", w ->
                data.getProductions().forEach(n -> generatePhase1(n, w, data)));
        options.set("DUMP_LOOKAHEADS", w ->
                data.getLoakaheads().forEach(e -> generate_phase2(e.getLaExpansion(), w, data)));
        options.set("DUMP_EXPANSIONS", w ->
                data.getExpansionCounts().forEach(e -> generate_phase3_routine(data, e.getKey(), e.getValue(), w)));

        generate(data, options);
    }

    protected abstract void generate(ParserData data, Context options);

    protected String getStringIndex(int i) {
        return "" + i;
    }

    protected final int nextLabelIndex() {
        return ++this.labelIndex;
    }

    protected final void printTrailingComments(LinePrinter printer, Token t) {
        if (t.next != null) {
            printLeadingComments(printer, t.next);
        }
    }

    private void generatePhase1(NormalProduction p, LinePrinter printer, ParserData data) {
        var default_return = generate_phase1_head(p, printer, data);
        printer.indent();

        // TreeNodes:
        var node_scope = p.getNodeScope();
        if (node_scope != null) {
            var nd = node_scope.getNodeDescriptor();
            var nodeClass = NodeDescriptor.getNodeClass(nd.getName(), data.options());

            printer.println(" // " + node_scope.getNodeDescriptorText());
            insertOpenNodeCode(node_scope, nodeClass, printer, data.options());
            printer.indent();
        }

        generate_phase1_body(p, printer, data, default_return, w ->
                generate_phase1_expansion(data, p.getExpansion(), node_scope, printer)
        );

        // TreeNodes:
        if (node_scope != null) {
            printer.outdent();
            insertCatchBlocks(node_scope, printer, data.options(), Collections.emptyList());
        }

        generate_phase1_tail(printer);
    }

    protected abstract String generate_phase1_head(NormalProduction p, LinePrinter printer, ParserData data);

    protected abstract void generate_phase1_body(NormalProduction p, LinePrinter printer, ParserData data, String returnType, Consumer<LinePrinter> consumer);

    protected void generate_phase1_tail(LinePrinter printer) {
        printer.println();
        printer.outdent();
        printer.println("}");
        printer.println();
    }

    protected final void generate_phase1_expansion(ParserData data, Expansion e, NodeScope ns, LinePrinter printer) {
        // TreeNodes:
        var node_scope = e.getNodeScope();
        if (node_scope != null) {
            var nd = node_scope.getNodeDescriptor();
            var nodeClass = NodeDescriptor.getNodeClass(nd.getName(), data.options());

            printer.println();
            printer.println("// " + node_scope.getNodeDescriptor().getDescriptor());
            insertOpenNodeCode(node_scope, nodeClass, printer, data.options());
            printer.indent();
        }
        var scope = node_scope != null ? node_scope : ns;

        switch (e) {
            case RExpression re -> {
                printer.println();
                if (!re.getLhsTokens().isEmpty()) {
                    setup_token(re.getLhsTokens().getFirst());
                    re.getLhsTokens().forEach(t -> printToken(t, scope, printer));
                    printTrailingComments(printer, re.getLhsTokens().getLast());
                    printer.print(" = ");
                }

                generate_phase1_regexp(printer);
                if (re.getLabel().isEmpty()) {
                    String label = data.getNameOfToken(re.getOrdinal());
                    printer.print(label != null ? label : "" + re.getOrdinal());
                } else {
                    printer.print(re.getLabel());
                }
                generate_phase1_regexp_end(re, printer);
            }
            case NonTerminal e_nrw -> {
                printer.println();
                if (!e_nrw.getLhsTokens().isEmpty()) {
                    setup_token((e_nrw.getLhsTokens().getFirst()));
                    e_nrw.getLhsTokens().forEach(t -> printToken(t, scope, printer));
                    printTrailingComments(printer, e_nrw.getLhsTokens().getLast());
                    printer.print(" = ");
                }
                generate_phase1_nonterminal(e_nrw, printer);
                if (!e_nrw.getArgumentTokens().isEmpty()) {
                    setup_token(e_nrw.getArgumentTokens().getFirst());
                    e_nrw.getArgumentTokens().forEach(t -> printToken(t, scope, printer));
                    printTrailingComments(printer, e_nrw.getArgumentTokens().getLast());
                }
                generate_phase1_nonterminal_end(printer);
            }
            case Action e_nrw -> {
                printer.println();
                if (!e_nrw.getActionTokens().isEmpty()) {
                    setup_token(e_nrw.getActionTokens().getFirst());
                    e_nrw.getActionTokens().forEach(t -> printToken(t, scope, printer));
                    printTrailingComments(printer, e_nrw.getActionTokens().getLast());
                }
            }
            case Choice e_nrw -> {
                // In previous line, the "throw" never throws an exception since the
                // evaluation of jj_consume_token(-1) causes ParseException to be
                // thrown first.
                Lookahead[] conds = data.getLoakaheads(e);
                print_lookahead_checker(printer, data, scope, conds, (p, i) -> {
                    if (i == e_nrw.getChoices().size()) {
                        generate_phase1_choice(printer);
                    } else {
                        generate_phase1_expansion(data, e_nrw.getChoices().get(i), scope, p);
                    }
                });
            }
            case Sequence e_nrw -> {
                // We skip the first element in the following iteration since it is the
                // Lookahead object.
                e_nrw.getUnits().forEach(exp -> generate_phase1_expansion(data, exp, scope, printer));
            }
            case ZeroOrOne e_nrw -> {
                Lookahead[] conds = data.getLoakaheads(e);
                print_lookahead_checker(printer, data, scope, conds,
                        (p, i) -> {
                            if (i == 0) {
                                generate_phase1_expansion(data, e_nrw.getExpansion(), scope, p);
//                    } else {
//                        p.print("\n;");
                            }
                        });
            }
            case OneOrMore e_nrw -> {
                printer.println();
                int labelIndex = nextLabelIndex();
                generate_phase1_more(labelIndex, printer);
                generate_phase1_expansion(data, e_nrw.getExpansion(), scope, printer);
                Lookahead[] conds = data.getLoakaheads(e);
                print_lookahead_checker(printer, data, scope, conds,
                        (p, i) -> print_phase1_more_end(labelIndex, p, i));
                printer.outdent();
                printer.println();
                printer.print("}");
                generate_phase1_more_end(labelIndex, printer);
            }
            case ZeroOrMore e_nrw -> {
                printer.println();
                int labelIndex = nextLabelIndex();
                generate_phase1_more(labelIndex, printer);
                Lookahead[] conds = data.getLoakaheads(e);
                print_lookahead_checker(printer, data, scope, conds,
                        (p, i) -> print_phase1_more_end(labelIndex, p, i));
                generate_phase1_expansion(data, e_nrw.getExpansion(), scope, printer);
                printer.outdent();
                printer.println();
                printer.print("}");
                generate_phase1_more_end(labelIndex, printer);
            }
            default -> {
            }
        }

        // TreeNodes:
        if (node_scope != null) {
            printer.outdent();
            insertCatchBlocks(node_scope, printer, data.options(), Collections.emptyList());
        }
    }

    protected abstract void generate_phase1_regexp(LinePrinter printer);

    protected abstract void generate_phase1_regexp_end(RExpression re, LinePrinter printer);

    protected abstract void generate_phase1_choice(LinePrinter printer);

    protected abstract void generate_phase1_nonterminal(NonTerminal non, LinePrinter printer);

    protected abstract void generate_phase1_nonterminal_end(LinePrinter printer);

    protected abstract void generate_phase1_more(int labelIndex, LinePrinter printer);

    protected abstract void print_phase1_more_end(int labelIndex, LinePrinter printer, int offset);

    protected void generate_phase1_more_end(int labelIndex, LinePrinter printer) {
    }

    /**
     * This method takes two parameters - an array of Lookahead's "conds", and an array of String's
     * "actions". "actions" contains exactly one element more than "conds". "actions" are Java
     * source code, and "conds" translate to conditions - so lets say "f(conds[i])" is true if the
     * lookahead required by "conds[i]" is indeed the case. This method returns a string
     * corresponding to the Java code for:
     * <p>
     * if (f(conds[0]) actions[0] else if (f(conds[1]) actions[1] . . . else
     * actions[action.length-1]
     * <p>
     * A particular action entry ("actions[i]") can be null, in which case, a noop is generated for
     * that action.
     */
    private void print_lookahead_checker(LinePrinter printer, ParserData data,
                                         NodeScope scope, Lookahead[] conds,
                                         BiConsumer<LinePrinter, Integer> actions) {
        // The state variables.
        var state = LookaheadState.NOOPENSTM;
        boolean[] casedValues = new boolean[data.getTokenCount()];
        Lookahead la = null;

        // Iterate over all the conditions.
        boolean jj2LA;
        int indentAmt = 0;
        int index = 0;
        while (index < conds.length) {
            la = conds[index];
            jj2LA = false;

            var offset = index;
            Consumer<LinePrinter> action = p -> actions.accept(p, offset);

            if ((la.getAmount() == 0) || Semanticize.emptyExpansionExists(la.getLaExpansion())) {
                // This handles the following cases:
                // . If syntactic lookahead is not wanted (and hence explicitly specified as 0).
                // . If it is possible for the lookahead expansion to recognize the empty
                // string - in which case the lookahead trivially passes.
                // . If the lookahead expansion has a JAVACODE production that it directly
                // expands to - in which case the lookahead trivially passes.
                if (la.getActionTokens().isEmpty()) {
                    // In addition, if there is no semantic lookahead, then the
                    // lookahead trivially succeeds. So break the main loop and
                    // treat this case as the default last action.
                    break;
                } else {
                    // This case is when there is only semantic lookahead
                    // (without any preceding syntactic lookahead). In this
                    // case, an "if" statement is generated.
                    switch (state) {
                        case NOOPENSTM:
                            indentAmt++;
                            break;
                        case OPENIF:
                            break;
                        case OPENSWITCH:
                            indentAmt += 2;
                    }

                    print_lookahead_amount0(printer, state, action, la, scope,
                            state == LookaheadState.OPENSWITCH && data.getErrorReporting() ? data.getIndex(la) : -1);

                    state = LookaheadState.OPENIF;
                }
            } else if ((la.getAmount() == 1) && la.getActionTokens().isEmpty()) {
                // Special optimal processing when the lookahead is exactly 1, and there
                // is no semantic lookahead.
                boolean[] firstSet = new boolean[data.getTokenCount()];
                for (int i = 0; i < data.getTokenCount(); i++) {
                    firstSet[i] = false;
                }

                // jj2LA is set to false at the beginning of the containing "if" statement.
                // It is checked immediately after the end of the same statement to determine
                // if lookaheads are to be performed using calls to the jj2 methods.
                jj2LA = data.genFirstSet(la.getLaExpansion(), firstSet, jj2LA);
                // genFirstSet may find that semantic attributes are appropriate for the next
                // token. In which case, it sets jj2LA to true.
                if (!jj2LA) {
                    // This case is if there is no applicable semantic lookahead and the lookahead
                    // is one (excluding the earlier cases such as JAVACODE, etc.).
                    switch (state) {
                        case OPENIF:
                            //$FALL-THROUGH$ Control flows through to next case.
                        case NOOPENSTM:
                            for (int i = 0; i < data.getTokenCount(); i++) {
                                casedValues[i] = false;
                            }
                            indentAmt++;
                            // Don't need to do anything if state is OPENSWITCH.
                        default:
                    }

                    var cases = new ArrayList<String>();
                    for (int i = 0; i < data.getTokenCount(); i++) {
                        if (firstSet[i] && !casedValues[i]) {
                            casedValues[i] = true;
                            String s = data.getNameOfToken(i);
                            cases.add((s == null) ? "" + i : s);
                        }
                    }

                    print_lookahead_amount1(printer, state, action, data.getCacheTokens(), cases);

                    state = LookaheadState.OPENSWITCH;
                }

            } else {
                // This is the case when lookahead is determined through calls to
                // jj2 methods. The other case is when lookahead is 1, but semantic
                // attributes need to be evaluated. Hence this crazy control structure.
                jj2LA = true;
            }

            if (jj2LA) {
                // In this case lookahead is determined by the jj2 methods.
                switch (state) {
                    case NOOPENSTM:
                        indentAmt++;
                        break;
                    case OPENIF:
                        break;
                    case OPENSWITCH:
                        indentAmt += 2;
                }

                print_lookahead(printer, state, action, la, scope,
                        state == LookaheadState.OPENSWITCH && data.getErrorReporting() ? data.getIndex(la) : -1);

                state = LookaheadState.OPENIF;
            }

            index++;
        }

        var offset = index;
        print_lookahead_tail(printer, state, p -> actions.accept(p, offset),
                state == LookaheadState.OPENSWITCH ? indentAmt + 1 : indentAmt,
                state == LookaheadState.OPENSWITCH && data.getErrorReporting() ? data.getIndex(la) : -1
        );
    }

    protected abstract void print_lookahead_amount0(LinePrinter printer, LookaheadState state, Consumer<LinePrinter> action, Lookahead la, NodeScope scope, int index);

    protected abstract void print_lookahead_amount1(LinePrinter printer, LookaheadState state, Consumer<LinePrinter> action
            , boolean cache_tokens, List<String> cases);

    protected abstract void print_lookahead(LinePrinter printer, LookaheadState state, Consumer<LinePrinter> action, Lookahead la, NodeScope scope, int index);

    protected abstract void print_lookahead_tail(LinePrinter printer, LookaheadState state, Consumer<LinePrinter> action, int indents, int index);

    protected abstract void generate_phase2(Expansion e, LinePrinter printer, ParserData data);

    protected abstract void generate_phase3_routine(ParserData data, Expansion e, int count, LinePrinter printer);

    public abstract void insertOpenNodeCode(NodeScope ns, String nodeClass, LinePrinter printer, Options options);

    public abstract void insertCloseNodeCode(NodeScope ns, LinePrinter printer, Options options, boolean isFinal);

    public abstract void insertCatchBlocks(NodeScope ns, LinePrinter printer, Options options, Collection<String> thrown_set);
}
