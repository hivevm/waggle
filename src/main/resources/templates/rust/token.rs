use std::cell::RefCell;
use std::rc::Rc;

pub struct Token {
    pub kind: u32,
    pub image: String,
    pub next: Option<Rc<RefCell<Token>>>,
    pub special: Option<Rc<RefCell<Token>>>,
//@if(KEEP_LINE_COLUMN)
    begin_line: usize,
    begin_column: usize,
    end_line: usize,
    end_column: usize,
//@fi
}

impl Token {
//@if(KEEP_LINE_COLUMN)
    pub fn new(
        kind: u32,
        image: String,
        begin_line: usize,
        begin_column: usize,
        end_line: usize,
        end_column: usize,
    ) -> Token {
//@else
    pub fn new(kind: u32, image: &'static str) -> Token {
//@fi
        Token {
            kind,
            image,
            next: None,
            special: None,
//@if(KEEP_LINE_COLUMN)
            begin_line,
            begin_column,
            end_line,
            end_column,
//@fi
        }
    }

    pub fn empty() -> Token {
        Token {
            kind: 0,
            image: String::new(),
            next: None,
            special: None,
//@if(KEEP_LINE_COLUMN)
            begin_line: 0,
            begin_column: 0,
            end_line: 0,
            end_column: 0,
//@fi
        }
    }

    pub fn set_token(&mut self, token: Token) {
        self.next = Some(Rc::new(RefCell::new(token)));
    }
}