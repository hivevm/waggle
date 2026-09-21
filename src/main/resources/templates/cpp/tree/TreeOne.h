// disable warnings on parser header files
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"

#ifndef WAGGLE_TREE_ONE
#define WAGGLE_TREE_ONE

#include "Node.h"
//@foreach(NODES)
#include "__NODES_NAME__.h"
//@end

#endif

#pragma GCC diagnostic pop