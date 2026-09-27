// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: src/main/resources/templates/cpp/CharStream.cc.template, src/main/resources/templates/SimpleCharStream.template

// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

#include "StringReader.h"

static constexpr int INITIAL_BUFFER_SIZE = 4096;

namespace {
template <class T>
void ArrayCopy(T* src, int src_offset, T* dest, int dest_offset, int len) {
	for (int i = 0; i < len; i++) {
		dest[dest_offset + i] = src[src_offset + i];
	}
}

class StringInputStream : public InputStream {
public:
	StringInputStream(const JJString& str) : str_(str), cur_(0), max_(str.size()) {}
	virtual size_t read(JJChar *bufptr, int offset, size_t len) {
		size_t count = str_.copy(bufptr + offset, len > max_ ? max_ : len, cur_);
		cur_ += count;
		max_ -= count;
		return count;
	}
	virtual ~StringInputStream() {}
	virtual bool endOfInput() {
		return max_ == 0;
	}

private:
	const JJString str_;
	size_t cur_;
	size_t max_;
};
}

//@if(CPP_NAMESPACE)
namespace __CPP_NAMESPACE__ {
//@fi

StringReader::StringReader(InputStream * stream)
	: inputStream(stream)
	, deleteStream(false)
{
	init();
}

StringReader::StringReader(const JJString & str)
	: inputStream(new StringInputStream(str))
	, deleteStream(true)
{
	init();
}

StringReader::~StringReader() {
	if (deleteStream) {
		delete inputStream;
	}
	deleteBuffers();
}

//@if(KEEP_LINE_COLUMN)
int StringReader::getBufcolumn(int pos) {
	if (trackLineColumn && pos>=0) {
		return bufcolumn[pos];
	} else {
		return -1;
	}
}

int StringReader::getBufline(int pos) {
	if (trackLineColumn && pos>=0) {
		return bufline[pos];
	} else {
		return -1;
	}
}
//@fi

void StringReader::init() {
	buffer = new JJChar[INITIAL_BUFFER_SIZE];
	bufline = new int[INITIAL_BUFFER_SIZE](); // zero: the EOF of an empty input is at 0:0, as in Java
	bufcolumn = new int[INITIAL_BUFFER_SIZE]();
	bufpos = -1;
	bufsize = INITIAL_BUFFER_SIZE;
	tokenBegin = 0;
	column = 0;
	line = 1;
	prevCharIsCR = false;
	prevCharIsLF = false;
	available = INITIAL_BUFFER_SIZE;
	maxNextCharInd = 0;
	inBuf = 0;
	tabSize = 8;
	trackLineColumn = true;
}

// "amount" counts characters, as the lexer does: with UTF-8 input one character can be up to
// four bytes, so backing up by bytes left the reader inside a character.
void StringReader::backup(int amount) {
#if (WAGGLE_CHAR_TYPE_SIZEOF == 1)
	while (amount-- > 0) {
		unsigned char b;
		do { // un-read continuation bytes up to and including the lead byte
			b = (unsigned char) buffer[bufpos];
			inBuf++;
			if (--bufpos < 0) {
				bufpos += bufsize;
			}
		} while ((b & 0xc0) == 0x80);
	}
#else
	inBuf += amount; bufpos -= amount;
	if (bufpos < 0) {
		bufpos += bufsize;
	}
#endif
}

uint32_t StringReader::beginToken() {
	tokenBegin = -1;
	uint32_t c = readChar();
	tokenBegin = bufpos;
	return decode(c); // the NFA reads code points, so the first one must be one too
}

uint32_t StringReader::readChar() {
	if (inBuf > 0) {
		--inBuf; ++bufpos;
		if (bufpos == bufsize) {
			bufpos = 0;
		}
		return buffer[bufpos];
	}

	++bufpos;
	if (bufpos >= maxNextCharInd) {
		fillBuff();
	}

	uint32_t c = buffer[bufpos];

//@if(KEEP_LINE_COLUMN)
	if (trackLineColumn) {
		updateLineColumn(c);
	}
//@fi
	return c;
}

JJString StringReader::getImage() {
	if (bufpos >= tokenBegin) {
		return JJString(buffer + tokenBegin, bufpos - tokenBegin + 1);
	}
	return JJString(buffer + tokenBegin, bufsize - tokenBegin).append(buffer, bufpos + 1);
}

JJString StringReader::getSuffix(int len) {
#if (WAGGLE_CHAR_TYPE_SIZEOF == 1)
	// "len" counts characters, as in backup: turn it into the bytes they take.
	int bytes = 0;
	for (int chars = 0; chars < len; bytes++) {
		int pos = bufpos - bytes;
		if (pos < 0) {
			pos += bufsize;
		}
		if ((((unsigned char) buffer[pos]) & 0xc0) != 0x80) {
			chars++;
		}
	}
	len = bytes;
#endif
	if ((bufpos + 1) >= len) {
		return JJString(buffer + bufpos - len + 1, len);
	}
	return JJString(buffer + bufsize - (len - bufpos - 1), len - bufpos - 1).append(buffer, bufpos + 1);
}

bool StringReader::endOfInput() {
	return inBuf == 0 && bufpos + 1 >= maxNextCharInd && inputStream->endOfInput();
}

void StringReader::deleteBuffers() {
	delete[] buffer;
	delete[] bufline;
	delete[] bufcolumn;
}

void StringReader::adjustBeginLineColumn(int newLine, int newCol) {
	int start = tokenBegin;
	int len;

	if (bufpos >= tokenBegin) {
		len = bufpos - tokenBegin + inBuf + 1;
	} else {
		len = bufsize - tokenBegin + bufpos + 1 + inBuf;
	}

	int i = 0, j = 0, k = 0;
	int nextColDiff = 0, columnDiff = 0;

	while (i < len && bufline[j = start % bufsize] ==
			 bufline[k = (start + 1) % bufsize]) {
		bufline[j] = newLine;
		nextColDiff = columnDiff + bufcolumn[k] - bufcolumn[j];
		bufcolumn[j] = newCol + columnDiff;
		columnDiff = nextColDiff;
		i++;
		start++;
	}

	if (i < len) {
		bufline[j] = newLine++;
		bufcolumn[j] = newCol + columnDiff;

		while (i++ < len) {
			if (bufline[j = start % bufsize] != bufline[(start + 1) % bufsize])
				bufline[j] = newLine++;
			else
				bufline[j] = newLine;
			start++;
		}
	}

	line = bufline[j];
	column = bufcolumn[j];
}

void StringReader::expandBuff(bool wrapAround) {
	JJChar *newbuffer = new JJChar[bufsize + 2048];
	int *newbufline = new int[bufsize + 2048]();
	int *newbufcolumn = new int[bufsize + 2048]();

	if (wrapAround) {
		ArrayCopy(buffer, tokenBegin, newbuffer, 0, bufsize - tokenBegin);
		ArrayCopy(buffer, 0, newbuffer, bufsize - tokenBegin, bufpos);
		ArrayCopy(bufline, tokenBegin, newbufline, 0, bufsize - tokenBegin);
		ArrayCopy(bufline, 0, newbufline, bufsize - tokenBegin, bufpos);
		ArrayCopy(bufcolumn, tokenBegin, newbufcolumn, 0, bufsize - tokenBegin);
		ArrayCopy(bufcolumn, 0, newbufcolumn, bufsize - tokenBegin, bufpos);
		bufpos += (bufsize - tokenBegin);
	} else {
		ArrayCopy(buffer, tokenBegin, newbuffer, 0, bufsize - tokenBegin);
		ArrayCopy(bufline, tokenBegin, newbufline, 0, bufsize - tokenBegin);
		ArrayCopy(bufcolumn, tokenBegin, newbufcolumn, 0, bufsize - tokenBegin);
		bufpos -= tokenBegin;
	}

	maxNextCharInd = bufpos;
	deleteBuffers();
	buffer = newbuffer;
	bufline = newbufline;
	bufcolumn = newbufcolumn;
	bufsize += 2048;
	available = bufsize;
	tokenBegin = 0;
}

void StringReader::fillBuff() {
	if (maxNextCharInd == available) {
		if (available == bufsize) {
			if (tokenBegin > 2048) {
				bufpos = maxNextCharInd = 0;
				available = tokenBegin;
			} else if (tokenBegin < 0) {
				bufpos = maxNextCharInd = 0;
			} else {
				expandBuff(false);
			}
		} else if (available > tokenBegin) {
			available = bufsize;
		} else if ((tokenBegin - available) < 2048) {
			expandBuff(true);
		} else {
			available = tokenBegin;
		}
	}

	size_t i = inputStream->read(buffer, maxNextCharInd, available - maxNextCharInd);
	if (i > 0) {
		maxNextCharInd += i;
	} else {
		--bufpos;
		backup(0);
		if (tokenBegin == -1) {
			tokenBegin = bufpos;
		}
	}
}

void StringReader::updateLineColumn(uint32_t c) {
	column++;
	if (prevCharIsLF) {
		prevCharIsLF = false;
		column = 1;
		line++;
	} else if (prevCharIsCR) {
		prevCharIsCR = false;
		if (c == '\n') {
			prevCharIsLF = true;
		} else {
			column = 1;
			line++;
		}
	}

	switch (c) {
	case '\r' :
		prevCharIsCR = true;
		break;
	case '\n' :
		prevCharIsLF = true;
		break;
	case '\t' :
		column--;
		column += (tabSize - (column % tabSize));
		break;
	default :
		break;
	}

	bufline[bufpos] = line;
	bufcolumn[bufpos] = column;
}

//  TOL: Support UTF-8
uint32_t StringReader::read() {
	return decode(readChar());
}

// The character whose UTF-8 encoding starts with the byte c, reading the rest of it.
//
// The automaton knows the characters up to U+FFFF: its tables are indexed by the high byte of a
// 16-bit character, and a code point beyond read past their end. Such a code point, and a sequence
// that is malformed or cut off by the end of the input, is read as U+FFFD. The image keeps the
// bytes as they were. A byte that cannot continue the sequence is left for the next character; the
// end of the input used to be read as stale bytes of the buffer.
uint32_t StringReader::decode(uint32_t c) {
	static constexpr uint32_t REPLACEMENT = 0xfffd;
#if (WAGGLE_CHAR_TYPE_SIZEOF == 1)
	c &= 0xff; // JJChar is a signed char on most platforms
	int more;
	if (c < 0x80) {
		return c;
	} else if ((c & 0xe0) == 0xc0) {
		more = 1;
		c &= 0x1f;
	} else if ((c & 0xf0) == 0xe0) {
		more = 2;
		c &= 0x0f;
	} else if ((c & 0xf8) == 0xf0) {
		more = 3;
		c &= 0x07;
	} else {
		return REPLACEMENT; // a continuation byte, or no UTF-8 at all
	}
	while (more-- > 0) {
		if (endOfInput()) {
			return REPLACEMENT;
		}
		uint32_t b = readChar() & 0xff;
		if ((b & 0xc0) != 0x80) {
			backup(1);
			return REPLACEMENT;
		}
		c = (c << 6) | (b & 0x3f);
	}
#endif
	return (c > 0xffff) ? REPLACEMENT : c;
}

//@if(CPP_NAMESPACE)
}
//@fi

#pragma GCC diagnostic pop