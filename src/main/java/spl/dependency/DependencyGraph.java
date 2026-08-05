package spl.dependency;

import java.util.List;
import java.util.Objects;

public record DependencyGraph(
        List<DependencyNode> nodes,
        List<DependencyEdge> edges,
        List<UnresolvedDependencyRelation> unresolvedRelations,
        DependencyGraphStatistics statistics
) {
    public DependencyGraph(List<DependencyNode> nodes, List<DependencyEdge> edges,
                           List<UnresolvedDependencyRelation> unresolvedRelations) {
        this(nodes, edges, unresolvedRelations, DependencyGraphStatistics.empty());
    }

    public DependencyGraph {
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
        unresolvedRelations = List.copyOf(Objects.requireNonNull(unresolvedRelations, "unresolvedRelations"));
        statistics = Objects.requireNonNull(statistics, "statistics");
    }
}
