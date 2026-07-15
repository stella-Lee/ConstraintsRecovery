package spl.feature;

import java.util.List;
import java.util.Objects;

public record FeatureEffectCandidateResult(
        List<FeatureEffectCandidate> candidates,
        List<CandidateDependency> candidateDependencies,
        List<UnassignedBlock> unassignedBlocks
) {
    public FeatureEffectCandidateResult {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        candidateDependencies = List.copyOf(Objects.requireNonNull(candidateDependencies, "candidateDependencies"));
        unassignedBlocks = List.copyOf(Objects.requireNonNull(unassignedBlocks, "unassignedBlocks"));
    }
}
