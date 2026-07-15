package spl.feature;

import spl.ProductSignature;
import spl.entity.JavaRelationType;

import java.util.Objects;

public record CandidateDependency(
        String sourceCandidateId,
        String targetCandidateId,
        CandidateDependencyScope dependencyScope,
        JavaRelationType relationType,
        int edgeCount,
        int crossFileEdgeCount,
        String sourceGroupId,
        String targetGroupId,
        ProductSignature sourceSignature,
        ProductSignature targetSignature
) {
    public CandidateDependency {
        sourceCandidateId = Objects.requireNonNull(sourceCandidateId, "sourceCandidateId");
        targetCandidateId = Objects.requireNonNull(targetCandidateId, "targetCandidateId");
        dependencyScope = Objects.requireNonNull(dependencyScope, "dependencyScope");
        relationType = Objects.requireNonNull(relationType, "relationType");
        sourceGroupId = Objects.requireNonNull(sourceGroupId, "sourceGroupId");
        targetGroupId = Objects.requireNonNull(targetGroupId, "targetGroupId");
        sourceSignature = Objects.requireNonNull(sourceSignature, "sourceSignature");
        targetSignature = Objects.requireNonNull(targetSignature, "targetSignature");
    }
}
