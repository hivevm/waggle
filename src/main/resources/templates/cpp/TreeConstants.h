// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

#ifndef JAVACC___CPP_DEFINE___TREECONSTANTS_H
#define JAVACC___CPP_DEFINE___TREECONSTANTS_H

#include "JavaCC.h"
//@if(CPP_NAMESPACE)
namespace __CPP_NAMESPACE__ {
//@fi
enum {
//@foreach(NODES)
    __LABEL__ = __ORDINAL__,
//@end
};

//@foreach(NODE_NAMES)
static JJChar jjtNodeName_arr___ORDINAL__[] = {__CHARS__0};
//@end
static JJString jjtNodeName[] = {
//@foreach(NODE_NAMES)
    jjtNodeName_arr___ORDINAL__,
//@end
};
//@if(CPP_NAMESPACE)
}
//@fi
#endif

#pragma GCC diagnostic pop