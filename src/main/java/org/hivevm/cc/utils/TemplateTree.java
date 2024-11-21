// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.utils;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class TemplateTree implements Iterable<TemplateTree> {

  public enum Kind {
    NONE,
    EXPR,
    SWITCH,
    FOREACH;
  }


  private Kind   kind;
  private String text;
  private String option;


  private final List<TemplateTree> nodes = new ArrayList<>();


  /**
   * Constructs an instance of {@link TemplateTree}.
   */
  public TemplateTree() {
    this.kind = Kind.NONE;
  }

  public final Kind kind() {
    return this.kind;
  }

  public final String text() {
    return this.text;
  }

  public final String option() {
    return this.option;
  }

  public TemplateTree newText(String text) {
    TemplateTree node = new TemplateTree();
    node.text = text;
    this.nodes.add(node);
    return node;
  }

  public TemplateTree newExpr(String text, String option) {
    TemplateTree node = new TemplateTree();
    node.kind = Kind.EXPR;
    node.text = text;
    node.option = option;
    this.nodes.add(node);
    return node;
  }

  public TemplateTree newSwitch() {
    TemplateTree node = new TemplateTree();
    node.kind = Kind.SWITCH;
    this.nodes.add(node);
    return node;
  }

  public TemplateTree newCase(String cond) {
    TemplateTree node = new TemplateTree();
    node.text = cond;
    this.nodes.add(node);
    return node;
  }

  public TemplateTree newForEach(String item, String list) {
    TemplateTree node = new TemplateTree();
    node.kind = Kind.FOREACH;
    node.text = item;
    node.option = list;
    this.nodes.add(node);
    return node;
  }

  @Override
  public final Iterator<TemplateTree> iterator() {
    return this.nodes.iterator();
  }

  /**
   * Use the template.
   *
   * @param writer
   * @param environment
   */
  public final void render(PrintWriter writer, Environment environment) {
    render(writer, environment, null, null, null);
  }

  /**
   * Use the template.
   *
   * @param writer
   * @param lines
   * @param env
   */
  private void render(PrintWriter writer, Environment env, Object item, String global, String local) {
    for (TemplateTree child : this) {
      switch (child.kind()) {
        case EXPR:
          String param = child.text();

          // Replace qualified parameter for iterators
          if (param.startsWith(local + ".")) {
            param = global + "." + param.substring(local.length() + 1);
          }

          String option = child.option();
          Object instance = env.get(param);
          if (option != null) {
            boolean validate = validate(param, env);
            writer.print(validate ? instance : option);
          } else if (instance instanceof Supplier) {
            Supplier<Object> func = (Supplier<Object>) instance;
            writer.print(func.get());
          } else if (instance instanceof Function) {
            Function<Object, Object> func = (Function<Object, Object>) instance;
            writer.print(func.apply(item));
          } else {
            writer.print(instance);
          }
          break;

        case SWITCH:
          boolean hasFound = false;
          Iterator<TemplateTree> iterator = child.iterator();
          while (!hasFound && iterator.hasNext()) {
            TemplateTree next = iterator.next();
            if ((next.text() == null) || validate(next.text(), env)) {
              hasFound = true;
              next.render(writer, env, item, global, local);
            }
          }
          break;

        case FOREACH:
          instance = env.get(child.option());
          Iterable<?> iterable = (instance instanceof Integer)
              ? IntStream.range(0, (Integer) instance).boxed().collect(Collectors.toList())
              : (Iterable<?>) instance;

          Environment e = new TemplateEnvironment(env);
          for (Object value : iterable) {
            e.set(child.text(), value);
            child.render(writer, e, value, child.option(), child.text());
          }
          break;

        case NONE:
          writer.append(child.text());
        default:
          break;
      }
    }
  }

  /**
   * Validates the condition against the properties.
   *
   * @param condition
   */
  private final boolean validate(String condition, Environment environment) {
    if (condition.startsWith("!")) { // negative condition
      return !validate(condition.substring(1), environment);
    }

    if (!environment.isSet(condition)) {
      return false;
    }

    Object value = environment.get(condition);
    if ((value == null) || ((value instanceof String) && ((String) value).isEmpty())) {
      return false;
    }
    if ((value instanceof Number) && (((Number) value).intValue() == 0)) {
      return false;
    }

    return (!(value instanceof Boolean) || ((Boolean) value));
  }
}
