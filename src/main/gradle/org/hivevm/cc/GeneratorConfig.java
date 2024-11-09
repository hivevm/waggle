// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Nested;

import java.util.List;

import javax.inject.Inject;

public abstract class GeneratorConfig {

  private final Project project;

  public Language       target;


  private final ListProperty<GeneratorStep> steps;

  @Inject
  public GeneratorConfig(Project project) {
    this.project = project;
    this.steps = project.getObjects().listProperty(GeneratorStep.class).empty();
  }

  protected final Project getProject() {
    return this.project;
  }

  @Nested
  public final List<GeneratorStep> getSteps() {
    return this.steps.get();
  }

  public final void step(Action<? super GeneratorStep> action) {
    this.steps.add(newInstance(GeneratorStep.class, action));
  }

  private <C> C newInstance(Class<C> clazz, Action<? super C> action) {
    C instance = getProject().getObjects().newInstance(clazz);
    action.execute(instance);
    return instance;
  }
}
