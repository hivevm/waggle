// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/ParseGenCPP.java

// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

#include "__PARSER_NAME__.h"
#include "TokenManagerError.h"
//@if(USE_AST)
#include "__PARSER_NAME__Tree.h"
//@fi


//@if(CPP_NAMESPACE)
namespace __CPP_NAMESPACE__ {
//@fi

	//@apply(PRODUCTIONS)

__PARSER_NAME__::__PARSER_NAME__(TokenManager *tokenManager)
{
	head = nullptr;
	ReInit(tokenManager);
}

__PARSER_NAME__::~__PARSER_NAME__()
{
	clear();
}

void __PARSER_NAME__::ReInit(TokenManager* tokenManager)
{
	clear();
	errorHandler = new ParserErrorHandler();
	delete_eh = true;
	hasError = false;
	token_source = tokenManager;
	head = token = new Token;
	jj_lookingAhead = false;
	jj_done = false;
	jj_scanpos = jj_lastpos = nullptr;
	indent = 0;
	trace = __DEBUG_PARSER__;
//@if(CPP_STACK_LIMIT)
	jj_stack_limit = __CPP_STACK_LIMIT__;
	jj_stack_error = jj_stack_check(true);
//@fi
//@if(CACHE_TOKENS)
	token->next() = jj_nt = token_source->getNextToken();
//@else
	jj_ntk = -1;
//@fi
//@if(USE_AST)
	jjtree.reset();
//@fi
//@if(DEPTH_LIMIT)
	jj_depth = 0;
	jj_depth_error = false;
//@fi
}


void __PARSER_NAME__::clear()
{
	//Since token manager was generate from outside,
	//parser should not take care of deleting
	//if (token_source) delete token_source;
	if (delete_tokens && head) {
		Token* next;
		Token* t = head;
		while (t) {
			next = t->next();
			delete t;
			t = next;
		}
	}
	if (delete_eh) {
		delete errorHandler, errorHandler = nullptr;
		delete_eh = false;
	}
//@if(DEPTH_LIMIT)
	assert(jj_depth==0);
//@fi
}
//@if(CPP_STACK_LIMIT)

bool __PARSER_NAME__::jj_stack_check(bool init)
{
	if(init) {
		jj_stack_base = nullptr;
		 return false;
	} else {
		volatile int q = 0;
		if(!jj_stack_base) {
			jj_stack_base = (void*)&q;
			return false;
		} else {
			// Stack can grow in both directions, depending on arch
			std::ptrdiff_t used = (char*)jj_stack_base-(char*)&q;
			return (std::abs(used) > jj_stack_limit);
		}
	}
}
//@fi

Token * __PARSER_NAME__::jj_consume_token(int kind)
{
//@if(CPP_STACK_LIMIT)
	if(kind != -1 && (jj_stack_error || jj_stack_check(false))) {
		if (!jj_stack_error) {
			errorHandler->handleOtherError(Stack overflow while trying to parse, this);
			jj_stack_error=true;
		}
		return jj_consume_token(-1);
	}
//@fi
//@if(CACHE_TOKENS)
	Token *oldToken = token;
	if ((token = jj_nt)->next() != nullptr) jj_nt = jj_nt->next();
	else jj_nt = jj_nt->next() = token_source->getNextToken();
//@else
	Token *oldToken;
	if ((oldToken = token)->next() != nullptr) token = token->next();
	else token = token->next() = token_source->getNextToken();
	jj_ntk = -1;
//@fi
	if (token->kind() == kind) {
//@if(DEBUG_PARSER)
		trace_token(token, "");
//@fi
		return token;
	}
//@if(CACHE_TOKENS)
    jj_nt = token;
//@fi
    token = oldToken;
//@if(CPP_STACK_LIMIT)
    if (!jj_stack_error) {
//@fi
    const JJString expectedImage = getTokenImage(kind);
    const JJString expectedLabel = getTokenLabel(kind);
    const Token*   actualToken   = getToken(1);
    const JJString actualImage   = getTokenImage(actualToken->kind());
    const JJString actualLabel   = getTokenLabel(actualToken->kind());
    errorHandler->unexpectedToken(expectedImage, expectedLabel, actualImage, actualLabel, actualToken);
//@if(CPP_STACK_LIMIT)
    }
//@fi
    hasError = true;
    return token;
}

//@if(JJ2_INDEX)
bool __PARSER_NAME__::jj_scan_token(int kind)
{
//@if(CPP_STACK_LIMIT)
    if(kind != -1 && (jj_stack_error || jj_stack_check(false))) {
        if (!jj_stack_error) {
            errorHandler->handleOtherError("Stack overflow while trying to parse", this);
            jj_stack_error=true;
        }
        return jj_consume_token(-1);
    }
//@fi
    if (jj_scanpos == jj_lastpos) {
        jj_la--;
        if (jj_scanpos->next() == nullptr) {
            jj_lastpos = jj_scanpos = jj_scanpos->next() = token_source->getNextToken();
        } else {
            jj_lastpos = jj_scanpos = jj_scanpos->next();
        }
    } else {
        jj_scanpos = jj_scanpos->next();
    }
//@if(DEBUG_LOOKAHEAD)
    trace_scan(jj_scanpos, kind);
//@fi
    if (jj_scanpos->kind() != kind) return true;
    if (jj_la == 0 && jj_scanpos == jj_lastpos) { return jj_done = true; }
    return false;
}

//@fi
/** Get the next Token. */
Token * __PARSER_NAME__::getNextToken()
{
//@if(CACHE_TOKENS)
    if ((token = jj_nt)->next() != nullptr) jj_nt = jj_nt->next();
    else jj_nt = jj_nt->next() = token_source->getNextToken();
//@else
    if (token->next() != nullptr) token = token->next();
    else token = token->next() = token_source->getNextToken();
    jj_ntk = -1;
//@fi
//@if(DEBUG_PARSER)
	trace_token(token, " (in getNextToken)");
//@fi
	return token;
}
/** Get the specific Token. */
Token * __PARSER_NAME__::getToken(int index)
{
//@if(LOOKAHEAD_NEEDED)
    Token *t = jj_lookingAhead ? jj_scanpos : token;
//@else
    Token *t = token;
//@fi
    for (int i = 0; i < index; i++) {
        if (t->next() != nullptr) t = t->next();
        else t = t->next() = token_source->getNextToken();
    }
    return t;
}

//@if(!CACHE_TOKENS)
int __PARSER_NAME__::jj_ntk_f()
{
    if ((jj_nt = token->next()) == nullptr)
        return (jj_ntk = (token->next() = token_source->getNextToken())->kind());
    else
        return (jj_ntk = jj_nt->kind());
}

//@fi

bool __PARSER_NAME__::trace_enabled()
{
    return trace;
}
bool __PARSER_NAME__::trace_la_enabled()
{
    return trace_la;
}

//@if(DEBUG_PARSER)
void __PARSER_NAME__::enable_tracing()
{
	trace = true;
}

void __PARSER_NAME__::disable_tracing()
{
	trace = false;
}

void __PARSER_NAME__::trace_call(const char *s)
{
    if (trace_enabled()) {
        for (int i = 0; i < indent; i++) { printf(" "); }
        printf("Call:   %s\n", s);
    }
    indent = indent + 2;
}

void __PARSER_NAME__::trace_return(const char *s)
{
    indent = indent - 2;
    if (trace_enabled()) {
        for (int i = 0; i < indent; i++) { printf(" "); }
        printf("Return: %s\n", s);
    }
}

void __PARSER_NAME__::trace_token(Token *t, const char *where)
{
    if (trace_enabled()) {
        for (int i = 0; i < indent; i++) { printf(" "); }
        printf("Consumed token: <kind: %d(%s), \"%s\"", t->kind(), addUnicodeEscapes(tokenImages[t->kind()]).c_str(), addUnicodeEscapes(t->image()).c_str());
        printf(" at line %d column %d> %s\n", t->beginLine(), t->beginColumn(), where);
    }
}

void __PARSER_NAME__::trace_scan(Token *t1, int t2)
{
    if (trace_enabled()) {
        for (int i = 0; i < indent; i++) { printf(" "); }
        printf("Visited token: <Kind: %d(%s), \"%s\"", t1->kind(), addUnicodeEscapes(tokenImages[t1->kind()]).c_str(), addUnicodeEscapes(t1->image()).c_str());
        printf(" at line %d column %d>; Expected token: %s\n", t1->beginLine(), t1->beginColumn(), addUnicodeEscapes(tokenImages[t2]).c_str());
    }
}

//@else
void __PARSER_NAME__::enable_tracing()
{
}
void __PARSER_NAME__::disable_tracing()
{
}
void __PARSER_NAME__::enable_la_tracing()
{
}
void __PARSER_NAME__::disable_la_tracing()
{
}

//@fi
//@if(CPP_NAMESPACE)
}
//@fi

#pragma GCC diagnostic pop