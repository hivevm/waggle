package __JAVA_PACKAGE__;

public interface NodeVisitor {

    __RETURN_TYPE__ visit(Node node, __ARGUMENT_TYPE__ data)__EXCEPTION__;
//@if(NODE_MULTI)
//@foreach(NODES)

    __RETURN_TYPE__ visit(AST__NODES_NAME__ node, __ARGUMENT_TYPE__ data)__EXCEPTION__;
//@end
//@fi
}