    JJEnter<std::function<void()>> jjenter([this]() {trace_call  ("__name__"); });
    JJExit <std::function<void()>> jjexit ([this]() {trace_return("__name__"); });
//@apply(inner)
