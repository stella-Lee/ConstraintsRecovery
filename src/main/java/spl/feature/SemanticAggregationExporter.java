package spl.feature;

import spl.entity.JavaEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SemanticAggregationExporter {
    public void export(SemanticAggregationResult result, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        exportCandidates(result, outputDirectory.resolve("aggregated-feature-effect-candidates.csv"));
        exportMembers(result, outputDirectory.resolve("aggregated-feature-effect-members.csv"));
        exportSimilarities(result, outputDirectory.resolve("component-similarities.csv"));
        exportMergeDecisions(result, outputDirectory.resolve("component-merge-decisions.csv"));
        exportLabels(result, outputDirectory.resolve("candidate-labels.csv"));
        exportUnassignedComponents(result, outputDirectory.resolve("unassigned-components.csv"));
    }

    private static void exportCandidates(SemanticAggregationResult result, Path outputFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("candidate_id,group_id,signature,commonality_classification,suggested_label,")
                .append("label_confidence,candidate_confidence,component_count,entity_count,block_count,")
                .append("file_count,internal_edge_count,incoming_edge_count,outgoing_edge_count,")
                .append("representative_tokens,unresolved_concerns\n");
        for (AggregatedFeatureEffectCandidate candidate : result.candidates()) {
            builder.append(csv(candidate.candidateId())).append(',')
                    .append(csv(candidate.signatureGroupId())).append(',')
                    .append(csv(candidate.productSignature().toString())).append(',')
                    .append(csv(candidate.commonalityClassification().name())).append(',')
                    .append(csv(candidate.suggestedLabel())).append(',')
                    .append(csv(candidate.labelConfidence().name())).append(',')
                    .append(csv(candidate.candidateConfidence().name())).append(',')
                    .append(candidate.structuralComponentIds().size()).append(',')
                    .append(candidate.memberEntities().size()).append(',')
                    .append(candidate.memberBlockIds().size()).append(',')
                    .append(candidate.involvedFiles().size()).append(',')
                    .append(candidate.internalEdges().size()).append(',')
                    .append(candidate.incomingEdges().size()).append(',')
                    .append(candidate.outgoingEdges().size()).append(',')
                    .append(csv(String.join("|", candidate.representativeTokens()))).append(',')
                    .append(csv(String.join("|", candidate.unresolvedConcerns()))).append('\n');
        }
        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private static void exportMembers(SemanticAggregationResult result, Path outputFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("candidate_id,member_type,member_id,entity_type,qualified_name,file,start_line,end_line,block_id,group_id,signature\n");
        for (AggregatedFeatureEffectCandidate candidate : result.candidates()) {
            for (String componentId : candidate.structuralComponentIds()) {
                builder.append(csv(candidate.candidateId())).append(',')
                        .append(csv("STRUCTURAL_COMPONENT")).append(',')
                        .append(csv(componentId)).append(",,,,,,")
                        .append(csv("")).append(',')
                        .append(csv(candidate.signatureGroupId())).append(',')
                        .append(csv(candidate.productSignature().toString())).append('\n');
            }
            for (JavaEntity entity : candidate.memberEntities()) {
                builder.append(csv(candidate.candidateId())).append(',')
                        .append(csv("ENTITY")).append(',')
                        .append(csv(entity.entityId())).append(',')
                        .append(csv(entity.entityType().name())).append(',')
                        .append(csv(entity.qualifiedName())).append(',')
                        .append(csv(entity.sourceFile().toString())).append(',')
                        .append(entity.startLine()).append(',')
                        .append(entity.endLine()).append(',')
                        .append(csv(entity.containingBlockId())).append(',')
                        .append(csv(entity.signatureGroupId())).append(',')
                        .append(csv(entity.effectiveProductSignature().toString())).append('\n');
            }
            for (String blockId : candidate.memberBlockIds()) {
                builder.append(csv(candidate.candidateId())).append(',')
                        .append(csv("BLOCK")).append(',')
                        .append(csv(blockId)).append(",,,,,,")
                        .append(csv(blockId)).append(',')
                        .append(csv(candidate.signatureGroupId())).append(',')
                        .append(csv(candidate.productSignature().toString())).append('\n');
            }
        }
        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private static void exportSimilarities(SemanticAggregationResult result, Path outputFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("group_id,component_a,component_b,lexical_similarity,context_similarity,")
                .append("neighborhood_similarity,direct_dependency,shared_block_evidence,combined_score,")
                .append("threshold,supporting_evidence_count\n");
        for (ComponentSimilarity similarity : result.similarities()) {
            builder.append(csv(similarity.groupId())).append(',')
                    .append(csv(similarity.componentA())).append(',')
                    .append(csv(similarity.componentB())).append(',')
                    .append(similarity.lexicalSimilarity()).append(',')
                    .append(similarity.contextSimilarity()).append(',')
                    .append(similarity.neighborhoodSimilarity()).append(',')
                    .append(similarity.directDependency()).append(',')
                    .append(similarity.sharedBlockEvidence()).append(',')
                    .append(similarity.combinedScore()).append(',')
                    .append(similarity.threshold()).append(',')
                    .append(similarity.supportingEvidenceCount()).append('\n');
        }
        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private static void exportMergeDecisions(SemanticAggregationResult result, Path outputFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("group_id,component_a,component_b,decision,combined_score,supporting_evidence,reason\n");
        for (ComponentMergeDecision decision : result.mergeDecisions()) {
            builder.append(csv(decision.groupId())).append(',')
                    .append(csv(decision.componentA())).append(',')
                    .append(csv(decision.componentB())).append(',')
                    .append(csv(decision.decision().name())).append(',')
                    .append(decision.combinedScore()).append(',')
                    .append(csv(String.join("|", decision.supportingEvidence()))).append(',')
                    .append(csv(decision.reason())).append('\n');
        }
        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private static void exportLabels(SemanticAggregationResult result, Path outputFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("candidate_id,suggested_label,label_confidence,representative_tokens,representative_entities,representative_strings,explanation\n");
        for (AggregatedFeatureEffectCandidate candidate : result.candidates()) {
            String entities = candidate.memberEntities().stream()
                    .limit(5)
                    .map(entity -> entity.qualifiedName() == null ? entity.simpleName() : entity.qualifiedName())
                    .reduce((left, right) -> left + "|" + right)
                    .orElse("");
            builder.append(csv(candidate.candidateId())).append(',')
                    .append(csv(candidate.suggestedLabel())).append(',')
                    .append(csv(candidate.labelConfidence().name())).append(',')
                    .append(csv(String.join("|", candidate.representativeTokens()))).append(',')
                    .append(csv(entities)).append(',')
                    .append(csv("")).append(',')
                    .append(csv("label derived from representative code identifier tokens")).append('\n');
        }
        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private static void exportUnassignedComponents(SemanticAggregationResult result, Path outputFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("component_id,group_id,signature,component_type,entity_count,block_count,reason\n");
        for (FeatureEffectCandidate component : result.unassignedComponents()) {
            builder.append(csv(component.candidateId())).append(',')
                    .append(csv(component.signatureGroupId())).append(',')
                    .append(csv(component.productSignature().toString())).append(',')
                    .append(csv(component.classificationStatus().name())).append(',')
                    .append(component.memberEntities().size()).append(',')
                    .append(component.memberBlocks().size()).append(',')
                    .append(csv("insufficient semantic aggregation evidence")).append('\n');
        }
        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
