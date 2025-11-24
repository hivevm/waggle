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
const int __STATES_NAME__ = __STATES_ORDINAL__;
//@end

// Literal token images
//@foreach(REGEXPS)
static const JJChar tokenImage___REGEXPS_ORDINAL__[] = {__REGEXPS_IMAGE__0};
//@end
static const JJChar* const tokenImages[] = {
//@foreach(REGEXPS)
    tokenImage___REGEXPS_ORDINAL__,
//@end
};

// Literal token labels
//@foreach(REGEXPS)
static const JJChar tokenLabel___REGEXPS_ORDINAL__[] = {__REGEXPS_LABEL__0};
//@end
static const JJChar* const tokenLabels[] = {
//@foreach(REGEXPS)
    tokenLabel___REGEXPS_ORDINAL__,
//@end
};

//@if(CPP_NAMESPACE)
}
//@fi

#endif

#pragma GCC diagnostic pop