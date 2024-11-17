// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.generator.ASTGenerator;
import org.hivevm.cc.generator.ASTGeneratorContext;
import org.hivevm.cc.jjtree.ASTNode;
import org.hivevm.cc.jjtree.ASTNodeDescriptor;
import org.hivevm.cc.jjtree.ASTWriter;
import org.hivevm.cc.jjtree.JJTreeGlobals;
import org.hivevm.cc.jjtree.NodeScope;
import org.hivevm.cc.utils.TemplateOptions;
import org.hivevm.cc.utils.TemplateProvider;

import java.util.Enumeration;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class JavaASTGenerator extends ASTGenerator {

  @Override
  protected final void insertOpenNodeCode(NodeScope ns, ASTWriter writer, String indent, ASTGeneratorContext context) {
    String type = ns.getNodeDescriptor().getNodeType();
    boolean isType = context.getNodeClass().isEmpty() || context.getMulti();
    String nodeClass = isType ? type : context.getNodeClass();

    context.addType(type);

    writer.print(indent + nodeClass + " " + ns.nodeVar + " = ");
    if (context.getNodeFactory().equals("*")) {
      // Old-style multiple-implementations.
      writer.println("(" + nodeClass + ")" + nodeClass + ".jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
    } else if (context.getNodeFactory().length() > 0) {
      writer.println(
          "(" + nodeClass + ")" + context.getNodeFactory() + ".jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
    } else {
      writer.println("new " + nodeClass + "(this, " + ns.getNodeDescriptor().getNodeId() + ");");
    }

    if (ns.usesCloseNodeVar()) {
      writer.println(indent + "boolean " + ns.closedVar + " = true;");
    }
    writer.println(indent + ns.getNodeDescriptor().openNode(ns.nodeVar));
    if (context.getNodeScopeHook()) {
      writer.println(indent + "jjtreeOpenNodeScope(" + ns.nodeVar + ");");
    }

    if (context.getTrackTokens()) {
      writer.println(indent + ns.nodeVar + ".jjtSetFirstToken(getToken(1));");
    }
  }

  @Override
  protected final void insertCloseNodeCode(NodeScope ns, ASTWriter writer, String indent, boolean isFinal,
      ASTGeneratorContext context) {
    String closeNode = ns.getNodeDescriptor().closeNode(ns.nodeVar);
    writer.println(indent + closeNode);
    if (ns.usesCloseNodeVar() && !isFinal) {
      writer.println(indent + ns.closedVar + " = false;");
    }
    if (context.getNodeScopeHook()) {
      writer.println(indent + "if (jjtree.nodeCreated()) {");
      writer.println(indent + " jjtreeCloseNodeScope(" + ns.nodeVar + ");");
      writer.println(indent + "}");
    }

    if (context.getTrackTokens()) {
      writer.println(indent + ns.nodeVar + ".jjtSetLastToken(getToken(0));");
    }
  }

  @Override
  protected final void insertCatchBlocks(NodeScope ns, ASTWriter writer, String indent, ASTNode expansion_unit) {
    writer.openCodeBlock(null);

    Enumeration<String> thrown_names = findThrown(ns, expansion_unit);

    if (thrown_names.hasMoreElements()) {
      writer.println(indent + "} catch (Throwable " + ns.exceptionVar + ") {");

      if (ns.usesCloseNodeVar()) {
        writer.println(indent + "  if (" + ns.closedVar + ") {");
        writer.println(indent + "    jjtree.clearNodeScope(" + ns.nodeVar + ");");
        writer.println(indent + "    " + ns.closedVar + " = false;");
        writer.println(indent + "  } else {");
        writer.println(indent + "    jjtree.popNode();");
        writer.println(indent + "  }");
      }

      String thrown = null;
      while (thrown_names.hasMoreElements()) {
        thrown = thrown_names.nextElement();
        writer.println(indent + "  if (" + ns.exceptionVar + " instanceof " + thrown + ") {");
        writer.println(indent + "    throw (" + thrown + ")" + ns.exceptionVar + ";");
        writer.println(indent + "  }");
      }
      // This is either an Error or an undeclared Exception. If it's an Error then the cast is good,
      // otherwise we want to force the user to declare it by crashing on the bad cast.
      writer.println(indent + "  throw (Error)" + ns.exceptionVar + ";");
    }

    writer.println(indent + "} finally {");
    if (ns.usesCloseNodeVar()) {
      writer.println(indent + "  if (" + ns.closedVar + ") {");
      insertCloseNodeCode(ns, writer, indent + "    ", true, expansion_unit.jjtOptions());
      writer.println(indent + "  }");
    }
    writer.print(indent + "}");
    writer.closeCodeBlock();
  }

  @Override
  public final void generate(ASTGeneratorContext context) {
    generateTreeState(context);
    generateTreeConstants(context);
    generateVisitors(context);

    // TreeClasses
    generateNode(context);
    generateTree(context);
    generateTreeNodes(context);
  }

  private void generateTreeState(ASTGeneratorContext context) {
    TemplateOptions options = new TemplateOptions();
    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.set(HiveCC.JJPARSER_JAVA_PACKAGE, context.getJavaPackage());

    TemplateProvider provider = JavaTemplate.TREE_STATE;
    provider.render(context, options, JJTreeGlobals.parserName);
  }


  private void generateTreeConstants(ASTGeneratorContext context) {
    TemplateOptions options = new TemplateOptions();
    options.add("NODE_NAMES", ASTNodeDescriptor.getNodeNames());
    options.add("NODES", ASTNodeDescriptor.getNodeIds().size()).set("ordinal", i -> i).set("label",
        i -> ASTNodeDescriptor.getNodeIds().get(i));

    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.set(HiveCC.JJPARSER_JAVA_PACKAGE, context.getJavaPackage());

    TemplateProvider provider = JavaTemplate.TREE_CONSTANTS;
    provider.render(context, options, JJTreeGlobals.parserName);
  }

  private void generateVisitors(ASTGeneratorContext context) {
    if (!context.getVisitor()) {
      return;
    }

    Stream<String> nodes = ASTNodeDescriptor.getNodeNames().stream().filter(n -> !n.equals("void"));
    String argumentType = context.getVisitorDataType().equals("") ? "Object" : context.getVisitorDataType().trim();
    String returnValue = JavaASTGenerator.returnValue(context.getVisitorReturnType(), argumentType);
    boolean isVoidReturnType = "void".equals(context.getVisitorReturnType());

    TemplateOptions options = new TemplateOptions();
    options.add("NODES", nodes.collect(Collectors.toList()));
    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.set(HiveCC.JJPARSER_JAVA_PACKAGE, context.getJavaPackage());
    options.set("RETURN_TYPE", context.getVisitorReturnType());
    options.set("RETURN_VALUE", returnValue);
    options.set("RETURN", isVoidReturnType ? "" : "return ");
    options.set("ARGUMENT_TYPE", argumentType);
    options.set("EXCEPTION", JavaASTGenerator.mergeVisitorException(context));
    options.set("IS_MULTI", context.getMulti());
    options.set("NODE_PREFIX", context.getNodePrefix());

    TemplateProvider provider = JavaTemplate.VISITOR;
    provider.render(context, options, JJTreeGlobals.parserName);

    provider = JavaTemplate.DEFAULT_VISITOR;
    provider.render(context, options, JJTreeGlobals.parserName);
  }

  private void generateNode(ASTGeneratorContext context) {
    TemplateOptions options = new TemplateOptions();
    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.set(HiveCC.JJPARSER_JAVA_PACKAGE, context.getJavaPackage());

    TemplateProvider provider = JavaTemplate.NODE;
    provider.render(context, options);
  }

  private void generateTree(ASTGeneratorContext context) {
    TemplateOptions options = new TemplateOptions();
    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.set(HiveCC.JJPARSER_JAVA_PACKAGE, context.getJavaPackage());

    TemplateProvider provider = JavaTemplate.TREE;
    provider.render(context, options);
  }

  private void generateTreeNodes(ASTGeneratorContext context) {
    TemplateOptions options = new TemplateOptions();
    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.set(HiveCC.JJPARSER_JAVA_PACKAGE, context.getJavaPackage());
    options.set(HiveCC.JJTREE_VISITOR_RETURN_VOID, Boolean.valueOf(context.getVisitorReturnType().equals("void")));

    Set<String> excludes = context.getExcudeNodes();
    for (String nodeType : context.nodesToGenerate()) {
      if (!context.getBuildNodeFiles() || excludes.contains(nodeType)) {
        continue;
      }

      options.set(HiveCC.JJTREE_NODE_TYPE, nodeType);

      TemplateProvider provider = JavaTemplate.MULTI_NODE;
      provider.render(context, options, nodeType);
    }
  }

  private static String mergeVisitorException(ASTGeneratorContext context) {
    String ve = context.getVisitorException();
    return "".equals(ve) ? ve : " throws " + ve;
  }

  private static String returnValue(String returnType, String argumentType) {
    boolean isVoidReturnType = "void".equals(returnType);
    if (isVoidReturnType) {
      return "";
    }

    if (returnType.equals(argumentType)) {
      return " data";
    }

    switch (returnType) {
      case "boolean":
        return " false";
      case "int":
        return " 0";
      case "long":
        return " 0L";
      case "double":
        return " 0.0d";
      case "float":
        return " 0.0f";
      case "short":
        return " 0";
      case "byte":
        return " 0";
      case "char":
        return " '\u0000'";
      default:
        return " null";
    }
  }
}
