// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

#ifndef __CPP_DEFINE___VISITOR
#define __CPP_DEFINE___VISITOR

#include "Waggle.h"
#include "__PARSER_NAME__Tree.h"

//@if(CPP_NAMESPACE)
namespace __CPP_NAMESPACE__ {
//@fi

class __PARSER_NAME__Visitor
{
public:

	virtual __VISITOR_RETURN_TYPE__ visit(const Node *node, __VISITOR_DATA_TYPE__ data) = 0;
//@if(NODE_MULTI)
//@foreach(NODES)
	virtual __VISITOR_RETURN_TYPE__ visit(const __NODES_TYPE__ *node, __VISITOR_DATA_TYPE__ data) = 0;
//@end
//@fi

	virtual ~__PARSER_NAME__Visitor() { }
};
		

class __PARSER_NAME__DefaultVisitor
    : public __PARSER_NAME__Visitor
{

public:
	virtual __VISITOR_RETURN_TYPE__ defaultVisit(const Node *node, __VISITOR_DATA_TYPE__ data) = 0;

	virtual __VISITOR_RETURN_TYPE__ visit(const Node *node, __VISITOR_DATA_TYPE__ data) {
//@if(VISITOR_RETURN_TYPE_VOID)
		defaultVisit(node, data);
//@else
		return defaultVisit(node, data);
//@fi
	}

//@if(NODE_MULTI)
//@foreach(NODES)
	virtual __VISITOR_RETURN_TYPE__ visit(const __NODES_TYPE__ *node, __VISITOR_DATA_TYPE__ data) {
//@if(VISITOR_RETURN_TYPE_VOID)
		defaultVisit(node, data);
//@else
		return defaultVisit(node, data);
//@fi
	}
//@end
//@fi

	~__PARSER_NAME__DefaultVisitor() { }
};

//@if(CPP_NAMESPACE)
}
//@fi

#endif

#pragma GCC diagnostic pop