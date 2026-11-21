package spl.requiresanalysis;

import spl.ProductSignature;

import java.util.Objects;

public record ImplementationEvidenceSummary(
        String summaryId,
        String sourceEvidenceId,
        ProductSignature sourceSignature,
        ProductSignature providerSignature,
        String evidenceImportance,
        int operationCount,
        int providerCount,
        int supportingRelationCount,
        String representativeProviders,
        String representativeOperations,
        String representativeTokens,
        String productSetRelation,
        String interpretationNote,
        String cautionReasons
) {
    public ImplementationEvidenceSummary {
        summaryId = Objects.requireNonNull(summaryId, "summaryId");
        sourceEvidenceId = Objects.requireNonNull(sourceEvidenceId, "sourceEvidenceId");
        sourceSignature = Objects.requireNonNull(sourceSignature, "sourceSignature");
        providerSignature = Objects.requireNonNull(providerSignature, "providerSignature");
        evidenceImportance = Objects.requireNonNull(evidenceImportance, "evidenceImportance");
        representativeProviders = Objects.requireNonNull(representativeProviders, "representativeProviders");
        representativeOperations = Objects.requireNonNull(representativeOperations, "representativeOperations");
        representativeTokens = Objects.requireNonNull(representativeTokens, "representativeTokens");
        productSetRelation = Objects.requireNonNull(productSetRelation, "productSetRelation");
        interpretationNote = Objects.requireNonNull(interpretationNote, "interpretationNote");
        cautionReasons = Objects.requireNonNull(cautionReasons, "cautionReasons");
    }
}
