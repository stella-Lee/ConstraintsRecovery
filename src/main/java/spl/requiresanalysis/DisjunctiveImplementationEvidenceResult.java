package spl.requiresanalysis;

import java.util.List;
import java.util.Objects;

public record DisjunctiveImplementationEvidenceResult(
        List<DisjunctiveImplementationEvidence> candidates,
        int examinedBranchObligations
) {
    public DisjunctiveImplementationEvidenceResult {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
    }
}
