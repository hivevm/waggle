// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.generator.JJTreeCodeGenerator;
import org.hivevm.cc.jjtree.ASTNodeDescriptor;
import org.hivevm.cc.jjtree.ASTWriter;
import org.hivevm.cc.jjtree.JJTreeGlobals;
import org.hivevm.cc.jjtree.JJTreeOptions;
import org.hivevm.cc.jjtree.NodeScope;
import org.hivevm.cc.parser.Options;
import org.hivevm.cc.utils.DigestOptions;
import org.hivevm.cc.utils.DigestWriter;
import org.hivevm.cc.utils.Template;

public class CppTreeGenerator extends JJTreeCodeGenerator {

  @Override
  protected final String getPointer() {
    return "->";
  }

  @Override
  protected final String getBoolean() {
    return "bool";
  }

  @Override
  protected String getTryFinally() {
    return "";
  }

  @Override
  protected final void insertOpenNodeCode(NodeScope ns, ASTWriter io, String indent, JJTreeOptions options) {
    String type = ns.getNodeDescriptor().getNodeType();
    final String nodeClass;
    if ((options.getNodeClass().length() > 0) && !options.getMulti()) {
      nodeClass = options.getNodeClass();
    } else {
      nodeClass = type;
    }

    addType(type);

    io.print(indent + nodeClass + " *" + ns.nodeVar + " = ");
    if (options.getNodeFactory().equals("*")) {
      // Old-style multiple-implementations.
      io.println("(" + nodeClass + "*)" + nodeClass + "::jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
    } else if (options.getNodeFactory().length() > 0) {
      io.println("(" + nodeClass + "*)" + options.getNodeFactory() + "->jjtCreate(" + ns.getNodeDescriptor().getNodeId()
          + ");");
    } else {
      io.println("new " + nodeClass + "(" + ns.getNodeDescriptor().getNodeId() + ");");
    }

    if (ns.usesCloseNodeVar()) {
      io.println(indent + getBoolean() + " " + ns.closedVar + " = true;");
    }
    io.println(indent + ns.getNodeDescriptor().openNode(ns.nodeVar));
    if (options.getNodeScopeHook()) {
      io.println(indent + "jjtreeOpenNodeScope(" + ns.nodeVar + ");");
    }

    if (options.getTrackTokens()) {
      io.println(indent + ns.nodeVar + getPointer() + "jjtSetFirstToken(getToken(1));");
    }
  }

  @Override
  protected final void insertCatchBlocks(NodeScope ns, ASTWriter io, Enumeration<String> thrown_names, String indent) {
    io.println(indent + "} catch (...) {"); // " + ns.exceptionVar + ") {");

    if (ns.usesCloseNodeVar()) {
      io.println(indent + "  if (" + ns.closedVar + ") {");
      io.println(indent + "    jjtree.clearNodeScope(" + ns.nodeVar + ");");
      io.println(indent + "    " + ns.closedVar + " = false;");
      io.println(indent + "  } else {");
      io.println(indent + "    jjtree.popNode();");
      io.println(indent + "  }");
    }
  }

  @Override
  public final void generateJJTree(JJTreeOptions o) {
    generateTreeConstants(o);
    generateVisitors(o);
    generateTreeState(o);

    // TreeClasses
    generateNodeHeader(o);
    generateNodeImpl(o);
    generateMultiTreeImpl(o);
    generateOneTreeInterface(o);
    // generateOneTreeImpl();
    generateTreeInterface(o);
  }

  private void generateTreeState(JJTreeOptions o) {
    DigestOptions options = new DigestOptions(o);
    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);

    CppTemplate template = CppTemplate.TREESTATE_H;
    try (DigestWriter writer = template.createDigestWriter(options)) {
      Template.of(template, writer.options()).render(writer);
    } catch (IOException e) {
      throw new Error(e.toString());
    }

    template = CppTemplate.TREESTATE;
    try (DigestWriter writer = template.createDigestWriter(options)) {
      Template.of(template, writer.options()).render(writer);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static List<String> headersForJJTreeH = new ArrayList<>();


  private void generateNodeHeader(JJTreeOptions o) {
    DigestOptions optionMap = new DigestOptions(o);
    optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppTreeGenerator.getVisitorReturnType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppTreeGenerator.getVisitorArgumentType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppTreeGenerator.getVisitorReturnType(o).equals("void")));

    CppTemplate template = CppTemplate.NODE_H;
    try (DigestWriter writer = template.createDigestWriter(optionMap)) {
      Template.of(template, writer.options()).render(writer);
    } catch (IOException e) {
      throw new Error(e.toString());
    }
  }

  private void generateTreeInterface(JJTreeOptions o) {
    String node = "Tree";
    DigestOptions optionMap = new DigestOptions(o);
    optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppTreeGenerator.getVisitorReturnType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppTreeGenerator.getVisitorArgumentType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppTreeGenerator.getVisitorReturnType(o).equals("void")));
    optionMap.set(HiveCC.JJTREE_NODE_TYPE, node);

    CppTemplate template = CppTemplate.TREE;
    try (DigestWriter writer = template.createDigestWriter(optionMap)) {
      Template.of(template, writer.options()).render(writer);
    } catch (IOException e) {
      throw new Error(e.toString());
    }
  }

  private void generateNodeImpl(JJTreeOptions o) {
    DigestOptions optionMap = new DigestOptions(o);
    optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppTreeGenerator.getVisitorReturnType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppTreeGenerator.getVisitorArgumentType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppTreeGenerator.getVisitorReturnType(o).equals("void")));

    CppTemplate template = CppTemplate.NODE;
    try (DigestWriter writer = template.createDigestWriter(optionMap)) {
      Template.of(template, writer.options()).render(writer);
    } catch (IOException e) {
      throw new Error(e.toString());
    }
  }

  private void generateMultiTreeImpl(JJTreeOptions o) {
    Set<String> excludes = o.getExcudeNodes();
    for (String node : nodesToGenerate()) {
      if (excludes.contains(node)) {
        continue;
      }

      DigestOptions optionMap = new DigestOptions(o);
      optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
      optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppTreeGenerator.getVisitorReturnType(o));
      optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppTreeGenerator.getVisitorArgumentType(o));
      optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
          Boolean.valueOf(CppTreeGenerator.getVisitorReturnType(o).equals("void")));
      optionMap.set(HiveCC.JJTREE_NODE_TYPE, node);

      CppTemplate template = CppTemplate.MULTINODE;
      try (DigestWriter writer = template.createDigestWriter(optionMap)) {
        Template.of(template, writer.options()).render(writer);
      } catch (IOException e) {
        throw new Error(e.toString());
      }
    }
  }


  private void generateOneTreeInterface(JJTreeOptions o) {
    DigestOptions optionMap = new DigestOptions(o);
    optionMap.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppTreeGenerator.getVisitorReturnType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppTreeGenerator.getVisitorArgumentType(o));
    optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
        Boolean.valueOf(CppTreeGenerator.getVisitorReturnType(o).equals("void")));

    File file = new File(o.getOutputDirectory(), JJTreeGlobals.parserName + "Tree.h");
    try (DigestWriter writer = DigestWriter.createCpp(file, HiveCC.VERSION, optionMap)) {
      // PrintWriter ostr = outputFile.getPrintWriter();
      file.getName().replace('.', '_').toUpperCase();
      writer.println("#ifndef JAVACC_ONE_TREE_H");
      writer.println("#define JAVACC_ONE_TREE_H");
      writer.println();
      writer.println("#include \"Node.h\"");
      for (String s : nodesToGenerate()) {
        writer.println("#include \"" + s + ".h\"");
      }
      writer.println("#endif");
    } catch (IOException e) {
      throw new Error(e.toString());
    }
  }

  private void generateTreeConstants(JJTreeOptions o) {
    String name = JJTreeGlobals.parserName + "TreeConstants";
    File file = new File(o.getOutputDirectory(), name + ".h");
    CppTreeGenerator.headersForJJTreeH.add(file.getName());

    try (DigestWriter ostr = DigestWriter.createCpp(file, HiveCC.VERSION, new DigestOptions(o))) {
      List<String> nodeIds = ASTNodeDescriptor.getNodeIds();
      List<String> nodeNames = ASTNodeDescriptor.getNodeNames();

      ostr.println("#ifndef JAVACC_" + file.getName().replace('.', '_').toUpperCase());
      ostr.println("#define JAVACC_" + file.getName().replace('.', '_').toUpperCase());

      ostr.println("\n#include \"JavaCC.h\"");
      boolean hasNamespace = ((String) ostr.options().get(HiveCC.JJPARSER_CPP_NAMESPACE)).length() > 0;
      if (hasNamespace) {
        ostr.println("namespace " + ostr.options().get(HiveCC.JJPARSER_CPP_NAMESPACE) + " {");
      }
      ostr.println("enum {");
      for (int i = 0; i < nodeIds.size(); ++i) {
        String n = nodeIds.get(i);
        ostr.println("    " + n + " = " + i + ",");
      }

      ostr.println("};");
      ostr.println();

      for (int i = 0; i < nodeNames.size(); ++i) {
        ostr.print("static JJChar jjtNodeName_arr_" + i + "[] = ");
        String n = nodeNames.get(i);
        ostr.print("{");
        CppFileGenerator.printCharArray(ostr, n);
        ostr.println("0};");
      }
      ostr.println("static JJString jjtNodeName[] = {");
      for (int i = 0; i < nodeNames.size(); i++) {
        ostr.println("    jjtNodeName_arr_" + i + ",");
      }
      ostr.println("};");

      if (hasNamespace) {
        ostr.println("}");
      }


      ostr.println("#endif");
    } catch (IOException e) {
      throw new Error(e.toString());
    }
  }

  private static String getVisitMethodName(String className) {
    return "visit";
  }

  private static String getVisitorArgumentType(Options o) {
    String ret = o.stringValue(HiveCC.JJTREE_VISITOR_DATA_TYPE);
    return (ret == null) || ret.equals("") || ret.equals("Object") ? "void *" : ret;
  }

  private static String getVisitorReturnType(Options o) {
    String ret = o.stringValue(HiveCC.JJTREE_VISITOR_RETURN_TYPE);
    return (ret == null) || ret.equals("") || ret.equals("Object") ? "void " : ret;
  }

  private void generateVisitors(JJTreeOptions o) {
    if (!o.getVisitor()) {
      return;
    }

    File file = new File(o.getOutputDirectory(), JJTreeGlobals.parserName + "Visitor.h");
    try (DigestWriter ostr = DigestWriter.createCpp(file, HiveCC.VERSION, new DigestOptions(o))) {
      ostr.println("#ifndef " + file.getName().replace('.', '_').toUpperCase());
      ostr.println("#define " + file.getName().replace('.', '_').toUpperCase());
      ostr.println("\n#include \"JavaCC.h\"");
      ostr.println("#include \"" + JJTreeGlobals.parserName + "Tree.h" + "\"");

      boolean hasNamespace = ((String) ostr.options().get(HiveCC.JJPARSER_CPP_NAMESPACE)).length() > 0;
      if (hasNamespace) {
        ostr.println("namespace " + ostr.options().get(HiveCC.JJPARSER_CPP_NAMESPACE) + " {");
      }

      generateVisitorInterface(ostr, o);
      generateDefaultVisitor(ostr, o);

      if (hasNamespace) {
        ostr.println("}");
      }

      ostr.println("#endif");
    } catch (IOException ioe) {
      throw new Error(ioe.toString());
    }
  }

  private void generateVisitorInterface(PrintWriter ostr, JJTreeOptions o) {
    String name = JJTreeGlobals.parserName + "Visitor";
    List<String> nodeNames = ASTNodeDescriptor.getNodeNames();

    ostr.println("class " + name);
    ostr.println("{");

    String argumentType = CppTreeGenerator.getVisitorArgumentType(o);
    String returnType = CppTreeGenerator.getVisitorReturnType(o);
    if (!o.getVisitorDataType().equals("")) {
      argumentType = o.getVisitorDataType();
    }
    ostr.println("  public:");

    ostr.println("  virtual " + returnType + " visit(const Node *node, " + argumentType + " data) = 0;");
    if (o.getMulti()) {
      for (String n : nodeNames) {
        if (n.equals("void")) {
          continue;
        }
        String nodeType = o.getNodePrefix() + n;
        ostr.println("  virtual " + returnType + " " + CppTreeGenerator.getVisitMethodName(nodeType) + "(const "
            + nodeType + " *node, " + argumentType + " data) = 0;");
      }
    }

    ostr.println("  virtual ~" + name + "() { }");
    ostr.println("};");
  }

  private void generateDefaultVisitor(PrintWriter ostr, JJTreeOptions o) {
    String className = JJTreeGlobals.parserName + "DefaultVisitor";
    List<String> nodeNames = ASTNodeDescriptor.getNodeNames();

    ostr.println("class " + className + " : public " + JJTreeGlobals.parserName + "Visitor {");

    String argumentType = CppTreeGenerator.getVisitorArgumentType(o);
    String ret = CppTreeGenerator.getVisitorReturnType(o);

    ostr.println("public:");
    ostr.println("  virtual " + ret + " defaultVisit(const Node *node, " + argumentType + " data) = 0;");

    ostr.println("  virtual " + ret + " visit(const Node *node, " + argumentType + " data) {");
    ostr.println("    " + (ret.trim().equals("void") ? "" : "return ") + "defaultVisit(node, data);");
    ostr.println("}");

    if (o.getMulti()) {
      for (String n : nodeNames) {
        if (n.equals("void")) {
          continue;
        }
        String nodeType = o.getNodePrefix() + n;
        ostr.println("  virtual " + ret + " " + CppTreeGenerator.getVisitMethodName(nodeType) + "(const " + nodeType
            + " *node, " + argumentType + " data) {");
        ostr.println("    " + (ret.trim().equals("void") ? "" : "return ") + "defaultVisit(node, data);");
        ostr.println("  }");
      }
    }
    ostr.println("  ~" + className + "() { }");
    ostr.println("};");
  }
}
