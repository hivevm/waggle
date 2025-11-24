// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

#ifndef JAVACC_TREE
#define JAVACC_TREE

#include <vector>
#include "JavaCC.h"
#include "Token.h"

//@if(CPP_NAMESPACE)
namespace __CPP_NAMESPACE__ {
//@fi
// All AST nodes must implement this interface.
// It provides basic  machinery for constructing the parent and child relationships between nodes.

class Node;
class __PARSER_NAME__;
//@if(VISITOR)
class __PARSER_NAME__Visitor;
//@fi

class Tree {
    friend class Node;

public:
    // This method is called after the node has been made the current node. It indicates that child nodes can now be added to it.
    virtual void    jjtOpen() const = 0;

    // This method is called after all the child nodes have been added.
    virtual void    jjtClose() const = 0;

    // This pair of methods are used to inform the node of its parent.
    virtual void    jjtSetParent(Node *n) = 0;
    virtual Node*   jjtGetParent() const = 0;

    // This method tells the node to add its argument to the node's list of children.
    virtual void    jjtAddChild(Node *n, int i) = 0;

    // This method returns a child node.  The children are numbered from zero, left to right.
    virtual Node*   jjtGetChild(int i) const = 0;

    // Return the number of children the node has.
    virtual int     jjtGetNumChildren() const = 0;
    virtual int     getId() const = 0;

//@if(VISITOR)
    // Accept the visitor.
    virtual __VISITOR_RETURN_TYPE:void__ jjtAccept(__PARSER_NAME__Visitor *visitor, __VISITOR_DATA_TYPE:void *__ data) const = 0;
//@fi

private: 
    // Clear list of children, and return children that we have before. Used in destructor to do linear destruction of tree.
    virtual std::vector<Node*>& jjtChildren() = 0;

public:
    Tree() { }
    virtual ~Tree() { }
};

//@if(NODE_FACTORY)
    class __NODE_FACTORY__;
    extern __NODE_FACTORY__ *nodeFactory;
  
    // Takes ownerhip of the factory
    void setNodeFactory(__NODE_FACTORY__ *factory);
    __NODE_FACTORY__ *getNodeFactory();
//@fi

//@if(CPP_NAMESPACE)
}
//@fi

#endif

#pragma GCC diagnostic pop