// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.doc;

import org.hivevm.waggle.model.Expansion;
import org.hivevm.waggle.model.NonTerminal;
import org.hivevm.waggle.model.NormalProduction;
import org.hivevm.waggle.model.RExpression;
import org.hivevm.waggle.model.TokenProduction;

/**
 * A report sink for a grammar: JJDoc walks the model and calls this to write the document.
 *
 * <p>Named DocGenerator, not Generator, because {@code codegen.Generator} is the back-end SPI and
 * the two met in stack traces and imports under one name.
 *
 * @author timp
 * @since 11-Dec-2006
 */
interface DocGenerator {

    /**
     * Output string with entity substitution for brackets and ampersands.
     *
     * @param s the String to output
     */
    void text(String s);

    /**
     * Output String.
     *
     * @param s String to output
     */
    void print(String s);

    /**
     * Output document header.
     */
    void documentStart();

    /**
     * Output document footer.
     */
    void documentEnd();

    /**
     * Output Special Tokens.
     *
     * @param s tokens to output
     */
    void specialTokens(String s);

    void handleTokenProduction(TokenProduction tp);

    /**
     * Output start of non-terminal.
     */
    void nonterminalsStart();

    /**
     * Output end of non-terminal.
     */
    void nonterminalsEnd();

    /**
     * Output start of tokens.
     */
    void tokensStart();

    /**
     * Output end of tokens.
     */
    void tokensEnd();

    /**
     * Output start of a normal production.
     *
     * @param np the NormalProduction being output
     */
    void productionStart(NormalProduction np);

    /**
     * Output end of a normal production.
     *
     * @param np the NormalProduction being output
     */
    void productionEnd(NormalProduction np);

    /**
     * Output start of an Expansion.
     *
     * @param e     Expansion being output
     * @param first whether this is the first expansion
     */
    void expansionStart(Expansion e, boolean first);

    /**
     * Output end of Expansion.
     *
     * @param e     Expansion being output
     * @param first whether this is the first expansion
     */
    void expansionEnd(Expansion e, boolean first);

    /**
     * Output start of non-terminal.
     *
     * @param nt the NonTerminal being output
     */
    void nonTerminalStart(NonTerminal nt);

    /**
     * Output end of non-terminal.
     *
     * @param nt the NonTerminal being output
     */
    void nonTerminalEnd(NonTerminal nt);

    /**
     * Output start of regular expression.
     *
     * @param re the RegularExpression being output
     */
    void reStart(RExpression re);

    /**
     * Output end of regular expression.
     *
     * @param re the RegularExpression being output
     */
    void reEnd(RExpression re);
}
