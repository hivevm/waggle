// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.generator.TemplateProvider;
import org.hivevm.cc.generator.TreeGenerator;
import org.hivevm.cc.generator.TreeOptions;
import org.hivevm.cc.jjtree.ASTBNFNonTerminal;
import org.hivevm.cc.jjtree.ASTNode;
import org.hivevm.cc.jjtree.ASTNodeDescriptor;
import org.hivevm.cc.jjtree.ASTProduction;
import org.hivevm.cc.jjtree.ASTWriter;
import org.hivevm.cc.jjtree.NodeScope;
import org.hivevm.cc.utils.TemplateOptions;

class JavaASTGenerator extends TreeGenerator {

  @Override
  protected final void insertOpenNodeCode(NodeScope ns, ASTWriter writer, TreeOptions context) {
    String type = ns.getNodeDescriptor().getNodeType();
    boolean isType = context.getNodeClass().isEmpty() || context.getMulti();
    String nodeClass = isType ? type : context.getNodeClass();

    addType(type);

    writer.print(nodeClass + " " + ns.nodeVar + " = ");
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
      writer.println("boolean " + ns.closedVar + " = true;");
    }
    writer.println(ns.getNodeDescriptor().openNode(ns.nodeVar));
    if (context.getNodeScopeHook()) {
      writer.println("jjtreeOpenNodeScope(" + ns.nodeVar + ");");
    }

    if (context.getTrackTokens()) {
      writer.println(ns.nodeVar + ".jjtSetFirstToken(getToken(1));");
    }
    writer.print("try {");
  }

  @Override
  protected final void insertCloseNodeCode(NodeScope ns, ASTWriter writer, TreeOptions context, boolean isFinal) {
    String closeNode = ns.getNodeDescriptor().closeNode(ns.nodeVar);
    writer.println(closeNode);
    if (ns.usesCloseNodeVar() && !isFinal) {
      writer.println(ns.closedVar + " = false;");
    }
    if (context.getNodeScopeHook()) {
      writer.println("if (jjtree.nodeCreated()) {");
      writer.println(" jjtreeCloseNodeScope(" + ns.nodeVar + ");");
      writer.println("}");
    }

    if (context.getTrackTokens()) {
      writer.println(ns.nodeVar + ".jjtSetLastToken(getToken(0));");
    }
  }

  @Override
  protected final void insertCatchBlocks(NodeScope ns, ASTWriter writer, ASTNode expansion_unit) {
    writer.openCodeBlock(null);

    Enumeration<String> thrown_names = findThrown(ns, expansion_unit);

    if (thrown_names.hasMoreElements()) {
      writer.println("} catch (Throwable " + ns.exceptionVar + ") {");

      if (ns.usesCloseNodeVar()) {
        writer.println("  if (" + ns.closedVar + ") {");
        writer.println("    jjtree.clearNodeScope(" + ns.nodeVar + ");");
        writer.println("    " + ns.closedVar + " = false;");
        writer.println("  } else {");
        writer.println("    jjtree.popNode();");
        writer.println("  }");
      }

      String thrown = null;
      while (thrown_names.hasMoreElements()) {
        thrown = thrown_names.nextElement();
        writer.println("  if (" + ns.exceptionVar + " instanceof " + thrown + ") {");
        writer.println("    throw (" + thrown + ")" + ns.exceptionVar + ";");
        writer.println("  }");
      }
      // This is either an Error or an undeclared Exception. If it's an Error then the cast is good,
      // otherwise we want to force the user to declare it by crashing on the bad cast.
      writer.println("  throw (Error)" + ns.exceptionVar + ";");
    }

    writer.println("} finally {");
    if (ns.usesCloseNodeVar()) {
      writer.println("  if (" + ns.closedVar + ") {");
      String previous = writer.setIndent(writer.getIndent() + "    ");
      insertCloseNodeCode(ns, writer, expansion_unit.jjtOptions(), true);
      writer.setIndent(previous);
      writer.println("  }");
    }
    writer.print("}");
    writer.closeCodeBlock();
  }

  @Override
  public final void generate(TreeOptions context) {
    generateTreeState(context);
    generateTreeConstants(context);
    generateVisitors(context);

    // TreeClasses
    generateNode(context);
    generateTree(context);
    generateTreeNodes(context);
  }

  private void generateTreeState(TreeOptions context) {
    TemplateProvider provider = JavaTemplate.TREE_STATE;
    provider.render(context, context.getParserName());
  }


  private void generateTreeConstants(TreeOptions context) {
    TemplateOptions options = new TemplateOptions(context);
    options.add("NODE_NAMES", ASTNodeDescriptor.getNodeNames());
    options.add("NODES", ASTNodeDescriptor.getNodeIds().size()).set("ordinal", i -> i).set("label",
        i -> ASTNodeDescriptor.getNodeIds().get(i));

    TemplateProvider provider = JavaTemplate.TREE_CONSTANTS;
    provider.render(options, context.getParserName());
  }

  private void generateVisitors(TreeOptions context) {
    if (!context.getVisitor()) {
      return;
    }

    Stream<String> nodes = ASTNodeDescriptor.getNodeNames().stream().filter(n -> !n.equals("void"));
    String argumentType = context.getVisitorDataType().equals("") ? "Object" : context.getVisitorDataType().trim();
    String returnValue = JavaASTGenerator.returnValue(context.getVisitorReturnType(), argumentType);
    boolean isVoidReturnType = "void".equals(context.getVisitorReturnType());

    TemplateOptions options = new TemplateOptions(context);
    options.add("NODES", nodes.collect(Collectors.toList()));
    options.set("RETURN_TYPE", context.getVisitorReturnType());
    options.set("RETURN_VALUE", returnValue);
    options.set("RETURN", isVoidReturnType ? "" : "return ");
    options.set("ARGUMENT_TYPE", argumentType);
    options.set("EXCEPTION", JavaASTGenerator.mergeVisitorException(context));
    options.set(HiveCC.JJTREE_MULTI, context.getMulti());
    options.set("NODE_PREFIX", context.getNodePrefix());

    TemplateProvider provider = JavaTemplate.VISITOR;
    provider.render(options, context.getParserName());

    provider = JavaTemplate.DEFAULT_VISITOR;
    provider.render(options, context.getParserName());
  }

  private void generateNode(TreeOptions context) {
    TemplateProvider provider = JavaTemplate.NODE;
    provider.render(context);
  }

  private void generateTree(TreeOptions context) {
    TemplateProvider provider = JavaTemplate.TREE;
    provider.render(context);
  }

  private void generateTreeNodes(TreeOptions context) {
    TemplateOptions options = new TemplateOptions(context);
    options.set(HiveCC.JJTREE_VISITOR_RETURN_VOID, Boolean.valueOf(context.getVisitorReturnType().equals("void")));

    Set<String> excludes = context.getExcudeNodes();
    for (String nodeType : nodesToGenerate()) {
      if (!context.getBuildNodeFiles() || excludes.contains(nodeType)) {
        continue;
      }

      options.set(HiveCC.JJTREE_NODE_TYPE, nodeType);

      TemplateProvider provider = JavaTemplate.MULTI_NODE;
      provider.render(options, nodeType);
    }
  }

  private static String mergeVisitorException(TreeOptions context) {
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

  private final Enumeration<String> findThrown(NodeScope ns, ASTNode expansion_unit) {
    Hashtable<String, String> thrown_set = new Hashtable<>();
    findThrown(ns, thrown_set, expansion_unit);
    return thrown_set.elements();
  }


  private void findThrown(NodeScope ns, Hashtable<String, String> thrown_set, ASTNode expansion_unit) {
    if (expansion_unit instanceof ASTBNFNonTerminal) {
      // Should really make the nonterminal explicitly maintain its name.
      String nt = expansion_unit.getFirstToken().image;
      ASTProduction prod = expansion_unit.getParser().getProduction(nt);
      if (prod != null) {
        prod.throwElements().forEach(t -> thrown_set.put(t, t));
      }
    }
    for (int i = 0; i < expansion_unit.jjtGetNumChildren(); ++i) {
      ASTNode n = (ASTNode) expansion_unit.jjtGetChild(i);
      findThrown(ns, thrown_set, n);
    }
  }
}
