package spl.requiresanalysis;

import spl.dependency.DependencyEdge;
import spl.dependency.DependencyKindClassifier;
import spl.dependency.UnresolvedDependencyRelation;
import spl.entity.JavaEntity;
import spl.entity.JavaRelationType;
import spl.entity.ResolutionStatus;

import java.util.Locale;
import java.util.Map;

public final class DependencyEvidenceScorer {
    private final EvidenceScoringConfig config;

    public DependencyEvidenceScorer() {
        this(EvidenceScoringConfig.defaults());
    }

    public DependencyEvidenceScorer(EvidenceScoringConfig config) {
        this.config = config;
    }

    public DependencyEvidence score(DependencyEdge edge, Map<String, JavaEntity> entities,
                                    Map<String, EntityClassification> classifications) {
        JavaEntity target = entities.get(edge.targetEntityId());
        EntityClassification source = classifications.get(edge.sourceEntityId());
        EntityClassification targetClass = classifications.get(edge.targetEntityId());
        EntityRole sourceRole = role(source);
        EntityRole targetRole = role(targetClass);
        DependencyExclusionReason reason = exclusionReason(edge, target, sourceRole, targetRole);
        double strength = strength(edge.relationType());
        double relevance = domainRelevance(source, targetClass, reason);
        return new DependencyEvidence(
                edge.edgeId(),
                edge.sourceBlockId(),
                target == null ? "" : target.containingBlockId(),
                edge.sourceEntityId(),
                edge.targetEntityId(),
                "",
                edge.relationType(),
                edge.sourceFile(),
                edge.sourceLine(),
                edge.sourceGroupId(),
                edge.targetGroupId(),
                edge.sourceSignature().toString(),
                edge.targetSignature().toString(),
                sourceRole,
                targetRole,
                strength,
                relevance,
                requiresRelevance(edge.relationType(), strength, relevance, reason),
                reason,
                edge.resolutionStatus(),
                edge.observedProducts()
        );
    }

    public DependencyEvidence score(UnresolvedDependencyRelation relation,
                                    Map<String, EntityClassification> classifications) {
        EntityClassification source = classifications.get(relation.sourceEntityId());
        EntityRole sourceRole = role(source);
        EntityRole targetRole = relation.resolutionStatus() == ResolutionStatus.RESOLVED_EXTERNAL
                ? EntityRole.EXTERNAL
                : EntityRole.UNKNOWN;
        DependencyExclusionReason reason = unresolvedReason(relation);
        double strength = strength(relation.relationType());
        double relevance = domainRelevance(source, null, reason);
        return new DependencyEvidence(
                relation.relationId(),
                relation.sourceBlockId(),
                "",
                relation.sourceEntityId(),
                "",
                relation.targetText(),
                relation.relationType(),
                relation.sourceFile(),
                relation.sourceLine(),
                relation.sourceGroupId(),
                "",
                relation.sourceSignature().toString(),
                "",
                sourceRole,
                targetRole,
                strength,
                relevance,
                requiresRelevance(relation.relationType(), strength, relevance, reason),
                reason,
                relation.resolutionStatus(),
                relation.observedProducts()
        );
    }

    private DependencyExclusionReason exclusionReason(DependencyEdge edge, JavaEntity target,
                                                      EntityRole sourceRole, EntityRole targetRole) {
        if (target != null && edge.sourceBlockId().equals(target.containingBlockId())) {
            return DependencyExclusionReason.INTRA_BLOCK;
        }
        if (sourceRole == EntityRole.UTILITY || targetRole == EntityRole.UTILITY) {
            return DependencyExclusionReason.UTILITY_ONLY;
        }
        if (sourceRole == EntityRole.INFRASTRUCTURE && targetRole == EntityRole.INFRASTRUCTURE) {
            return DependencyExclusionReason.COMMON_INFRASTRUCTURE;
        }
        String joined = (edge.sourceEntityId() + " " + edge.targetEntityId()).toLowerCase(Locale.ROOT);
        if (containsAny(joined, config.loggingTokens())) {
            return DependencyExclusionReason.LOGGING_ONLY;
        }
        if (containsAny(joined, config.frameworkCallbackTokens())) {
            return DependencyExclusionReason.FRAMEWORK_CALLBACK;
        }
        return DependencyExclusionReason.NONE;
    }

    private DependencyExclusionReason unresolvedReason(UnresolvedDependencyRelation relation) {
        if (relation.resolutionStatus() == ResolutionStatus.RESOLVED_EXTERNAL) {
            String target = relation.targetText() == null ? "" : relation.targetText();
            return target.startsWith("java.") || target.startsWith("javax.")
                    ? DependencyExclusionReason.STANDARD_LIBRARY
                    : DependencyExclusionReason.EXTERNAL_LIBRARY;
        }
        if (relation.targetText() == null || relation.targetText().isBlank()
                || "missing source entity".equals(relation.failureReason())) {
            return DependencyExclusionReason.UNKNOWN_TARGET;
        }
        String text = relation.targetText().toLowerCase(Locale.ROOT);
        if (containsAny(text, config.loggingTokens())) {
            return DependencyExclusionReason.LOGGING_ONLY;
        }
        if (containsAny(text, config.frameworkCallbackTokens())) {
            return DependencyExclusionReason.FRAMEWORK_CALLBACK;
        }
        return DependencyExclusionReason.UNKNOWN_TARGET;
    }

    private double strength(JavaRelationType type) {
        return switch (type) {
            case EXTENDS, IMPLEMENTS -> 0.9;
            case METHOD_CALL, CONSTRUCTOR_CALL -> 0.75;
            case FIELD_REFERENCE -> 0.6;
            case TYPE_REFERENCE -> 0.45;
        };
    }

    private double domainRelevance(EntityClassification source, EntityClassification target,
                                   DependencyExclusionReason reason) {
        if (reason != DependencyExclusionReason.NONE) {
            return reason == DependencyExclusionReason.INTRA_BLOCK ? 0.2 : 0.1;
        }
        double sourceScore = source == null ? 0.0 : source.domainScore();
        double targetScore = target == null ? 0.0 : target.domainScore();
        return target == null ? sourceScore * 0.5 : (sourceScore + targetScore) / 2.0;
    }

    private RequiresRelevance requiresRelevance(JavaRelationType type, double strength, double relevance,
                                                DependencyExclusionReason reason) {
        if (!DependencyKindClassifier.isImplementationDependency(type)) {
            return RequiresRelevance.LOW;
        }
        if (reason != DependencyExclusionReason.NONE) {
            return RequiresRelevance.LOW;
        }
        double score = strength * relevance;
        if (score >= config.highDomainThreshold()) {
            return RequiresRelevance.HIGH;
        }
        if (score >= config.mediumDomainThreshold()) {
            return RequiresRelevance.MEDIUM;
        }
        return RequiresRelevance.LOW;
    }

    private EntityRole role(EntityClassification classification) {
        return classification == null ? EntityRole.UNKNOWN : classification.entityRole();
    }

    private boolean containsAny(String text, java.util.Set<String> tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
