// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

#[derive(Debug)]
pub enum TreeConstants {
//@foreach(NODES)
	__name__,
//@end
}

pub const JJT_NODE_NAME: [&str; __NODE_COUNT__] = [
//@foreach(NODE_NAMES)
	"__name__",
//@end
];
