use std::cell::RefCell;
use std::rc::Rc;

use crate::__RUST_MODULE__::lexer::Lexer;
use crate::__RUST_MODULE__::node::{Node, new_node};
use crate::__RUST_MODULE__::parserconstants::*;
use crate::__RUST_MODULE__::token::Token;
use crate::__RUST_MODULE__::treeconstants::TreeConstants;
//@if(USE_AST)
use crate::__RUST_MODULE__::treestate::TreeState;
//@fi

//@foreach(TOKEN_MASKS)
const JJ_LA1__TOKEN_MASKS_INDEX__: [u32; __MASK_INDEX__] = [
    __TOKEN_MASKS_VALUE__
];
//@end

pub struct Parser<'a> {
//@if(USE_AST)
    jjtree: TreeState,
//@fi
    lexer: Lexer<'a>,
    token: Rc<RefCell<Token>>,
    jj_nt: Option<Rc<RefCell<Token>>>,
//@if(!CACHE_TOKENS)
    jj_ntk: Option<Rc<Token>>,
//@fi
//@if(DEPTH_LIMIT)
    jj_depth: u32,
//@fi
//@if(MASK_INDEX)
    jj_scanpos: Option<Rc<RefCell<Token>>>,
    jj_lastpos: Option<Rc<RefCell<Token>>>,
    jj_la: u32,
//@if(LOOKAHEAD_NEEDED)
    jj_lookingAhead: bool = false,
    jj_semLA: bool,
//@fi
//@fi
//@if(ERROR_REPORTING)
    jj_gen: u32,
    jj_la1: [u32; __MASK_INDEX__],
    jj_kind: u32,
//@if(MASK_INDEX)
    jj_2_rtns: [Option<&'a mut JJCalls>; __JJ2_INDEX__],
    jj_rescan: bool,
    jj_gc: u32,
//@fi
//@fi
    /*
    //@if(ERROR_REPORTING)
      private final java.util.List<int[]> jj_expentries = new java.util.ArrayList<>();
      private int[]                       jj_expentry;
    //@if(JJ2_INDEX)
      private final int[]                 jj_lasttokens = new int[100];
      private int                         jj_endpos;
    //@fi
    */
}

impl<'a> Parser<'a> {
    pub fn new(text: &'a str) -> Self {
        let mut parser = Parser {
//@if(USE_AST)
            jjtree: TreeState::new(),
//@fi
            lexer: Lexer::new(text),
            token: Rc::new(RefCell::new(Token::empty())),
            jj_nt: None,
//@if(MASK_INDEX)
            jj_scanpos: None,
            jj_lastpos: None,
            jj_la: 0,
//@fi
//@if(ERROR_REPORTING)
            jj_gen: 0,
            jj_la1: [0; __MASK_INDEX__],
            jj_kind: 0,
//@fi
//@if(MASK_INDEX)
            jj_2_rtns: Default::default(),
//@if(ERROR_REPORTING)
            jj_rescan: false,
            jj_gc: 0,
//@fi
//@fi
        };

//@if(CACHE_TOKENS)
        parser
            .token
            .borrow_mut()
            .set_token(parser.lexer.get_next_token());
        parser.jj_nt = parser.token.borrow().next.clone();
//@else
        parser.jj_ntk = None;
//@fi
//@if(DEPTH_LIMIT)
        parser.jj_depth = -1;
//@fi
//@if(ERROR_REPORTING)
//@if(MASK_INDEX)
        for i in 0..__MASK_INDEX__ {
            parser.jj_la1[i] = u32::MAX; //-1;
        }
//@fi
//@if(JJ2_INDEX)
        for i in 0..parser.jj_2_rtns.len() {
            //      parser.jj_2_rtns[i] = JJCalls::new();
        }
//@fi
//@fi
        parser
    }

    pub fn root_node(&self) -> Option<&Rc<dyn Node>> {
        self.jjtree.root_node()
    }
//@foreach(NORMALPRODUCTIONS)
    //@invoke(NORMALPRODUCTION)
//@end
//@foreach(LOOKAHEADS)
    //@invoke(LOOKAHEAD)
//@end
//@foreach(EXPANSIONS)
    //@invoke(EXPANSION)
//@end
    fn jj_consume_token(&mut self, kind: u32) -> Result<(), std::io::Error> {
//@if(CACHE_TOKENS)
        let mut old_token = self.token.clone();
        self.token = self.jj_nt.clone().unwrap().clone();
        if self.token.clone().borrow_mut().next.is_none() {
            self.jj_nt
                .clone()
                .unwrap()
                .borrow_mut()
                .set_token(self.lexer.get_next_token());
        }
        self.jj_nt = self.jj_nt.clone().unwrap().borrow().next.clone();
//@else
        Token old_token;
        if ((old_token = token).next != null)
            token = token.next;
        else
            token = token.next = token_source.get_next_token();
        jj_ntk = -1;
//@fi
        if self.token.clone().borrow().kind == kind {
//@if(ERROR_REPORTING)
            self.jj_gen += 1;
//@if(JJ2_INDEX)
            self.jj_gc += 1;
            if self.jj_gc > 100 {
                self.jj_gc = 0;
                let len = self.jj_2_rtns.len();
                for i in 0..len {
                    /*
                              JJCalls c = jj_2_rtns[i];
                              while (c != null) {
                                if (c.gen < jj_gen)
                                  c.first = null;
                                c = c.next;
                              }
                    */
                }
            }
//@fi
//@fi
//@if(DEBUG_PARSER)
            trace_token(token, "");
//@fi
            return Ok(());
        }
//@if(CACHE_TOKENS)
        self.jj_nt = Some(self.token.clone());
//@fi
        self.token = old_token.clone();
//@if(ERROR_REPORTING)
        self.jj_kind = kind;
//@fi
        Err(std::io::Error::new(
            std::io::ErrorKind::Other,
            "generateParseException",
        ))
    }

    /*
//@if(JJ2_INDEX)
      @SuppressWarnings("serial")
      static private final class LookaheadSuccess extends java.lang.RuntimeException {
        @Override
        public Throwable fillInStackTrace() {
          return this;
        }
      }
      static private final LookaheadSuccess jj_ls = new LookaheadSuccess();
    */
    fn jj_scan_token(&mut self, kind: u32) -> bool {
        if Rc::ptr_eq(
            &self.jj_scanpos.clone().unwrap(),
            &self.jj_lastpos.clone().unwrap(),
        ) {
            self.jj_la -= 1;
            if self.jj_scanpos.clone().unwrap().borrow().next.is_none() {
                self.jj_scanpos
                    .clone()
                    .unwrap()
                    .borrow_mut()
                    .set_token(self.lexer.get_next_token());
                self.jj_lastpos = Some(
                    self.jj_scanpos
                        .clone()
                        .unwrap()
                        .borrow()
                        .next
                        .clone()
                        .unwrap()
                        .clone(),
                );
                self.jj_scanpos = Some(self.jj_lastpos.clone().unwrap().clone());
            } else {
                self.jj_lastpos = Some(
                    self.jj_scanpos
                        .clone()
                        .unwrap()
                        .borrow()
                        .next
                        .clone()
                        .unwrap()
                        .clone(),
                );
                self.jj_scanpos = Some(self.jj_lastpos.clone().unwrap().clone());
            }
        } else {
            self.jj_scanpos = Some(
                self.jj_scanpos
                    .clone()
                    .unwrap()
                    .borrow()
                    .next
                    .clone()
                    .unwrap(),
            );
        }
//@if(ERROR_REPORTING)
        if self.jj_rescan {
            let mut i: u32 = 0;
            let mut tok = Some(self.token.clone());
            while !tok.is_none()
                && Rc::ptr_eq(&tok.clone().unwrap(), &self.jj_scanpos.clone().unwrap())
            {
                i += 1;
                tok = tok.unwrap().borrow_mut().next.clone()
            }
            if !tok.is_none() {
                self.jj_add_error_token(kind, i);
            }
//@if(DEBUG_LOOKAHEAD)
        } else {
            trace_scan(jj_scanpos, kind);
//@fi
        }
//@if(DEBUG_LOOKAHEAD)
        trace_scan(jj_scanpos, kind);
//@fi
        if self.jj_scanpos.clone().unwrap().borrow().kind != kind {
            return true;
        }
        /*
            if (jj_la == 0 && jj_scanpos == jj_lastpos)
              throw __PARSER_NAME__.jj_ls;
        */
        false
    }
    /*
//@fi

      final public Token get_next_token() {
//@if(CACHE_TOKENS)
        if ((token = jj_nt).next != null)
          jj_nt = jj_nt.next;
        else
          jj_nt = jj_nt.next = token_source.get_next_token();
//@else
        if (token.next != null)
          token = token.next;
        else
          token = token.next = token_source.get_next_token();
          jj_ntk = -1;
//@fi
//@if(ERROR_REPORTING)
        jj_gen++;
//@fi
//@if(DEBUG_PARSER)
        trace_token(token, " (in get_next_token)");
//@fi
        return this.token;
      }

      final public Token getToken(int index) {
//@if(LOOKAHEAD_NEEDED)
        Token t = jj_lookingAhead ? jj_scanpos : token;
//@else
        Token t = this.token;
//@fi
        for (int i = 0; i < index; i++) {
          if (t.next != null)
            t = t.next;
          else
            t = t.next = token_source.get_next_token();
        }
        return t;
      }

//@if(!CACHE_TOKENS)
      private int jj_ntk_f() {
        if ((jj_nt=token.next) == null)
          return (jj_ntk = (token.next=token_source.get_next_token()).kind);
        else
          return (jj_ntk = jj_nt.kind);
      }
//@fi
//@fi
    */
    fn jj_add_error_token(&mut self, kind: u32, pos: u32) {
        /*
            if (pos >= 100) {
              return;
            }

            if (pos == (this.jj_endpos + 1)) {
              this.jj_lasttokens[this.jj_endpos++] = kind;
            } else if (this.jj_endpos != 0) {
              this.jj_expentry = new int[this.jj_endpos];

              for (int i = 0; i < this.jj_endpos; i++) {
                this.jj_expentry[i] = this.jj_lasttokens[i];
              }

              for (int[] oldentry : this.jj_expentries) {
                if (oldentry.length == this.jj_expentry.length) {
                  boolean isMatched = true;

                  for (int i = 0; i < this.jj_expentry.length; i++) {
                    if (oldentry[i] != this.jj_expentry[i]) {
                      isMatched = false;
                      break;
                    }

                  }
                  if (isMatched) {
                    this.jj_expentries.add(this.jj_expentry);
                    break;
                  }
                }
              }

              if (pos != 0) {
                this.jj_lasttokens[(this.jj_endpos = pos) - 1] = kind;
              }
            }
        */
    }
    /*
      public ParseException generateParseException() {
        this.jj_expentries.clear();
        boolean[] la1tokens = new boolean[__TOKEN_COUNT__];
        if (this.jj_kind >= 0) {
          la1tokens[this.jj_kind] = true;
          this.jj_kind = -1;
        }
        for (int i = 0; i < __MASK_INDEX__; i++) {
          if (this.jj_la1[i] == this.jj_gen) {
            for (int j = 0; j < 32; j++) {
//@foreach(TOKEN_MASKS_LA1)
              if ((__PARSER_NAME__.JJ_LA1__LA1_SUFFIX__[i] & (1 << j)) != 0) {
                la1tokens[__LA1_MASK__j] = true;
              }
//@end
            }
          }
        }
        for (int i = 0; i < __TOKEN_COUNT__; i++) {
          if (la1tokens[i]) {
            this.jj_expentry = new int[1];
            this.jj_expentry[0] = i;
            this.jj_expentries.add(this.jj_expentry);
          }
        }
//@if(JJ2_INDEX)
        this.jj_endpos = 0;
        jj_rescan_token();
        jj_add_error_token(0, 0);
//@fi
        int[][] exptokseq = new int[this.jj_expentries.size()][];
        for (int i = 0; i < this.jj_expentries.size(); i++) {
          exptokseq[i] = this.jj_expentries.get(i);
        }

        return new ParseException(this.token, exptokseq, __PARSER_NAME__Constants.tokenImage,
            this.token_source == null ? null : __PARSER_NAME__TokenManager.LEX_STATE_NAMES[this.token_source.curLexState]);
      }

//@else
      public ParseException generateParseException() {
         Token errortok = token.next;
//@if(KEEP_LINE_COLUMN)
  	     int line = errortok.beginLine, column = errortok.beginColumn;
//@fi
    	 String mess = (errortok.kind == 0) ? tokenImage[0] : errortok.image;
//@if(KEEP_LINE_COLUMN)
         return new ParseException("Parse error at line " + line + ", column " + column + ".  "
                + "Encountered: " + mess);
//@else
  	     return new ParseException("Parse error at <unknown location>.  Encountered: " + mess);
//@fi
      }

//@fi
      private boolean trace_enabled;

      final public boolean trace_enabled() {
        return this.trace_enabled;
      }

//@if(DEBUG_PARSER)
      private int trace_indent = 0;

      final public void enable_tracing() {
         trace_enabled = true;
      }

      final public void disable_tracing() {
         trace_enabled = false;
      }

      protected void trace_call(String s) {
         if (trace_enabled) {
           for (int i = 0; i < trace_indent; i++) { System.out.print(" "); }
           System.out.println("Call:	" + s);
         }
         trace_indent = trace_indent + 2;
      }

      protected void trace_return(String s) {
         trace_indent = trace_indent - 2;
         if (trace_enabled) {
           for (int i = 0; i < trace_indent; i++) { System.out.print(" "); }
           System.out.println("Return: " + s);
         }
      }

      protected void trace_token(Token t, String where) {
         if (trace_enabled) {
           for (int i = 0; i < trace_indent; i++) { System.out.print(" "); }
           System.out.print("Consumed token: <" + tokenImage[t.kind]);
           if (t.kind != 0 && !tokenImage[t.kind].equals("\\"" + t.image + "\\"")) {
             System.out.print(": \\"" + TokenException.addEscapes(" + "t.image) + "\\"");
           }
          genCodeLine(
              "	   System.out.println(" at line " + t.beginLine + " + "" column " + t.beginColumn + ">" + where);
         }
      }

      protected void trace_scan(Token t1, int t2) {
         if (trace_enabled) {
           for (int i = 0; i < trace_indent; i++) { System.out.print(" "); }
           System.out.print("Visited token: <" + tokenImage[t1.kind]);
           if (t1.kind != 0 && !tokenImage[t1.kind].equals("\\"" + t1.image + "\\"")) {
             System.out.print(": \\"" + TokenException.addEscapes(" + "t1.image) + "\\"");
           }
           System.out.println(" at line " + t1.beginLine + ""
              + " column " + t1.beginColumn + ">; Expected token: <" + tokenImage[t2] + ">");
         }
      }

//@else
      final public void enable_tracing() {}

      final public void disable_tracing() {}

//@fi
//@if(JJ2_INDEX)
//@if(ERROR_REPORTING)

    */

    fn jj_rescan_token(&mut self) {
        self.jj_rescan = true;
        for i in 0..8 {
            /*
                    let p: JJCalls = self.jj_2_rtns[i];

                    do {
                      if (p.gen > this.jj_gen) {
                        this.jj_la = p.arg;
                        this.jj_lastpos = this.jj_scanpos = p.first;
                        switch (i) {
//@foreach(JJ2_OFFSET)
                          case __JJ2_OFFSET_INDEX__:
                            jj_3__JJ2_OFFSET_VALUE__();
                            break;
//@end
                        }
                      }
                      p = p.next;
                    } while (p != null);
            */
        }
        self.jj_rescan = false;
    }

    fn jj_save(&mut self, index: u32, xla: u32) {
        /*
            JJCalls p = this.jj_2_rtns[index];
            while (p.gen > this.jj_gen) {
              if (p.next == null) {
                p = p.next = new JJCalls();
                break;
              }
              p = p.next;
            }

            p.gen = (this.jj_gen + xla) - this.jj_la;
            p.first = this.token;
            p.arg = xla;
        */
    }
//@fi
//@fi

//@if(USE_AST)
    pub fn jjtree_open_node_scope(&self, node: &dyn Node) {
        print!(">>> Open: {:?}\n", node.get_id());
    }
    pub fn jjtree_close_node_scope(&self, node: &dyn Node) {
        print!(">>> Close: {:?}\n", node.get_id());
    }
//@fi
}

//@if(JJ2_INDEX)
//@if(ERROR_REPORTING)
struct JJCalls {
    gen0: u32,
    arg: u32,
    /*
      Token   first;
      JJCalls next;
    */
}

impl JJCalls {
    fn new() -> JJCalls {
        JJCalls { gen0: 0, arg: 0 }
    }
}
//@fi
//@fi