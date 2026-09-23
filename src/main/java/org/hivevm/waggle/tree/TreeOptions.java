// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2005-2006, Kees Jan Koster. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/jjtree/JJTreeOptions.java

package org.hivevm.waggle.tree;

import org.hivevm.waggle.diag.Diagnostics;
import org.hivevm.waggle.api.Options;

import java.util.Set;

/**
 * The {@code NODE_*} and {@code VISITOR_*} settings of a grammar that builds a tree.
 *
 * <p>They used to sit in the one option set beside {@code LOOKAHEAD} and {@code JAVA_PACKAGE}, and
 * the {@code VISITOR_*} combinations were checked for every grammar — including one that declares no
 * node and will never see a visitor (ADR-0016). The option names in grammars are unchanged; this is
 * only where they are read and when they are checked.
 *
 * @param useAst          {@code USE_AST}: the grammar builds a tree at all (ADR-0028)
 * @param multi           {@code NODE_MULTI}: a distinct node class per rule
 * @param defaultVoid     {@code NODE_DEFAULT_VOID}: rules without a descriptor produce no node
 * @param scopeHook       {@code NODE_SCOPE_HOOK}: the parser calls open/close hooks
 * @param trackTokens     {@code TRACK_TOKENS}: nodes remember their first and last token
 * @param buildNodeFiles  {@code BUILD_NODE_FILES}: the node classes are written
 * @param nodeClass       {@code NODE_CLASS}: the base class of the generated nodes
 * @param nodeFactory     {@code NODE_FACTORY}: who constructs a node
 * @param customNodes     {@code NODE_CUSTOM}: nodes the grammar author supplies
 * @param visitor         {@code VISITOR}: a visitor interface is written
 * @param visitorDataType {@code VISITOR_DATA_TYPE}: the payload passed through {@code jjtAccept}
 * @param visitorReturn   {@code VISITOR_RETURN_TYPE}
 * @param visitorException {@code VISITOR_EXCEPTION}
 */
public record TreeOptions(boolean useAst, boolean multi, boolean defaultVoid, boolean scopeHook, boolean trackTokens,
                          boolean buildNodeFiles, String nodeClass, String nodeFactory,
                          Set<String> customNodes, boolean visitor, String visitorDataType,
                          String visitorReturn, String visitorException) {

    public static TreeOptions from(Options options) {
        return new TreeOptions(options.getUseAst(), options.getMulti(), options.getNodeDefaultVoid(),
                options.getNodeScopeHook(), options.getTrackTokens(), options.getBuildNodeFiles(),
                options.getNodeClass(), options.getNodeFactory(), options.getExcludeNodes(),
                options.getVisitor(), options.getVisitorDataType(), options.getVisitorReturnType(),
                options.getVisitorException());
    }

    /**
     * Reports the settings that will be ignored. Only a grammar that actually builds a tree is
     * asked: warning about {@code VISITOR_DATA_TYPE} in a grammar with no nodes tells the author
     * about a decision they never made.
     */
    public void validate(Diagnostics diagnostics) {
        if (this.visitor) {
            return;
        }
        if (!this.visitorDataType.isEmpty()) {
            diagnostics.warning("VISITOR_DATA_TYPE option will be ignored since VISITOR is false");
        }
        if (!this.visitorReturn.isEmpty() && !this.visitorReturn.equals("Object")) {
            diagnostics.warning("VISITOR_RETURN_TYPE option will be ignored since VISITOR is false");
        }
        if (!this.visitorException.isEmpty()) {
            diagnostics.warning("VISITOR_EXCEPTION option will be ignored since VISITOR is false");
        }
    }
}
