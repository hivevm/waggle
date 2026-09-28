// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/NfaState.java, src/main/resources/templates/cpp/DumpDebugMethods.template

// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

#ifndef WAGGLE___CPP_DEFINE___TOKENMANAGER
#define WAGGLE___CPP_DEFINE___TOKENMANAGER

#include "Waggle.h"
#include "Reader.h"
#include "Token.h"
#include "ParserErrorHandler.h"
#include "TokenManager.h"
#include "__PARSER_NAME__Constants.h"

//@if(CPP_NAMESPACE)
namespace __CPP_NAMESPACE__ {
//@fi

class __PARSER_NAME__TokenManager : public TokenManager {
public:
	FILE *debugStream;
	void setDebugStream(FILE *ds);
	//@invoke(DUMP_NFA_AND_DFA_HEADER)
	Token * jjFillToken();
//@foreach(NON_ASCII_TABLE)
bool jjCanMove___NON_ASCII_TABLE_NAME__(int hiByte, int i1, int i2, unsigned long long l1, unsigned long long l2);
//@end
public:
	int defaultLexState;
	int curLexState = 0;
	int jjnewStateCnt = 0;
	int jjround = 0;
	int jjmatchedPos = 0;
	int jjmatchedKind = 0;

Token * getNextToken();
//@if(HAS_SKIP_ACTIONS)
void SkipLexicalActions(Token *matchedToken);
//@fi
//@if(HAS_MORE_ACTIONS)
void MoreLexicalActions();
//@fi
//@if(HAS_TOKEN_ACTIONS)
void TokenLexicalActions(Token *matchedToken);
//@fi
	Reader*        reader;

private:
	void ReInitRounds();
	void jjCheckNAdd(int state);
	void jjAddStates(int start, int end);
	void jjCheckNAddTwoStates(int state1, int state2);
//@if(CHECK_NADD_STATES_DUAL_NEEDED)
	void jjCheckNAddStates(int start, int end);
//@fi
//@if(CHECK_NADD_STATES_UNARY_NEEDED)
	void jjCheckNAddStates(int start);
//@fi
//@if(HAS_LOOP)
	// Where each lexical state last matched the empty string, to catch a loop on it.
	int  jjemptyLineNo[__MAX_LEX_STATES__] = {};
	int  jjemptyColNo[__MAX_LEX_STATES__] = {};
	bool jjbeenHere[__MAX_LEX_STATES__] = {};
//@fi

public:
	__PARSER_NAME__TokenManager(Reader * stream, int lexState = __DEFAULT_LEX_STATE__);
	virtual ~__PARSER_NAME__TokenManager();

protected:
	void ReInit(Reader * stream, int lexState = __DEFAULT_LEX_STATE__);
	void SwitchTo(int lexState);
	void clear();
//@if(DEBUG_TOKEN_MANAGER)
	const Latin1 jjKindsForBitVector(int i, unsigned long long vec);
	const Latin1 jjKindsForStateVector(int lexState, int vec[], int start, int end);
//@fi

	int                       jjrounds[__STATE_SET_SIZE__];
	int                       jjstateSet[__STATE_SET_SIZE_2__];
	JJString                  image;
	int                       jjimageLen;
	int                       lengthOfMatch;
	uint32_t                  curChar; // UTF8: Support Unicode
	TokenManagerErrorHandler* errorHandler = nullptr;

public:
	void	 lexicalError();
	const  TokenManagerErrorHandler*	 getErrorHandler() const;
};
//@if(CPP_NAMESPACE)
}
//@fi

#endif

#pragma GCC diagnostic pop