package spl.requiresanalysis;

import spl.ProductSignature;

import java.util.List;
import java.util.Objects;

public record SignatureProviderBundleEvidence(
        String evidenceId,
        ProductSignature sourceSignature,
        ProductSignature providerSignature,
        String sourceGroupIds,
        String sourceBlockIds,
        String sourceEntityNames,
        String operationKeys,
        String providerEntityNames,
        String providerGroupIds,
        String providerBlockIds,
        String evidenceKinds,
        String relationTypes,
        int operationCount,
        int providerCount,
        int supportingRelationCount,
        List<String> observedProducts,
        String productSetRelation,
        String cautionReasons
) {
    public SignatureProviderBundleEvidence {
        evidenceId = Objects.requireNonNull(evidenceId, "evidenceId");
        sourceSignature = Objects.requireNonNull(sourceSignature, "sourceSignature");
        providerSignature = Objects.requireNonNull(providerSignature, "providerSignature");
        sourceGroupIds = Objects.requireNonNull(sourceGroupIds, "sourceGroupIds");
        sourceBlockIds = Objects.requireNonNull(sourceBlockIds, "sourceBlockIds");
        sourceEntityNames = Objects.requireNonNull(sourceEntityNames, "sourceEntityNames");
        operationKeys = Objects.requireNonNull(operationKeys, "operationKeys");
        providerEntityNames = Objects.requireNonNull(providerEntityNames, "providerEntityNames");
        providerGroupIds = Objects.requireNonNull(providerGroupIds, "providerGroupIds");
        providerBlockIds = Objects.requireNonNull(providerBlockIds, "providerBlockIds");
        evidenceKinds = Objects.requireNonNull(evidenceKinds, "evidenceKinds");
        relationTypes = Objects.requireNonNull(relationTypes, "relationTypes");
        observedProducts = List.copyOf(Objects.requireNonNull(observedProducts, "observedProducts"));
        productSetRelation = Objects.requireNonNull(productSetRelation, "productSetRelation");
        cautionReasons = Objects.requireNonNull(cautionReasons, "cautionReasons");
    }
}
