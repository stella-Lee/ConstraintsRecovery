package spl.feature;

import spl.ConditionalBlock;
import spl.ProductSignature;
import spl.dependency.DependencyEdge;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelationType;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record FeatureEffectCandidate(
        String candidateId,
        String signatureGroupId,
        ProductSignature productSignature,
        List<JavaEntity> memberEntities,
        List<ConditionalBlock> memberBlocks,
        List<DependencyEdge> internalEdges,
        List<DependencyEdge> incomingEdges,
        List<DependencyEdge> outgoingEdges,
        List<Path> involvedAssetFiles,
        List<String> observedProducts,
        CandidateClassificationStatus classificationStatus,
        Map<JavaEntityType, Long> entityCountsByType,
        Map<JavaRelationType, Long> edgeCountsByType,
        int weaklyConnectedComponentSize,
        int crossFileInternalEdgeCount,
        int isolatedEntityCount
) {
    public FeatureEffectCandidate {
        candidateId = Objects.requireNonNull(candidateId, "candidateId");
        signatureGroupId = Objects.requireNonNull(signatureGroupId, "signatureGroupId");
        productSignature = Objects.requireNonNull(productSignature, "productSignature");
        memberEntities = List.copyOf(Objects.requireNonNull(memberEntities, "memberEntities"));
        memberBlocks = List.copyOf(Objects.requireNonNull(memberBlocks, "memberBlocks"));
        internalEdges = List.copyOf(Objects.requireNonNull(internalEdges, "internalEdges"));
        incomingEdges = List.copyOf(Objects.requireNonNull(incomingEdges, "incomingEdges"));
        outgoingEdges = List.copyOf(Objects.requireNonNull(outgoingEdges, "outgoingEdges"));
        involvedAssetFiles = List.copyOf(Objects.requireNonNull(involvedAssetFiles, "involvedAssetFiles"));
        observedProducts = List.copyOf(Objects.requireNonNull(observedProducts, "observedProducts"));
        classificationStatus = Objects.requireNonNull(classificationStatus, "classificationStatus");
        entityCountsByType = Map.copyOf(Objects.requireNonNull(entityCountsByType, "entityCountsByType"));
        edgeCountsByType = Map.copyOf(Objects.requireNonNull(edgeCountsByType, "edgeCountsByType"));
    }
}
