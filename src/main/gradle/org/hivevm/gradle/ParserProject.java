// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.gradle;

import java.util.List;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Nested;
import org.hivevm.cc.Language;

public abstract class ParserProject {

  private final Project project;

  public Language       target;

  public String         jjFile;
  public String         jjtFile;

  public String         output;


  private final ListProperty<ParserTask> tasks;

  @Inject
  public ParserProject(Project project) {
    this.project = project;
    this.tasks = project.getObjects().listProperty(ParserTask.class).empty();
  }

  protected final Project getProject() {
    return this.project;
  }

  @Nested
  public final List<ParserTask> getTasks() {
    return this.tasks.get();
  }

  public final void task(Action<? super ParserTask> action) {
    this.tasks.add(newInstance(ParserTask.class, action));
  }

  private <C> C newInstance(Class<C> clazz, Action<? super C> action) {
    C instance = getProject().getObjects().newInstance(clazz);
    action.execute(instance);
    return instance;
  }
}
