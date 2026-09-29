//@if(production)
 // __descriptor__
//@else

// __descriptor__
//@fi
let __nodeVar__ = new_node(&TreeConstants::__nodeId__);
let mut __closedVar__ = true;
self.jjtree.open_node_scope(&__nodeVar__);
//@if(scopeHook)
self.jjtree_open_node_scope(__nodeVar__.as_ref());
//@fi
