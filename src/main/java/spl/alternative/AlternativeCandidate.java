package spl.alternative;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record AlternativeCandidate(
        String candidateId,
        String featureConditionA,
        String featureConditionB,
        String productSignatureA,
        String productSignatureB,
        AlternativeConfidence confidence,
        Set<AlternativeEvidenceCategory> evidenceCategories,
        List<String> supportingBlockPairs,
        List<String> supportingEntityPairs,
        String productSignatures,
        String sharedContext,
        String notes
) {
    public AlternativeCandidate {
        candidateId = Objects.requireNonNull(candidateId, "candidateId");
        featureConditionA = Objects.requireNonNull(featureConditionA, "featureConditionA");
        featureConditionB = Objects.requireNonNull(featureConditionB, "featureConditionB");
        productSignatureA = Objects.requireNonNull(productSignatureA, "productSignatureA");
        productSignatureB = Objects.requireNonNull(productSignatureB, "productSignatureB");
        confidence = Objects.requireNonNull(confidence, "confidence");
        evidenceCategories = Set.copyOf(Objects.requireNonNull(evidenceCategories, "evidenceCategories"));
        supportingBlockPairs = List.copyOf(Objects.requireNonNull(supportingBlockPairs, "supportingBlockPairs"));
        supportingEntityPairs = List.copyOf(Objects.requireNonNull(supportingEntityPairs, "supportingEntityPairs"));
        productSignatures = Objects.requireNonNull(productSignatures, "productSignatures");
        sharedContext = Objects.requireNonNull(sharedContext, "sharedContext");
        notes = Objects.requireNonNull(notes, "notes");
    }
}
