package spl.requiresanalysis;

import java.util.List;
import java.util.Objects;

public record OperationProviderEvidenceResult(
        int inputProviderEvidenceCount,
        int operationCandidateCount,
        List<OperationProviderEvidence> evidence
) {
    public OperationProviderEvidenceResult {
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    }
}
