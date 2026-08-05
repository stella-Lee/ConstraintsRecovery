package spl.requiresanalysis;

import java.util.List;
import java.util.Objects;

public record AuditReportResult(
        List<AuditedEntityClassification> entityClassifications,
        List<AuditedBlockPair> blockPairs
) {
    public AuditReportResult {
        entityClassifications = List.copyOf(Objects.requireNonNull(entityClassifications, "entityClassifications"));
        blockPairs = List.copyOf(Objects.requireNonNull(blockPairs, "blockPairs"));
    }
}
