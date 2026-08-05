package spl.requiresanalysis;

import java.util.Objects;

public record RequiresCandidateEvidenceRow(
        String evidenceId,
        String sourceFile,
        int sourceLine,
        String sourceEntity,
        String sourceEntityType,
        String sourceBlockId,
        String sourceAssetId,
        boolean sourceIsOutermost,
        String targetFile,
        int targetLine,
        String targetEntity,
        String targetEntityType,
        String targetBlockId,
        String targetAssetId,
        boolean targetIsOutermost,
        String dependencyKind,
        String codeSnippet,
        String sourceProducts,
        String targetProducts
) {
    public RequiresCandidateEvidenceRow {
        evidenceId = Objects.requireNonNull(evidenceId, "evidenceId");
        sourceFile = sourceFile == null ? "" : sourceFile;
        sourceEntity = sourceEntity == null ? "" : sourceEntity;
        sourceEntityType = sourceEntityType == null ? "" : sourceEntityType;
        sourceBlockId = sourceBlockId == null ? "" : sourceBlockId;
        sourceAssetId = sourceAssetId == null ? "" : sourceAssetId;
        targetFile = targetFile == null ? "" : targetFile;
        targetEntity = targetEntity == null ? "" : targetEntity;
        targetEntityType = targetEntityType == null ? "" : targetEntityType;
        targetBlockId = targetBlockId == null ? "" : targetBlockId;
        targetAssetId = targetAssetId == null ? "" : targetAssetId;
        dependencyKind = dependencyKind == null ? "" : dependencyKind;
        codeSnippet = codeSnippet == null ? "" : codeSnippet;
        sourceProducts = sourceProducts == null ? "" : sourceProducts;
        targetProducts = targetProducts == null ? "" : targetProducts;
    }
}
