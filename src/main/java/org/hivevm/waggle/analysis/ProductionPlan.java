// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.analysis;

import org.hivevm.waggle.model.CodeText;
import org.hivevm.waggle.model.NodeScope;
import org.hivevm.waggle.model.Parameter;

import java.util.List;

/**
 * A production as the parser writes it: its signature, which the generator copies from the
 * grammar, and its body.
 *
 * @param signature its name, result and parameters
 * @param body      what it runs
 */
public record ProductionPlan(Signature signature, PlanNode body) {

    /**
     * What a generator writes to declare the production.
     *
     * @param name       the production's name in the grammar
     * @param head       its first token, which the comments in front of the production hang on
     * @param returnType the result type as the grammar wrote it, or null for none
     * @param parameters the parameter list as the grammar wrote it, empty when it takes none
     * @param split      the same parameters one by one, for a target that reorders them
     * @param scope      the node scope the production opens, or {@code null}
     */
    public record Signature(String name, CodeText head, String returnType, CodeText parameters,
                            List<Parameter> split, NodeScope scope) {
    }
}
