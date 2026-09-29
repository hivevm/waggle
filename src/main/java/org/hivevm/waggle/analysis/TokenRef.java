// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.analysis;

/**
 * A token as the parser refers to it: by its name when it has one, by its ordinal otherwise. Which
 * name a place uses — the grammar's label, or the name of the token kind — is decided where the
 * reference is planned; a target only spells it (ADR-0029).
 *
 * @param name    the name, or {@code null} when the reference is by ordinal
 * @param ordinal the token kind
 */
public record TokenRef(String name, int ordinal) {
}
