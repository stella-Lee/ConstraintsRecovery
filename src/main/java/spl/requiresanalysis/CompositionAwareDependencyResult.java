package spl.requiresanalysis;

import java.util.List;
import java.util.Objects;

public record CompositionAwareDependencyResult(
        List<CompositionAwareDependency> dependencies,
        int examinedMethodCalls
) {
    public CompositionAwareDependencyResult {
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
    }
}
