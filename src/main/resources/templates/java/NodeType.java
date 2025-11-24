package __JAVA_PACKAGE__;

public interface NodeType {
//@foreach(NODES)
    int __NODES_LABEL__ = __NODES_ORDINAL__;
//@end


    String[] jjtNodeName = {
//@foreach(NODE_NAMES)
        "__NODE_NAMES_TITLE__",
//@end
    };
}
