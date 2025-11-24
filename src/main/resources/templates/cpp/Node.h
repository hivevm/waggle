// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

#ifndef JAVACC_NODE
#define JAVACC_NODE

#include <vector>
#include "JavaCC.h"
#include "Token.h"
#include "Tree.h"
#include "__PARSER_NAME__TreeConstants.h"
//@if(NODE_EXTENDS)
#include "__NODE_EXTENDS__.h"
//@fi

//@if(CPP_NAMESPACE)
namespace __CPP_NAMESPACE__ {
//@fi
//@if(VISITOR)
class __PARSER_NAME__Visitor;
//@fi

class __PARSER_NAME__;

//@if(NODE_EXTENDS)
class Node : public __NODE_EXTENDS__, public Tree {
//@else
class Node : public Tree {
//@fi
protected: 
    std::vector<Node*> children;
    Node*              parent;
    void*              value;
//@if(TRACK_TOKENS)
    Token*             firstToken;
    Token*             lastToken;
//@fi
    __PARSER_NAME__*    parser;
    int                id;

public: 
    Node(int id);
    Node(__PARSER_NAME__* parser, int id);
    virtual ~Node();

// @_if(!NODE_FACTORY)
//#define jjtCreate(id) new Node(id)
//#define jjtCreate(p, id) new Node(p, id)
// @_fi
    virtual void jjtOpen() const;
    virtual void jjtClose() const;
    virtual void jjtSetParent(Node *n);
    virtual Node * jjtGetParent() const;
    virtual void jjtAddChild(Node *n, int i);
    virtual Node * jjtGetChild(int i) const;
    virtual int jjtGetNumChildren() const;
    virtual void jjtSetValue(void * value);
    virtual void * jjtGetValue() const;

//@if(TRACK_TOKENS)
    virtual Token * jjtGetFirstToken() const;
    virtual Token * jjtGetLastToken() const;
    virtual void jjtSetFirstToken(Token* token);
    virtual void jjtSetLastToken(Token* token);

//@fi
//@if(VISITOR)
    virtual __VISITOR_RETURN_TYPE:void__ jjtAccept(__PARSER_NAME__Visitor* visitor,__VISITOR_DATA_TYPE__ data) const;
    virtual void jjtChildrenAccept(__PARSER_NAME__Visitor* visitor, __VISITOR_DATA_TYPE__ data) const;
    virtual void jjtChildAccept(int childNo, __PARSER_NAME__Visitor* visitor, __VISITOR_DATA_TYPE__ data) const;
//@fi
    virtual std::vector<Node*>& jjtChildren();

  /* You can override these two methods in subclasses of Node to customize the way the node appears when the tree is dumped.
     If your output uses more than one line you should override toString(string), otherwise overriding toString() is probably all
     you need to do. */
    virtual JJString toString() const;
    virtual JJString toString(const JJString& prefix) const;

  /* Override this method if you want to customize how the node dumps out its children. */
    virtual void dump(const JJString& prefix) const;
    virtual void dumpToBuffer(const JJString& prefix, const JJString& separator, JJString* sb) const;
    virtual int getId() const { return id;  }
};
//@if(CPP_NAMESPACE)
}
//@fi

#endif

#pragma GCC diagnostic pop