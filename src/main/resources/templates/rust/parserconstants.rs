pub const EOF: i32 = 0;

// RegularExpression Ids
//@foreach(TOKENS)
pub const __TOKENS_LABEL__: u32 = __TOKENS_ORDINAL__;
//@end

// Lexical states
//@foreach(STATES)
pub const __STATES_NAME__: u32 = __STATES_INDEX__;
//@end

pub const TOKEN_IMAGE: [&str; __REXPRESSION_COUNT__] = [
//@foreach(REXPRESSION_COUNT)
	__REXPRESSION_LABEL__
//@end
];