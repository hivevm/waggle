// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.options.OptionValues;

/**
 * The {@link GeneratorTask} class.
 */
public abstract class GeneratorTask extends DefaultTask {

  @Inject
  public GeneratorTask() {
    setGroup("HiveVM");
    setDescription("Generates a parser");
  }

  @Input
  @Optional
  @Option(option = "target", description = "Sets the target language.")
  public abstract Property<Language> getTarget();

  @OptionValues("target")
  public List<Language> getAvailableOutputTypes() {
    return new ArrayList<>(Arrays.asList(Language.values()));
  }

  @TaskAction
  public void process() {
    GeneratorConfig config = getProject().getExtensions().findByType(GeneratorConfig.class);

    if (config == null) {
      getProject().getLogger().error("No configuration defined");
      return;
    }

    Language defaultTarget = (config.target == null) ? Language.JAVA : config.target;

    config.getSteps().forEach(s -> process(s, defaultTarget));
  }

  protected File getFile(String pathname) {
    if (pathname == null) {
      return null;
    }

    File targetDir = new File(pathname);
    if (targetDir.isAbsolute()) {
      return targetDir;
    }

    File projectDir = getProject().getProjectDir();
    return new File(projectDir, pathname);
  }


  protected void process(GeneratorStep step, Language target) {
    Language language = (step.target == null) ? target : step.target;
    HiveCCBuilder builder = HiveCCBuilder.of(language);
    builder.setTargetDir(getFile(step.directory));
    builder.setJJTreeFile(getFile(step.jjtFile));
    builder.setJJFile(getFile(step.jjFile));
    builder.setExcludes(step.excludes);
    builder.build();
  }
}