// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

#ifndef __DEFINE_VISITOR___VISITOR
#define __DEFINE_VISITOR___VISITOR

#include "JavaCC.h"
#include "__PARSER_NAME__Tree.h"

//@if(CPP_NAMESPACE)
namespace __CPP_NAMESPACE__ {
//@fi

class __PARSER_NAME__Visitor
{
  public:

  virtual __RETURN_TYPE__ visit(const Node *node, __ARGUMENT_TYPE__ data) = 0;
//@if(NODE_MULTI)
//@foreach(NODES)
  virtual __RETURN_TYPE__ visit(const {{NODES_TYPE} *node, __ARGUMENT_TYPE__ data) = 0;
//@end
//@endif

  virtual ~__PARSER_NAME__Visitor() { }
};
    

class __PARSER_NAME__DefaultVisitor() : public __PARSER_NAME__Visitor {

public:
  virtual __RETURN_TYPE__ defaultVisit(const Node *node, __ARGUMENT_TYPE__ data) = 0;

  virtual __RETURN_TYPE__ visit(const Node *node, __ARGUMENT_TYPE__ data) {
     __RETURN__defaultVisit(node, data);
  }

//@if(NODE_MULTI)
//@foreach(NODES)
  virtual __RETURN_TYPE__ visit(const __NODES_TYPE__ *node, __ARGUMENT_TYPE__ data) {
     __RETURN__defaultVisit(node, data);
  }
//@end
//@endif

  ~__PARSER_NAME__DefaultVisitor() { }
};

//@if(CPP_NAMESPACE)
}
//@fi

#endif

#pragma GCC diagnostic pop