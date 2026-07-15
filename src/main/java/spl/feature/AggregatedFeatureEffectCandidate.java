package spl.feature;

import spl.ProductSignature;
import spl.dependency.DependencyEdge;
import spl.entity.JavaEntity;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record AggregatedFeatureEffectCandidate(
        String candidateId,
        String signatureGroupId,
        ProductSignature productSignature,
        CommonalityClassification commonalityClassification,
        List<String> structuralComponentIds,
        List<JavaEntity> memberEntities,
        Set<String> memberBlockIds,
        List<Path> involvedFiles,
        List<DependencyEdge> internalEdges,
        List<DependencyEdge> incomingEdges,
        List<DependencyEdge> outgoingEdges,
        List<String> representativeTokens,
        List<ComponentMergeDecision> aggregationDecisions,
        String suggestedLabel,
        EvidenceConfidence labelConfidence,
        CandidateConfidence candidateConfidence,
        List<String> unresolvedConcerns
) {
    public AggregatedFeatureEffectCandidate {
        candidateId = Objects.requireNonNull(candidateId, "candidateId");
        signatureGroupId = Objects.requireNonNull(signatureGroupId, "signatureGroupId");
        productSignature = Objects.requireNonNull(productSignature, "productSignature");
        commonalityClassification = Objects.requireNonNull(commonalityClassification, "commonalityClassification");
        structuralComponentIds = List.copyOf(Objects.requireNonNull(structuralComponentIds, "structuralComponentIds"));
        memberEntities = List.copyOf(Objects.requireNonNull(memberEntities, "memberEntities"));
        memberBlockIds = Set.copyOf(Objects.requireNonNull(memberBlockIds, "memberBlockIds"));
        involvedFiles = List.copyOf(Objects.requireNonNull(involvedFiles, "involvedFiles"));
        internalEdges = List.copyOf(Objects.requireNonNull(internalEdges, "internalEdges"));
        incomingEdges = List.copyOf(Objects.requireNonNull(incomingEdges, "incomingEdges"));
        outgoingEdges = List.copyOf(Objects.requireNonNull(outgoingEdges, "outgoingEdges"));
        representativeTokens = List.copyOf(Objects.requireNonNull(representativeTokens, "representativeTokens"));
        aggregationDecisions = List.copyOf(Objects.requireNonNull(aggregationDecisions, "aggregationDecisions"));
        suggestedLabel = Objects.requireNonNull(suggestedLabel, "suggestedLabel");
        labelConfidence = Objects.requireNonNull(labelConfidence, "labelConfidence");
        candidateConfidence = Objects.requireNonNull(candidateConfidence, "candidateConfidence");
        unresolvedConcerns = List.copyOf(Objects.requireNonNull(unresolvedConcerns, "unresolvedConcerns"));
    }
}
