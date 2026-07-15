package spl.dependency;

import spl.ProductSignature;
import spl.entity.JavaRelationType;
import spl.entity.ResolutionStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record DependencyEdge(
        String edgeId,
        String sourceEntityId,
        String targetEntityId,
        JavaRelationType relationType,
        Path sourceFile,
        int sourceLine,
        Path targetFile,
        String sourceBlockId,
        String sourceGroupId,
        ProductSignature sourceSignature,
        String targetGroupId,
        ProductSignature targetSignature,
        boolean crossFile,
        List<String> observedProducts,
        ResolutionStatus resolutionStatus
) {
    public DependencyEdge {
        edgeId = Objects.requireNonNull(edgeId, "edgeId");
        sourceEntityId = Objects.requireNonNull(sourceEntityId, "sourceEntityId");
        targetEntityId = Objects.requireNonNull(targetEntityId, "targetEntityId");
        relationType = Objects.requireNonNull(relationType, "relationType");
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        targetFile = Objects.requireNonNull(targetFile, "targetFile");
        sourceBlockId = Objects.requireNonNull(sourceBlockId, "sourceBlockId");
        sourceGroupId = Objects.requireNonNull(sourceGroupId, "sourceGroupId");
        sourceSignature = Objects.requireNonNull(sourceSignature, "sourceSignature");
        targetGroupId = Objects.requireNonNull(targetGroupId, "targetGroupId");
        targetSignature = Objects.requireNonNull(targetSignature, "targetSignature");
        observedProducts = List.copyOf(Objects.requireNonNull(observedProducts, "observedProducts"));
        resolutionStatus = Objects.requireNonNull(resolutionStatus, "resolutionStatus");
    }
}
