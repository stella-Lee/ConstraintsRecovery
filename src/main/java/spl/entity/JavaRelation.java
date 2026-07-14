package spl.entity;

import spl.ProductSignature;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record JavaRelation(
        String relationId,
        String sourceEntityId,
        String targetEntityId,
        String unresolvedTargetText,
        JavaRelationType relationType,
        Path sourceFile,
        int sourceLine,
        String containingBlockId,
        String signatureGroupId,
        ProductSignature effectiveProductSignature,
        List<String> observedProducts,
        ResolutionStatus resolutionStatus
) {
    public JavaRelation {
        relationId = Objects.requireNonNull(relationId, "relationId");
        relationType = Objects.requireNonNull(relationType, "relationType");
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        containingBlockId = Objects.requireNonNull(containingBlockId, "containingBlockId");
        signatureGroupId = Objects.requireNonNull(signatureGroupId, "signatureGroupId");
        effectiveProductSignature = Objects.requireNonNull(effectiveProductSignature, "effectiveProductSignature");
        observedProducts = List.copyOf(Objects.requireNonNull(observedProducts, "observedProducts"));
        resolutionStatus = Objects.requireNonNull(resolutionStatus, "resolutionStatus");
    }
}
