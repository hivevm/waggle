// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import org.hivevm.waggle.api.Language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hivevm.source.LinePrinter;
import org.hivevm.waggle.codegen.ExpansionDecorator;
import org.hivevm.waggle.codegen.GeneratorProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.StringWriter;

/**
 * Tree code reaches the parser through one hook, and nothing else (ADR-0016).
 *
 * <p>It used to be woven into {@code ParserGenerator.generate_phase1_expansion}, behind three
 * abstract methods every back end had to implement whether or not it could emit a tree.
 */
class TreeEmissionTest {

    /** With no tree, the hook is what a back end without tree support also gets: nothing. */
    @Test
    void theEmptyDecoratorWritesNothing() {
        var writer = new StringWriter();
        var printer = LinePrinter.wrap(writer);

        ExpansionDecorator.NONE.beforeProduction(null, printer);
        ExpansionDecorator.NONE.beforeExpansion(null, printer);
        ExpansionDecorator.NONE.after(null, printer);
        printer.println();

        assertEquals(System.lineSeparator(), writer.toString(),
                "the no-op decorator must not emit anything of its own");
    }

    /**
     * Tree support is optional in the SPI, but every target this repository ships has it. If one
     * stops having it, that is a decision, and this test is where it is made visible.
     */
    @ParameterizedTest
    @EnumSource(Language.class)
    void everyShippedTargetSupportsTrees(Language language) {
        assertTrue(GeneratorProvider.generatorFor(language).treeSupport().isPresent(),
                language + " no longer declares tree support");
    }
}
