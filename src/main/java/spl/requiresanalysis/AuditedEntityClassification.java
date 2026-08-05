package spl.requiresanalysis;

import spl.entity.JavaEntityType;

import java.util.Objects;

public record AuditedEntityClassification(
        String entityId,
        String qualifiedName,
        JavaEntityType entityKind,
        String declaringType,
        String packageName,
        String returnOrFieldType,
        SemanticRole semanticRole,
        ArchitecturalRole architecturalRole,
        EvidenceLevel domainRelevance,
        EvidenceLevel variabilityRelevance,
        ClassificationConfidence classificationConfidence,
        String nameEvidence,
        String declaringTypeEvidence,
        String typeSignatureEvidence,
        String packageEvidence,
        String usageContextEvidence,
        String dependencyContextEvidence,
        String positiveEvidence,
        String negativeEvidence,
        EntityRole previousRole,
        boolean roleChanged,
        String changeReason
) {
    public AuditedEntityClassification {
        entityId = Objects.requireNonNull(entityId, "entityId");
        qualifiedName = qualifiedName == null ? "" : qualifiedName;
        entityKind = Objects.requireNonNull(entityKind, "entityKind");
        declaringType = declaringType == null ? "" : declaringType;
        packageName = packageName == null ? "" : packageName;
        returnOrFieldType = returnOrFieldType == null ? "" : returnOrFieldType;
        semanticRole = Objects.requireNonNull(semanticRole, "semanticRole");
        architecturalRole = Objects.requireNonNull(architecturalRole, "architecturalRole");
        domainRelevance = Objects.requireNonNull(domainRelevance, "domainRelevance");
        variabilityRelevance = Objects.requireNonNull(variabilityRelevance, "variabilityRelevance");
        classificationConfidence = Objects.requireNonNull(classificationConfidence, "classificationConfidence");
        nameEvidence = nameEvidence == null ? "" : nameEvidence;
        declaringTypeEvidence = declaringTypeEvidence == null ? "" : declaringTypeEvidence;
        typeSignatureEvidence = typeSignatureEvidence == null ? "" : typeSignatureEvidence;
        packageEvidence = packageEvidence == null ? "" : packageEvidence;
        usageContextEvidence = usageContextEvidence == null ? "" : usageContextEvidence;
        dependencyContextEvidence = dependencyContextEvidence == null ? "" : dependencyContextEvidence;
        positiveEvidence = positiveEvidence == null ? "" : positiveEvidence;
        negativeEvidence = negativeEvidence == null ? "" : negativeEvidence;
        previousRole = Objects.requireNonNull(previousRole, "previousRole");
        changeReason = changeReason == null ? "" : changeReason;
    }
}
