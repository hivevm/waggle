// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.utils;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    TemplateTree root = new TemplateTree();
    int offset = walk(root, data, 0, matcher);
    if (offset < data.length()) {
      root.newText(data.substring(offset));
    }

    root.render(writer, this.environment);
  }

  /**
   * Use the template.
   *
   * @param writer
   * @param lines
   * @param env
   */
  private int walk(TemplateTree node, String data, int offset, Matcher matcher) {
    while (matcher.find()) {
      if (matcher.start() > offset) {
        node.newText(data.substring(offset, matcher.start()));
      }
      offset = matcher.end();

      String cond = matcher.group(1);
      if (cond != null) {
        switch (cond) {
          case COND_IF:
            TemplateTree child = node.newSwitch();
            while (!Template.COND_END_IF.equals(matcher.group(1))) {
              switch (matcher.group(1)) {
                case COND_IF:
                case COND_ELSE:
                case COND_ELSE_IF:
                  if ("!LEGACY".equals(matcher.group(2))) {
                    System.out.printf("%s\n", matcher.group(2));
                  }
                  TemplateTree n = child.newCase(matcher.group(2));
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
}
