/**********************************************
 * THE JAVACC TOKEN SPECIFICATION STARTS HERE *
 **********************************************/

/* JAVACC RESERVED WORDS: These are the only tokens in JavaCC but not in Java */

TOKEN =
  < _LOOKAHEAD: "LOOKAHEAD" >
| < _IGNORE_CASE: "IGNORE_CASE" >
| < _GRAMMAR: "grammar" >
| < _OPTIONS: "options" >
| < _TOKEN: "TOKEN" >
| < _SPECIAL_TOKEN: "SPECIAL_TOKEN" >
| < _MORE: "MORE" >
| < _SKIP: "SKIP" >
| < _EOF: "EOF" >
;

/*
 * The remainder of the tokens are exactly (except for the removal of tokens
 * as in the Java grammar and must be diff equivalent
 * (again with the exceptions above) to it.
 */

/* WHITE SPACE */

SPECIAL_TOKEN =
  " "
| "\t"
| "\n"
| "\r"
| "\f"
;

/* COMMENTS */

MORE =
  "//" : IN_SINGLE_LINE_COMMENT
| "/*" : IN_MULTI_LINE_COMMENT
| "<?" : PARSER_CODE
;


SPECIAL_TOKEN <IN_SINGLE_LINE_COMMENT>= <SINGLE_LINE_COMMENT: "\n" | "\r" | "\r\n" > : DEFAULT ;
SPECIAL_TOKEN <IN_MULTI_LINE_COMMENT>= <MULTI_LINE_COMMENT: "*/" > : DEFAULT ;


TOKEN <PARSER_CODE>=
  < ECODE_END: "?>" > : DEFAULT
| < ECODE_DATA: (~["?"] | "?" ~[">"])+ >
;

MORE <IN_SINGLE_LINE_COMMENT,IN_MULTI_LINE_COMMENT>= < ~[] > ;

/* JAVA RESERVED WORDS AND LITERALS */

TOKEN =
  < BOOLEAN: "boolean" >
| < INT: "int" >
| < CHAR: "char" >
| < NULL: "null" >
| < TRUE: "true" >
| < FALSE: "false" >
;

/* JAVA LITERALS */

TOKEN =
  < INTEGER_LITERAL:
      <DECIMAL_LITERAL> (["l","L"])?
    | <HEX_LITERAL> (["l","L"])?
    | <OCTAL_LITERAL> (["l","L"])?
  >
| < #DECIMAL_LITERAL: ["1"-"9"] (["0"-"9"])* >
| < #HEX_LITERAL: "0" ["x","X"] (["0"-"9","a"-"f","A"-"F"])+ >
| < #OCTAL_LITERAL: "0" (["0"-"7"])* >
| < FLOATING_POINT_LITERAL:
      <DECIMAL_FLOATING_POINT_LITERAL>
    | <HEXADECIMAL_FLOATING_POINT_LITERAL>
  >
| < #DECIMAL_FLOATING_POINT_LITERAL:
      (["0"-"9"])+ "." (["0"-"9"])* (<DECIMAL_EXPONENT>)? (["f","F","d","D"])?
    | "." (["0"-"9"])+ (<DECIMAL_EXPONENT>)? (["f","F","d","D"])?
    | (["0"-"9"])+ <DECIMAL_EXPONENT> (["f","F","d","D"])?
    | (["0"-"9"])+ (<DECIMAL_EXPONENT>)? ["f","F","d","D"]
  >
| < #DECIMAL_EXPONENT: ["e","E"] (["+","-"])? (["0"-"9"])+ >
| < #HEXADECIMAL_FLOATING_POINT_LITERAL:
     "0" ["x", "X"] (["0"-"9","a"-"f","A"-"F"])+ (".")? <HEXADECIMAL_EXPONENT> (["f","F","d","D"])?
   | "0" ["x", "X"] (["0"-"9","a"-"f","A"-"F"])* "." (["0"-"9","a"-"f","A"-"F"])+ <HEXADECIMAL_EXPONENT> (["f","F","d","D"])?
  >
| < #HEXADECIMAL_EXPONENT: ["p","P"] (["+","-"])? (["0"-"9"])+ >
| < CHARACTER_LITERAL:
    "'"
    ( (~["'","\\","\n","\r"])
      | ("\\"
        ( ["n","t","b","r","f","\\","'","\""]
        | ["0"-"7"] ( ["0"-"7"] )?
        | ["0"-"3"] ["0"-"7"] ["0"-"7"]
        )
      )
    )
    "'"
  >
| < STRING_LITERAL:
    "\""
    ( (~["\"","\\","\n","\r"])
      | ("\\"
        ( ["n","t","b","r","f","\\","'","\""]
        | ["0"-"7"] ( ["0"-"7"] )?
        | ["0"-"3"] ["0"-"7"] ["0"-"7"]
        )
      )
    )*
    "\""
  >
;

/* SEPARATORS */

TOKEN =
  < LPAREN: "(" >
| < RPAREN: ")" >
| < LBRACE: "{" >
| < RBRACE: "}" >
| < LBRACKET: "[" >
| < RBRACKET: "]" >
| < COMMA: "," >
| < SEMICOLON: ";" >
| < DOT: "." >
;

/* OPERATORS */

TOKEN =
  < ASSIGN: "=" >
| < GT: ">" >
| < LT: "<" >
| < TILDE: "~" >
| < HOOK: "?" >
| < HASH: "#" >
| < COLON: ":" >
| < PLUS: "+" >
| < MINUS: "-" >
| < STAR: "*" >
| < BIT_OR: "|" >
;

/* IDENTIFIERS */

TOKEN =
  < IDENTIFIER: <LETTER> (<PART_LETTER>)* >
| < #LETTER:
    // Approximates Character.isIdentifierStart. Deliberately over-accepts: this is a
    // strict superset of the per-script ranges it replaces, so every identifier that
    // lexed before still lexes. No other token starts with a non-ASCII character.
    [
      "$",
      "A"-"Z",
      "_",
      "a"-"z",
      "\u00a2"-"\uffff"
    ]
  >
| < #PART_LETTER:
    // Approximates Character.isIdentifierPart, likewise a strict superset. The low
    // ranges are the ignorable control characters Java counts as identifier parts.
    [
      "\u0000"-"\u0008",
      "\u000e"-"\u001b",
      "$",
      "0"-"9",
      "A"-"Z",
      "_",
      "a"-"z",
      "\u007f"-"\uffff"
    ]
  >
;
