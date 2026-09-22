// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

#[derive(Debug)]
pub enum TreeConstants {
//@foreach(NODES)
	__LABEL__,
//@end
}

pub const JJT_NODE_NAME: [&str; __NODES__] = [
//@foreach(NODES)
	"__TITLE__",
//@end
];
