// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Generates boiler-plate files from templates.
 */
public class Template {

  private static final String COND_IF      = "if";
  private static final String COND_ELSE_IF = "elif";
  private static final String COND_ELSE    = "else";
  private static final String COND_FOREACH = "foreach";

  private static final String COND_END     = "end";
  private static final String COND_END_IF  = "fi";

  // COMMAND (ARGUMENT (,ARGUMENT)* )?
  private static final Pattern COMMAND = Pattern.compile(
      "@(if|elif|else|foreach|fi|end)(?:\\s*(?:\\(([^\\)]+)\\)))?\\n?|\\{\\{([^\\{\\}\\:]+)(?:\\:([^\\}]*))?\\}\\}");

  private final byte[]         bytes;
  private final Environment    environment;

  /**
   * @param bytes
   * @param environment
   */
  public Template(byte[] bytes, Environment environment) {
    this.bytes = bytes;
    this.environment = environment;
  }

  /**
   * Use the template.
   *
   * @param writer
   */
  public final void render(PrintWriter writer) throws IOException {
    String data = new String(this.bytes, StandardCharsets.UTF_8);
    Matcher matcher = Template.COMMAND.matcher(data);

    TemplateNode root = new TemplateNode();
    int offset = walk(root, data, 0, matcher);
    if (offset < data.length()) {
      root.newText(data.substring(offset));
    }

    renderData(writer, root, this.environment, null, null, null);
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
    if (value == null) {
      return false;
    }
    if ((value instanceof String) && ((String) value).isEmpty()) {
      return false;
    }
    if ((value instanceof Number) && (((Number) value).intValue() == 0)) {
      return false;
    }

    return (!(value instanceof Boolean) || ((Boolean) value));
  }

  /**
   * Use the template.
   *
   * @param writer
   * @param lines
   * @param env
   */
  private int walk(TemplateNode node, String data, int offset, Matcher matcher) {
    while (matcher.find()) {
      if (matcher.start() > offset) {
        node.newText(data.substring(offset, matcher.start()));
      }
      offset = matcher.end();

      String cond = matcher.group(1);
      if (cond != null) {
        switch (cond) {
          case COND_IF:
            TemplateNode child = node.newSwitch();
            while (!Template.COND_END_IF.equals(matcher.group(1))) {
              switch (matcher.group(1)) {
                case COND_IF:
                case COND_ELSE:
                case COND_ELSE_IF:
                  if ("!LEGACY".equals(matcher.group(2))) {
                    System.out.printf("%s\n", matcher.group(2));
                  }
                  TemplateNode n = child.newCase(matcher.group(2));
                  offset = walk(n, data, offset, matcher);
              }
            }
            break;

          case COND_ELSE:
          case COND_ELSE_IF:
          case COND_END:
          case COND_END_IF:
            return offset;

          case COND_FOREACH:
            List<String> args =
                Arrays.asList(matcher.group(2).split(":")).stream().map(k -> k.trim()).collect(Collectors.toList());
            child = node.newForEach(args.get(0), args.get(1));
            offset = walk(child, data, offset, matcher);
            break;

          default:
            break;
        }
      } else {
        node.newExpr(matcher.group(3), matcher.group(4));
      }
    }
    return offset;
  }

  /**
   * Use the template.
   *
   * @param writer
   * @param lines
   * @param env
   */
  private void renderData(PrintWriter writer, TemplateNode node, Environment env, Object item, String global,
      String local) {
    for (TemplateNode child : node.nodes) {
      switch (child.kind) {
        case EXPR:
          String param = child.text;

          // Replace qualified parameter for iterators
          if (param.startsWith(local + ".")) {
            param = global + "." + param.substring(local.length() + 1);
          }

          String option = child.option;
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
          Iterator<TemplateNode> iterator = child.nodes.iterator();
          while (!hasFound && iterator.hasNext()) {
            TemplateNode next = iterator.next();
            if ((next.text == null) || validate(next.text, env)) {
              hasFound = true;
              renderData(writer, next, env, item, global, local);
            }
          }
          break;

        case FOREACH:
          instance = env.get(child.option);
          Iterable<?> iterable = (instance instanceof Integer)
              ? IntStream.range(0, (Integer) instance).boxed().collect(Collectors.toList())
              : (Iterable<?>) instance;

          Environment e = new TemplateEnvironment(env);
          for (Object value : iterable) {
            e.set(child.text, value);
            renderData(writer, child, e, value, child.option, child.text);
          }
          break;

        case NONE:
          writer.append(child.text);
        default:
          break;
      }
    }
  }

  private enum NodeKind {
    NONE,
    EXPR,
    SWITCH,
    FOREACH;
  }

  private class TemplateNode {

    private NodeKind                 kind;
    private final List<TemplateNode> nodes = new ArrayList<>();

    private String                   text;
    private String                   option;

    /**
     * Constructs an instance of {@link TemplateNode}.
     */
    public TemplateNode() {
      this.kind = NodeKind.NONE;
    }

    public TemplateNode newText(String text) {
      TemplateNode node = new TemplateNode();
      node.text = text;
      this.nodes.add(node);
      return node;
    }

    public TemplateNode newExpr(String text, String option) {
      TemplateNode node = new TemplateNode();
      node.kind = NodeKind.EXPR;
      node.text = text;
      node.option = option;
      this.nodes.add(node);
      return node;
    }

    public TemplateNode newSwitch() {
      TemplateNode node = new TemplateNode();
      node.kind = NodeKind.SWITCH;
      this.nodes.add(node);
      return node;
    }

    public TemplateNode newCase(String cond) {
      TemplateNode node = new TemplateNode();
      node.text = cond;
      this.nodes.add(node);
      return node;
    }

    public TemplateNode newForEach(String item, String list) {
      TemplateNode node = new TemplateNode();
      node.kind = NodeKind.FOREACH;
      node.text = item;
      node.option = list;
      this.nodes.add(node);
      return node;
    }
  }

  /**
   * Creates a new {@link Template}.
   *
   * @param template
   * @param environment
   */
  public static Template of(TemplateProvider provider, Environment environment) throws IOException {
    String path = String.format("/templates/%s.template", provider.getTemplate());
    InputStream stream = Template.class.getResourceAsStream(path);
    if (stream == null) {
      throw new IOException("Invalid template name: " + path);
    }
    return new Template(stream.readAllBytes(), environment);
  }
}
