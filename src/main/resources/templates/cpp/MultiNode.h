// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

#ifndef JAVACC___NODE_TYPE__
#define JAVACC___NODE_TYPE__

#include "__NODE_CLASS__.h"

//@if(CPP_NAMESPACE)
namespace __CPP_NAMESPACE__ {
//@fi

class __NODE_TYPE__ : public __NODE_CLASS__ {
public: 
  __NODE_TYPE__(int id);
  virtual ~__NODE_TYPE__();

//@if(VISITOR)
  /** Accept the visitor. **/
  virtual __VISITOR_RETURN_TYPE__ jjtAccept(__PARSER_NAME__Visitor* visitor, __VISITOR_DATA_TYPE__ data) const ;
//@fi
};

//@if(CPP_NAMESPACE)
}
//@fi

#endif

#pragma GCC diagnostic pop