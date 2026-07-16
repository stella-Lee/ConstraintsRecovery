package spl.feature;

import spl.entity.JavaEntity;

import java.util.Objects;

public record EntityImportance(
        JavaEntity entity,
        int rank,
        int degree,
        int incomingDegree,
        int outgoingDegree,
        int importanceScore
) {
    public EntityImportance {
        entity = Objects.requireNonNull(entity, "entity");
    }
}
