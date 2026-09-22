// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import org.hivevm.core.Environment;

/**
 * Everything this template engine needs from whoever is rendering: values by name, somewhere to put
 * the result, and the banner a generated file carries.
 *
 * <p>The engine used to take {@code org.hivevm.waggle.api.Options} instead — the whole option
 * surface of a parser generator, of which it used exactly these three things — and
 * {@code TemplateContext} reached the sink through an {@code instanceof Options} downcast. That made
 * {@code org.hivevm.source} depend on Waggle while 34 Waggle files depend on it, a cycle that no
 * module or separate artifact can contain (ADR-0023).
 *
 * <p>A caller extends this rather than the engine importing the caller.
 */
public interface RenderContext extends Environment {

    /**
     * Where the rendered source goes. A caller that did not choose one writes files (ADR-0018).
     */
    default OutputSink outputSink() {
        return new FileSink();
    }

    /**
     * The title a rendered file is stamped with. The engine does not know the name of the product
     * it is shipped with, so it asks (ADR-0023).
     */
    String renderTitle();
}
