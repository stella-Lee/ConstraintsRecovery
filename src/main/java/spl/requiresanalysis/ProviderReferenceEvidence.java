package spl.requiresanalysis;

import spl.ProductSignature;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ProviderReferenceEvidence(
        String evidenceId,
        String sourceEntityId,
        String sourceEntityName,
        String sourceEntityType,
        String sourceBlockId,
        String sourceGroupId,
        ProductSignature sourceSignature,
        Path sourceFile,
        int sourceLine,
        String targetEntityId,
        String targetEntityName,
        String targetEntityType,
        String targetBlockId,
        String targetGroupId,
        ProductSignature targetSignature,
        Path targetFile,
        int targetLine,
        String referencedType,
        String productSetRelation,
        List<String> observedProducts,
        String evidenceType,
        String cautionReasons
) {
    public ProviderReferenceEvidence {
        evidenceId = Objects.requireNonNull(evidenceId, "evidenceId");
        sourceEntityId = Objects.requireNonNull(sourceEntityId, "sourceEntityId");
        sourceEntityName = Objects.requireNonNull(sourceEntityName, "sourceEntityName");
        sourceEntityType = Objects.requireNonNull(sourceEntityType, "sourceEntityType");
        sourceBlockId = Objects.requireNonNull(sourceBlockId, "sourceBlockId");
        sourceGroupId = Objects.requireNonNull(sourceGroupId, "sourceGroupId");
        sourceSignature = Objects.requireNonNull(sourceSignature, "sourceSignature");
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        targetEntityId = Objects.requireNonNull(targetEntityId, "targetEntityId");
        targetEntityName = Objects.requireNonNull(targetEntityName, "targetEntityName");
        targetEntityType = Objects.requireNonNull(targetEntityType, "targetEntityType");
        targetBlockId = Objects.requireNonNull(targetBlockId, "targetBlockId");
        targetGroupId = Objects.requireNonNull(targetGroupId, "targetGroupId");
        targetSignature = Objects.requireNonNull(targetSignature, "targetSignature");
        targetFile = Objects.requireNonNull(targetFile, "targetFile");
        referencedType = Objects.requireNonNull(referencedType, "referencedType");
        productSetRelation = Objects.requireNonNull(productSetRelation, "productSetRelation");
        observedProducts = List.copyOf(Objects.requireNonNull(observedProducts, "observedProducts"));
        evidenceType = Objects.requireNonNull(evidenceType, "evidenceType");
        cautionReasons = Objects.requireNonNull(cautionReasons, "cautionReasons");
    }
}
