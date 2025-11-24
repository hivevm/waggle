// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.generator.cpp;

import java.util.Set;
import java.util.stream.Collectors;

import org.hivevm.cc.HiveCC;
import org.hivevm.cc.generator.TreeGenerator;
import org.hivevm.cc.generator.TreeOptions;
import org.hivevm.cc.jjtree.ASTNode;
import org.hivevm.cc.jjtree.ASTNodeDescriptor;
import org.hivevm.cc.jjtree.ASTWriter;
import org.hivevm.cc.jjtree.NodeScope;
import org.hivevm.cc.parser.Options;
import org.hivevm.source.Template;

class CppASTGenerator extends TreeGenerator {

    @Override
    protected final void insertOpenNodeCode(NodeScope ns, ASTWriter writer, TreeOptions context) {
        var type = ns.getNodeDescriptor().getNodeType();
        var isType = context.getNodeClass().isEmpty() || context.getMulti();
        var nodeClass = isType ? type : context.getNodeClass();

        addType(type);

        writer.print(nodeClass + " *" + ns.nodeVar + " = ");
        if (context.getNodeFactory().equals("*")) {
            // Old-style multiple-implementations.
            writer.println("(" + nodeClass + "*)" + nodeClass + "::jjtCreate(" + ns.getNodeDescriptor().getNodeId() + ");");
        }
        else if (!context.getNodeFactory().isEmpty()) {
            writer.println("(" + nodeClass + "*)"
                + context.getNodeFactory() + "->jjtCreate("
                + ns.getNodeDescriptor().getNodeId() + ");");
        }
        else {
            writer.println("new " + nodeClass + "(" + ns.getNodeDescriptor().getNodeId() + ");");
        }

        if (ns.usesCloseNodeVar())
            writer.println("bool " + ns.closedVar + " = true;");

        writer.println(ns.getNodeDescriptor().openNode(ns.nodeVar));
        if (context.getNodeScopeHook())
            writer.println("jjtreeOpenNodeScope(" + ns.nodeVar + ");");

        if (context.getTrackTokens()) {
            writer.println(ns.nodeVar + "->jjtSetFirstToken(getToken(1));");
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
            writer.println(ns.nodeVar + "->jjtSetLastToken(getToken(0));");
        }
    }

    @Override
    protected final void insertCatchBlocks(NodeScope ns, ASTWriter writer, ASTNode expansion_unit) {
        writer.openCodeBlock(null);

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
        generateOneTreeInterface(context);
    }

    private void generateTreeState(TreeOptions context) {
        CppSources.TREESTATE_H.render(context);
        CppSources.TREESTATE.render(context);
    }

    private void generateTreeConstants(TreeOptions context) {
        var options = Template.newContext(context);
        options.add("NODES", ASTNodeDescriptor.getNodeIds().size())
            .set("ORDINAL", i -> i)
            .set("LABEL", i -> ASTNodeDescriptor.getNodeIds().get(i));
        options.add("NODE_NAMES", ASTNodeDescriptor.getNodeNames().size())
            .set("ORDINAL", i -> i)
            .set("label", i -> ASTNodeDescriptor.getNodeNames().get(i))
            .set("CHARS", i -> CppASTGenerator.toCharArray(ASTNodeDescriptor.getNodeNames().get(i)));
        options.set(HiveCC.JJPARSER_CPP_DEFINE, context.getParserName().toUpperCase());

        CppSources.TREE_CONSTANTS.render(options, context.getParserName());
    }

    private void generateVisitors(TreeOptions context) {
        if (!context.getVisitor()) {
            return;
        }

        var nodeNames = ASTNodeDescriptor.getNodeNames().stream()
            .filter(n -> !n.equals("void"))
            .collect(Collectors.toList());

        var options = Template.newContext(context);
        options.add("NODES", nodeNames).set("NODES_TYPE", n -> "AST" + n);

        var argumentType = CppASTGenerator.getVisitorArgumentType(context);
        var returnType = CppASTGenerator.getVisitorReturnType(context);
        if (!context.getVisitorDataType().equals("")) {
            argumentType = context.getVisitorDataType();
        }

        options.set(HiveCC.JJPARSER_CPP_DEFINE, context.getParserName().toUpperCase());
        options.set("ARGUMENT_TYPE", argumentType);
        options.set("RETURN_TYPE", returnType);
        options.set("RETURN", returnType.equals("void") ? "" : "return ");
        options.set(HiveCC.JJTREE_MULTI, context.getMulti());

        CppSources.VISITOR.render(options, context.getParserName());
    }

    private void generateNode(TreeOptions context) {
        var optionMap = Template.newContext(context);
        optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppASTGenerator.getVisitorReturnType(context));
        optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppASTGenerator.getVisitorArgumentType(context));
        optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
                CppASTGenerator.getVisitorReturnType(context).equals("void"));

        CppSources.NODE.render(optionMap);
    }

    private void generateNodeInterface(TreeOptions context) {
        var optionMap = Template.newContext(context);
        optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppASTGenerator.getVisitorReturnType(context));
        optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppASTGenerator.getVisitorArgumentType(context));
        optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
                CppASTGenerator.getVisitorReturnType(context).equals("void"));

        CppSources.NODE_H.render(optionMap);
    }

    private void generateTree(TreeOptions context) {
        var optionMap = Template.newContext(context);
        optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppASTGenerator.getVisitorReturnType(context));
        optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppASTGenerator.getVisitorArgumentType(context));
        optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
                CppASTGenerator.getVisitorReturnType(context).equals("void"));
        optionMap.set(HiveCC.JJTREE_NODE_TYPE, "Tree");

        CppSources.TREE.render(optionMap);
    }

    private void generateTreeNodes(TreeOptions context) {
        Set<String> excludes = context.getExcudeNodes();
        for (String node : nodesToGenerate()) {
            if (excludes.contains(node)) {
                continue;
            }

            var optionMap = Template.newContext(context);
            optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE,
                    CppASTGenerator.getVisitorReturnType(context));
            optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE,
                    CppASTGenerator.getVisitorArgumentType(context));
            optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
                    CppASTGenerator.getVisitorReturnType(context).equals("void"));
            optionMap.set(HiveCC.JJTREE_NODE_TYPE, node);

            CppSources.MULTINODE.render(optionMap);
        }
    }


    private void generateOneTreeInterface(TreeOptions context) {
        var optionMap = Template.newContext(context);
        optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_TYPE, CppASTGenerator.getVisitorReturnType(context));
        optionMap.set(HiveCC.JJTREE_VISITOR_DATA_TYPE, CppASTGenerator.getVisitorArgumentType(context));
        optionMap.set(HiveCC.JJTREE_VISITOR_RETURN_VOID,
                CppASTGenerator.getVisitorReturnType(context).equals("void"));
        optionMap.add("NODES", nodesToGenerate()).set("NODES_NAME", v->v);

        CppSources.TREE_ONE.render(optionMap, context.getParserName());
    }

    private static String getVisitorArgumentType(Options o) {
        String ret = o.stringValue(HiveCC.JJTREE_VISITOR_DATA_TYPE);
        return (ret == null) || ret.equals("") || ret.equals("Object") ? "void *" : ret;
    }

    private static String getVisitorReturnType(Options o) {
        String ret = o.stringValue(HiveCC.JJTREE_VISITOR_RETURN_TYPE);
        return (ret == null) || ret.equals("") || ret.equals("Object") ? "void " : ret;
    }

    // Used by the CPP code generatror
    private static String toCharArray(String s) {
        var charArray = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            charArray.append("0x" + Integer.toHexString(s.charAt(i)) + ", ");
        }
        return charArray.toString();
    }
}
