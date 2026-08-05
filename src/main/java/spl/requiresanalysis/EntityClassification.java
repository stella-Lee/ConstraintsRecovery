package spl.requiresanalysis;

import spl.entity.JavaEntityType;

import java.util.List;
import java.util.Objects;

public record EntityClassification(
        String entityId,
        String qualifiedName,
        JavaEntityType entityKind,
        EntityRole entityRole,
        double domainScore,
        double variabilityScore,
        String packageName,
        List<String> observedProductSet,
        String classificationEvidence
) {
    public EntityClassification {
        entityId = Objects.requireNonNull(entityId, "entityId");
        entityKind = Objects.requireNonNull(entityKind, "entityKind");
        entityRole = Objects.requireNonNull(entityRole, "entityRole");
        packageName = packageName == null ? "" : packageName;
        observedProductSet = List.copyOf(Objects.requireNonNull(observedProductSet, "observedProductSet"));
        classificationEvidence = classificationEvidence == null ? "" : classificationEvidence;
    }
}
