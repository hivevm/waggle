// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.gradle;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
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

    /**
     * The grammars this task reads, including the optional sibling lexical file.
     *
     * <p>Without this, Gradle saw no inputs at all: the task never went UP-TO-DATE, never came from
     * the build cache, and — worse — did not re-run when a grammar changed.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public List<File> getGrammarFiles() {
        var files = new ArrayList<File>();
        var config = getConfig();
        if (config == null) {
            return files;
        }

        for (ParserTask task : config.getTasks()) {
            File grammar = getFile(task.file == null ? config.file : task.file);
            if (grammar == null) {
                continue;
            }
            files.add(grammar);

            File lexer = ParserGenerator.lexerFile(grammar);
            if (lexer.exists()) {
                files.add(lexer);
            }
        }
        return files;
    }

    /**
     * The directories this task writes. Without an output, {@code @CacheableTask} is inert — Gradle
     * disables caching for a task that declares nothing to cache.
     */
    @OutputDirectories
    public List<File> getOutputDirectories() {
        var dirs = new ArrayList<File>();
        var config = getConfig();
        if (config == null) {
            return dirs;
        }

        for (ParserTask task : config.getTasks()) {
            File output = getFile(task.output == null ? config.output : task.output);
            if ((output != null) && !dirs.contains(output)) {
                dirs.add(output);
            }
        }
        return dirs;
    }

    @TaskAction
    public void process() {
        ParserProject config = getConfig();

        if (config == null) {
            // Logging and returning left the build green with nothing generated.
            throw new GradleException("No 'parserProject' configuration defined");
        }

        config.getTasks().forEach(s -> process(s, config));
    }

    private ParserProject getConfig() {
        return getProject().getExtensions().findByType(ParserProject.class);
    }

    /** The sibling "*.lex" of a grammar, which the generator appends when it exists. */
    private static File lexerFile(File grammar) {
        String name = grammar.getName();
        int dot = name.lastIndexOf('.');
        return new File(grammar.getParentFile(),
                (dot < 0 ? name : name.substring(0, dot)) + ".lex");
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

        var builder = new ParserBuilder();
        builder.setLanguage(language);
        builder.setTargetDir(getFile(task.output == null ? config.output : task.output));
        builder.setParserFile(getFile(task.file == null ? config.file : task.file));
        builder.setCustomNodes(task.treeNodes);
        builder.build().parse();
    }
}