package spl.entity;

import spl.ProductSignature;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record JavaEntity(
        String entityId,
        JavaEntityType entityType,
        String simpleName,
        String qualifiedName,
        Path sourceFile,
        int startLine,
        int endLine,
        String containingBlockId,
        String signatureGroupId,
        ProductSignature effectiveProductSignature,
        List<String> observedProducts,
        ResolutionStatus resolutionStatus
) {
    public JavaEntity {
        entityId = Objects.requireNonNull(entityId, "entityId");
        entityType = Objects.requireNonNull(entityType, "entityType");
        simpleName = Objects.requireNonNull(simpleName, "simpleName");
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        containingBlockId = Objects.requireNonNull(containingBlockId, "containingBlockId");
        signatureGroupId = Objects.requireNonNull(signatureGroupId, "signatureGroupId");
        effectiveProductSignature = Objects.requireNonNull(effectiveProductSignature, "effectiveProductSignature");
        observedProducts = List.copyOf(Objects.requireNonNull(observedProducts, "observedProducts"));
        resolutionStatus = Objects.requireNonNull(resolutionStatus, "resolutionStatus");
    }
}
