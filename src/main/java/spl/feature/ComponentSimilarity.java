package spl.feature;

import java.util.List;
import java.util.Objects;

public record ComponentSimilarity(
        String groupId,
        String componentA,
        String componentB,
        double lexicalSimilarity,
        double contextSimilarity,
        double neighborhoodSimilarity,
        double directDependency,
        double sharedBlockEvidence,
        double combinedScore,
        double threshold,
        int supportingEvidenceCount,
        List<String> supportingEvidence
) {
    public ComponentSimilarity {
        groupId = Objects.requireNonNull(groupId, "groupId");
        componentA = Objects.requireNonNull(componentA, "componentA");
        componentB = Objects.requireNonNull(componentB, "componentB");
        supportingEvidence = List.copyOf(Objects.requireNonNull(supportingEvidence, "supportingEvidence"));
    }
}
