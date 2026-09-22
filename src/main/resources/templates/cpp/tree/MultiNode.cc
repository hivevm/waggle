// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: src/main/resources/templates/cpp/MultiNode.cc.template, src/main/resources/templates/cpp/SimpleNode.cc.template

// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

#include "__NODE_TYPE__.h"
//@if(VISITOR)
#include "__PARSER_NAME__Visitor.h"
//@fi

//@if(CPP_NAMESPACE)
namespace __CPP_NAMESPACE__ {
//@fi
	
__NODE_TYPE__::__NODE_TYPE__(int id) : __NODE_CLASS__(id) {
}
__NODE_TYPE__::~__NODE_TYPE__() {
}

//@if(VISITOR)
/** Accept the visitor. **/
__VISITOR_RETURN_TYPE__ __NODE_TYPE__::jjtAccept(__PARSER_NAME__Visitor *visitor, __VISITOR_DATA_TYPE__ data) const {
//@if(!VISITOR_RETURN_TYPE_VOID)
	return
//@fi
	visitor->visit(this, data);
}
//@fi

//@if(CPP_NAMESPACE)
}
//@fi

#pragma GCC diagnostic pop