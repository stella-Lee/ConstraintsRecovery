package spl.dependency;

public record DependencyGraphStatistics(
        int implementationDependencyCount,
        int structuralDependencyCount,
        int excludedDependencyCount,
        int externalDependencyRemovedCount,
        int unresolvedDependencyRemovedCount
) {
    public static DependencyGraphStatistics empty() {
        return new DependencyGraphStatistics(0, 0, 0, 0, 0);
    }
}
