// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.hivevm.cc.parser.JavaCCErrors;
import org.hivevm.cc.parser.Options;

/**
 * A class with static state that stores all option information.
 */
public class HiveCCOptions implements Options {

  private static final String OUTPUT_LANGUAGE__CPP  = "cpp";
  private static final String OUTPUT_LANGUAGE__JAVA = "java";


  private static final Set<OptionInfo> userOptions;


  static {
    TreeSet<OptionInfo> temp = new TreeSet<>();

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
    temp.add(new OptionInfo(HiveCC.JJPARSER_CODEGENERATOR, HiveCCOptions.OUTPUT_LANGUAGE__JAVA));
    temp.add(new OptionInfo(HiveCC.JJPARSER_DEPTH_LIMIT, Integer.valueOf(0)));

    temp.add(new OptionInfo(HiveCC.JJPARSER_BASE_PARSER, ""));
    temp.add(new OptionInfo(HiveCC.JJPARSER_BASE_LEXER, ""));

    temp.add(new OptionInfo(HiveCC.JJPARSER_JAVA_PACKAGE, ""));
    temp.add(new OptionInfo(HiveCC.JJPARSER_JAVA_IMPORTS, ""));

    temp.add(new OptionInfo(HiveCC.JJPARSER_CPP_NAMESPACE, ""));
    temp.add(new OptionInfo(HiveCC.JJPARSER_CPP_STACK_LIMIT, ""));
    temp.add(new OptionInfo(HiveCC.JJPARSER_CPP_STOP_ON_FIRST_ERROR, Boolean.FALSE));

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
  private final Set<String>         cmdLineSetting;

  /**
   * Keep track of what options were set from the grammar file. We use this to see if the options
   * set from the command line and the ones set in the input files clash in any way.
   */
  private final Set<String>         inputFileSetting;

  // Limit subclassing to derived classes.
  public HiveCCOptions() {
    this.optionValues = new HashMap<>();
    this.cmdLineSetting = new HashSet<>();
    this.inputFileSetting = new HashSet<>();

    for (OptionInfo info : HiveCCOptions.userOptions) {
      set(info.getName(), info.getDefault());
    }
  }

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

  public final void setOption(Object nameloc, Object valueloc, String name, Object value) {
    String nameUpperCase = name.toUpperCase();
    if (!this.optionValues.containsKey(nameUpperCase)) {
      JavaCCErrors.warning(nameloc, "Bad option name \"" + name + "\".  Option setting will be ignored.");
      return;
    }

    if (name.equalsIgnoreCase(HiveCC.JJTREE_NODE_FACTORY) && (value.getClass() == Boolean.class)) {
      if (((Boolean) value)) {
        value = "*";
      } else {
        value = "";
      }
    }

    final Object existingValue = this.optionValues.get(nameUpperCase);
    if (existingValue != null) {
      Object object = null;
      if (value instanceof List) {
        object = ((List<?>) value).get(0);
      } else {
        object = value;
      }
      boolean isValidInteger = ((object instanceof Integer) && (((Integer) value).intValue() <= 0));
      if (isValidInteger) {
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

    set(nameUpperCase, value);
    this.inputFileSetting.add(nameUpperCase);
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
      if (this.optionValues.containsKey(name)) {
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
          // i.e., there is space for two '"'s in value
          if ((s.length() > (index + 2)) && ((s.charAt(index + 1) == '"') && (s.charAt(s.length() - 1) == '"'))) {
            // remove the two '"'s.
            Val = s.substring(index + 2, s.length() - 1);
          }
        }
      }
    }

    if (!this.optionValues.containsKey(name)) {
      System.out.println("Warning: Bad option \"" + arg + "\" will be ignored.");
      return;
    }
    Object valOrig = this.optionValues.get(name);
    if (Val.getClass() != valOrig.getClass()) {
      System.out.println("Warning: Bad option value in \"" + arg + "\" will be ignored.");
      return;
    }
    if (this.cmdLineSetting.contains(name)) {
      System.out.println("Warning: Duplicate option setting \"" + arg + "\" will be ignored.");
      return;
    }

    set(name, Val);
    this.cmdLineSetting.add(name);
  }

  /**
   * @return the output language. default java
   */
  public final Language getOutputLanguage() {
    String language = (String) this.optionValues.get(HiveCC.JJPARSER_CODEGENERATOR);
    if (language.equalsIgnoreCase(HiveCCOptions.OUTPUT_LANGUAGE__CPP)) {
      return Language.CPP;
    } else if (language.equalsIgnoreCase(HiveCCOptions.OUTPUT_LANGUAGE__JAVA)) {
      return Language.JAVA;
    }
    return Language.JAVA;
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
      if ((obj == null) || (getClass() != obj.getClass())) {
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

  @Override
  public boolean isSet(String name) {
    return this.optionValues.containsKey(name);
  }

  @Override
  public Object get(String name) {
    return this.optionValues.get(name);
  }

  public void setParser(String value) {
    set(HiveCC.PARSER_NAME, value);
    set(HiveCC.JJPARSER_CPP_DEFINE, value.toUpperCase());

  }

  @Override
  public void set(String name, Object value) {
    if (HiveCC.JJPARSER_JAVA_IMPORTS.equalsIgnoreCase(name)) {
      value = ((value instanceof String) && !value.toString().isEmpty()) ? Arrays.asList(value.toString().split(","))
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
