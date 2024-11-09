// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.generator.JJTreeCodeGenerator;
import org.hivevm.cc.jjtree.ASTNodeDescriptor;
import org.hivevm.cc.jjtree.ASTWriter;
import org.hivevm.cc.jjtree.JJTreeGlobals;
import org.hivevm.cc.jjtree.JJTreeOptions;
import org.hivevm.cc.jjtree.NodeScope;
import org.hivevm.cc.utils.DigestOptions;
import org.hivevm.cc.utils.DigestWriter;
import org.hivevm.cc.utils.Template;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

public class JavaTreeGenerator extends JJTreeCodeGenerator {

  @Override
  protected String getPointer() {
    return ".";
  }

  @Override
  protected String getBoolean() {
    return "boolean";
  }

  @Override
  protected final String getTryFinally() {
    return "finally ";
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

    io.print(indent + nodeClass + " " + ns.nodeVar + " = ");
    if (options.getNodeFactory().equals("*")) {
      // Old-style multiple-implementations.
      io.println("(" + nodeClass + ")" + nodeClass + ".jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
    } else if (options.getNodeFactory().length() > 0) {
      io.println(
          "(" + nodeClass + ")" + options.getNodeFactory() + ".jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
    } else {
      io.println("new " + nodeClass + "(this, " + ns.getNodeDescriptor().getNodeId() + ");");
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
  protected final void insertCatchBlocks(NodeScope ns, ASTWriter io, Enumeration<String> thrown_names,
      String indent) {
    if (thrown_names.hasMoreElements()) {
      io.println(indent + "} catch (Throwable " + ns.exceptionVar + ") {");

      if (ns.usesCloseNodeVar()) {
        io.println(indent + "  if (" + ns.closedVar + ") {");
        io.println(indent + "    jjtree.clearNodeScope(" + ns.nodeVar + ");");
        io.println(indent + "    " + ns.closedVar + " = false;");
        io.println(indent + "  } else {");
        io.println(indent + "    jjtree.popNode();");
        io.println(indent + "  }");
      }

      String thrown = null;
      while (thrown_names.hasMoreElements()) {
        thrown = thrown_names.nextElement();
        io.println(indent + "  if (" + ns.exceptionVar + " instanceof " + thrown + ") {");
        io.println(indent + "    throw (" + thrown + ")" + ns.exceptionVar + ";");
        io.println(indent + "  }");
      }
      // This is either an Error or an undeclared Exception. If it's an Error then the cast is good,
      // otherwise we want to force the user to declare it by crashing on the bad cast.
      io.println(indent + "  throw (Error)" + ns.exceptionVar + ";");
    }
  }

  @Override
  public final void generateJJTree(JJTreeOptions o) {
    generateTreeConstants(o);
    generateVisitors(o);
    generateTreeState(o);
    generateDefaultVisitors(o);

    // TreeClasses
    generateNodeClass(o);
    generateTreeClass(o);
    generateTreeClasses(o);
  }

  private void generateTreeState(JJTreeOptions o) {
    DigestOptions options = new DigestOptions(o);
    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.set(HiveCC.JJPARSER_JAVA_PACKAGE, o.getJavaPackage());

    String filePrefix = new File(o.getOutputDirectory(), "JJT" + JJTreeGlobals.parserName + "State").getAbsolutePath();

    File file = new File(filePrefix + ".java");
    try (DigestWriter ostr = DigestWriter.create(file, HiveCC.VERSION, options)) {
      Template.of("/templates/java/TreeState.template", options).render(ostr);
    } catch (IOException e) {
      throw new Error(e.toString());
    }
  }

  private static void generatePrologue(PrintWriter ostr, JJTreeOptions o) {
    ostr.println("package " + o.getJavaPackage() + ";");
    ostr.println();
  }

  private void generateTreeConstants(JJTreeOptions o) {
    String name = JJTreeGlobals.parserName + "TreeConstants";
    File file = new File(o.getOutputDirectory(), name + ".java");

    try (PrintWriter ostr = DigestWriter.create(file, HiveCC.VERSION, new DigestOptions(o))) {
      List<String> nodeIds = ASTNodeDescriptor.getNodeIds();
      List<String> nodeNames = ASTNodeDescriptor.getNodeNames();

      JavaTreeGenerator.generatePrologue(ostr, o);
      ostr.println("public interface " + name);
      ostr.println("{");

      for (int i = 0; i < nodeIds.size(); ++i) {
        String n = nodeIds.get(i);
        ostr.println("  public final int " + n + " = " + i + ";");
      }

      ostr.println();
      ostr.println();

      ostr.println("  public static String[] jjtNodeName = {");
      for (String n : nodeNames) {
        ostr.println("    \"" + n + "\",");
      }
      ostr.println("  };");

      ostr.println("}");
    } catch (IOException e) {
      throw new Error(e.toString());
    }
  }

  private void generateVisitors(JJTreeOptions o) {
    if (!o.getVisitor()) {
      return;
    }

    String name = JJTreeGlobals.parserName + "Visitor";
    File file = new File(o.getOutputDirectory(), name + ".java");

    try (PrintWriter ostr = DigestWriter.create(file, HiveCC.VERSION, new DigestOptions(o))) {
      List<String> nodeNames = ASTNodeDescriptor.getNodeNames();

      JavaTreeGenerator.generatePrologue(ostr, o);
      ostr.println("public interface " + name);
      ostr.println("{");

      String ve = JavaTreeGenerator.mergeVisitorException(o);

      String argumentType = "Object";
      if (!o.getVisitorDataType().equals("")) {
        argumentType = o.getVisitorDataType();
      }

      ostr.println("  public " + o.getVisitorReturnType() + " visit(Node node, " + argumentType + " data)" + ve + ";");
      if (o.getMulti()) {
        for (String n : nodeNames) {
          if (n.equals("void")) {
            continue;
          }
          String nodeType = o.getNodePrefix() + n;
          ostr.println("  public " + o.getVisitorReturnType() + " visit(" + nodeType + " node, " + argumentType
              + " data)" + ve + ";");
        }
      }
      ostr.println("}");
    } catch (IOException e) {
      throw new Error(e.toString());
    }
  }

  private void generateDefaultVisitors(JJTreeOptions o) {
    if (!o.getVisitor()) {
      return;
    }

    String className = JJTreeGlobals.parserName + "DefaultVisitor";
    File file = new File(o.getOutputDirectory(), className + ".java");

    try (PrintWriter ostr = DigestWriter.create(file, HiveCC.VERSION, new DigestOptions(o))) {
      List<String> nodeNames = ASTNodeDescriptor.getNodeNames();

      JavaTreeGenerator.generatePrologue(ostr, o);
      ostr.println("public class " + className + " implements " + JJTreeGlobals.parserName + "Visitor{");

      final String ve = JavaTreeGenerator.mergeVisitorException(o);

      String argumentType = "Object";
      if (!o.getVisitorDataType().equals("")) {
        argumentType = o.getVisitorDataType().trim();
      }

      final String returnType = o.getVisitorReturnType().trim();
      final boolean isVoidReturnType = "void".equals(returnType);

      ostr.println("  public " + returnType + " defaultVisit(Node node, " + argumentType + " data)" + ve + "{");
      ostr.println("    node.childrenAccept(this, data);");
      ostr.print("    return");
      if (!isVoidReturnType) {
        if (returnType.equals(argumentType)) {
          ostr.print(" data");
        } else if ("boolean".equals(returnType)) {
          ostr.print(" false");
        } else if ("int".equals(returnType)) {
          ostr.print(" 0");
        } else if ("long".equals(returnType)) {
          ostr.print(" 0L");
        } else if ("double".equals(returnType)) {
          ostr.print(" 0.0d");
        } else if ("float".equals(returnType)) {
          ostr.print(" 0.0f");
        } else if ("short".equals(returnType)) {
          ostr.print(" 0");
        } else if ("byte".equals(returnType)) {
          ostr.print(" 0");
        } else if ("char".equals(returnType)) {
          ostr.print(" '\u0000'");
        } else {
          ostr.print(" null");
        }
      }
      ostr.println(";");
      ostr.println("  }");

      ostr.println("  public " + returnType + " visit(Node node, " + argumentType + " data)" + ve + "{");
      ostr.println("    " + (isVoidReturnType ? "" : "return ") + "defaultVisit(node, data);");
      ostr.println("  }");

      if (o.getMulti()) {
        for (String n : nodeNames) {
          if (n.equals("void")) {
            continue;
          }
          String nodeType = o.getNodePrefix() + n;
          ostr.println(
              "  public " + returnType + " visit(" + nodeType + " node, " + argumentType + " data)" + ve + "{");
          ostr.println("    " + (isVoidReturnType ? "" : "return ") + "defaultVisit(node, data);");
          ostr.println("  }");
        }
      }

      ostr.println("}");
    } catch (final IOException e) {
      throw new Error(e.toString());
    }
  }

  private void generateTreeClass(JJTreeOptions o) {
    String nodeType = "Tree";
    File file = new File(o.getOutputDirectory(), nodeType + ".java");

    DigestOptions options = new DigestOptions(o);
    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.set(HiveCC.JJPARSER_JAVA_PACKAGE, o.getJavaPackage());
    try (DigestWriter writer = DigestWriter.create(file, HiveCC.VERSION, options)) {
      Template.of("/templates/java/Tree.template", options).render(writer);
    } catch (IOException e) {
      throw new Error(e.toString());
    }
  }

  private void generateNodeClass(JJTreeOptions o) {
    String nodeType = "Node";
    File file = new File(o.getOutputDirectory(), nodeType + ".java");

    DigestOptions options = new DigestOptions(o);
    options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
    options.set(HiveCC.JJPARSER_JAVA_PACKAGE, o.getJavaPackage());
    try (DigestWriter writer = DigestWriter.create(file, HiveCC.VERSION, options)) {
      Template.of("/templates/java/Node.template", options).render(writer);
    } catch (IOException e) {
      throw new Error(e.toString());
    }
  }

  private void generateTreeClasses(JJTreeOptions o) {
    Set<String> excludes = o.getExcudeNodes();
    for (String nodeType : nodesToGenerate()) {
      if (!o.getBuildNodeFiles() || excludes.contains(nodeType)) {
        continue;
      }

      File file = new File(o.getOutputDirectory(), nodeType + ".java");

      DigestOptions options = new DigestOptions(o);
      options.set(HiveCC.PARSER_NAME, JJTreeGlobals.parserName);
      options.set(HiveCC.JJPARSER_JAVA_PACKAGE, o.getJavaPackage());
      try (DigestWriter writer = DigestWriter.create(file, HiveCC.VERSION, options)) {
        options.set(HiveCC.JJTREE_NODE_TYPE, nodeType);
        options.set(HiveCC.JJTREE_VISITOR_RETURN_VOID, Boolean.valueOf(o.getVisitorReturnType().equals("void")));
        Template.of("/templates/java/MultiNode.template", options).render(writer);
      } catch (IOException e) {
        throw new Error(e.toString());
      }
    }
  }

  private static String mergeVisitorException(JJTreeOptions o) {
    String ve = o.getVisitorException();
    if (!"".equals(ve)) {
      ve = " throws " + ve;
    }
    return ve;
  }
}
