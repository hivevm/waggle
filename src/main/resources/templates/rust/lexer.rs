use crate::__RUST_MODULE__::charstream::CharStream;
use crate::__RUST_MODULE__::token::Token;
use std::cmp;

//@foreach(LOHI_BYTES)
const JJBIT_VEC__LOHI_BYTES_INDEX__: [u64; __LOHI_BYTES_LENGTH__] = [__LOHI_BYTES_BYTES__];
//@end

pub const LEX_STATE_NAMES: [&str; __STATE_NAMES_LENGTH__] = [
//@foreach(STATE_NAMES)
     "__STATE_NAME__",
//@end
];

pub const JJSTR_LITERAL_IMAGES: [&str; __LITERAL_IMAGES_LENGTH__] = [
//@foreach(LITERAL_IMAGES)
    "__LITERAL_IMAGE_NAME__",
//@end
];


//@invoke(DUMP_STATIC_VAR_DECLARATION)

//@invoke(DUMP_STATES_SET)

//@if(DEBUG_TOKEN_MANAGER)
const statesForState: [][][] = {__STATES_FOR_STATE__};
const kindForState: [][] = {__KIND_FOR_STATE__};
//@fi

pub struct Lexer<'a> {
    cur_lex_state: i8,
    default_lex_state: i8,
    jjnew_state_cnt: usize,
    jjround: usize,
    jjmatched_pos: usize,
    jjmatched_kind: u32,

    jjrounds: [usize; __STATE_SET_SIZE__],
    jjstate_set: [usize; __STATE_SET_SIZE2__],
    jjimage_len: usize,
    length_of_match: usize,
    cur_char: u32,
    input_stream: CharStream<'a>,
//@if(HAS_LOOP)
    jjemptyLineNo: [usize; __MAX_LEX_STATES__],
    jjemptyColNo: [usize; __MAX_LEX_STATES__],
    jjbeenHere: [bool; __MAX_LEX_STATES__],
//@fi
}

impl<'a> Lexer<'a> {
    pub fn new(text: &'a str) -> Self {
        Lexer {
            cur_lex_state: __DEFAULT_LEX_STATE__,
            default_lex_state: __DEFAULT_LEX_STATE__,
            jjnew_state_cnt: 0,
            jjround: 0,
            jjmatched_pos: 0,
            jjmatched_kind: 0,
            jjrounds: [0; __STATE_SET_SIZE__],
            jjstate_set: [0; __STATE_SET_SIZE2__],
            jjimage_len: 0,
            length_of_match: 0,
            cur_char: 0,
            input_stream: CharStream::new(text),
//@if(HAS_LOOP)
            jjemptyLineNo: [0, __MAX_LEX_STATES__],
            jjemptyColNo: [0, __MAX_LEX_STATES__],
            jjbeenHere: [false, __MAX_LEX_STATES__],
//@fi
        }
    }

    pub fn new_from_state(text: &'a str, lex_state: i8) -> Self {
        let mut lexer = Lexer {
            cur_lex_state: lex_state,
            default_lex_state: lex_state,
            jjnew_state_cnt: 0,
            jjround: 0,
            jjmatched_pos: 0,
            jjmatched_kind: 0,
            jjrounds: [0; __STATE_SET_SIZE__],
            jjstate_set: [0; __STATE_SET_SIZE2__],
            jjimage_len: 0,
            length_of_match: 0,
            cur_char: 0,
            input_stream: CharStream::new(text),
//@if(HAS_LOOP)
            jjemptyLineNo: [0, __MAX_LEX_STATES__],
            jjemptyColNo: [0, __MAX_LEX_STATES__],
            jjbeenHere: [false, __MAX_LEX_STATES__],
//@fi
        };
        lexer.re_init_rounds();
        lexer.switch_to(lex_state);
        lexer
    }
    /*
      pub TokenManager(JavaCharStream stream) {
        input_stream = stream;
      }
    */

    pub fn switch_to(&mut self, lex_state: i8) {
        if lex_state >= __STATE_COUNT__ {
            panic!(
                "Error: Ignoring invalid lexical state : {}. State unchanged.",
                lex_state
            );
        } else {
            self.cur_lex_state = lex_state;
        }
    }

    fn re_init_rounds(&mut self) {
        let mut i: usize = __STATE_SET_SIZE__;
        self.jjround = 0x80000001;
        while i > 0 {
            i -= 1;
            self.jjrounds[i] = 0x80000000;
        }
    }

    fn jj_fill_token(&self) -> Token {
//@if(KEEP_LINE_COOL)
        let mut begin_line: usize = 0;
        let mut end_line: usize = 0;
        let mut begin_column: usize = 0;
        let mut end_column: usize = 0;
//@fi
//@if(HAS_EMPTY_MATCH)
        if self.jjmatched_pos < 0 {
            if (image == null) {
                cur_token_image = "";
            } else {
               cur_token_image = image.toString();
            }

//@if(KEEP_LINE_COOL)
            begin_line = end_line = self.input_stream.get_end_line();
            begin_column = end_column = self.input_stream.get_end_column();
//@fi
        } else {
            String im = JJSTR_LITERAL_IMAGES[self.jjmatched_kind as usize];
            cur_token_image: &str = if im == "" { self.input_stream.get_image() } else { im };
//@if(KEEP_LINE_COOL)
            begin_line = self.input_stream.get_begin_line();
            begin_column = self.input_stream.get_begin_column();
            end_line = self.input_stream.get_end_line();
            end_column = self.input_stream.get_end_column();
//@fi
        }
//@else
        let im: &str = JJSTR_LITERAL_IMAGES[self.jjmatched_kind as usize];
        let cur_token_image = if im == "" {
            self.input_stream.get_image()
        } else {
            im.to_string()
        };
//@if(KEEP_LINE_COOL)
        begin_line = self.input_stream.get_begin_line();
        begin_column = self.input_stream.get_begin_column();
        end_line = self.input_stream.get_end_line();
        end_column = self.input_stream.get_end_column();
//@fi
//@fi
//@if(KEEP_LINE_COOL)
        Token::new(
            self.jjmatched_kind,
            cur_token_image,
            begin_line,
            begin_column,
            end_line,
            end_column,
        )
//@else
        Token::new(self.jjmatched_kind, cur_token_image)
//@fi
    }

    pub fn get_next_token(&mut self) -> Token {
//@if(HAS_SPECIAL)
        let mut specialToken: Token;
//@fi
        let mut matched_token: Token;
        let mut cur_pos: usize = 0;

        'EOFLoop: loop {
            let result = self.input_stream.begin_token();
            if !result.is_err() {
                self.cur_char = u32::from(result.unwrap());
            } else {
//@if(DEBUG_TOKEN_MANAGER)
                debugStream.println("Returning the <EOF> token.\n");
//@fi
                self.jjmatched_kind = 0;
                self.jjmatched_pos = usize::MAX;
                matched_token = self.jj_fill_token();
//@if(HAS_SPECIAL)
                matched_token.specialToken = specialToken;
//@fi
            //@invoke(DUMP_GET_NEXT_TOKEN)
        }
    }

    fn skip_lexical_actions(&self, matched_token: &Token) {
        match self.jjmatched_kind {
            //@invoke(DUMP_SKIP_ACTIONS)
            _ => {}
        }
    }

    fn more_lexical_actions(&mut self) {
        self.length_of_match = self.jjmatched_pos + 1;
        self.jjimage_len += self.length_of_match;
        match self.jjmatched_kind {
            //@invoke(DUMP_MORE_ACTIONS)
            _ => {}
        }
    }

    fn token_lexical_actions(&self, matched_token: &Token) {
        match self.jjmatched_kind {
            //@invoke(DUMP_TOKEN_ACTIONS)
            _ => {}
        }
    }

    fn jj_check_n_add(&mut self, state: usize) {
        if self.jjrounds[state] != self.jjround {
            self.jjstate_set[self.jjnew_state_cnt] = state;
            self.jjnew_state_cnt += 1;
            self.jjrounds[state] = self.jjround;
        }
    }

    fn jj_add_states(&mut self, start: usize, end: usize) {
        let mut do_while = true;
        let mut index = start;
        while do_while {
            self.jjstate_set[self.jjnew_state_cnt] = JJNEXT_STATES[index];
            self.jjnew_state_cnt += 1;
            do_while = index != end;
            index += 1;
        }
    }

    fn jj_check_n_add_two_states(&mut self, state1: usize, state2: usize) {
        self.jj_check_n_add(state1);
        self.jj_check_n_add(state2);
    }
//@if(CHECK_NADD_STATES_DUAL_NEEDED)

    fn jj_check_n_add_states(&mut self, start: usize, end: usize) {
        let mut do_while = true;
        let mut index = start;
        while do_while {
            self.jj_check_n_add(JJNEXT_STATES[index]);
            do_while = index != end;
            index += 1;
        }
    }
//@fi
//@if(CHECK_NADD_STATES_UNARY_NEEDED)

    fn jj_check_n_add_states(&mut self, start: usize) {
        self.jj_check_n_add(JJNEXT_STATES[start]);
        self.jj_check_n_add(JJNEXT_STATES[start + 1]);
    }
//@fi

//@foreach(STATES)
    //@invoke(NFA_AND_DFA)
//@end
}

//@foreach(NON_ASCII_TABLE)
fn jj_can_move__NON_ASCII_TABLE_NAME__(hi_byte: u32, i1: usize, i2: usize, l1: u64, l2: u64) -> bool {
    match hi_byte {
        //@invoke(NON_ASCII_TABLE_MOVE)
    }
}

//@end