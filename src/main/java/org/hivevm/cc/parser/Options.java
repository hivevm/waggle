// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.parser;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.Language;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

/**
 * A class with static state that stores all option information.
 */
public class Options {

  // Limit subclassing to derived classes.
  public Options() {
    Options.optionValues = new HashMap<>();
    this.cmdLineSetting = new HashSet<>();
    this.inputFileSetting = new HashSet<>();

    for (OptionInfo info : Options.userOptions) {
      Options.optionValues.put(info.getName(), info.getDefault());
    }
  }

  private static final String OUTPUT_LANGUAGE__CPP  = "cpp";
  private static final String OUTPUT_LANGUAGE__JAVA = "java";


  private static final Set<OptionInfo> userOptions;


  static {
    TreeSet<OptionInfo> temp = new TreeSet<>();

    temp.add(new OptionInfo(HiveCC.JJPARSER_LEGACY, Boolean.FALSE));
    temp.add(new OptionInfo(HiveCC.JJPARSER_LOOKAHEAD, Integer.valueOf(1)));

    temp.add(new OptionInfo(HiveCC.JJPARSER_CHOICE_AMBIGUITY_CHECK, Integer.valueOf(2)));
    temp.add(new OptionInfo(HiveCC.JJPARSER_OTHER_AMBIGUITY_CHECK, Integer.valueOf(1)));
    temp.add(new OptionInfo(HiveCC.JJPARSER_NO_DFA, Boolean.FALSE));
    temp.add(new OptionInfo(HiveCC.JJPARSER_DEBUG_PARSER, Boolean.FALSE));

    temp.add(new OptionInfo(HiveCC.JJPARSER_DEBUG_LOOKAHEAD, Boolean.FALSE));
    temp.add(new OptionInfo(HiveCC.JJPARSER_DEBUG_TOKEN_MANAGER, Boolean.FALSE));
    temp.add(new OptionInfo(HiveCC.JJPARSER_ERROR_REPORTING, Boolean.TRUE));

    temp.add(new OptionInfo(HiveCC.JJPARSER_IGNORE_CASE, Boolean.FALSE));
    temp.add(new OptionInfo(HiveCC.JJPARSER_SANITY_CHECK, Boolean.TRUE));

    temp.add(new OptionInfo(HiveCC.JJPARSER_FORCE_LA_CHECK, Boolean.FALSE));
    temp.add(new OptionInfo(HiveCC.JJPARSER_CACHE_TOKENS, Boolean.FALSE));
    temp.add(new OptionInfo(HiveCC.JJPARSER_KEEP_LINE_COLUMN, Boolean.TRUE));

    temp.add(new OptionInfo(HiveCC.JJPARSER_OUTPUT_DIRECTORY, "."));
    temp.add(new OptionInfo(HiveCC.JJPARSER_CODEGENERATOR, Options.OUTPUT_LANGUAGE__JAVA));
    temp.add(new OptionInfo(HiveCC.JJPARSER_DEPTH_LIMIT, Integer.valueOf(0)));

    temp.add(new OptionInfo(HiveCC.JJPARSER_JAVA_PACKAGE, ""));
    temp.add(new OptionInfo(HiveCC.JJPARSER_JAVA_IMPORTS, ""));
    temp.add(new OptionInfo(HiveCC.JJPARSER_JAVA_EXTENDS, ""));
    temp.add(new OptionInfo(HiveCC.JJPARSER_JAVA_LEXER, ""));

    temp.add(new OptionInfo(HiveCC.JJPARSER_CPP_NAMESPACE, ""));
    temp.add(new OptionInfo(HiveCC.JJPARSER_CPP_STOP_ON_FIRST_ERROR, Boolean.FALSE));
    temp.add(new OptionInfo(HiveCC.JJPARSER_CPP_STACK_LIMIT, ""));

    userOptions = Collections.unmodifiableSet(temp);
  }

  /**
   * A mapping of option names (Strings) to values (Integer, Boolean, String). This table is
   * initialized by the main program. Its contents defines the set of legal options. Its initial
   * values define the default option values, and the option types can be determined from these
   * values too.
   */
  protected static Map<String, Object> optionValues = null;

  /**
   * Convenience method to retrieve integer options.
   */
  private int intValue(final String option) {
    return ((Integer) Options.optionValues.get(option));
  }

  /**
   * Convenience method to retrieve boolean options.
   */
  public final boolean booleanValue(final String option) {
    return ((Boolean) Options.optionValues.get(option));
  }

  /**
   * Convenience method to retrieve string options.
   */
  public final String stringValue(final String option) {
    return (String) Options.optionValues.get(option);
  }


  public final Map<String, Object> getOptions() {
    return new HashMap<>(Options.optionValues);
  }

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


  /**
   * Determine if a given command line argument might be an option flag. Command line options start
   * with a dash&nbsp;(-).
   *
   * @param opt The command line argument to examine.
   * @return True when the argument looks like an option flag.
   */
  public final boolean isOption(final String opt) {
    return (opt != null) && (opt.length() > 1) && (opt.charAt(0) == '-');
  }

  /**
   * Help function to handle cases where the meaning of an option has changed over time. If the user
   * has supplied an option in the old format, it will be converted to the new format.
   *
   * @param name The name of the option being checked.
   * @param value The option's value.
   * @return The upgraded value.
   */
  private Object upgradeValue(final String name, Object value) {
    if (name.equalsIgnoreCase(HiveCC.JJTREE_NODE_FACTORY) && (value.getClass() == Boolean.class)) {
      if (((Boolean) value)) {
        value = "*";
      } else {
        value = "";
      }
    }

    return value;
  }

  public final void setInputOption(Object nameloc, Object valueloc, String name, Object value) {
    String nameUpperCase = name.toUpperCase();
    if (!Options.optionValues.containsKey(nameUpperCase)) {
      JavaCCErrors.warning(nameloc, "Bad option name \"" + name + "\".  Option setting will be ignored.");
      return;
    }
    final Object existingValue = Options.optionValues.get(nameUpperCase);

    value = upgradeValue(name, value);

    if (existingValue != null) {

      Object object = null;
      if (value instanceof List) {
        object = ((List<?>) value).get(0);
      } else {
        object = value;
      }
      boolean isValidInteger = ((object instanceof Integer) && (((Integer) value).intValue() <= 0));
      if ((existingValue.getClass() != object.getClass()) || (isValidInteger)) {
        JavaCCErrors.warning(valueloc,
            "Bad option value \"" + value + "\" for \"" + name + "\".  Option setting will be ignored.");
        return;
      }

      if (this.inputFileSetting.contains(nameUpperCase)) {
        JavaCCErrors.warning(nameloc, "Duplicate option setting for \"" + name + "\" will be ignored.");
        return;
      }

      if (this.cmdLineSetting.contains(nameUpperCase)) {
        if (!existingValue.equals(value)) {
          JavaCCErrors.warning(nameloc, "Command line setting of \"" + name + "\" modifies option value in file.");
        }
        return;
      }
    }

    Options.optionValues.put(nameUpperCase, value);
    this.inputFileSetting.add(nameUpperCase);

    // Special case logic block here for setting indirect flags

    if (nameUpperCase.equalsIgnoreCase(HiveCC.JJPARSER_CPP_NAMESPACE)) {
      processCPPNamespaceOption((String) value);
    }
  }


  /**
   * Process a single command-line option. The option is parsed and stored in the optionValues map.
   *
   * @param arg
   */
  public final void setCmdLineOption(String arg) {
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
    } else if (index1 < index2) {
      index = index1;
    } else {
      index = index2;
    }

    if (index < 0) {
      name = s.toUpperCase();
      if (Options.optionValues.containsKey(name)) {
        Val = Boolean.TRUE;
      } else if ((name.length() > 2) && (name.charAt(0) == 'N') && (name.charAt(1) == 'O')) {
        Val = Boolean.FALSE;
        name = name.substring(2);
      } else {
        System.out.println("Warning: Bad option \"" + arg + "\" will be ignored.");
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
            System.out.println("Warning: Bad option value in \"" + arg + "\" will be ignored.");
            return;
          }
          Val = Integer.valueOf(i);
        } catch (NumberFormatException e) {
          Val = s.substring(index + 1);
          if (s.length() > (index + 2)) {
            // i.e., there is space for two '"'s in value
            if ((s.charAt(index + 1) == '"') && (s.charAt(s.length() - 1) == '"')) {
              // remove the two '"'s.
              Val = s.substring(index + 2, s.length() - 1);
            }
          }
        }
      }
    }

    if (!Options.optionValues.containsKey(name)) {
      System.out.println("Warning: Bad option \"" + arg + "\" will be ignored.");
      return;
    }
    Object valOrig = Options.optionValues.get(name);
    if (Val.getClass() != valOrig.getClass()) {
      System.out.println("Warning: Bad option value in \"" + arg + "\" will be ignored.");
      return;
    }
    if (this.cmdLineSetting.contains(name)) {
      System.out.println("Warning: Duplicate option setting \"" + arg + "\" will be ignored.");
      return;
    }

    Val = upgradeValue(name, Val);

    Options.optionValues.put(name, Val);
    this.cmdLineSetting.add(name);
    if (name.equalsIgnoreCase(HiveCC.JJPARSER_CPP_NAMESPACE)) {
      processCPPNamespaceOption((String) Val);
    }
  }

  public final void normalize() {
    if (getDebugLookahead() && !getDebugParser()) {
      if (this.cmdLineSetting.contains(HiveCC.JJPARSER_DEBUG_PARSER)
          || this.inputFileSetting.contains(HiveCC.JJPARSER_DEBUG_PARSER)) {
        JavaCCErrors
            .warning("True setting of option DEBUG_LOOKAHEAD overrides " + "false setting of option DEBUG_PARSER.");
      }
      Options.optionValues.put(HiveCC.JJPARSER_DEBUG_PARSER, Boolean.TRUE);
    }
  }

  /**
   * Find the lookahead setting.
   *
   * @return The requested lookahead value.
   */
  public final int getLookahead() {
    return intValue(HiveCC.JJPARSER_LOOKAHEAD);
  }

  /**
   * Find the choice ambiguity check value.
   *
   * @return The requested choice ambiguity check value.
   */
  public final int getChoiceAmbiguityCheck() {
    return intValue(HiveCC.JJPARSER_CHOICE_AMBIGUITY_CHECK);
  }

  /**
   * Find the other ambiguity check value.
   *
   * @return The requested other ambiguity check value.
   */
  public final int getOtherAmbiguityCheck() {
    return intValue(HiveCC.JJPARSER_OTHER_AMBIGUITY_CHECK);
  }

  public final boolean getNoDfa() {
    return booleanValue(HiveCC.JJPARSER_NO_DFA);
  }

  /**
   * Find the debug parser value.
   *
   * @return The requested debug parser value.
   */
  public final boolean getDebugParser() {
    return booleanValue(HiveCC.JJPARSER_DEBUG_PARSER);
  }

  /**
   * Find the debug lookahead value.
   *
   * @return The requested debug lookahead value.
   */
  public final boolean getDebugLookahead() {
    return booleanValue(HiveCC.JJPARSER_DEBUG_LOOKAHEAD);
  }

  /**
   * Find the debug tokenmanager value.
   *
   * @return The requested debug tokenmanager value.
   */
  public final boolean getDebugTokenManager() {
    return booleanValue(HiveCC.JJPARSER_DEBUG_TOKEN_MANAGER);
  }

  /**
   * Find the error reporting value.
   *
   * @return The requested error reporting value.
   */
  public final boolean getErrorReporting() {
    return booleanValue(HiveCC.JJPARSER_ERROR_REPORTING);
  }

  /**
   * Find the ignore case value.
   *
   * @return The requested ignore case value.
   */
  public final boolean getIgnoreCase() {
    return booleanValue(HiveCC.JJPARSER_IGNORE_CASE);
  }

  /**
   * Find the sanity check value.
   *
   * @return The requested sanity check value.
   */
  public final boolean getSanityCheck() {
    return booleanValue(HiveCC.JJPARSER_SANITY_CHECK);
  }

  /**
   * Find the force lookahead check value.
   *
   * @return The requested force lookahead value.
   */
  public final boolean getForceLaCheck() {
    return booleanValue(HiveCC.JJPARSER_FORCE_LA_CHECK);
  }

  /**
   * Find the cache tokens value.
   *
   * @return The requested cache tokens value.
   */
  public final boolean getCacheTokens() {
    return booleanValue(HiveCC.JJPARSER_CACHE_TOKENS);
  }

  /**
   * Find the keep line column value.
   *
   * @return The requested keep line column value.
   */
  public final boolean getKeepLineColumn() {
    return booleanValue(HiveCC.JJPARSER_KEEP_LINE_COLUMN);
  }

  /**
   * Return the file encoding; this will return the file.encoding system property if no value was
   * explicitly set
   *
   * @return The file encoding (e.g., UTF-8, ISO_8859-1, MacRoman)
   */
  public final String getGrammarEncoding() {
    return System.getProperties().getProperty("file.encoding");
  }

  /**
   * Find the output directory.
   *
   * @return The requested output directory.
   */
  public final File getOutputDirectory() {
    return new File(stringValue(HiveCC.JJPARSER_OUTPUT_DIRECTORY));
  }

  /**
   * @return the output language. default java
   */
  public static Language getOutputLanguage() {
    String language = (String) Options.optionValues.get(HiveCC.JJPARSER_CODEGENERATOR);
    if (language.equalsIgnoreCase(Options.OUTPUT_LANGUAGE__CPP)) {
      return Language.CPP;
    } else if (language.equalsIgnoreCase(Options.OUTPUT_LANGUAGE__JAVA)) {
      return Language.JAVA;
    }
    return Language.JAVA;
  }

  public final String getJavaPackage() {
    return stringValue(HiveCC.JJPARSER_JAVA_PACKAGE);
  }

  public final String getJavaImports() {
    return stringValue(HiveCC.JJPARSER_JAVA_IMPORTS);
  }

  public final String getJavaExtends() {
    return stringValue(HiveCC.JJPARSER_JAVA_EXTENDS);
  }

  public final String getJavaLexer() {
    return stringValue(HiveCC.JJPARSER_JAVA_LEXER);
  }

  public final void setStringOption(String optionName, String optionValue) {
    Options.optionValues.put(optionName, optionValue);
    if (optionName.equalsIgnoreCase(HiveCC.JJPARSER_CPP_NAMESPACE)) {
      processCPPNamespaceOption(optionValue);
    }
  }

  private final void processCPPNamespaceOption(String optionValue) {
    String ns = optionValue;
    if (ns.length() > 0) {
      // We also need to split it.
      StringTokenizer st = new StringTokenizer(ns, "::");
      String expanded_ns = st.nextToken() + " {";
      String ns_close = "}";
      while (st.hasMoreTokens()) {
        expanded_ns = expanded_ns + "\nnamespace " + st.nextToken() + " {";
        ns_close = ns_close + "\n}";
      }
    }
  }

  /**
   * Get defined parser recursion depth limit.
   *
   * @return The requested recursion limit.
   */
  public final int getDepthLimit() {
    return intValue(HiveCC.JJPARSER_DEPTH_LIMIT);
  }

  private static class OptionInfo implements Comparable<OptionInfo> {

    private final String _name;
    private final Object _default;

    private OptionInfo(String name, Object default1) {
      this._name = name;
      this._default = default1;
    }

    public String getName() {
      return this._name;
    }

    public Object getDefault() {
      return this._default;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = (prime * result) + ((this._default == null) ? 0 : this._default.hashCode());
      return (prime * result) + ((this._name == null) ? 0 : this._name.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      OptionInfo other = (OptionInfo) obj;
      if (this._default == null) {
        if (other._default != null) {
          return false;
        }
      } else if (!this._default.equals(other._default)) {
        return false;
      }
      if (this._name == null) {
        if (other._name != null) {
          return false;
        }
      } else if (!this._name.equals(other._name)) {
        return false;
      }
      return true;
    }

    @Override
    public int compareTo(OptionInfo o) {
      return this._name.compareTo(o._name);
    }
  }
}
