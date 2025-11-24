pub const EOF: i32 = 0;

// RegularExpression Ids
//@foreach(TOKENS)
pub const __TOKEN_LABEL__: u32 = __TOKEN_ORDINAL__;
//@end

// Lexical states
//@foreach(STATES)
pub const __STATE_NAME__: u32 = __STATE_INDEX__;
//@end

pub const TOKEN_IMAGE: [&str; __PRODUCTIONS_COUNT__] = [
    "<EOF>",
//@foreach(PRODUCTIONS)
    __PRODUCTION_LABEL__
//@end
];