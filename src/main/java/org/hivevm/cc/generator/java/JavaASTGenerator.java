// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.java;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.stream.Collectors;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.generator.CodeBlock;
import org.hivevm.cc.generator.TreeGenerator;
import org.hivevm.cc.generator.TreeOptions;
import org.hivevm.cc.jjtree.ASTBNFNonTerminal;
import org.hivevm.cc.jjtree.ASTNode;
import org.hivevm.cc.jjtree.ASTNodeDescriptor;
import org.hivevm.cc.jjtree.ASTWriter;
import org.hivevm.cc.jjtree.NodeScope;
import org.hivevm.source.Template;

class JavaASTGenerator extends TreeGenerator {

    @Override
    protected final void insertOpenNodeCode(NodeScope ns, ASTWriter writer, TreeOptions context) {
        var type = ns.getNodeDescriptor().getNodeType();
        var isType = context.getNodeClass().isEmpty() || context.getMulti();
        var nodeClass = isType ? type : context.getNodeClass();

        addType(type);

        writer.print(nodeClass + " " + ns.nodeVar + " = ");
        if (context.getNodeFactory().equals("*")) {
            // Old-style multiple-implementations.
            writer.println("(" + nodeClass + ")" + nodeClass + ".jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
        }
        else if (!context.getNodeFactory().isEmpty()) {
            writer.println("(" + nodeClass + ")"
                + context.getNodeFactory() + ".jjtCreate("
                + ns.getNodeDescriptor().getNodeId() + ");");
        }
        else {
            writer.println("new " + nodeClass + "(this, " + "NodeType." + ns.getNodeDescriptor().getNodeId() + ");");
        }

        if (ns.usesCloseNodeVar())
            writer.println("boolean " + ns.closedVar + " = true;");

        writer.println(ns.getNodeDescriptor().openNode(ns.nodeVar));
        if (context.getNodeScopeHook())
            writer.println("jjtreeOpenNodeScope(" + ns.nodeVar + ");");

        if (context.getTrackTokens()) {
            writer.println(ns.nodeVar + ".jjtSetFirstToken(getToken(1));");
        }
        writer.print("try {");
    }

    @Override
    protected final void insertCloseNodeCode(NodeScope ns, ASTWriter writer, TreeOptions options,
                                             boolean isFinal) {
        writer.println(ns.getNodeDescriptor().closeNode(ns.nodeVar));
        if (ns.usesCloseNodeVar() && !isFinal) {
            writer.println(ns.closedVar + " = false;");
        }
        if (options.getNodeScopeHook()) {
            writer.println("if (jjtree.nodeCreated()) {");
            writer.println(" jjtreeCloseNodeScope(" + ns.nodeVar + ");");
            writer.println("}");
        }

        if (options.getTrackTokens()) {
            writer.println(ns.nodeVar + ".jjtSetLastToken(getToken(0));");
        }
    }

    @Override
    protected final void insertCatchBlocks(NodeScope ns, ASTWriter writer, ASTNode expansion_unit) {
        writer.append("\n").append(CodeBlock.begin());

        var thrown_names = findThrown(ns, expansion_unit);
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

            String thrown;
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
        writer.append(CodeBlock.end());
    }

    @Override
    public final void generate(TreeOptions context) {
        generateTreeConstants(context);
        generateVisitors(context);

        // TreeClasses
        generateNode(context);
        generateTreeNodes(context);

        JavaSources.NODESTATE.render(context);
    }

    private void generateTreeConstants(TreeOptions context) {
        var options = Template.newContext(context);
        options.add("NODE_NAMES", ASTNodeDescriptor.getNodeNames())
            .set("NODE_NAMES_TITLE", i -> i);
        options.add("NODES", ASTNodeDescriptor.getNodeIds().size())
            .set("NODES_ORDINAL", i -> i)
            .set("NODES_LABEL", i -> ASTNodeDescriptor.getNodeIds().get(i));

        JavaSources.NODETYPE.render(options, context.getParserName());
    }

    private void generateVisitors(TreeOptions context) {
        if (!context.getVisitor()) {
            return;
        }

        var nodes = ASTNodeDescriptor.getNodeNames().stream()
            .filter(n -> !n.equals("void"))
            .collect(Collectors.toList());
        var argumentType = context.getVisitorDataType().equals("") ? "Object" : context.getVisitorDataType().trim();
        var returnValue = JavaASTGenerator.returnValue(context.getVisitorReturnType(), argumentType);
        var isVoidReturnType = "void".equals(context.getVisitorReturnType());

        var options = Template.newContext(context);
        options.add("NODES", nodes)
            .set("NODES_NAME", i -> i);
        options.set("RETURN_TYPE", context.getVisitorReturnType());
        options.set("RETURN_VALUE", returnValue);
        options.set("RETURN", isVoidReturnType ? "" : "return ");
        options.set("ARGUMENT_TYPE", argumentType);
        options.set("EXCEPTION", JavaASTGenerator.mergeVisitorException(context));
        options.set(HiveCC.JJTREE_MULTI, context.getMulti());

        JavaSources.MULTI_NODE_VISITOR.render(options);
        JavaSources.MULTI_NODE_DEFAULT_VISITOR.render(options);
    }

    private void generateNode(TreeOptions context) {
        JavaSources.NODE.render(context);
    }

    private void generateTreeNodes(TreeOptions context) {
        var options = Template.newContext(context);
        options.set(HiveCC.JJTREE_VISITOR_RETURN_VOID, context.getVisitorReturnType().equals("void"));

        var excludes = context.getExcudeNodes();
        for (String nodeType : nodesToGenerate()) {
            if (!context.getBuildNodeFiles() || excludes.contains(nodeType)) {
                continue;
            }
            options.set(HiveCC.JJTREE_NODE_TYPE, nodeType);

            JavaSources.MULTI_NODE.render(options, nodeType);
        }
    }

    private static String mergeVisitorException(TreeOptions context) {
        var ve = context.getVisitorException();
        return "".equals(ve) ? ve : " throws " + ve;
    }

    private static String returnValue(String returnType, String argumentType) {
        var isVoidReturnType = "void".equals(returnType);
        if (isVoidReturnType) {
            return "";
        }

        if (returnType.equals(argumentType)) {
            return " data";
        }

        return switch (returnType) {
            case "boolean" -> " false";
            case "int", "short", "byte" -> " 0";
            case "long" -> " 0L";
            case "double" -> " 0.0d";
            case "float" -> " 0.0f";
            case "char" -> " '\u0000'";
            default -> " null";
        };
    }

    private Enumeration<String> findThrown(NodeScope ns, ASTNode expansion_unit) {
        var thrown_set = new Hashtable<String, String>();
        findThrown(ns, thrown_set, expansion_unit);
        return thrown_set.elements();
    }


    private void findThrown(NodeScope ns, Hashtable<String, String> thrown_set,
                            ASTNode expansion_unit) {
        if (expansion_unit instanceof ASTBNFNonTerminal) {
            // Should really make the nonterminal explicitly maintain its name.
            var nt = expansion_unit.getFirstToken().image;
            var prod = expansion_unit.jjtParser().getProduction(nt);
            if (prod != null) {
                prod.throwElements().forEach(t -> thrown_set.put(t, t));
            }
        }
        for (int i = 0; i < expansion_unit.jjtGetNumChildren(); ++i) {
            findThrown(ns, thrown_set, (ASTNode) expansion_unit.jjtGetChild(i));
        }
    }
}
