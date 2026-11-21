package spl.requiresanalysis;

import spl.ProductSignature;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ProviderDataFlowEvidence(
        String evidenceId,
        String evidenceKind,
        String sourceEntityId,
        String sourceEntityName,
        String sourceEntityType,
        String sourceBlockId,
        String sourceGroupId,
        ProductSignature sourceSignature,
        Path sourceFile,
        int sourceLine,
        String providerEntityId,
        String providerEntityName,
        String providerEntityType,
        String providerBlockId,
        String providerGroupId,
        ProductSignature providerSignature,
        Path providerFile,
        int providerLine,
        String intermediateEntityId,
        String intermediateEntityName,
        String intermediateEntityType,
        String relationId,
        String relationType,
        String codeSnippet,
        String productSetRelation,
        List<String> observedProducts,
        String cautionReasons
) {
    public ProviderDataFlowEvidence {
        evidenceId = Objects.requireNonNull(evidenceId, "evidenceId");
        evidenceKind = Objects.requireNonNull(evidenceKind, "evidenceKind");
        sourceEntityId = Objects.requireNonNull(sourceEntityId, "sourceEntityId");
        sourceEntityName = Objects.requireNonNull(sourceEntityName, "sourceEntityName");
        sourceEntityType = Objects.requireNonNull(sourceEntityType, "sourceEntityType");
        sourceBlockId = Objects.requireNonNull(sourceBlockId, "sourceBlockId");
        sourceGroupId = Objects.requireNonNull(sourceGroupId, "sourceGroupId");
        sourceSignature = Objects.requireNonNull(sourceSignature, "sourceSignature");
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        providerEntityId = Objects.requireNonNull(providerEntityId, "providerEntityId");
        providerEntityName = Objects.requireNonNull(providerEntityName, "providerEntityName");
        providerEntityType = Objects.requireNonNull(providerEntityType, "providerEntityType");
        providerBlockId = Objects.requireNonNull(providerBlockId, "providerBlockId");
        providerGroupId = Objects.requireNonNull(providerGroupId, "providerGroupId");
        providerSignature = Objects.requireNonNull(providerSignature, "providerSignature");
        providerFile = Objects.requireNonNull(providerFile, "providerFile");
        intermediateEntityId = Objects.requireNonNull(intermediateEntityId, "intermediateEntityId");
        intermediateEntityName = Objects.requireNonNull(intermediateEntityName, "intermediateEntityName");
        intermediateEntityType = Objects.requireNonNull(intermediateEntityType, "intermediateEntityType");
        relationId = Objects.requireNonNull(relationId, "relationId");
        relationType = Objects.requireNonNull(relationType, "relationType");
        codeSnippet = Objects.requireNonNull(codeSnippet, "codeSnippet");
        productSetRelation = Objects.requireNonNull(productSetRelation, "productSetRelation");
        observedProducts = List.copyOf(Objects.requireNonNull(observedProducts, "observedProducts"));
        cautionReasons = Objects.requireNonNull(cautionReasons, "cautionReasons");
    }
}
