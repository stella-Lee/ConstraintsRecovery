package spl.dependency;

import spl.entity.JavaRelation;

public record DependencyResolutionResult(
        JavaRelation relation,
        DependencyEdge edge,
        UnresolvedDependencyRelation unresolvedRelation
) {
    public boolean resolved() {
        return edge != null;
    }
}
