package spl.alternative;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AlternativeCandidateResult(
        int blockPairsExamined,
        int weakEvidenceRemovedCount,
        int trivialRemovedCount,
        int commonBlockRemovedCount,
        List<AlternativeCandidate> candidates,
        Map<AlternativeEvidenceCategory, Long> evidenceFrequency
) {
    public AlternativeCandidateResult {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        evidenceFrequency = Map.copyOf(Objects.requireNonNull(evidenceFrequency, "evidenceFrequency"));
    }
}
