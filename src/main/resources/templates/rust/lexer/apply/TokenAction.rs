__kind__ => {
//@if(loopCheck)
if self.jjmatched_pos == usize::MAX {
    if self.jjbeenHere[__lexState__]
        && self.jjemptyLineNo[__lexState__] == self.input_stream.get_begin_line()
        && self.jjemptyColNo[__lexState__] == self.input_stream.get_begin_column()
    {
        return Err(LexicalError {
            line: self.input_stream.get_begin_line(),
            column: self.input_stream.get_begin_column(),
            message: format!(
                "Bailing out of infinite loop caused by repeated empty string matches at line {}, column {}.",
                self.input_stream.get_begin_line(), self.input_stream.get_begin_column()
            ),
        });
    }
    self.jjemptyLineNo[__lexState__] = self.input_stream.get_begin_line();
    self.jjemptyColNo[__lexState__] = self.input_stream.get_begin_column();
    self.jjbeenHere[__lexState__] = true;
}
//@fi
//@if(hasCode)
//@if(reset)
self.image.clear();
//@else
//@if(literal)
self.image.push_str(JJSTR_LITERAL_IMAGES[__kind__]);
self.length_of_match = JJSTR_LITERAL_IMAGES[__kind__].len();
//@else
self.length_of_match = self.jjmatched_pos + 1;
let suffix = self.input_stream.get_suffix(self.jjimage_len + self.length_of_match);
self.image.push_str(&suffix);
//@fi
//@fi
__code__
//@fi
}
