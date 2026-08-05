package spl.requiresanalysis;

import java.util.List;
import java.util.Objects;

public record AuditedBlockPair(
        String sourceBlockId,
        String targetBlockId,
        String sourceEntity,
        String sourceEntityType,
        String targetEntity,
        String targetEntityType,
        String dependencyKind,
        String sourceAssetId,
        String targetAssetId,
        boolean sameAsset,
        boolean sourceIsOutermost,
        boolean targetIsOutermost,
        boolean crossAsset,
        boolean eligibleForRequires,
        boolean requiresManualInspection,
        String sourceProductSet,
        String targetProductSet,
        String signatureRelation,
        ProductSetRelation productSetRelation,
        int occurrenceCount,
        int distinctSourceLineCount,
        boolean crossFile,
        boolean crossClass,
        RequiresCandidateStatus candidateStatus,
        EvidenceLevel confidence,
        String primaryEvidenceId,
        List<RequiresCandidateEvidenceRow> evidenceRows,
        String dependencyKinds,
        RequiresCandidateCategory candidateCategory,
        int methodCallCount,
        int constructorCallCount,
        int fieldReferenceCount,
        int extendsCount,
        int implementsCount,
        int distinctSourceEntityCount,
        int distinctTargetEntityCount,
        int distinctSourceBlockCount,
        int distinctTargetBlockCount,
        int distinctFileCount,
        String referencedEntities,
        int mergedDependencyCount,
        EvidenceLevel structuralStrength,
        EvidenceLevel domainRelevance,
        EvidenceLevel productSetSupport,
        EvidenceLevel directionSupport,
        EvidenceLevel sameFeaturePossibility,
        EvidenceLevel sharedSupportPossibility,
        EvidenceLevel commonContextPossibility,
        EvidenceLevel resolutionConfidence,
        String interpretationCategories,
        RequiresRelevance requiresRelevance,
        String relevanceReasons,
        String cautionReasons,
        String exclusionReasons,
        String targetPresenceScope,
        int targetEntityCount,
        int directlyReferencedTargetEntityCount,
        double targetScopeCoarseness,
        String targetScopeQuality,
        String referencedTargetEntities,
        String referencedTargetMethods,
        String referencedTargetFields,
        String targetDeclarationRanges,
        String primaryTargetEntity,
        String primaryTargetOperation,
        String primaryTargetDeclaration,
        String primaryTargetPresenceScope,
        String primaryTargetFeatureSpecificity,
        String primarySelectionReason,
        String primaryEvidenceRelationId,
        String primaryEvidenceSourceEntity,
        String primaryEvidenceSourceEntityType,
        String primaryEvidenceTargetEntityType,
        String primaryEvidenceDependencyKind,
        String primaryEvidenceSourceFile,
        int primaryEvidenceSourceLine,
        String alternativeTargetEntities,
        String commonTargetEntities,
        String featureSpecificTargetEntities,
        String evidenceSnippets
) {
    public AuditedBlockPair {
        sourceBlockId = Objects.requireNonNull(sourceBlockId, "sourceBlockId");
        targetBlockId = Objects.requireNonNull(targetBlockId, "targetBlockId");
        sourceEntity = sourceEntity == null ? "" : sourceEntity;
        sourceEntityType = sourceEntityType == null ? "" : sourceEntityType;
        targetEntity = targetEntity == null ? "" : targetEntity;
        targetEntityType = targetEntityType == null ? "" : targetEntityType;
        dependencyKind = dependencyKind == null ? "" : dependencyKind;
        sourceAssetId = sourceAssetId == null ? "" : sourceAssetId;
        targetAssetId = targetAssetId == null ? "" : targetAssetId;
        sourceProductSet = sourceProductSet == null ? "" : sourceProductSet;
        targetProductSet = targetProductSet == null ? "" : targetProductSet;
        signatureRelation = signatureRelation == null ? "" : signatureRelation;
        productSetRelation = Objects.requireNonNull(productSetRelation, "productSetRelation");
        candidateStatus = Objects.requireNonNull(candidateStatus, "candidateStatus");
        confidence = Objects.requireNonNull(confidence, "confidence");
        primaryEvidenceId = primaryEvidenceId == null ? "" : primaryEvidenceId;
        evidenceRows = List.copyOf(Objects.requireNonNull(evidenceRows, "evidenceRows"));
        dependencyKinds = dependencyKinds == null ? "" : dependencyKinds;
        candidateCategory = Objects.requireNonNull(candidateCategory, "candidateCategory");
        referencedEntities = referencedEntities == null ? "" : referencedEntities;
        structuralStrength = Objects.requireNonNull(structuralStrength, "structuralStrength");
        domainRelevance = Objects.requireNonNull(domainRelevance, "domainRelevance");
        productSetSupport = Objects.requireNonNull(productSetSupport, "productSetSupport");
        directionSupport = Objects.requireNonNull(directionSupport, "directionSupport");
        sameFeaturePossibility = Objects.requireNonNull(sameFeaturePossibility, "sameFeaturePossibility");
        sharedSupportPossibility = Objects.requireNonNull(sharedSupportPossibility, "sharedSupportPossibility");
        commonContextPossibility = Objects.requireNonNull(commonContextPossibility, "commonContextPossibility");
        resolutionConfidence = Objects.requireNonNull(resolutionConfidence, "resolutionConfidence");
        interpretationCategories = interpretationCategories == null ? "" : interpretationCategories;
        requiresRelevance = Objects.requireNonNull(requiresRelevance, "requiresRelevance");
        relevanceReasons = relevanceReasons == null ? "" : relevanceReasons;
        cautionReasons = cautionReasons == null ? "" : cautionReasons;
        exclusionReasons = exclusionReasons == null ? "" : exclusionReasons;
        targetPresenceScope = targetPresenceScope == null ? "" : targetPresenceScope;
        targetScopeQuality = targetScopeQuality == null ? "" : targetScopeQuality;
        referencedTargetEntities = referencedTargetEntities == null ? "" : referencedTargetEntities;
        referencedTargetMethods = referencedTargetMethods == null ? "" : referencedTargetMethods;
        referencedTargetFields = referencedTargetFields == null ? "" : referencedTargetFields;
        targetDeclarationRanges = targetDeclarationRanges == null ? "" : targetDeclarationRanges;
        primaryTargetEntity = primaryTargetEntity == null ? "" : primaryTargetEntity;
        primaryTargetOperation = primaryTargetOperation == null ? "" : primaryTargetOperation;
        primaryTargetDeclaration = primaryTargetDeclaration == null ? "" : primaryTargetDeclaration;
        primaryTargetPresenceScope = primaryTargetPresenceScope == null ? "" : primaryTargetPresenceScope;
        primaryTargetFeatureSpecificity = primaryTargetFeatureSpecificity == null ? "" : primaryTargetFeatureSpecificity;
        primarySelectionReason = primarySelectionReason == null ? "" : primarySelectionReason;
        primaryEvidenceRelationId = primaryEvidenceRelationId == null ? "" : primaryEvidenceRelationId;
        primaryEvidenceSourceEntity = primaryEvidenceSourceEntity == null ? "" : primaryEvidenceSourceEntity;
        primaryEvidenceSourceEntityType = primaryEvidenceSourceEntityType == null ? "" : primaryEvidenceSourceEntityType;
        primaryEvidenceTargetEntityType = primaryEvidenceTargetEntityType == null ? "" : primaryEvidenceTargetEntityType;
        primaryEvidenceDependencyKind = primaryEvidenceDependencyKind == null ? "" : primaryEvidenceDependencyKind;
        primaryEvidenceSourceFile = primaryEvidenceSourceFile == null ? "" : primaryEvidenceSourceFile;
        alternativeTargetEntities = alternativeTargetEntities == null ? "" : alternativeTargetEntities;
        commonTargetEntities = commonTargetEntities == null ? "" : commonTargetEntities;
        featureSpecificTargetEntities = featureSpecificTargetEntities == null ? "" : featureSpecificTargetEntities;
        evidenceSnippets = evidenceSnippets == null ? "" : evidenceSnippets;
    }
}
