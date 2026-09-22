// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.core;

/**
 * The Env interface provides methods for checking the existence of and retrieving values associated
 * with a given name in an environment.
 */
public interface Environment {

    /**
     * Checks whether a given name exists in the environment.
     */
    boolean has(String name);

    /**
     * Retrieves the value associated with a given name.
     */
    Object get(String name);
}
