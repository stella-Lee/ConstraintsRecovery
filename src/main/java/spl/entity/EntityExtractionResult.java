package spl.entity;

import java.util.List;
import java.util.Objects;

public record EntityExtractionResult(
        List<JavaEntity> entities,
        List<JavaRelation> relations,
        List<ParseFailure> parseFailures,
        int parsedAssetFileCount,
        int projectionAttemptCount,
        int successfulProjectionCount
) {
    public EntityExtractionResult {
        entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        relations = List.copyOf(Objects.requireNonNull(relations, "relations"));
        parseFailures = List.copyOf(Objects.requireNonNull(parseFailures, "parseFailures"));
    }
}
