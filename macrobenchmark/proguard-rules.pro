# Macrobenchmark/test dependencies can reference Java compiler APIs that are
# compile-time/annotation-only and are not packaged in an Android runtime.
-dontwarn javax.lang.model.element.Modifier
