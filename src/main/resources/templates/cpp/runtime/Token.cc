// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: src/main/resources/templates/cpp/Token.cc.template

// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

#include "Token.h"
//@if(CPP_NAMESPACE)
namespace __CPP_NAMESPACE__ {
//@fi

Token::Token() {}

Token::Token(int kind) : _kind(kind) {}

Token::Token(int kind, const JJString& image) : _kind(kind), _image(image) {}

Token* Token::newToken(int kind, const JJString& image)
{
	switch(kind)
	{
	default : return new Token(kind, image);
	}
}

Token* Token::newToken(int kind)
{
	return newToken(kind, JJString());
}

Token::~Token()
{
	delete _specialToken;
}


//@if(CPP_NAMESPACE)
}
//@fi

#pragma GCC diagnostic pop