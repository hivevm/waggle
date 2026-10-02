// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: src/main/resources/templates/cpp/TokenManagerBoilerPlateMethods.template, src/main/resources/templates/TokenManagerBoilerPlateMethods.template

// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

#include <set>
#include "__PARSER_NAME__TokenManager.h"
#include "TokenManagerError.h"
#include "TokenManagerErrorHandler.h"


//@foreach(LOHI_BYTES)
static const unsigned long long jjbitVec__LOHI_BYTES_INDEX__[] = { __LOHI_BYTES_VALUE__ };
//@end
//@if(LITERAL_IMAGE_COUNT)
//@apply(LITERAL_IMAGE_ROWS)
static const JJString jjstrLiteralImages[] = {
//@apply(LITERAL_IMAGE_REFS)
};
//@else
static const JJString jjstrLiteralImages[] = {};
//@fi

//@apply(NEXT_STATES)

//@if(DEBUG_TOKEN_MANAGER)
//@apply(STATES_FOR_STATE_CPP)
//@apply(KIND_FOR_STATE_CPP)

static int jjKindCnt = 0;

/** The token kinds a bit vector of the string-literal DFA still allows. */
const Latin1 __PARSER_NAME__TokenManager::jjKindsForBitVector(int i, unsigned long long vec)
{
	Latin1 names;
	if (i == 0) {
		jjKindCnt = 0;
	}
	for (int j = 0; j < 64; j++) {
		if ((vec & (1ULL << j)) != 0ULL) {
			if (jjKindCnt++ > 0) {
				names += ", ";
			}
			if ((jjKindCnt % 5) == 0) {
				names += "\n     ";
			}
			names += tokenImages[(i * 64) + j];
		}
	}
	return names;
}

/** The token kinds the NFA states in "vec" can still lead to. */
const Latin1 __PARSER_NAME__TokenManager::jjKindsForStateVector(int lexState, int vec[], int start, int end)
{
	std::set<int> done;
	Latin1 names;
	int cnt = 0;
	for (int i = start; i < end; i++) {
		if (vec[i] == -1) {
			continue;
		}
		const int* states = statesForState[lexState][vec[i]];
		for (int k = 0; k < statesForStateLen[lexState][vec[i]]; k++) {
			int kind = kindForState[lexState][states[k]];
			if (done.insert(kind).second) {
				if (cnt++ > 0) {
					names += "\n     ";
				}
				names += tokenImages[kind];
			}
		}
	}
	return (cnt == 0) ? Latin1("{  }") : "{ " + names + " }";
}
//@fi
/** Lexer state names. */
//@foreach(STATE_NAMES_AS_CHARS)
static const JJChar lexStateNames_arr___STATE_NAMES_AS_CHARS_INDEX__[] =
{__STATE_NAMES_AS_CHARS_CHARS__0};
//@end
static const JJString lexStateNames[] = {
//@foreach(MAX_LEX_STATES)
lexStateNames_arr___MAX_LEX_STATES_INDEX__,
//@end
};

//@apply(LEX_STATE_TABLE)
//@apply(KIND_VECTORS)
//@if(CPP_NAMESPACE)
namespace __CPP_NAMESPACE__ {
//@fi

	void __PARSER_NAME__TokenManager::setDebugStream(FILE *ds) { debugStream = ds; }

void __PARSER_NAME__TokenManager::jjCheckNAdd(int state) {
	if (jjrounds[state] != jjround) {
		jjstateSet[jjnewStateCnt++] = state;
		jjrounds[state] = jjround;
	}
}

void __PARSER_NAME__TokenManager::jjAddStates(int start, int end) {
	for (int x = start; x <= end; x++) {
		jjstateSet[jjnewStateCnt++] = jjnextStates[x];
	}
}

void __PARSER_NAME__TokenManager::jjCheckNAddTwoStates(int state1, int state2) {
	jjCheckNAdd(state1);
	jjCheckNAdd(state2);
}
//@if(CHECK_NADD_STATES_DUAL_NEEDED)

void __PARSER_NAME__TokenManager::jjCheckNAddStates(int start, int end) {
	for (int x = start; x <= end; x++) {
		jjCheckNAdd(jjnextStates[x]);
	}
}
//@fi
//@if(CHECK_NADD_STATES_UNARY_NEEDED)

void __PARSER_NAME__TokenManager::jjCheckNAddStates(int start) {
	jjCheckNAdd(jjnextStates[start]);
	jjCheckNAdd(jjnextStates[start + 1]);
}
//@fi

	//@apply(LEX_STATES)

	Token * __PARSER_NAME__TokenManager::jjFillToken() {
	Token *t;
	JJString curTokenImage;
//@if(KEEP_LINE_COLUMN)
	int beginLine   = -1;
	int endLine     = -1;
	int beginColumn = -1;
	int endColumn   = -1;
//@fi
//@if(HAS_EMPTY_MATCH)
	if (jjmatchedPos < 0)
	{
		curTokenImage = image.c_str();
//@if(KEEP_LINE_COLUMN)
		if(reader->getTrackLineColumn()) {
			beginLine = endLine = reader->getEndLine();
			beginColumn = endColumn = reader->getEndColumn();
		}
//@fi
	} else {
		const JJString& im = jjstrLiteralImages[jjmatchedKind];
		curTokenImage = ((im.length() == 0) && (jjmatchedKind != 0)) ? reader->getImage() : im;
//@if(KEEP_LINE_COLUMN)
		if (reader->getTrackLineColumn()) {
				beginLine = reader->getBeginLine();
				beginColumn = reader->getBeginColumn();
				endLine = reader->getEndLine();
				endColumn = reader->getEndColumn();
		}
//@fi
	}
//@else
	// No literal has an empty image, so "" stands for "none" here, except for <EOF>: its image is
	// empty, as in Java. Reading it from the input gave the previous token's text, or with empty
	// input a whole buffer of uninitialised memory.
	const JJString& im = jjstrLiteralImages[jjmatchedKind];
	curTokenImage = ((im.length() == 0) && (jjmatchedKind != 0)) ? reader->getImage() : im;
//@if(KEEP_LINE_COLUMN)
	if (reader->getTrackLineColumn()) {
		beginLine = reader->getBeginLine();
		beginColumn = reader->getBeginColumn();
		endLine = reader->getEndLine();
		endColumn = reader->getEndColumn();
	}
//@fi
//@fi
	t = Token::newToken(jjmatchedKind, curTokenImage);
//@if(KEEP_LINE_COLUMN)

	t->beginLine() = beginLine;
	t->endLine() = endLine;
	t->beginColumn() = beginColumn;
	t->endColumn() = endColumn;
//@fi

	return t;
}
//@apply(NON_ASCII_TABLE)
/** Get the next Token. */
Token * __PARSER_NAME__TokenManager::getNextToken() {
//@if(HAS_SPECIAL)
	Token *specialToken = nullptr;
//@fi
	Token *matchedToken = nullptr;
	int curPos = 0;

	for (;;)
	{
        EOFLoop:
	    if (reader->endOfInput())
	    {
//@if(DEBUG_TOKEN_MANAGER)
			fprintf(debugStream, "Returning the <EOF> token.\n");
//@fi
			jjmatchedKind = 0;
			jjmatchedPos = -1;
			matchedToken = jjFillToken();
//@if(HAS_SPECIAL)
			matchedToken->specialToken() = specialToken;
//@fi
		//@apply(GET_NEXT_TOKEN)
		int error_line = reader->getEndLine();
		int error_column = reader->getEndColumn();
		JJString error_after = JJEMPTY;
		bool EOFSeen = reader->endOfInput();
		if (EOFSeen) {
			if (curChar == '\n' || curChar == '\r') {
				error_line++;
				error_column = 0;
			}
			else
				error_column++;
		}
		error_after = curPos <= 1 ? JJEMPTY : reader->getImage();
		errorHandler->lexicalError(EOFSeen, curLexState, error_line, error_column, error_after, curChar);
	}
}

//@if(HAS_SKIP_ACTIONS)


void __PARSER_NAME__TokenManager::SkipLexicalActions(Token *matchedToken){
   switch(jjmatchedKind)
   {
//@apply(SKIP_ACTIONS)
      default:
         break;
   }
}
//@fi
//@if(HAS_MORE_ACTIONS)


void __PARSER_NAME__TokenManager::MoreLexicalActions(){
   jjimageLen += (lengthOfMatch = jjmatchedPos + 1);
   switch(jjmatchedKind)
   {
//@apply(MORE_ACTIONS)
      default:
         break;
   }
}
//@fi
//@if(HAS_TOKEN_ACTIONS)


void __PARSER_NAME__TokenManager::TokenLexicalActions(Token *matchedToken){
   switch(jjmatchedKind)
   {
//@apply(TOKEN_ACTIONS)
      default:
         break;
   }
}
//@fi

/** Reinitialise parser. */
void __PARSER_NAME__TokenManager::ReInit(Reader * stream, int lexState)
{
	clear();
	jjmatchedPos = jjnewStateCnt = 0;
	defaultLexState = 0;
	curLexState = 0;
	reader = stream;
	ReInitRounds();
	debugStream = stdout; // init
	SwitchTo(lexState);
	errorHandler = new TokenManagerErrorHandler();
}

void __PARSER_NAME__TokenManager::ReInitRounds() {
	int i;
	jjround = 0x80000001;
	for (i = __STATE_SET_SIZE__; i-- > 0;)
		jjrounds[i] = 0x80000000;
}

//@if(HAS_LOOP)
/** Bails out of a loop of empty matches, which would never end. */
void __PARSER_NAME__TokenManager::loopDetected()
{
#if (WAGGLE_CHAR_TYPE_SIZEOF == 1)
	auto number = [](int n) { return std::to_string(n); };
#else
	auto number = [](int n) { return std::to_wstring(n); };
#endif
	JJString message;
	message += JJWIDE(Error: Bailing out of infinite loop caused by repeated empty string matches at line);
	message += JJSPACE;
	message += number(reader->getBeginLine());
	message += JJSPACE;
	message += JJWIDE(column);
	message += JJSPACE;
	message += number(reader->getBeginColumn());
	message += JJWIDE(.);
	throw TokenManagerError(message, LOOP_DETECTED);
}

//@fi
/** Switch to specified lex state. */
void __PARSER_NAME__TokenManager::SwitchTo(int lexState)
{
	if (lexState >= __STATE_COUNT__ || lexState < 0) {
		JJString message;
		message += JJWIDE(Error: Ignoring invalid lexical state :);
		message += JJSPACE;
#if (WAGGLE_CHAR_TYPE_SIZEOF == 1)
		message += std::to_string(lexState);
#else
		message += std::to_wstring(lexState);
#endif
		message += JJWIDE(. State unchanged.);
		throw TokenManagerError(message, INVALID_LEXICAL_STATE);
	} else
		curLexState = lexState;
}

void __PARSER_NAME__TokenManager::lexicalError() {
	std::clog << "Lexical error encountered." << std::endl;
}
const  TokenManagerErrorHandler* __PARSER_NAME__TokenManager::getErrorHandler() const {
	return errorHandler;
}

/** Constructor. */
__PARSER_NAME__TokenManager::__PARSER_NAME__TokenManager(Reader * stream, int lexState)
{
	reader = nullptr;
	ReInit(stream, lexState);
}

// Destructor
__PARSER_NAME__TokenManager::~__PARSER_NAME__TokenManager() {
	clear();
}

// clear
void __PARSER_NAME__TokenManager::clear() {
	//Since reader was generated outside of TokenManager
	//TokenManager should not take care of deleting it
	//if (reader) delete reader;
	if (errorHandler) delete errorHandler, errorHandler = nullptr;
}

//@if(CPP_NAMESPACE)
}
//@fi

#pragma GCC diagnostic pop