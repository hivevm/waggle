// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

#include "TokenManagerErrorHandler.h"
#if (WAGGLE_CHAR_TYPE_SIZEOF != 1)
#include <codecvt>
#include <locale>
#endif
#include "Waggle.h"
#include "Token.h"
#include "ParserErrorHandler.h"

//@if(CPP_NAMESPACE)
namespace __CPP_NAMESPACE__ {
//@fi

void TokenManagerErrorHandler::lexicalError(bool EOFSeen, int lexState, int errorLine, int errorColumn, JJString errorAfter, JJChar curChar) {
	JJOUT
        << JJWIDE(Lexical error at)  << JJSPACE << errorLine << JJWIDE(:) << errorColumn << JJWIDE(.)
        << JJWIDE( Encountered:)  << JJSPACE << curChar <<JJWIDE( after:)  << JJSPACE << errorAfter << JJWIDE(.) << std::endl;
}

void TokenManagerErrorHandler::lexicalError(const JJString& errorMessage) {
	JJOUT << errorMessage << std::endl;
}

//@if(CPP_NAMESPACE)
}
//@fi

#pragma GCC diagnostic pop