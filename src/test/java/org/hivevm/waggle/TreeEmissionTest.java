// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle;

import org.hivevm.waggle.api.Language;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hivevm.waggle.codegen.ExpansionDecorator;
import org.hivevm.waggle.codegen.GeneratorProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tree code reaches the parser through one hook, and nothing else (ADR-0016).
 *
 * <p>It used to be woven into {@code ParserGenerator.generate_phase1_expansion}, behind three
 * abstract methods every back end had to implement whether or not it could emit a tree.
 */
class TreeEmissionTest {

    /** With no tree, the hook is what a back end without tree support also gets: nothing. */
    @Test
    void theEmptyDecoratorWrapsNothing() {
        assertNull(ExpansionDecorator.NONE.open(null, true),
                "the no-op decorator must not open a scope of its own");
        assertNull(ExpansionDecorator.NONE.open(null, false),
                "the no-op decorator must not open a scope of its own");
        assertNull(ExpansionDecorator.NONE.close(null),
                "the no-op decorator must not close a scope of its own");
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
