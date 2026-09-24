// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Versions sort by semantic version precedence. The order used to be reversed. */
class VersionTest {

    @Test
    void theLowerVersionSortsFirst() {
        var sorted = List.of("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta",
                "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0", "1.0.1", "1.2", "2.0.0");

        var shuffled = new ArrayList<>(sorted);
        Collections.reverse(shuffled);
        var versions = new ArrayList<>(shuffled.stream().map(Version::parse).toList());
        Collections.sort(versions);

        assertEquals(sorted, versions.stream().map(Version::toString).toList());
    }

    @Test
    void aMissingPatchIsZeroAndTheBuildIsIgnored() {
        assertEquals(0, Version.parse("1.2").compareTo(Version.parse("1.2.0")));
        assertEquals(0, Version.parse("1.2.0+build.1").compareTo(Version.parse("1.2.0+build.2")));
    }
}
