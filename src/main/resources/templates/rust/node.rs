use std::rc::Rc;

use crate::__RUST_MODULE__::treeconstants::TreeConstants;

pub trait Node {
    fn get_id(&self) -> &'static TreeConstants;

    fn jjt_open(&self);
    fn jjt_close(&self);

    fn jjt_set_parent(&self, _n: &Rc<dyn Node>) {
        //this.parent = n;
    }

    fn jjt_add_child(&self, _n: Rc<dyn Node>, _i: usize) {
        // if (this.children == null) {
        //   this.children = new Node[i + 1];
        // } else if (i >= this.children.length) {
        //   Node[] c = new Node[i + 1];
        //   System.arraycopy(this.children, 0, c, 0, this.children.length);
        //   this.children = c;
        // }
        // this.children[i] = n;
    }
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

/*
  private final __PARSER_NAME__ parser;
  private final __PARSER_NAME__TreeConstants id;

  private Node   parent;
  private Node[] children;
  private Object value;
//@if(TRACK_TOKENS)
  private Token  firstToken;
  private Token  lastToken;
//@fi

  public Node(__PARSER_NAME__ p, __PARSER_NAME__TreeConstants i) {
    this.id = i;
    this.parser = p;
  }

  public __PARSER_NAME__ jjtParser() {
    return parser;
  }

  public Node jjtGetParent() {
    return this.parent;
  }

  public Node jjtGetChild(int i) {
    return this.children[i];
  }

  public int jjtGetNumChildren() {
    return (this.children == null) ? 0 : this.children.length;
  }

  public void jjtSetValue(Object value) {
    this.value = value;
  }

  public Object jjtGetValue() {
    return this.value;
  }

//@if(TRACK_TOKENS)
  public Token jjtGetFirstToken() { return firstToken; }
  public void jjtSetFirstToken(Token token) { this.firstToken = token; }
  public Token jjtGetLastToken() { return lastToken; }
  public void jjtSetLastToken(Token token) { this.lastToken = token; }

//@fi
//@if(VISITOR)
  public __VISITOR_RETURN_TYPE__ jjtAccept(__PARSER_NAME__Visitor visitor, __VISITOR_DATA_TYPE:Object__ data)
//@if(VISITOR_EXCEPTION)
     throws __VISITOR_EXCEPTION__
//@fi
{
//@if(VISITOR_RETURN_TYPE_VOID)
    visitor.visit(this, data);
//@else
    return visitor.visit(this, data);
//@fi
  }

  public Object childrenAccept(__PARSER_NAME__Visitor visitor, __VISITOR_DATA_TYPE:Object__ data)
//@if(VISITOR_EXCEPTION)
     throws __VISITOR_EXCEPTION__
//@fi
{
    if (children != null) {
      for (int i = 0; i < children.length; ++i) {
        children[i].jjtAccept(visitor, data);
      }
    }
    return data;
  }

//@fi

  @Override
  public String toString() {
    return this.id.value;
  }

  public String toString(String prefix) {
    return prefix + toString();
  }

  public void dump(String prefix) {
    System.out.println(toString(prefix));
    if (this.children != null) {
      for (Node child : this.children) {
        Node n = child;
        if (n != null) {
          n.dump(prefix + " ");
        }
      }
    }
  }
*/