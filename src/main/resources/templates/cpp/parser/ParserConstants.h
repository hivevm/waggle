// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

/**
 * Token literal values and constants.
 */
#ifndef WAGGLE_PARSER_CONSTANTS
#define WAGGLE_PARSER_CONSTANTS

#include "Waggle.h"

//@if(CPP_NAMESPACE)
namespace __CPP_NAMESPACE__ {
//@fi

// RegularExpressions
const int _EOF = 0; // End of File
//@foreach(TOKENS)
const int __name__ = __value__;
//@end

// Lexical states
//@foreach(STATES)
const int __name__ = __value__;
//@end

// Literal token images
//@foreach(TOKEN_IMAGES)
static const JJChar tokenImage___index__[] = {__image__0};
//@end
static const JJChar* const tokenImages[] = {
//@foreach(TOKEN_IMAGES)
	tokenImage___index__,
//@end
};

// Literal token labels
//@foreach(TOKEN_IMAGES)
static const JJChar tokenLabel___index__[] = {__label__0};
//@end
static const JJChar* const tokenLabels[] = {
//@foreach(TOKEN_IMAGES)
	tokenLabel___index__,
//@end
};

//@if(CPP_NAMESPACE)
}
//@fi

#endif

#pragma GCC diagnostic pop