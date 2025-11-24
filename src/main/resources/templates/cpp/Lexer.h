// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

#ifndef JAVACC___CPP_DEFINE___TOKENMANAGER
#define JAVACC___CPP_DEFINE___TOKENMANAGER

#include "JavaCC.h"
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
//@foreach(STATES)
__STATES_HEAD__
//@end
Token * jjFillToken();
//@foreach(NON_ASCII_TABLE)
bool jjCanMove___NON_ASCII_TABLE_OFFSET__(int hiByte, int i1, int i2, unsigned long long l1, unsigned long long l2);
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
#define jjCheckNAdd(state)\
{\
   if (jjrounds[state] != jjround)\
   {\
      jjstateSet[jjnewStateCnt++] = state;\
      jjrounds[state] = jjround;\
   }\
}
#define jjAddStates(start, end)\
{\
   for (int x = start; x <= end; x++) {\
      jjstateSet[jjnewStateCnt++] = jjnextStates[x];\
   } /*while (start++ != end);*/\
}
#define jjCheckNAddTwoStates(state1, state2)\
{\
   jjCheckNAdd(state1);\
   jjCheckNAdd(state2);\
}

//@if(CHECK_NADD_STATES_DUAL_NEEDED)
#define jjCheckNAddStates(start, end)\
{\
   for (int x = start; x <= end; x++) {\
      jjCheckNAdd(jjnextStates[x]);\
   } /*while (start++ != end);*/\
}

//@fi
//@if(CHECK_NADD_STATES_UNARY_NEEDED)
#define jjCheckNAddStates(start)\
{\
   jjCheckNAdd(jjnextStates[start]);\
   jjCheckNAdd(jjnextStates[start + 1]);\
}
//@fi
  Reader*        reader;

private:
  void ReInitRounds();

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
  int                       jjstateSet[2 * __STATE_SET_SIZE__];
  JJString                  jjimage;
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