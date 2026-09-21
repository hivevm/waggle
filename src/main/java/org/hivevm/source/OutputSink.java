// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.source;

import java.io.File;

/**
 * Where rendered source goes.
 *
 * <p>Rendering used to be writing a file: the engine resolved the target, created its directories
 * and opened a stream, so the rendered text never existed as a value and no back end could be
 * asked what it emits without a temporary directory (ADR-0018). One method is the whole interface —
 * generation writes files and never reads or deletes them.
 */
@FunctionalInterface
public interface OutputSink {

    void write(File file, String content);
}
