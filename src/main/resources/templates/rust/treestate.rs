use std::rc::Rc;

use crate::__RUST_MODULE__::node::Node;

pub struct TreeState {
    sp: usize,
    mk: usize,
    node_created: bool,
    marks: Vec<usize>,
    nodes: Vec<Rc<dyn Node>>,
}

impl TreeState {
    pub fn new() -> Self {
        TreeState::default()
    }

    pub fn is_node_created(&self) -> bool {
        self.node_created
    }

    pub fn root_node(&self) -> Option<&Rc<dyn Node>> {
        self.nodes.first()
    }

    pub fn node_arity(&self) -> usize {
        self.sp - self.mk
    }

    pub fn peek_node(&self) -> Option<&Rc<dyn Node>> {
        self.nodes.last()
    }

    pub fn pop_node(&mut self) -> Option<Rc<dyn Node>> {
        self.sp -= 1;
        if self.sp < self.mk {
            self.mk = self.marks.pop().unwrap();
        }
        self.nodes.pop()
    }

    pub fn push_node(&mut self, node: Rc<dyn Node>) {
        self.nodes.push(node);
        self.sp += 1;
    }

    pub fn clear_node_scope(&mut self, _n: Rc<dyn Node>) {
        while self.sp > self.mk {
            self.pop_node();
        }
        self.mk = self.marks.pop().unwrap();
    }

    pub fn open_node_scope(&mut self, n: &Rc<dyn Node>) {
        self.marks.push(self.mk);
        self.mk = self.sp;
        n.jjt_open();
    }

    pub fn close_node_scope(&mut self, n: &Rc<dyn Node>, num: usize) {
        self.mk = self.marks.pop().unwrap();
        for i in (0..num).rev() {
            let c = self.pop_node().unwrap();
            c.jjt_set_parent(n);
            n.jjt_add_child(c, i);
        }
        n.jjt_close();
        self.push_node(n.clone());
        self.node_created = true;
    }

    pub fn close_node_scope_bool(&mut self, n: &Rc<dyn Node>, condition: bool) {
        if condition {
            let a = self.node_arity();
            self.mk = self.marks.pop().unwrap();
            for i in (0..a).rev() {
                let c = self.pop_node().unwrap();
                c.jjt_set_parent(n);
                n.jjt_add_child(c, i);
            }
            n.jjt_close();
            self.push_node(n.clone());
            self.node_created = true;
        } else {
            self.mk = self.marks.pop().unwrap();
            self.node_created = false;
        }
    }

    pub fn reset(&mut self) {
        self.sp = 0;
        self.mk = 0;
        self.nodes.clear();
        self.marks.clear();
    }
}

impl Default for TreeState {
    fn default() -> Self {
        TreeState {
            sp: 0,
            mk: 0,
            node_created: false,
            marks: Vec::new(),
            nodes: Vec::new(),
        }
    }
}