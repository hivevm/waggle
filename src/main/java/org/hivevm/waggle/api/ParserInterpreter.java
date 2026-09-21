// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.api;

import org.hivevm.waggle.analysis.Semanticize;
import org.hivevm.waggle.diag.Diagnostics;
import org.hivevm.waggle.grammar.JavaCCData;
import org.hivevm.waggle.grammar.JavaCCParserDefault;
import org.hivevm.waggle.grammar.StringProvider;
import org.hivevm.waggle.lexer.LexerBuilder;
import org.hivevm.waggle.lexer.LexerInterpreter;

import java.util.List;

/**
 * Runs a grammar over input without generating, compiling or loading anything.
 *
 * <p>The pipeline is the same one a generation runs — parse the grammar, analyse it, build the
 * automaton — and then stops before the back end: {@link LexerInterpreter} simulates the automaton
 * instead of rendering a token manager from it. That is what makes trying a grammar out a matter of
 * calling a method, rather than generating sources, compiling them and loading the result.
 *
 * <p>JavaCC had the same idea as an "interpreted token manager" and had to invent a data structure
 * for it, because its lexer stage produced code and nothing else. Here the automaton is the output
 * of stage 4 (ADR-0012), so the interpreter reads the same model the generators do.
 */
public final class ParserInterpreter {

    private final Diagnostics diagnostics;

    public ParserInterpreter() {
        this(new Diagnostics());
    }

    public ParserInterpreter(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    /** What this run had to say about the grammar. */
    public Diagnostics diagnostics() {
        return this.diagnostics;
    }

    /**
     * Tokenises {@code input} with the lexical specification of {@code grammar}.
     *
     * @param grammar the grammar source, including its token definitions
     * @param input   the text to read
     * @throws GenerationException when the grammar cannot be read
     */
    public List<LexerInterpreter.Match> tokenize(String grammar, String input) {
        return new LexerInterpreter(buildLexer(grammar)).tokenize(input);
    }

    /**
     * Builds the automaton of {@code grammar}.
     *
     * <p>NO_DFA is on: the string-literal DFA and the NFA are two renderings of one specification,
     * and with it on the whole specification lands in the NFA, which is the one the interpreter
     * walks. It changes what the automaton is built as, never what it accepts.
     */
    private org.hivevm.waggle.lexer.LexerData buildLexer(String grammar) {
        var options = new WaggleOptions();
        options.set(Waggle.JJPARSER_NO_DFA, Boolean.TRUE);

        var context = new GenerationContext(options, this.diagnostics);
        var data = new JavaCCData(context);
        try {
            var parser = new JavaCCParserDefault(new StringProvider(grammar), options);
            parser.initialize(data);
            parser.javacc_input();
            Semanticize.semanticize(data, options);
        } catch (Exception e) {
            throw new GenerationException("Failed to read the grammar: detected "
                    + this.diagnostics.errorCount() + " error(s) and "
                    + this.diagnostics.warningCount() + " warning(s)", e);
        }

        if (this.diagnostics.hasError()) {
            throw new GenerationException("Failed to read the grammar: detected "
                    + this.diagnostics.errorCount() + " error(s)");
        }
        return new LexerBuilder().build(data);
    }
}
