package spl.dependency;

import spl.ProductSignature;
import spl.entity.JavaRelation;
import spl.entity.JavaRelationType;
import spl.entity.ResolutionStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record UnresolvedDependencyRelation(
        String relationId,
        JavaRelationType relationType,
        String sourceEntityId,
        String targetText,
        Path sourceFile,
        int sourceLine,
        String sourceBlockId,
        String sourceGroupId,
        ProductSignature sourceSignature,
        ResolutionStatus resolutionStatus,
        String failureReason,
        List<String> observedProducts
) {
    public UnresolvedDependencyRelation {
        relationId = Objects.requireNonNull(relationId, "relationId");
        relationType = Objects.requireNonNull(relationType, "relationType");
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        sourceBlockId = Objects.requireNonNull(sourceBlockId, "sourceBlockId");
        sourceGroupId = Objects.requireNonNull(sourceGroupId, "sourceGroupId");
        sourceSignature = Objects.requireNonNull(sourceSignature, "sourceSignature");
        resolutionStatus = Objects.requireNonNull(resolutionStatus, "resolutionStatus");
        failureReason = Objects.requireNonNull(failureReason, "failureReason");
        observedProducts = List.copyOf(Objects.requireNonNull(observedProducts, "observedProducts"));
    }

    public static UnresolvedDependencyRelation from(JavaRelation relation, String failureReason) {
        return new UnresolvedDependencyRelation(
                relation.relationId(),
                relation.relationType(),
                relation.sourceEntityId(),
                relation.unresolvedTargetText(),
                relation.sourceFile(),
                relation.sourceLine(),
                relation.containingBlockId(),
                relation.signatureGroupId(),
                relation.effectiveProductSignature(),
                relation.resolutionStatus(),
                failureReason,
                relation.observedProducts()
        );
    }
}
