//@if(nfa)
//@apply(sets)
static const int* const* const statesForState[] = {
//@apply(refs)
};
static const int* const statesForStateLen[] = {
//@apply(lenRefs)
};
//@else
static const int* const* const statesForState[] = { nullptr };
static const int* const statesForStateLen[] = { nullptr };
//@fi
