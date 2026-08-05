package spl.requiresanalysis;

import spl.entity.JavaRelationType;
import spl.entity.ResolutionStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record DependencyEvidence(
        String relationId,
        String sourceBlockId,
        String targetBlockId,
        String sourceEntityId,
        String targetEntityId,
        String targetText,
        JavaRelationType relationType,
        Path sourceFile,
        int sourceLine,
        String sourceGroupId,
        String targetGroupId,
        String sourceSignature,
        String targetSignature,
        EntityRole sourceEntityRole,
        EntityRole targetEntityRole,
        double dependencyStrength,
        double domainRelevance,
        RequiresRelevance requiresRelevance,
        DependencyExclusionReason exclusionReason,
        ResolutionStatus resolutionStatus,
        List<String> observedProducts
) {
    public DependencyEvidence {
        relationId = Objects.requireNonNull(relationId, "relationId");
        sourceBlockId = sourceBlockId == null ? "" : sourceBlockId;
        targetBlockId = targetBlockId == null ? "" : targetBlockId;
        sourceEntityId = sourceEntityId == null ? "" : sourceEntityId;
        targetEntityId = targetEntityId == null ? "" : targetEntityId;
        targetText = targetText == null ? "" : targetText;
        relationType = Objects.requireNonNull(relationType, "relationType");
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        sourceGroupId = sourceGroupId == null ? "" : sourceGroupId;
        targetGroupId = targetGroupId == null ? "" : targetGroupId;
        sourceSignature = sourceSignature == null ? "" : sourceSignature;
        targetSignature = targetSignature == null ? "" : targetSignature;
        sourceEntityRole = Objects.requireNonNull(sourceEntityRole, "sourceEntityRole");
        targetEntityRole = Objects.requireNonNull(targetEntityRole, "targetEntityRole");
        requiresRelevance = Objects.requireNonNull(requiresRelevance, "requiresRelevance");
        exclusionReason = Objects.requireNonNull(exclusionReason, "exclusionReason");
        resolutionStatus = Objects.requireNonNull(resolutionStatus, "resolutionStatus");
        observedProducts = List.copyOf(Objects.requireNonNull(observedProducts, "observedProducts"));
    }
}
