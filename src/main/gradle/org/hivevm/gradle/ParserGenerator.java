// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.gradle;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.options.OptionValues;

import org.hivevm.cc.Language;
import org.hivevm.cc.ParserBuilder;

/**
 * The {@link ParserGenerator} class.
 */
@CacheableTask
public abstract class ParserGenerator extends DefaultTask {

    @Inject
    public ParserGenerator() {
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
        ParserProject config = getProject().getExtensions().findByType(ParserProject.class);

        if (config == null) {
            getProject().getLogger().error("No configuration defined");
            return;
        }

        config.getTasks().forEach(s -> process(s, config));
    }

    /**
     * Get the {@link File} from pathname.
     *
     * @param pathname
     */
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

    /**
     * Processes the {@link ParserTask}
     *
     * @param task
     * @param config
     */
    protected void process(ParserTask task, ParserProject config) {
        Language target = (config.target == null) ? Language.JAVA : config.target;
        Language language = (task.target == null) ? target : task.target;

        var builder = ParserBuilder.of(language);
        builder.setTargetDir(getFile(task.output == null ? config.output : task.output));
        builder.setParserFile(getFile(task.file == null ? config.file : task.file));
        builder.setCustomNodes(task.treeNodes);
        builder.build().parse();
    }
}