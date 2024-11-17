// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.generator.ASTGenerator;
import org.hivevm.cc.generator.ASTGeneratorContext;
import org.hivevm.cc.jjtree.ASTNode;
import org.hivevm.cc.jjtree.ASTNodeDescriptor;
import org.hivevm.cc.jjtree.ASTWriter;
import org.hivevm.cc.jjtree.JJTreeGlobals;
import org.hivevm.cc.jjtree.NodeScope;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.utils.TemplateOptions;
import org.hivevm.cc.utils.TemplateProvider;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class CppASTGenerator extends ASTGenerator {

  @Override
  protected final void insertOpenNodeCode(NodeScope ns, ASTWriter writer, String indent, ASTGeneratorContext context) {
    String type = ns.getNodeDescriptor().getNodeType();
    boolean isType = context.getNodeClass().isEmpty() || context.getMulti();
    String nodeClass = isType ? type : context.getNodeClass();

    context.addType(type);

    writer.print(indent + nodeClass + " *" + ns.nodeVar + " = ");
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
      writer.println(indent + "bool " + ns.closedVar + " = true;");
    }
    writer.println(indent + ns.getNodeDescriptor().openNode(ns.nodeVar));
    if (context.getNodeScopeHook()) {
      writer.println(indent + "jjtreeOpenNodeScope(" + ns.nodeVar + ");");
    }

    if (context.getTrackTokens()) {
      writer.println(indent + ns.nodeVar + "->jjtSetFirstToken(getToken(1));");
    }
  }

  @Override
  protected final void insertCloseNodeCode(NodeScope ns, ASTWriter writer, String indent, boolean isFinal,
      ASTGeneratorContext options) {
    String closeNode = ns.getNodeDescriptor().closeNode(ns.nodeVar);
    writer.println(indent + closeNode);
    if (ns.usesCloseNodeVar() && !isFinal) {
      writer.println(indent + ns.closedVar + " = false;");
    }
    if (options.getNodeScopeHook()) {
      writer.println(indent + "if (jjtree.nodeCreated()) {");
      writer.println(indent + " jjtreeCloseNodeScope(" + ns.nodeVar + ");");
      writer.println(indent + "}");
    }

    if (options.getTrackTokens()) {
      writer.println(indent + ns.nodeVar + "->jjtSetLastToken(getToken(0));");
    }
  }

  @Override
  protected final void insertCatchBlocks(NodeScope ns, ASTWriter writer, String indent, ASTNode expansion_unit) {
    writer.openCodeBlock(null);

    // Enumeration<String> thrown_names = findThrown(ns, expansion_unit);

    writer.println(indent + "} catch (...) {"); // " + ns.exceptionVar + ") {");

    if (ns.usesCloseNodeVar()) {
      writer.println(indent + "  if (" + ns.closedVar + ") {");
      writer.println(indent + "    jjtree.clearNodeScope(" + ns.nodeVar + ");");
      writer.println(indent + "    " + ns.closedVar + " = false;");
      writer.println(indent + "  } else {");
      writer.println(indent + "    jjtree.popNode();");
      writer.println(indent + "  }");
    }

    writer.println(indent + "} {");
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
    generateNodeInterface(context);
    generateTree(context);
    generateTreeNodes(context);
    // generateOneTreeImpl();
    generateOneTreeInterface(context);
  }

  private void generateTreeState(ASTGeneratorContext context) {
    TemplateOptions options = new TemplateOptions();
    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);

    TemplateProvider template = CppTemplate.TREESTATE_H;
    template.render(context, options);

    template = CppTemplate.TREESTATE;
    template.render(context, options);
  }

  private void generateTreeConstants(ASTGeneratorContext context) {
    TemplateOptions options = new TemplateOptions();
    options.add("NODES", ASTNodeDescriptor.getNodeIds().size()).set("ordinal", i -> i).set("label",
        i -> ASTNodeDescriptor.getNodeIds().get(i));
    options.add("NODE_NAMES", ASTNodeDescriptor.getNodeNames().size()).set("ordinal", i -> i)
        .set("label", i -> ASTNodeDescriptor.getNodeNames().get(i))
        .set("chars", i -> CppFileGenerator.toCharArray(ASTNodeDescriptor.getNodeNames().get(i)));
    options.set("NAME_UPPER", JJTreeGlobals.parserName.toUpperCase());

    TemplateProvider template = CppTemplate.TREE_CONSTANTS;
    template.render(context, options, JJTreeGlobals.parserName);
  }

  private void generateVisitors(ASTGeneratorContext context) {
    if (!context.getVisitor()) {
      return;
    }

    List<String> nodeNames =
        ASTNodeDescriptor.getNodeNames().stream().filter(n -> !n.equals("void")).collect(Collectors.toList());

    TemplateOptions options = new TemplateOptions();
    options.add("NODES", nodeNames).set("type", n -> context.getNodePrefix() + n);

    String argumentType = CppASTGenerator.getVisitorArgumentType(context);
    String returnType = CppASTGenerator.getVisitorReturnType(context);
    if (!context.getVisitorDataType().equals("")) {
      argumentType = context.getVisitorDataType();
    }

    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.set("NAME_UPPER", JJTreeGlobals.parserName.toUpperCase());
    options.set("ARGUMENT_TYPE", argumentType);
    options.set("RETURN_TYPE", returnType);
    options.set("RETURN", returnType.equals("void") ? "" : "return ");
    options.set("IS_MULTI", context.getMulti());

    TemplateProvider template = CppTemplate.VISITOR;
    template.render(context, options, JJTreeGlobals.parserName);
  }

  private void generateNode(ASTGeneratorContext context) {
    TemplateOptions optionMap = new TemplateOptions();
    optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppASTGenerator.getVisitorReturnType(context));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppASTGenerator.getVisitorArgumentType(context));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppASTGenerator.getVisitorReturnType(context).equals("void")));

    TemplateProvider template = CppTemplate.NODE;
    template.render(context, optionMap);
  }

  private void generateNodeInterface(ASTGeneratorContext context) {
    TemplateOptions optionMap = new TemplateOptions();
    optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppASTGenerator.getVisitorReturnType(context));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppASTGenerator.getVisitorArgumentType(context));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppASTGenerator.getVisitorReturnType(context).equals("void")));

    TemplateProvider template = CppTemplate.NODE_H;
    template.render(context, optionMap);
  }

  private void generateTree(ASTGeneratorContext context) {
    TemplateOptions optionMap = new TemplateOptions();
    optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppASTGenerator.getVisitorReturnType(context));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppASTGenerator.getVisitorArgumentType(context));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppASTGenerator.getVisitorReturnType(context).equals("void")));
    optionMap.set(HiveCC.JJTREE_NODE_TYPE, "Tree");

    TemplateProvider template = CppTemplate.TREE;
    template.render(context, optionMap);
  }

  private void generateTreeNodes(ASTGeneratorContext context) {
    Set<String> excludes = context.getExcudeNodes();
    for (String node : context.nodesToGenerate()) {
      if (excludes.contains(node)) {
        continue;
      }

      TemplateOptions optionMap = new TemplateOptions();
      optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
      optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppASTGenerator.getVisitorReturnType(context));
      optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppASTGenerator.getVisitorArgumentType(context));
      optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
          Boolean.valueOf(CppASTGenerator.getVisitorReturnType(context).equals("void")));
      optionMap.set(HiveCC.JJTREE_NODE_TYPE, node);

      TemplateProvider template = CppTemplate.MULTINODE;
      template.render(context, optionMap);
    }
  }


  private void generateOneTreeInterface(ASTGeneratorContext context) {
    TemplateOptions optionMap = new TemplateOptions();
    optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppASTGenerator.getVisitorReturnType(context));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppASTGenerator.getVisitorArgumentType(context));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppASTGenerator.getVisitorReturnType(context).equals("void")));
    optionMap.set("NODES", context.nodesToGenerate());

    TemplateProvider template = CppTemplate.TREE_ONE;
    template.render(context, optionMap, JJTreeGlobals.parserName);
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
