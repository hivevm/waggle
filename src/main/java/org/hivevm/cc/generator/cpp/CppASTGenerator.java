// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.generator.TemplateProvider;
import org.hivevm.cc.generator.TreeGenerator;
import org.hivevm.cc.generator.TreeOptions;
import org.hivevm.cc.jjtree.ASTNode;
import org.hivevm.cc.jjtree.ASTNodeDescriptor;
import org.hivevm.cc.jjtree.ASTWriter;
import org.hivevm.cc.jjtree.NodeScope;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.utils.TemplateOptions;

class CppASTGenerator extends TreeGenerator {

  @Override
  protected final void insertOpenNodeCode(NodeScope ns, ASTWriter writer, TreeOptions context) {
    String type = ns.getNodeDescriptor().getNodeType();
    boolean isType = context.getNodeClass().isEmpty() || context.getMulti();
    String nodeClass = isType ? type : context.getNodeClass();

    addType(type);

    writer.print(nodeClass + " *" + ns.nodeVar + " = ");
    if (context.getNodeFactory().equals("*")) {
      // Old-style multiple-implementations.
      writer.println("(" + nodeClass + "*)" + nodeClass + "::jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
    } else if (context.getNodeFactory().length() > 0) {
      writer.println("(" + nodeClass + "*)" + context.getNodeFactory() + "->jjtCreate("
          + ns.getNodeDescriptor().getNodeId() + ");");
    } else {
      writer.println("new " + nodeClass + "(" + ns.getNodeDescriptor().getNodeId() + ");");
    }

    if (ns.usesCloseNodeVar()) {
      writer.println("bool " + ns.closedVar + " = true;");
    }
    writer.println(ns.getNodeDescriptor().openNode(ns.nodeVar));
    if (context.getNodeScopeHook()) {
      writer.println("jjtreeOpenNodeScope(" + ns.nodeVar + ");");
    }

    if (context.getTrackTokens()) {
      writer.println(ns.nodeVar + "->jjtSetFirstToken(getToken(1));");
    }
    writer.print("try {");
  }

  @Override
  protected final void insertCloseNodeCode(NodeScope ns, ASTWriter writer, TreeOptions options, boolean isFinal) {
    String closeNode = ns.getNodeDescriptor().closeNode(ns.nodeVar);
    writer.println(closeNode);
    if (ns.usesCloseNodeVar() && !isFinal) {
      writer.println(ns.closedVar + " = false;");
    }
    if (options.getNodeScopeHook()) {
      writer.println("if (jjtree.nodeCreated()) {");
      writer.println(" jjtreeCloseNodeScope(" + ns.nodeVar + ");");
      writer.println("}");
    }

    if (options.getTrackTokens()) {
      writer.println(ns.nodeVar + "->jjtSetLastToken(getToken(0));");
    }
  }

  @Override
  protected final void insertCatchBlocks(NodeScope ns, ASTWriter writer, ASTNode expansion_unit) {
    writer.openCodeBlock(null);

    // Enumeration<String> thrown_names = findThrown(ns, expansion_unit);

    writer.println("} catch (...) {"); // " + ns.exceptionVar + ") {");

    if (ns.usesCloseNodeVar()) {
      writer.println("  if (" + ns.closedVar + ") {");
      writer.println("    jjtree.clearNodeScope(" + ns.nodeVar + ");");
      writer.println("    " + ns.closedVar + " = false;");
      writer.println("  } else {");
      writer.println("    jjtree.popNode();");
      writer.println("  }");
    }

    writer.println("} {");
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
    generateNodeInterface(context);
    generateTree(context);
    generateTreeNodes(context);
    // generateOneTreeImpl();
    generateOneTreeInterface(context);
  }

  private void generateTreeState(TreeOptions context) {
    TemplateProvider template = CppTemplate.TREESTATE_H;
    template.render(context);

    template = CppTemplate.TREESTATE;
    template.render(context);
  }

  private void generateTreeConstants(TreeOptions context) {
    TemplateOptions options = new TemplateOptions(context);
    options.add("NODES", ASTNodeDescriptor.getNodeIds().size()).set("ordinal", i -> i).set("label",
        i -> ASTNodeDescriptor.getNodeIds().get(i));
    options.add("NODE_NAMES", ASTNodeDescriptor.getNodeNames().size()).set("ordinal", i -> i)
        .set("label", i -> ASTNodeDescriptor.getNodeNames().get(i))
        .set("chars", i -> CppFileGenerator.toCharArray(ASTNodeDescriptor.getNodeNames().get(i)));
    options.set(HiveCC.JJPARSER_CPP_DEFINE, context.getParserName().toUpperCase());

    TemplateProvider template = CppTemplate.TREE_CONSTANTS;
    template.render(options, context.getParserName());
  }

  private void generateVisitors(TreeOptions context) {
    if (!context.getVisitor()) {
      return;
    }

    List<String> nodeNames =
        ASTNodeDescriptor.getNodeNames().stream().filter(n -> !n.equals("void")).collect(Collectors.toList());

    TemplateOptions options = new TemplateOptions(context);
    options.add("NODES", nodeNames).set("type", n -> context.getNodePrefix() + n);

    String argumentType = CppASTGenerator.getVisitorArgumentType(context);
    String returnType = CppASTGenerator.getVisitorReturnType(context);
    if (!context.getVisitorDataType().equals("")) {
      argumentType = context.getVisitorDataType();
    }

    options.set(HiveCC.JJPARSER_CPP_DEFINE, context.getParserName().toUpperCase());
    options.set("ARGUMENT_TYPE", argumentType);
    options.set("RETURN_TYPE", returnType);
    options.set("RETURN", returnType.equals("void") ? "" : "return ");
    options.set(HiveCC.JJTREE_MULTI, context.getMulti());

    TemplateProvider template = CppTemplate.VISITOR;
    template.render(options, context.getParserName());
  }

  private void generateNode(TreeOptions context) {
    TemplateOptions optionMap = new TemplateOptions(context);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppASTGenerator.getVisitorReturnType(context));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppASTGenerator.getVisitorArgumentType(context));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppASTGenerator.getVisitorReturnType(context).equals("void")));

    TemplateProvider template = CppTemplate.NODE;
    template.render(optionMap);
  }

  private void generateNodeInterface(TreeOptions context) {
    TemplateOptions optionMap = new TemplateOptions(context);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppASTGenerator.getVisitorReturnType(context));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppASTGenerator.getVisitorArgumentType(context));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppASTGenerator.getVisitorReturnType(context).equals("void")));

    TemplateProvider template = CppTemplate.NODE_H;
    template.render(optionMap);
  }

  private void generateTree(TreeOptions context) {
    TemplateOptions optionMap = new TemplateOptions(context);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppASTGenerator.getVisitorReturnType(context));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppASTGenerator.getVisitorArgumentType(context));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppASTGenerator.getVisitorReturnType(context).equals("void")));
    optionMap.set(HiveCC.JJTREE_NODE_TYPE, "Tree");

    TemplateProvider template = CppTemplate.TREE;
    template.render(optionMap);
  }

  private void generateTreeNodes(TreeOptions context) {
    Set<String> excludes = context.getExcudeNodes();
    for (String node : nodesToGenerate()) {
      if (excludes.contains(node)) {
        continue;
      }

      TemplateOptions optionMap = new TemplateOptions(context);
      optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppASTGenerator.getVisitorReturnType(context));
      optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppASTGenerator.getVisitorArgumentType(context));
      optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
          Boolean.valueOf(CppASTGenerator.getVisitorReturnType(context).equals("void")));
      optionMap.set(HiveCC.JJTREE_NODE_TYPE, node);

      TemplateProvider template = CppTemplate.MULTINODE;
      template.render(optionMap);
    }
  }


  private void generateOneTreeInterface(TreeOptions context) {
    TemplateOptions optionMap = new TemplateOptions(context);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppASTGenerator.getVisitorReturnType(context));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppASTGenerator.getVisitorArgumentType(context));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppASTGenerator.getVisitorReturnType(context).equals("void")));
    optionMap.set("NODES", nodesToGenerate());

    TemplateProvider template = CppTemplate.TREE_ONE;
    template.render(optionMap, context.getParserName());
  }

  private static String getVisitorArgumentType(Options o) {
    String ret = o.stringValue(HiveCC.JJTREE_VISITOR_DATA_TYPE);
    return (ret == null) || ret.equals("") || ret.equals("Object") ? "void *" : ret;
  }

  private static String getVisitorReturnType(Options o) {
    String ret = o.stringValue(HiveCC.JJTREE_VISITOR_RETURN_TYPE);
    return (ret == null) || ret.equals("") || ret.equals("Object") ? "void " : ret;
  }
}
