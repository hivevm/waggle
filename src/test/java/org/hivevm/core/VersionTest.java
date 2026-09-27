// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A version reads back as it was written, and formats to the width of a pattern. */
class VersionTest {

    @Test
    void aParsedVersionPrintsAsWritten() {
        for (String text : new String[] {"1.2", "1.0.13", "1.0.0-rc.1", "19.12-beta1+build.1.2"}) {
            assertEquals(text, Version.parse(text).toString());
        }
    }

    @Test
    void theFormatSetsTheWidthOfEachPart() {
        assertEquals("1.0", Version.parse("1.0.13").toString("0.0"));
        assertEquals("01.02.000", Version.parse("1.2").toString("00.00.000"));
        assertEquals("1.0.13-rc.1", Version.parse("1.0.13-rc.1+b.7").toString("0.0.0-0"));
    }
}
