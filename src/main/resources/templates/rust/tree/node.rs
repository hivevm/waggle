// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

use std::rc::Rc;

use crate::__RUST_MODULE__::treeconstants::TreeConstants;

pub trait Node {
	fn get_id(&self) -> &'static TreeConstants;

	fn jjt_open(&self);
	fn jjt_close(&self);

	fn jjt_set_parent(&self, _n: &Rc<dyn Node>) {}

	fn jjt_add_child(&self, _n: Rc<dyn Node>, _i: usize) {}
}

struct NodeImpl {
	id: &'static TreeConstants,
}

impl Node for NodeImpl {
	fn get_id(&self) -> &'static TreeConstants {
		self.id
	}

	fn jjt_open(&self) {}
	fn jjt_close(&self) {}
}

pub fn new_node(id: &'static TreeConstants) -> Rc<dyn Node> {
	Rc::new(NodeImpl { id })
}

