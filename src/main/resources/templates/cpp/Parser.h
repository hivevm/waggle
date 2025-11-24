// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

#ifndef JAVACC___CPP_DEFINE__
#define JAVACC___CPP_DEFINE__

#include "JavaCC.h"
#include "Reader.h"
#include "Token.h"
#include "TokenManager.h"
#include "ParserErrorHandler.h"
#include "__PARSER_NAME__Constants.h"
//@if(USE_AST)
#include "__PARSER_NAME__Tree.h"
#include "TreeState.h"
//@fi

//@if(CPP_NAMESPACE)
namespace __CPP_NAMESPACE__ {

//@fi
  struct JJCalls {
    int        gen;
    int        arg;
    JJCalls*   next;
    Token*     first;
    ~JJCalls() { if (next) delete next; }
     JJCalls() { next = nullptr; arg = 0; gen = -1; first = nullptr; }
  };

class __PARSER_NAME__ {
public:
//@foreach(PRODUCTION_NAMES)
  void __PRODUCTION_NAMES_METHOD__();
//@end

//@foreach(LOOKAHEADS)
__LOOKAHEADS_PHASE__
//@end

//@foreach(EXPANSIONS)
__EXPANSIONS_PHASE__
//@end

public:
  void setErrorHandler(ParserErrorHandler* eh) {
    if (delete_eh) delete errorHandler;
    errorHandler = eh;
    delete_eh = false;
  }
  const ParserErrorHandler* getErrorHandler() {
    return errorHandler;
  }
  static const JJChar* getTokenImage(int kind) {
    return kind >= 0 ? tokenImages[kind] : tokenImages[0];
  }
  static const JJChar* getTokenLabel(int kind) {
    return kind >= 0 ? tokenLabels[kind] : tokenLabels[0];
  }

  TokenManager*          token_source = nullptr;
  Token*                 token = nullptr;  // Current token.
  Token*                 jj_nt = nullptr;  // Next token.

private:
  int                    jj_ntk;
  JJCalls                jj_2_rtns[__JJ2_INDEX__ + 1];
  bool                   jj_rescan;
  int                    jj_gc;
  Token*                 jj_scanpos;
  Token*                 jj_lastpos;
  int                    jj_la;
  bool                   jj_lookingAhead;  // Whether we are looking ahead.
  bool                   jj_semLA;
  int                    jj_gen;
  int                    jj_la1[__MASK_INDEX__ + 1];
  ParserErrorHandler*    errorHandler = nullptr;

protected:
  bool                   delete_eh = false;
  bool                   delete_tokens = true;
  bool                   hasError;

//@if(DEPTH_LIMIT)
  private: int jj_depth;
  private: bool jj_depth_error;
  friend class __jj_depth_inc;
  class __jj_depth_inc {public:
    __PARSER_NAME__* parent;
    __jj_depth_inc(__PARSER_NAME__* p): parent(p) { parent->jj_depth++; };
    ~__jj_depth_inc(){ parent->jj_depth--; }
  };
//@fi
//@if(CPP_STACK_LIMIT)
  public: size_t jj_stack_limit;
  private: void* jj_stack_base;
  private: bool jj_stack_error;
//@fi
  Token *head;
public:
  __PARSER_NAME__(TokenManager *tokenManager);
  virtual ~__PARSER_NAME__();
void ReInit(TokenManager* tokenManager);
void clear();
//@if(CPP_STACK_LIMIT)
 virtual bool jj_stack_check(bool init);
//@fi
Token * jj_consume_token(int kind);
//@if(JJ2_INDEX)
bool jj_scan_token(int kind);
//@fi
Token * getNextToken();
Token * getToken(int index);
//@if(!CACHE_TOKENS)
int jj_ntk_f();
//@fi
private:
  int jj_kind;
//@if(ERROR_REPORTING)
  int **jj_expentries;
  int *jj_expentry;
//@if(JJ2_INDEX)
  void jj_add_error_token(int kind, int pos);
//@fi

protected:
  /** Generate ParseException. */
  void parseError();
//@else
  void parseError();
//@fi
private:
  int  indent; // trace indentation
  bool trace = __DEBUG_PARSER__;
  bool trace_la = __DEBUG_PARSER__;

public:
  bool trace_enabled();
  bool trace_la_enabled();
//@if(DEBUG_PARSER)
  void enable_tracing();
  void disable_tracing();
  void trace_call(const char *s);
  void trace_return(const char *s);
  void trace_token(Token *t, const char *where);
  void trace_scan(Token *t1, int t2);
//@else
  void enable_tracing();
  void disable_tracing();
  void enable_la_tracing();
  void disable_la_tracing();
//@fi
//@if(JJ2_INDEX)
//@if(ERROR_REPORTING)
  void jj_rescan_token();
  void jj_save(int index, int xla);
//@fi
//@fi
protected:
  virtual void jjtreeOpenNodeScope(Node * node) = 0;
  virtual void jjtreeCloseNodeScope(Node * node) = 0;

//@if(USE_AST)
  TreeState jjtree;
//@fi
private:
  bool jj_done;
};
//@if(CPP_NAMESPACE)
}
//@fi

#endif

#pragma GCC diagnostic pop