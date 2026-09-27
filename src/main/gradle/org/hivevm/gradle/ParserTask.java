// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.gradle;

import java.util.List;

import org.hivevm.waggle.api.Language;

public class ParserTask {

    public String   name;
    public Language target;

    public String file;

    public String       output;
    public List<String> treeNodes;
}
