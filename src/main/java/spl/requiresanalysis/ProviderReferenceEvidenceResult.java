package spl.requiresanalysis;

import java.util.List;
import java.util.Objects;

public record ProviderReferenceEvidenceResult(
        int examinedTypeReferences,
        int unresolvedReferences,
        int externalReferences,
        int commonTargetReferences,
        int nonSubsetReferences,
        List<ProviderReferenceEvidence> evidence
) {
    public ProviderReferenceEvidenceResult {
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    }
}
