package __JAVA_PACKAGE__;

/**
 * Token Manager.
 */
//@if(BASE_LEXER)
class Lexer extends __BASE_LEXER__ {
//@else
class Lexer {
//@fi

//@foreach(LOHI_BYTES)
    static final long[] jjbitVec__LOHI_BYTES_INDEX__ = { __LOHI_BYTES_VALUE__ };
//@end
//@foreach(STATES)
__STATES_NFA_AND_DFA__
//@end
    /**
     * Token literal values.
     */
    public static final String[] jjstrLiteralImages = {
    __LITERAL_IMAGES__};

    protected Token jjFillToken() {
        final Token t;
        final String curTokenImage;
//@if(KEEP_LINE_COOL)
        final int beginLine;
        final int endLine;
        final int beginColumn;
        final int endColumn;
//@fi
//@if(HAS_EMPTY_MATCH)
        if (jjmatchedPos < 0) {
            if (image == null) {
                curTokenImage = "";
            } else {
                curTokenImage = image.toString();
            }
//@if(KEEP_LINE_COOL)
            beginLine = endLine = input_stream.getEndLine();
            beginColumn = endColumn = input_stream.getEndColumn();
//@fi
        } else {
            String im = jjstrLiteralImages[jjmatchedKind];
            curTokenImage = (im == null) ? input_stream.GetImage() : im;
//@if(KEEP_LINE_COOL)
            beginLine = input_stream.getBeginLine();
            beginColumn = input_stream.getBeginColumn();
            endLine = input_stream.getEndLine();
            endColumn = input_stream.getEndColumn();
//@fi
        }
//@else
        String im = jjstrLiteralImages[jjmatchedKind];
        curTokenImage = (im == null) ? input_stream.GetImage() : im;
//@if(KEEP_LINE_COOL)
        beginLine = input_stream.getBeginLine();
        beginColumn = input_stream.getBeginColumn();
        endLine = input_stream.getEndLine();
        endColumn = input_stream.getEndColumn();
//@fi
//@fi
        t = new Token(jjmatchedKind, curTokenImage);
//@if(KEEP_LINE_COOL)
        t.beginLine = beginLine;
        t.endLine = endLine;
        t.beginColumn = beginColumn;
        t.endColumn = endColumn;
//@fi
        return t;
    }

//@invoke(DUMP_STATE_SETS)
//@foreach(NON_ASCII_TABLE)
    private static final boolean jjCanMove___NON_ASCII_TABLE_METHOD__(int hiByte, int i1, int i2, long l1, long l2) {
        switch (hiByte) {
            __NON_ASCII_TABLE_MOVE__
            return false;
        }
    }

//@end

    int curLexState = __DEFAULT_LEX_STATE__;
    int defaultLexState = __DEFAULT_LEX_STATE__;
    int jjnewStateCnt;
    int jjround;
    int jjmatchedPos;
    int jjmatchedKind;

/** Get the next Token. */
    public Token getNextToken() {
//@if(HAS_SPECIAL)
        Token specialToken = null;
//@fi
        Token matchedToken;
        int curPos = 0;

EOFLoop :
        for (;;) {
            try {
                curChar = input_stream.BeginToken();
            } catch(Exception e) {
//@if(DEBUG_TOKEN_MANAGER)
                debugStream.println(\"Returning the <EOF> token.\\n\");
//@fi
                jjmatchedKind = 0;
                jjmatchedPos = -1;
                matchedToken = jjFillToken();
//@if(HAS_SPECIAL)
                matchedToken.specialToken = specialToken;
//@fi
//@invoke(DUMP_GET_NEXT_TOKEN)
        }
    }

//@if(DEBUG_TOKEN_MANAGER)
    protected static final int[][][] statesForState = __STATES_FOR_STATE__;
    protected static final int[][] kindForState = __KIND_FOR_STATE__;
//@fi
//@if(HAS_LOOP)
	int[] jjemptyLineNo = new int[__MAX_LEX_STATES__];
	int[] jjemptyColNo = new int[__MAX_LEX_STATES__];
    boolean[] jjbeenHere = new boolean[__MAX_LEX_STATES__];
//@fi
    void SkipLexicalActions(Token matchedToken) {
        switch (jjmatchedKind) {
//@invoke(DUMP_SKIP_ACTIONS)
            default:
                break;
        }
    }

    void MoreLexicalActions() {
        jjimageLen += (lengthOfMatch = jjmatchedPos + 1);
        switch (jjmatchedKind) {
//@invoke(DUMP_MORE_ACTIONS)
            default:
                break;
        }
    }

    void TokenLexicalActions(Token matchedToken) {
        switch (jjmatchedKind) {
//@invoke(DUMP_TOKEN_ACTIONS)
            default:
                break;
        }
    }

    private void jjCheckNAdd(int state) {
        if (jjrounds[state] != jjround) {
            jjstateSet[jjnewStateCnt++] = state;
            jjrounds[state] = jjround;
        }
    }

    private void jjAddStates(int start, int end) {
        do {
            jjstateSet[jjnewStateCnt++] = jjnextStates[start];
        } while (start++ != end);
    }

    private void jjCheckNAddTwoStates(int state1, int state2) {
        jjCheckNAdd(state1);
        jjCheckNAdd(state2);
    }

//@if(CHECK_NADD_STATES_DUAL_NEEDED)
    private void jjCheckNAddStates(int start, int end) {
        do {
            jjCheckNAdd(jjnextStates[start]);
        } while (start++ != end);
    }

//@fi
//@if(CHECK_NADD_STATES_UNARY_NEEDED)
    private void jjCheckNAddStates(int start) {
        jjCheckNAdd(jjnextStates[start]);
        jjCheckNAdd(jjnextStates[start + 1]);
    }

//@fi

    /**
     * Constructor.
     */
    public Lexer(JavaCharStream stream) {
        input_stream = stream;
    }

    /**
     * Constructor.
     */
    public Lexer(JavaCharStream stream, int lexState) {
        ReInit(stream);
        SwitchTo(lexState);
    }

    /**
     * Reinitialise parser.
     */
    public void ReInit(JavaCharStream stream) {
        jjmatchedPos = 0;
        jjnewStateCnt = 0;
        curLexState = defaultLexState;
        input_stream = stream;
        ReInitRounds();
    }

    private void ReInitRounds() {
        int i;
        jjround = 0x80000001;
        for (i = __STATE_SET_SIZE__; i-- > 0; ) {
            jjrounds[i] = 0x80000000;
        }
    }

    /**
     * Reinitialise parser.
     */
    public void ReInit(JavaCharStream stream, int lexState) {
        ReInit(stream);
        SwitchTo(lexState);
    }

    /**
     * Switch to specified lex state.
     */
    public void SwitchTo(int lexState) {
        if (lexState >= __STATE_COUNT__ || lexState < 0)
            throw new TokenException(
                "Error: Ignoring invalid lexical state : " + lexState + ". State unchanged.",
                TokenException.INVALID_LEXICAL_STATE);
        else
            curLexState = lexState;
    }

    /**
     * Lexer state names.
     */
    public static final String[] lexStateNames = {
//@foreach(STATE_NAMES)
        "__STATE_NAMES_VALUE__",
//@end
    };
//@invoke(DUMP_STATIC_VAR_DECLARATIONS)

    private       JavaCharStream input_stream;
    private final int[]          jjrounds   = new int[__STATE_SET_SIZE__];
    private final int[]          jjstateSet = new int[2 * __STATE_SET_SIZE__];
    private final StringBuilder  jjimage    = new StringBuilder();
    private       StringBuilder  image      = jjimage;
    private       int            jjimageLen;
    private       int            lengthOfMatch;
    protected     int            curChar;
}