// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.generator.ASTGenerator;
import org.hivevm.cc.jjtree.ASTNode;
import org.hivevm.cc.jjtree.ASTNodeDescriptor;
import org.hivevm.cc.jjtree.ASTWriter;
import org.hivevm.cc.jjtree.JJTreeGlobals;
import org.hivevm.cc.jjtree.JJTreeOptions;
import org.hivevm.cc.jjtree.NodeScope;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.utils.TemplateOptions;
import org.hivevm.cc.utils.TemplateProvider;

public class CppASTGenerator extends ASTGenerator {

  @Override
  protected final void insertOpenNodeCode(NodeScope ns, ASTWriter writer, String indent, JJTreeOptions options) {
    String type = ns.getNodeDescriptor().getNodeType();
    boolean isType = options.getNodeClass().isEmpty() || options.getMulti();
    String nodeClass = isType ? type : options.getNodeClass();

    addType(type);

    writer.print(indent + nodeClass + " *" + ns.nodeVar + " = ");
    if (options.getNodeFactory().equals("*")) {
      // Old-style multiple-implementations.
      writer.println("(" + nodeClass + "*)" + nodeClass + "::jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
    } else if (options.getNodeFactory().length() > 0) {
      writer.println("(" + nodeClass + "*)" + options.getNodeFactory() + "->jjtCreate("
          + ns.getNodeDescriptor().getNodeId() + ");");
    } else {
      writer.println("new " + nodeClass + "(" + ns.getNodeDescriptor().getNodeId() + ");");
    }

    if (ns.usesCloseNodeVar()) {
      writer.println(indent + "bool " + ns.closedVar + " = true;");
    }
    writer.println(indent + ns.getNodeDescriptor().openNode(ns.nodeVar));
    if (options.getNodeScopeHook()) {
      writer.println(indent + "jjtreeOpenNodeScope(" + ns.nodeVar + ");");
    }

    if (options.getTrackTokens()) {
      writer.println(indent + ns.nodeVar + "->jjtSetFirstToken(getToken(1));");
    }
  }

  @Override
  protected final void insertCloseNodeCode(NodeScope ns, ASTWriter writer, String indent, boolean isFinal,
      JJTreeOptions options) {
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

//    Enumeration<String> thrown_names = findThrown(ns, expansion_unit);

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
  public final void generate(JJTreeOptions o) {
    generateTreeState(o);
    generateTreeConstants(o);
    generateVisitors(o);

    // TreeClasses
    generateNode(o);
    generateNodeInterface(o);
    generateTree(o);
    generateTreeNodes(o);
    // generateOneTreeImpl();
    generateOneTreeInterface(o);
  }

  private void generateTreeState(JJTreeOptions o) {
    TemplateOptions options = new TemplateOptions();
    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);

    TemplateProvider template = CppTemplate.TREESTATE_H;
    template.render(o, options);

    template = CppTemplate.TREESTATE;
    template.render(o, options);
  }

  private void generateTreeConstants(JJTreeOptions o) {
    TemplateOptions options = new TemplateOptions();
    options.add("NODES", ASTNodeDescriptor.getNodeIds().size()).set("ordinal", i -> i).set("label",
        i -> ASTNodeDescriptor.getNodeIds().get(i));
    options.add("NODE_NAMES", ASTNodeDescriptor.getNodeNames().size()).set("ordinal", i -> i)
        .set("label", i -> ASTNodeDescriptor.getNodeNames().get(i))
        .set("chars", i -> CppFileGenerator.toCharArray(ASTNodeDescriptor.getNodeNames().get(i)));
    options.set("NAME_UPPER", JJTreeGlobals.parserName.toUpperCase());

    TemplateProvider template = CppTemplate.TREE_CONSTANTS;
    template.render(o, options, JJTreeGlobals.parserName);
  }

  private void generateVisitors(JJTreeOptions o) {
    if (!o.getVisitor()) {
      return;
    }

    List<String> nodeNames =
        ASTNodeDescriptor.getNodeNames().stream().filter(n -> !n.equals("void")).collect(Collectors.toList());

    TemplateOptions options = new TemplateOptions();
    options.add("NODES", nodeNames).set("type", n -> o.getNodePrefix() + n);

    String argumentType = CppASTGenerator.getVisitorArgumentType(o);
    String returnType = CppASTGenerator.getVisitorReturnType(o);
    if (!o.getVisitorDataType().equals("")) {
      argumentType = o.getVisitorDataType();
    }

    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.set("NAME_UPPER", JJTreeGlobals.parserName.toUpperCase());
    options.set("ARGUMENT_TYPE", argumentType);
    options.set("RETURN_TYPE", returnType);
    options.set("RETURN", returnType.equals("void") ? "" : "return ");
    options.set("IS_MULTI", o.getMulti());

    TemplateProvider template = CppTemplate.VISITOR;
    template.render(o, options, JJTreeGlobals.parserName);
  }

  private void generateNode(JJTreeOptions o) {
    TemplateOptions optionMap = new TemplateOptions();
    optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppASTGenerator.getVisitorReturnType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppASTGenerator.getVisitorArgumentType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppASTGenerator.getVisitorReturnType(o).equals("void")));

    TemplateProvider template = CppTemplate.NODE;
    template.render(o, optionMap);
  }

  private void generateNodeInterface(JJTreeOptions o) {
    TemplateOptions optionMap = new TemplateOptions();
    optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppASTGenerator.getVisitorReturnType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppASTGenerator.getVisitorArgumentType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppASTGenerator.getVisitorReturnType(o).equals("void")));

    TemplateProvider template = CppTemplate.NODE_H;
    template.render(o, optionMap);
  }

  private void generateTree(JJTreeOptions o) {
    TemplateOptions optionMap = new TemplateOptions();
    optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppASTGenerator.getVisitorReturnType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppASTGenerator.getVisitorArgumentType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppASTGenerator.getVisitorReturnType(o).equals("void")));
    optionMap.set(HiveCC.JJTREE_NODE_TYPE, "Tree");

    TemplateProvider template = CppTemplate.TREE;
    template.render(o, optionMap);
  }

  private void generateTreeNodes(JJTreeOptions o) {
    Set<String> excludes = o.getExcudeNodes();
    for (String node : nodesToGenerate()) {
      if (excludes.contains(node)) {
        continue;
      }

      TemplateOptions optionMap = new TemplateOptions();
      optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
      optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppASTGenerator.getVisitorReturnType(o));
      optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppASTGenerator.getVisitorArgumentType(o));
      optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
          Boolean.valueOf(CppASTGenerator.getVisitorReturnType(o).equals("void")));
      optionMap.set(HiveCC.JJTREE_NODE_TYPE, node);

      TemplateProvider template = CppTemplate.MULTINODE;
      template.render(o, optionMap);
    }
  }


  private void generateOneTreeInterface(JJTreeOptions o) {
    TemplateOptions optionMap = new TemplateOptions();
    optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppASTGenerator.getVisitorReturnType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppASTGenerator.getVisitorArgumentType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppASTGenerator.getVisitorReturnType(o).equals("void")));
    optionMap.set("NODES", nodesToGenerate());

    TemplateProvider template = CppTemplate.TREE_ONE;
    template.render(o, optionMap, JJTreeGlobals.parserName);
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
