package spl.requiresanalysis;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record BlockSummary(
        String blockId,
        String groupId,
        String signature,
        String file,
        String branchType,
        int startLine,
        int endLine,
        int entityCount,
        int incomingDependencyCount,
        int outgoingDependencyCount,
        int highRelevanceDependencyCount,
        int mediumRelevanceDependencyCount,
        int lowRelevanceDependencyCount,
        Map<EntityRole, Long> dominantEntityRoles,
        List<String> observedProductSet
) {
    public BlockSummary {
        blockId = Objects.requireNonNull(blockId, "blockId");
        groupId = Objects.requireNonNull(groupId, "groupId");
        signature = signature == null ? "" : signature;
        file = Objects.requireNonNull(file, "file");
        branchType = Objects.requireNonNull(branchType, "branchType");
        dominantEntityRoles = Map.copyOf(Objects.requireNonNull(dominantEntityRoles, "dominantEntityRoles"));
        observedProductSet = List.copyOf(Objects.requireNonNull(observedProductSet, "observedProductSet"));
    }
}
