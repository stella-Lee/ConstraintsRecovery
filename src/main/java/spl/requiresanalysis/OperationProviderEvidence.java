package spl.requiresanalysis;

import spl.ProductSignature;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record OperationProviderEvidence(
        String evidenceId,
        String operationKey,
        String operationName,
        ProductSignature sourceSignature,
        String sourceGroupIds,
        String sourceBlockIds,
        String sourceEntityIds,
        String sourceEntityNames,
        String providerEntityId,
        String providerEntityName,
        String providerEntityType,
        String providerBlockId,
        String providerGroupId,
        ProductSignature providerSignature,
        Path providerFile,
        int providerLine,
        String evidenceKinds,
        String relationTypes,
        String relationIds,
        int supportingRelationCount,
        List<String> observedProducts,
        String productSetRelation,
        String cautionReasons
) {
    public OperationProviderEvidence {
        evidenceId = Objects.requireNonNull(evidenceId, "evidenceId");
        operationKey = Objects.requireNonNull(operationKey, "operationKey");
        operationName = Objects.requireNonNull(operationName, "operationName");
        sourceSignature = Objects.requireNonNull(sourceSignature, "sourceSignature");
        sourceGroupIds = Objects.requireNonNull(sourceGroupIds, "sourceGroupIds");
        sourceBlockIds = Objects.requireNonNull(sourceBlockIds, "sourceBlockIds");
        sourceEntityIds = Objects.requireNonNull(sourceEntityIds, "sourceEntityIds");
        sourceEntityNames = Objects.requireNonNull(sourceEntityNames, "sourceEntityNames");
        providerEntityId = Objects.requireNonNull(providerEntityId, "providerEntityId");
        providerEntityName = Objects.requireNonNull(providerEntityName, "providerEntityName");
        providerEntityType = Objects.requireNonNull(providerEntityType, "providerEntityType");
        providerBlockId = Objects.requireNonNull(providerBlockId, "providerBlockId");
        providerGroupId = Objects.requireNonNull(providerGroupId, "providerGroupId");
        providerSignature = Objects.requireNonNull(providerSignature, "providerSignature");
        providerFile = Objects.requireNonNull(providerFile, "providerFile");
        evidenceKinds = Objects.requireNonNull(evidenceKinds, "evidenceKinds");
        relationTypes = Objects.requireNonNull(relationTypes, "relationTypes");
        relationIds = Objects.requireNonNull(relationIds, "relationIds");
        observedProducts = List.copyOf(Objects.requireNonNull(observedProducts, "observedProducts"));
        productSetRelation = Objects.requireNonNull(productSetRelation, "productSetRelation");
        cautionReasons = Objects.requireNonNull(cautionReasons, "cautionReasons");
    }
}
