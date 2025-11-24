const BUFFER_SIZE: usize = 4096;

pub struct CharStream<'a> {
    token_begin: usize,
    bufpos: isize,
    bufsize: usize,
    available: usize,
    buffer: [char; BUFFER_SIZE],
    next_char_buf: [char; BUFFER_SIZE],
//@if(KEEP_LINE_COLUMN)
    line: usize,
    column: usize,
    bufline: [usize; BUFFER_SIZE],
    bufcolumn: [usize; BUFFER_SIZE],
//@fi
    tab_size: usize,
    max_next_char_ind: usize,
    next_char_ind: isize,
    in_buf: usize,
    input_stream: &'a str,
    prev_char_is_cr: bool,
    prev_char_is_lf: bool,
}

impl<'a> CharStream<'a> {
    pub fn new(text: &'a str) -> Self {
        CharStream {
            token_begin: usize::MAX,
            bufpos: -1,
            available: BUFFER_SIZE,
            bufsize: BUFFER_SIZE,
            buffer: ['\0'; BUFFER_SIZE],
            next_char_buf: ['\0'; BUFFER_SIZE],
//@if(KEEP_LINE_COLUMN)
            line: 1,
            column: 0,
            bufline: [0; BUFFER_SIZE],
            bufcolumn: [0; BUFFER_SIZE],
//@fi
            tab_size: 1,
            max_next_char_ind: 0,
            next_char_ind: -1,
            in_buf: 0,
            input_stream: text,
            prev_char_is_cr: false,
            prev_char_is_lf: false,
        }
    }

    pub fn get_image(&self) -> String {
        if self.bufpos >= self.token_begin.try_into().unwrap() {
            return self.buffer[self.token_begin..(self.bufpos as usize - self.token_begin) + 1]
                .iter()
                .collect::<String>();
        } else {
            return self.buffer[self.token_begin..(self.bufsize - self.token_begin)]
                .iter()
                .collect::<String>()
                + &self.buffer[0..self.bufpos as usize + 1]
                    .iter()
                    .collect::<String>();
        }
    }

    fn fill_buff(&mut self) -> Result<(), std::io::Error> {
        let mut i: usize = 0;
        if self.max_next_char_ind == BUFFER_SIZE {
            self.max_next_char_ind = 0;
            self.next_char_ind = 0;
        }

        self.input_stream
            .chars()
            .skip(self.max_next_char_ind)
            .take(BUFFER_SIZE - self.max_next_char_ind)
            .for_each(|c| {
                self.next_char_buf[self.max_next_char_ind] = c;
                self.max_next_char_ind += 1;
                i += 1;
            });
        /*
        try {
          if ((i = this.input_stream.read(self.next_char_buf, self.max_next_char_ind,
              BUFFER_SIZE - self.max_next_char_ind)) == -1) {
            this.input_stream.close();
            throw new java.io.IOException();
          } else {
            self.max_next_char_ind += i;
          }
        } catch (java.io.IOException e) {
          if self.bufpos != 0 {
            self.bufpos -= 1;
            selfbackup(0);
          } else {
            self.bufline[self.bufpos as usize] = self.line;
            self.bufcolumn[self.bufpos as usize] = self.column;
          }
          return Err(std::io::Error::new(std::io::ErrorKind::Other, "Not implemented"))
        }
        */
        return Ok(());
    }

    fn read_byte(&mut self) -> Result<char, std::io::Error> {
        self.next_char_ind += 1;
        if self.next_char_ind >= self.max_next_char_ind.try_into().unwrap() {
            let result = self.fill_buff();
            if result.is_err() {
                return Err(result.err().unwrap());
            }
        }
        return Ok(self.next_char_buf[self.next_char_ind as usize]);
    }

    pub fn begin_token(&mut self) -> Result<char, std::io::Error> {
        if self.in_buf > 0 {
            self.in_buf -= 1;

            self.bufpos += 1;
            if self.bufpos == self.bufsize.try_into().unwrap() {
                self.bufpos = 0;
            }

            self.token_begin = self.bufpos as usize;
            return Ok(self.buffer[self.bufpos as usize]);
        }

        self.token_begin = 0;
        self.bufpos = -1;

        self.read_char()
    }
//@if(KEEP_LINE_COLUMN)

    fn update_line_column(&mut self, c: char) {
        if self.prev_char_is_lf {
            self.prev_char_is_lf = false;
            self.column = 1;
            self.line += self.column;
        } else if self.prev_char_is_cr {
            self.prev_char_is_cr = false;
            if c == '\n' {
                self.prev_char_is_lf = true;
            } else {
                self.column = 1;
                self.line += self.column;
            }
        }

        match c {
            '\r' => {
                self.prev_char_is_cr = true;
            }
            '\n' => {
                self.prev_char_is_lf = true;
            }
            '\t' => {
                self.column -= 1;
                self.column += self.tab_size - (self.column % self.tab_size);
            }
            _ => {}
        }

        self.bufline[self.bufpos as usize] = self.line;
        self.bufcolumn[self.bufpos as usize] = self.column;
    }
//@fi

    pub fn read_char(&mut self) -> Result<char, std::io::Error> {
        if self.in_buf > 0 {
            self.in_buf -= 1;

            self.bufpos += 1;
            if self.bufpos == self.bufsize.try_into().unwrap() {
                self.bufpos = 0;
            }

            return Ok(self.buffer[self.bufpos as usize]);
        }

        self.bufpos += 1;
        if self.bufpos == self.available.try_into().unwrap() {
            self.adjust_buff_size();
        }

        let mut c: char = self.read_byte()?;
        self.buffer[self.bufpos as usize] = c;
        if self.buffer[self.bufpos as usize] == '\\' {
//@if(KEEP_LINE_COLUMN)
            self.update_line_column(c);
//@fi

            let mut back_slash_cnt: usize = 1;
            loop {
                // Read all the backslashes
                self.bufpos += 1;
                if self.bufpos == self.available.try_into().unwrap() {
                    self.adjust_buff_size();
                }

                let result = self.read_byte();
                if result.is_err() {
                    // We are returning one backslash so we should only backup (count-1)
                    if back_slash_cnt > 1 {
                        self.backup(back_slash_cnt - 1);
                    }
                    return Ok('\\');
                }

                c = result.unwrap();
                self.buffer[self.bufpos as usize] = c;
                if self.buffer[self.bufpos as usize] != '\\' {
                    self.update_line_column(c);
                    // found a non-backslash char.
                    if c == 'u' && (back_slash_cnt & 1) == 1 {
                        self.bufpos -= 1;
                        if self.bufpos < 0 {
                            self.bufpos = self.bufsize as isize - 1;
                        }
                        break;
                    }

                    self.backup(back_slash_cnt);
                    return Ok('\\');
                }

//@if(KEEP_LINE_COLUMN)
                self.update_line_column(c);
//@fi
                back_slash_cnt += 1;
            }

            /*
                  // Here, we have seen an odd number of backslash's followed by a 'u'
                  try {
                    while ((c = self.read_byte()) == 'u') {
//@if(KEEP_LINE_COLUMN)
                      self.column += 1;
//@else
                      ;
//@fi
                    }

                    self.buffer[self.bufpos] =
                        c = (char) ((hexval(c) << 12) | (hexval(read_byte()) << 8)
                            | (hexval(read_byte()) << 4) | hexval(read_byte()));

//@if(KEEP_LINE_COLUMN)
                    self.column += 4;
//@fi
                  } catch (java.io.IOException e) {
//@if(KEEP_LINE_COLUMN)
                    return Err(std::io::Error::new(std::io::ErrorKind::Other, "Invalid escape character at line {} column {}.", self.line, self.column))
//@else
                   return Err(std::io::Error::new(std::io::ErrorKind::Other, "Invalid escape character in input"))
//@fi
                  }
            */

            if back_slash_cnt == 1 {
                return Ok(c);
            } else {
                self.backup(back_slash_cnt - 1);
                return Ok('\\');
            }
        } else {
//@if(KEEP_LINE_COLUMN)
            self.update_line_column(c);
//@fi
            return Ok(c);
        }
    }

    pub fn backup(&mut self, amount: usize) {
        self.in_buf += amount;
        self.bufpos -= amount as isize;
        if self.bufpos < 0 {
            self.bufpos += self.bufsize as isize;
        }
    }

    fn done(&mut self) {
        //    this.next_char_buf = null;
        //    this.buffer = null;
//@if(KEEP_LINE_COLUMN)
        //    this.bufline = null;
        //    this.bufcolumn = null;
//@fi
    }

    fn get_tab_size(&self) -> usize {
        self.tab_size
    }

    fn set_tab_size(&mut self, i: usize) {
        self.tab_size = i;
    }

//@if(KEEP_LINE_COLUMN)
    pub fn get_end_column(&self) -> usize {
        self.bufcolumn[self.bufpos as usize]
    }

    pub fn get_end_line(&self) -> usize {
        self.bufline[self.bufpos as usize]
    }

    pub fn get_begin_column(&self) -> usize {
        self.bufcolumn[self.token_begin]
    }

    pub fn get_begin_line(&self) -> usize {
        self.bufline[self.token_begin]
    }
//@fi

    fn adjust_buff_size(&mut self) {
        /*
            if (this.available == this.bufsize) {
              if (this.token_begin > 2048) {
                this.bufpos = 0;
                this.available = this.token_begin;
              } else {
                expand_buff(false);
              }
            } else if (this.available > this.token_begin) {
              this.available = this.bufsize;
            } else if ((this.token_begin - this.available) < 2048) {
              expand_buff(true);
            } else {
              this.available = this.token_begin;
            }
        */
    }
    /*

      fn expand_buff(&mut self, wrapAround: bool) {
        char[] newbuffer = new char[this.bufsize + 2048];
//@if(KEEP_LINE_COLUMN)
        int newbufline[] = new int[this.bufsize + 2048];
        int newbufcolumn[] = new int[this.bufsize + 2048];
//@fi

        try {
          if (wrapAround) {
            System.arraycopy(this.buffer, this.token_begin, newbuffer, 0, this.bufsize - this.token_begin);
            System.arraycopy(this.buffer, 0, newbuffer, this.bufsize - this.token_begin, this.bufpos);
            this.buffer = newbuffer;
//@if(KEEP_LINE_COLUMN)

            System.arraycopy(this.bufline, this.token_begin, newbufline, 0, this.bufsize - this.token_begin);
            System.arraycopy(this.bufline, 0, newbufline, this.bufsize - this.token_begin, this.bufpos);
            this.bufline = newbufline;

            System.arraycopy(this.bufcolumn, this.token_begin, newbufcolumn, 0, this.bufsize - this.token_begin);
            System.arraycopy(this.bufcolumn, 0, newbufcolumn, this.bufsize - this.token_begin, this.bufpos);
            this.bufcolumn = newbufcolumn;
//@fi

            this.bufpos += (this.bufsize - this.token_begin) as isize;
          } else {
            System.arraycopy(this.buffer, this.token_begin, newbuffer, 0, this.bufsize - this.token_begin);
            this.buffer = newbuffer;
//@if(KEEP_LINE_COLUMN)

            System.arraycopy(this.bufline, this.token_begin, newbufline, 0, this.bufsize - this.token_begin);
            this.bufline = newbufline;

            System.arraycopy(this.bufcolumn, this.token_begin, newbufcolumn, 0, this.bufsize - this.token_begin);
            this.bufcolumn = newbufcolumn;
//@fi

            this.bufpos -= this.token_begin as isize;
          }
        } catch (Exception t) {
          throw new RuntimeException(t.getMessage());
        }

        this.available = (this.bufsize += 2048);
        this.token_begin = 0;
      }
    */
}

fn hexval(c: char) -> usize {
    match c {
        '0' => 0,
        '1' => 1,
        '2' => 2,
        '3' => 3,
        '4' => 4,
        '5' => 5,
        '6' => 6,
        '7' => 7,
        '8' => 8,
        '9' => 9,
        'a' | 'A' => 10,
        'b' | 'B' => 11,
        'c' | 'C' => 12,
        'd' | 'D' => 13,
        'e' | 'E' => 14,
        'f' | 'F' => 15,
        _ => usize::MAX,
    }
}