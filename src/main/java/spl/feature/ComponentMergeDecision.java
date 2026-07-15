package spl.feature;

import java.util.List;
import java.util.Objects;

public record ComponentMergeDecision(
        String groupId,
        String componentA,
        String componentB,
        MergeDecision decision,
        double combinedScore,
        List<String> supportingEvidence,
        String reason
) {
    public ComponentMergeDecision {
        groupId = Objects.requireNonNull(groupId, "groupId");
        componentA = Objects.requireNonNull(componentA, "componentA");
        componentB = Objects.requireNonNull(componentB, "componentB");
        decision = Objects.requireNonNull(decision, "decision");
        supportingEvidence = List.copyOf(Objects.requireNonNull(supportingEvidence, "supportingEvidence"));
        reason = Objects.requireNonNull(reason, "reason");
    }
}
