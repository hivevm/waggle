// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/Options.java

package org.hivevm.waggle.api;



import org.hivevm.source.FileSink;
import org.hivevm.source.OutputSink;
import org.hivevm.waggle.diag.Diagnostics;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The resolved settings of one generation: the defaults, overridden by what the caller asked for and
 * by the grammar's own {@code options { … }} block.
 */
public class WaggleOptions implements Options {

    private static final String OUTPUT_LANGUAGE_CPP = "cpp";
    private static final String OUTPUT_LANGUAGE_JAVA = "java";
    private static final String OUTPUT_LANGUAGE_RUST = "rust";

    private static final Set<OptionInfo> userOptions;

    static {
        TreeSet<OptionInfo> temp = new TreeSet<>();

        temp.add(new OptionInfo(Waggle.LOOKAHEAD, 1));

        temp.add(new OptionInfo(Waggle.CHOICE_AMBIGUITY_CHECK, 2));
        temp.add(new OptionInfo(Waggle.OTHER_AMBIGUITY_CHECK, 1));
        temp.add(new OptionInfo(Waggle.NO_DFA, Boolean.FALSE));
        temp.add(new OptionInfo(Waggle.DEBUG_PARSER, Boolean.FALSE));

        temp.add(new OptionInfo(Waggle.DEBUG_LOOKAHEAD, Boolean.FALSE));
        temp.add(new OptionInfo(Waggle.DEBUG_TOKEN_MANAGER, Boolean.FALSE));
        temp.add(new OptionInfo(Waggle.ERROR_REPORTING, Boolean.TRUE));

        temp.add(new OptionInfo(Waggle.IGNORE_CASE, Boolean.FALSE));
        temp.add(new OptionInfo(Waggle.SANITY_CHECK, Boolean.TRUE));

        temp.add(new OptionInfo(Waggle.FORCE_LA_CHECK, Boolean.FALSE));
        temp.add(new OptionInfo(Waggle.CACHE_TOKENS, Boolean.FALSE));
        temp.add(new OptionInfo(Waggle.KEEP_LINE_COLUMN, Boolean.TRUE));

        temp.add(new OptionInfo(Waggle.OUTPUT_DIRECTORY, "."));
        temp.add(new OptionInfo(Waggle.CODE_GENERATOR, WaggleOptions.OUTPUT_LANGUAGE_JAVA));
        temp.add(new OptionInfo(Waggle.DEPTH_LIMIT, 0));

        temp.add(new OptionInfo(Waggle.BASE_PARSER, ""));
        temp.add(new OptionInfo(Waggle.BASE_LEXER, ""));

        temp.add(new OptionInfo(Waggle.JAVA_PACKAGE, ""));
        temp.add(new OptionInfo(Waggle.JAVA_IMPORTS, ""));

        temp.add(new OptionInfo(Waggle.RUST_MODULE, ""));

        temp.add(new OptionInfo(Waggle.CPP_NAMESPACE, ""));
        temp.add(new OptionInfo(Waggle.CPP_STACK_LIMIT, ""));

        userOptions = Collections.unmodifiableSet(temp);
    }

    /**
     * A mapping of option names (Strings) to values (Integer, Boolean, String). This table is
     * initialized by the main program. Its contents defines the set of legal options. Its initial
     * values define the default option values, and the option types can be determined from these
     * values too.
     */
    private final Map<String, Object> optionValues;

    /**
     * Keep track of what options were set as a command line argument. We use this to see if the
     * options set from the command line and the ones set in the input files clash in any way.
     */
    private final Set<String> cmdLineSetting;

    /**
     * Keep track of what options were set from the grammar file. We use this to see if the options
     * set from the command line and the ones set in the input files clash in any way.
     */
    private final Set<String> inputFileSetting;

    private OutputSink outputSink = new FileSink();

    // Limit subclassing to derived classes.
    public WaggleOptions() {
        this.optionValues = new HashMap<>();
        this.cmdLineSetting = new HashSet<>();
        this.inputFileSetting = new HashSet<>();

        for (OptionInfo info : WaggleOptions.userOptions) {
            set(info.getName(), info.getDefault());
        }

        // Got from TreeOptions
        set(Waggle.PARSER_NAME, "");
        set(Waggle.USE_AST, Boolean.TRUE);
        set(Waggle.NODE_MULTI, Boolean.FALSE);
        set(Waggle.NODE_DEFAULT_VOID, Boolean.FALSE);
        set(Waggle.NODE_SCOPE_HOOK, Boolean.FALSE);
        set(Waggle.BUILD_NODE_FILES, Boolean.TRUE);
        set(Waggle.VISITOR, Boolean.FALSE);
        set(Waggle.TRACK_TOKENS, Boolean.FALSE);
        set(Waggle.NODE_EXTENDS, "");
        set(Waggle.NODE_CLASS, "");
        set(Waggle.NODE_FACTORY, "");
        set(Waggle.NODE_CUSTOM, "");
        set(Waggle.OUTPUT_FILE, "");
        set(Waggle.VISITOR_DATA_TYPE, "");
        set(Waggle.VISITOR_RETURN_TYPE, "Object");
        set(Waggle.VISITOR_EXCEPTION, "");
    }

    /**
     * Takes over what the caller asked for, as the values they are.
     *
     * <p>Each of these counts as a caller setting, so the grammar's own {@code options { … }} block
     * is told when it disagrees — exactly as when they arrived as {@code -CODE_GENERATOR=…} strings.
     */
    @Override
    public final OutputSink outputSink() {
        return this.outputSink;
    }

    public final void setOutputSink(OutputSink sink) {
        this.outputSink = sink;
    }

    public final void apply(GenerationRequest request) {
        setFromCaller(Waggle.CODE_GENERATOR, request.language().name());
        setFromCaller(Waggle.OUTPUT_DIRECTORY,
                request.outputDirectory().getAbsolutePath());
        if (!request.customNodes().isEmpty()) {
            setFromCaller(Waggle.NODE_CUSTOM, String.join(",", request.customNodes()));
        }
    }

    private void setFromCaller(String name, Object value) {
        set(name, value);
        this.cmdLineSetting.add(name);
    }

    /**
     * Determine if a given command line argument might be an option flag. Command line options
     * start with a dash&nbsp;(-).
     *
     * @param opt The command line argument to examine.
     * @return True when the argument looks like an option flag.
     */
    public final boolean isOption(final String opt) {
        return (opt != null) && (opt.length() > 1) && (opt.charAt(0) == '-');
    }

    private static String typeName(Class<?> type) {
        if (type == Boolean.class) {
            return "true or false";
        }
        return (type == Integer.class) ? "a number" : "a string";
    }

    public final void setOption(Diagnostics diagnostics, Object nameloc, Object valueloc,
            String name, Object value) {
        String nameUpperCase = name.toUpperCase();
        if (!this.optionValues.containsKey(nameUpperCase)) {
            diagnostics.warning(nameloc,
                    "Bad option name \"" + name + "\".  Option setting will be ignored.");
            return;
        }

        if (name.equalsIgnoreCase(Waggle.NODE_FACTORY) && (value.getClass()
                == Boolean.class)) {
            value = ((Boolean) value) ? "*" : "";
        }

        final Object existingValue = this.optionValues.get(nameUpperCase);
        if (existingValue != null) {
            // A list-valued option is judged by its first element. Casting "value" here instead of
            // the unwrapped element threw a ClassCastException for exactly that case; the flag was
            // also named for the opposite of what it tests.
            Object element = (value instanceof List<?> list) ? list.getFirst() : value;

            // The default fixes the option's type. Without this check a mistyped value was stored
            // as written and failed much later, as a ClassCastException without a grammar position.
            // A list is written as one comma-separated string and split by set().
            var expected = (existingValue instanceof List<?>) ? String.class : existingValue.getClass();
            if (!(value instanceof List<?>) && (value.getClass() != expected)) {
                diagnostics.warning(valueloc,
                        "Bad option value \"" + value + "\" for \"" + name + "\": expected "
                                + WaggleOptions.typeName(expected)
                                + ".  Option setting will be ignored.");
                return;
            }

            if ((element instanceof Integer number) && (number <= 0)) {
                diagnostics.warning(valueloc,
                        "Bad option value \"" + value + "\" for \"" + name
                                + "\".  Option setting will be ignored.");
                return;
            }

            if (this.inputFileSetting.contains(nameUpperCase)) {
                diagnostics.warning(nameloc,
                        "Duplicate option setting for \"" + name + "\" will be ignored.");
                return;
            }

            if (this.cmdLineSetting.contains(nameUpperCase)) {
                if (!existingValue.equals(value)) {
                    diagnostics.warning(nameloc,
                            "Command line setting of \"" + name + "\" modifies option value in file.");
                }
                return;
            }
        }

        set(nameUpperCase, value);
        this.inputFileSetting.add(nameUpperCase);
    }

    /**
     * Process a single command-line option. The option is parsed and stored in the optionValues
     * map.
     *
     * <p>The complaints below used to go to {@code System.out} while {@link #setOption}, the path
     * taken for the grammar's own {@code options} block, reported the same kinds of complaint to
     * {@link Diagnostics}. One option set now has one channel (ADR-0015).
     */
    public final void setCmdLineOption(Diagnostics diagnostics, String arg) {
        final String s;

        if (arg.charAt(0) == '-') {
            s = arg.substring(1);
        } else {
            s = arg;
        }

        String name;
        Object Val;

        // Look for the first ":" or "=", which will separate the option name
        // from its value (if any).
        final int index1 = s.indexOf('=');
        final int index2 = s.indexOf(':');
        final int index;

        if (index1 < 0) {
            index = index2;
        } else if (index2 < 0) {
            index = index1;
        } else
            index = Math.min(index1, index2);

        if (index < 0) {
            name = s.toUpperCase();
            if (this.optionValues.containsKey(name)) {
                Val = Boolean.TRUE;
            } else if ((name.length() > 2) && (name.charAt(0) == 'N') && (name.charAt(1) == 'O')) {
                Val = Boolean.FALSE;
                name = name.substring(2);
            } else {
                diagnostics.warning("Bad option \"" + arg + "\" will be ignored.");
                return;
            }
        } else {
            name = s.substring(0, index).toUpperCase();
            if (s.substring(index + 1).equalsIgnoreCase("TRUE")) {
                Val = Boolean.TRUE;
            } else if (s.substring(index + 1).equalsIgnoreCase("FALSE")) {
                Val = Boolean.FALSE;
            } else {
                try {
                    int i = Integer.parseInt(s.substring(index + 1));
                    if (i <= 0) {
                        diagnostics.warning(
                                "Bad option value in \"" + arg + "\" will be ignored.");
                        return;
                    }
                    Val = i;
                } catch (NumberFormatException e) {
                    Val = s.substring(index + 1);
                    // i.e., there is space for two '"'s in value
                    if ((s.length() > (index + 2)) && ((s.charAt(index + 1) == '"') && (
                            s.charAt(s.length() - 1) == '"'))) {
                        // remove the two '"'s.
                        Val = s.substring(index + 2, s.length() - 1);
                    }
                }
            }
        }

        if (!this.optionValues.containsKey(name)) {
            diagnostics.warning("Bad option \"" + arg + "\" will be ignored.");
            return;
        }
        Object valOrig = this.optionValues.get(name);
        if (Val.getClass() != valOrig.getClass()) {
            diagnostics.warning("Bad option value in \"" + arg + "\" will be ignored.");
            return;
        }
        if (this.cmdLineSetting.contains(name)) {
            diagnostics.warning(
                    "Duplicate option setting \"" + arg + "\" will be ignored.");
            return;
        }

        set(name, Val);
        this.cmdLineSetting.add(name);
    }

    /**
     * @return the output language. default java
     */
    public final Language getOutputLanguage() {
        String language = (String) this.optionValues.get(Waggle.CODE_GENERATOR);
        if (language.equalsIgnoreCase(WaggleOptions.OUTPUT_LANGUAGE_CPP))
            return Language.CPP;
        if (language.equalsIgnoreCase(WaggleOptions.OUTPUT_LANGUAGE_RUST))
            return Language.RUST;
        return Language.JAVA; // default (also covers OUTPUT_LANGUAGE_JAVA)
    }

    private record OptionInfo(String _name, Object _default) implements Comparable<OptionInfo> {

        public String getName() {
            return this._name;
        }

        public Object getDefault() {
            return this._default;
        }

        // equals/hashCode come from the record (component-wise, matching the former hand-written
        // equals); only the name-based ordering needs an explicit implementation.
        @Override
        public int compareTo(OptionInfo o) {
            return this._name.compareTo(o._name);
        }
    }

    @Override
    public boolean has(String name) {
        return this.optionValues.containsKey(name);
    }

    @Override
    public Object get(String name) {
        return this.optionValues.get(name);
    }

    public void setParser(String value) {
        set(Waggle.PARSER_NAME, value);
    }

    @Override
    public void set(String name, Object value) {
        if (Waggle.PARSER_NAME.equalsIgnoreCase(name) && (value instanceof String text)) {
            set(Waggle.CPP_DEFINE, text.toUpperCase());
        } else if (Waggle.JAVA_IMPORTS.equalsIgnoreCase(name)) {
            value = ((value instanceof String text) && !text.isEmpty())
                    ? Arrays.asList(text.split(","))
                    : Collections.emptyList();
        }
        this.optionValues.put(name, value);
    }

    /**
     * Return the file encoding; this will return the file.encoding system property if no value was
     * explicitly set
     */
    public static String getFileEncoding() {
        return System.getProperties().getProperty("file.encoding");
    }
}
