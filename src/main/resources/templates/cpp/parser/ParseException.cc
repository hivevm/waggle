// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: src/main/resources/templates/cpp/ParseException.cc.template, src/main/resources/templates/ParseException.template

// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

#include "ParseException.h"
//@if(CPP_NAMESPACE)
namespace __CPP_NAMESPACE__ {
//@fi

ParseException::ParseException() {
}

ParseException::ParseException(const JJString& message) {
}

ParseException::ParseException(const Token* currentToken, const int** expectedTokenSequences, const JJString* tokenImage)
{
	this->currentToken = currentToken;
	this->expectedTokenSequences = expectedTokenSequences;
	this->tokenImage = tokenImage;
}

//@if(CPP_NAMESPACE)
}
//@fi

#pragma GCC diagnostic pop