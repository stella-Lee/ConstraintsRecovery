package spl.requiresanalysis;

import spl.ConditionalBlock;
import spl.entity.JavaEntity;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RequiresEvidenceResult(
        List<ConditionalBlock> blocks,
        List<JavaEntity> entities,
        List<DependencyEvidence> dependencies,
        List<EntityClassification> classifications,
        List<BlockSummary> blockSummaries,
        Map<String, String> blockToGroupId
) {
    public RequiresEvidenceResult {
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
        entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        classifications = List.copyOf(Objects.requireNonNull(classifications, "classifications"));
        blockSummaries = List.copyOf(Objects.requireNonNull(blockSummaries, "blockSummaries"));
        blockToGroupId = Map.copyOf(Objects.requireNonNull(blockToGroupId, "blockToGroupId"));
    }
}
