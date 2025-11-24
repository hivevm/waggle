// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

#include "__PARSER_NAME__TokenManager.h"
#include "TokenManagerError.h"
#include "TokenManagerErrorHandler.h"


//@foreach(LOHI_BYTES)
static const unsigned long long jjbitVec__LOHI_BYTES_INDEX__[] = { __LOHI_BYTES_BYTES__ };
//@end
//@invoke(DUMP_STR_LITERAL_IMAGES)

//@invoke(DUMP_STATE_SETS)

//@if(DEBUG_TOKEN_MANAGER)
//@invoke(DUMP_STATES_FOR_STATE_CPP)
//@invoke(DUMP_STATES_FOR_KIND)
//@fi
//@if(HAS_LOOP)
static int  jjemptyLineNo[__MAX_LEX_STATES__];
static int  jjemptyColNo[__MAX_LEX_STATES__];
static bool jjbeenHere[__MAX_LEX_STATES__];
//@fi

/** Lexer state names. */
//@foreach(STATE_NAMES_AS_CHARS)
static const JJChar lexStateNames_arr___STATE_NAMES_AS_CHARS_INDEX__[] =
{__STATE_NAMES_AS_CHARS_CHARS__, 0};
//@end
static const JJString lexStateNames[] = {
//@foreach(MAX_LEX_STATES)
lexStateNames_arr___MAX_LEX_STATES_INDEX__,
//@end
};

//@invoke(DUMP_STATIC_VAR_DECLARATIONS)
//@if(CPP_NAMESPACE)
namespace __CPP_NAMESPACE__ {
//@fi

  void __PARSER_NAME__TokenManager::setDebugStream(FILE *ds) { debugStream = ds; }
//@foreach(STATES)
__STATES_BODY__
//@end

Token * __PARSER_NAME__TokenManager::jjFillToken() {
   Token *t;
   JJString curTokenImage;
//@if(KEEP_LINE_COOL)
   int beginLine   = -1;
   int endLine     = -1;
   int beginColumn = -1;
   int endColumn   = -1;
//@fi
//@if(HAS_EMPTY_MATCH)
   if (jjmatchedPos < 0)
   {
       curTokenImage = image.c_str();
//@if(KEEP_LINE_COOL)
     if (reader->getTrackLineColumn()) {
        beginLine = endLine = reader->getEndLine();
        beginColumn = endColumn = reader->getEndColumn();
     }
//@fi
   } else {
     JJString im = jjstrLiteralImages[jjmatchedKind];
     curTokenImage = (im.length() == 0) ? reader->getImage() : im;
//@if(KEEP_LINE_COOL)
    if (reader->getTrackLineColumn()) {
        beginLine = reader->getBeginLine();
        beginColumn = reader->getBeginColumn();
        endLine = reader->getEndLine();
        endColumn = reader->getEndColumn();
    }
//@fi
   }
//@else
   JJString im = jjstrLiteralImages[jjmatchedKind];
   curTokenImage = (im.length() == 0) ? reader->getImage() : im;
//@if(KEEP_LINE_COOL)
   if (reader->getTrackLineColumn()) {
     beginLine = reader->getBeginLine();
     beginColumn = reader->getBeginColumn();
     endLine = reader->getEndLine();
     endColumn = reader->getEndColumn();
   }
//@fi
//@fi
   t = Token::newToken(jjmatchedKind, curTokenImage);
//@if(KEEP_LINE_COOL)

   t->beginLine() = beginLine;
   t->endLine() = endLine;
   t->beginColumn() = beginColumn;
   t->endColumn() = endColumn;
//@fi

   return t;
}
//@foreach(NON_ASCII_TABLE)
__NON_ASCII_TABLE_METHOD__
//@end
/** Get the next Token. */
Token * __PARSER_NAME__TokenManager::getNextToken() {
//@if (HAS_SPECIAL)
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
      fprintf(debugStream, \"Returning the <EOF> token.\\n\");
//@fi
      jjmatchedKind = 0;
      jjmatchedPos = -1;
      matchedToken = jjFillToken();
//@if(HAS_SPECIAL)
      matchedToken->specialToken = specialToken;
//@fi
//@invoke(DUMP_GET_NEXT_TOKEN)
//@if(MAX_LEX_STATES)
     }
     int error_line = reader->getEndLine();
     int error_column = reader->getEndColumn();
     JJString error_after = JJEMPTY;
     bool EOFSeen = false;
     if (reader->endOfInput()) {
        EOFSeen = true;
        error_after = curPos <= 1 ? JJEMPTY : reader->getImage();
        if (curChar == '\n' || curChar == '\r') {
           error_line++;
           error_column = 0;
        }
        else
           error_column++;
     }
     if (!EOFSeen) {
        error_after = curPos <= 1 ? JJEMPTY : reader->getImage();
     }
     errorHandler->lexicalError(EOFSeen, curLexState, error_line, error_column, error_after, curChar);
//@fi
//@if(HAS_MORE)
 }
//@fi
  }
}

//@if(HAS_SKIP_ACTIONS)

//@invoke(DUMP_SKIP_ACTIONS)
//@fi
//@if(HAS_MORE_ACTIONS)

//@invoke(DUMP_MORE_ACTIONS)
//@fi
//@if(HAS_TOKEN_ACTIONS)

//@invoke(DUMP_TOKEN_ACTIONS)
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

/** Switch to specified lex state. */
void __PARSER_NAME__TokenManager::SwitchTo(int lexState)
{
    if (lexState >= __STATE_COUNT__ || lexState < 0) {
      JJString message;
      message += JJWIDE(Error: Ignoring invalid lexical state : );
      message += lexState; message += JJWIDE(. State unchanged.);
      throw new TokenManagerError(message, INVALID_LEXICAL_STATE);
    } else
      curLexState = lexState;
}

void OQLTokenManager::lexicalError() {
	std::clog << "Lexical error encountered." << std::endl;
}
const  TokenManagerErrorHandler* OQLTokenManager::getErrorHandler() const {
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