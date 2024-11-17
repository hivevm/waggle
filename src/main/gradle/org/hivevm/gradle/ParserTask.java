// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.gradle;

import org.gradle.api.Project;
import org.hivevm.cc.Language;

import java.util.List;

import javax.inject.Inject;

public class ParserTask {

  private final Project project;


  public String       name;
  public Language     target;

  public String       jjFile;
  public String       jjtFile;

  public String       output;
  public List<String> treeNodes;


  @Inject
  public ParserTask(Project project) {
    this.project = project;
  }

  public final Project getProject() {
    return this.project;
  }
}
