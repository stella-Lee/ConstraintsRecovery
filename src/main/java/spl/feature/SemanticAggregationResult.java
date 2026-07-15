package spl.feature;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SemanticAggregationResult(
        List<AggregatedFeatureEffectCandidate> candidates,
        Map<String, ComponentEvidence> evidenceByComponentId,
        List<ComponentSimilarity> similarities,
        List<ComponentMergeDecision> mergeDecisions,
        List<FeatureEffectCandidate> unassignedComponents
) {
    public SemanticAggregationResult {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        evidenceByComponentId = Map.copyOf(Objects.requireNonNull(evidenceByComponentId, "evidenceByComponentId"));
        similarities = List.copyOf(Objects.requireNonNull(similarities, "similarities"));
        mergeDecisions = List.copyOf(Objects.requireNonNull(mergeDecisions, "mergeDecisions"));
        unassignedComponents = List.copyOf(Objects.requireNonNull(unassignedComponents, "unassignedComponents"));
    }
}
