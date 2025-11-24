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
