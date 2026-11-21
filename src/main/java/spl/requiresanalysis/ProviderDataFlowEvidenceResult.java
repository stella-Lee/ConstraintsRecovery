package spl.requiresanalysis;

import java.util.List;
import java.util.Objects;

public record ProviderDataFlowEvidenceResult(
        int examinedRelations,
        int unresolvedRelations,
        int unresolvedProviders,
        int commonProviderTargets,
        int nonSubsetRelations,
        List<ProviderDataFlowEvidence> evidence
) {
    public ProviderDataFlowEvidenceResult {
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    }
}
