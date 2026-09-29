if self.jjmatched_kind != 0 && self.jjmatched_kind != 0x7fffffff {
   eprintln!("   Currently matched the first {} characters as a {} token.", self.jjmatched_pos.wrapping_add(1), TOKEN_IMAGE[self.jjmatched_kind as usize]);
}
let mut kind_cnt = 0;
eprintln!("   Possible string literal matches : {{ {} }} ", //@apply(vectors));
