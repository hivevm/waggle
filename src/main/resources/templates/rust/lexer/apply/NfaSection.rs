//@if(low)
let l: u64 = 1u64 << self.cur_char;
//@fi
//@if(high)
let l: u64 = 1u64 << (self.cur_char & 0o77);
//@fi
//@if(other)
let hi_byte: u32 = self.cur_char >> 8;
let l1: u64 = 1u64 << (hi_byte & 0o77);
let l2: u64 = 1u64 << (self.cur_char & 0o77);
let i1: usize = (hi_byte >> 6) as usize;
let i2: usize = ((self.cur_char & 0xff) >> 6) as usize;
//@fi
let mut while_cond = true;
while while_cond {
	i -= 1;
	loop {
		match self.jjstate_set[i] {
			//@apply(arms)
			_ => {
//@if(breakInDefault)
				break;
//@fi
			}
		}
		break;
	}
	while_cond = i != starts_at;
}
