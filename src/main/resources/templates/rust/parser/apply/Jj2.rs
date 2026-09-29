fn jj_2__name__(&mut self, xla: i32) -> Result<bool, ParseError> {
    self.jj_la = xla;
    self.jj_lastpos = self.token;
    self.jj_scanpos = self.token;
    self.jj_ls = false;
    let result = !self.jj_3__name__() || self.jj_ls;
    self.jj_ls = false;
//@if(RECORDS_EXPECTED_TOKENS)
    self.jj_save(__saveSlot__, xla);
//@fi
    if let Some(error) = &self.jj_lexical_error {
        return Err(error.clone().into());
    }
    Ok(result)
}

