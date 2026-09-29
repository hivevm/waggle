//@if(nfa)
//@apply(sets)
static const int* const* const statesForState[] = {__setRefs__ };
static const int* const statesForStateLen[] = {__lenRefs__ };
//@else
static const int* const* const statesForState[] = { nullptr };
static const int* const statesForStateLen[] = { nullptr };
//@fi
