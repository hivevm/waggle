// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

/**
 * Token literal values and constants.
 */
#ifndef JAVACC_PARSER_CONSTANTS
#define JAVACC_PARSER_CONSTANTS

#include "JavaCC.h"

//@if(CPP_NAMESPACE)
namespace __CPP_NAMESPACE__ {
//@fi

// RegularExpressions
const int _EOF = 0; // End of File
//@foreach(TOKENS)
const int __TOKENS_LABEL__ = __TOKENS_ORDINAL__;
//@end

// Lexical states
//@foreach(STATES)
const int __STATES_NAME__ = __STATES_INDEX__;
//@end

// Literal token images
//@foreach(REXPRESSION_COUNT)
static const JJChar tokenImage___REXPRESSION_INDEX__[] = {__REXPRESSION_IMAGE__0};
//@end
static const JJChar* const tokenImages[] = {
//@foreach(REXPRESSION_COUNT)
	tokenImage___REXPRESSION_INDEX__,
//@end
};

// Literal token labels
//@foreach(REXPRESSION_COUNT)
static const JJChar tokenLabel___REXPRESSION_INDEX__[] = {__REXPRESSION_LABEL__0};
//@end
static const JJChar* const tokenLabels[] = {
//@foreach(REXPRESSION_COUNT)
	tokenLabel___REXPRESSION_INDEX__,
//@end
};

//@if(CPP_NAMESPACE)
}
//@fi

#endif

#pragma GCC diagnostic pop