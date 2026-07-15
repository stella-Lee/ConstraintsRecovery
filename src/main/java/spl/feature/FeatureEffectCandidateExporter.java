package spl.feature;

import spl.ConditionalBlock;
import spl.dependency.DependencyEdge;
import spl.entity.JavaEntity;
import spl.entity.JavaRelationType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public final class FeatureEffectCandidateExporter {
    public void export(FeatureEffectCandidateResult result, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        exportCandidates(result, outputDirectory.resolve("feature-effect-candidates.csv"));
        exportMembers(result, outputDirectory.resolve("feature-effect-members.csv"));
        exportDependencies(result, outputDirectory.resolve("candidate-dependencies.csv"));
        exportUnassignedBlocks(result, outputDirectory.resolve("unassigned-blocks.csv"));
    }

    private static void exportCandidates(FeatureEffectCandidateResult result, Path outputFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("candidate_id,group_id,signature,classification_status,entity_count,block_count,file_count,")
                .append("internal_edge_count,incoming_edge_count,outgoing_edge_count,")
                .append("cross_file_internal_edge_count,observed_products\n");
        for (FeatureEffectCandidate candidate : result.candidates()) {
            builder.append(csv(candidate.candidateId())).append(',')
                    .append(csv(candidate.signatureGroupId())).append(',')
                    .append(csv(candidate.productSignature().toString())).append(',')
                    .append(csv(candidate.classificationStatus().name())).append(',')
                    .append(candidate.memberEntities().size()).append(',')
                    .append(candidate.memberBlocks().size()).append(',')
                    .append(candidate.involvedAssetFiles().size()).append(',')
                    .append(candidate.internalEdges().size()).append(',')
                    .append(candidate.incomingEdges().size()).append(',')
                    .append(candidate.outgoingEdges().size()).append(',')
                    .append(candidate.crossFileInternalEdgeCount()).append(',')
                    .append(csv(String.join("|", candidate.observedProducts()))).append('\n');
        }
        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private static void exportMembers(FeatureEffectCandidateResult result, Path outputFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("candidate_id,member_type,member_id,entity_type,qualified_name,file,start_line,end_line,block_id,group_id,signature\n");
        for (FeatureEffectCandidate candidate : result.candidates()) {
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
            for (ConditionalBlock block : candidate.memberBlocks()) {
                builder.append(csv(candidate.candidateId())).append(',')
                        .append(csv("BLOCK")).append(',')
                        .append(csv(blockId(block))).append(',')
                        .append(csv("")).append(',')
                        .append(csv("")).append(',')
                        .append(csv(block.filePath().toString())).append(',')
                        .append(block.startLine()).append(',')
                        .append(block.endLine()).append(',')
                        .append(csv(blockId(block))).append(',')
                        .append(csv(candidate.signatureGroupId())).append(',')
                        .append(csv(candidate.productSignature().toString())).append('\n');
            }
            for (DependencyEdge edge : candidate.internalEdges()) {
                builder.append(csv(candidate.candidateId())).append(',')
                        .append(csv("INTERNAL_DEPENDENCY")).append(',')
                        .append(csv(edge.edgeId())).append(',')
                        .append(csv(edge.relationType().name())).append(',')
                        .append(csv(edge.sourceEntityId() + "->" + edge.targetEntityId())).append(',')
                        .append(csv(edge.sourceFile().toString())).append(',')
                        .append(edge.sourceLine()).append(',')
                        .append(edge.sourceLine()).append(',')
                        .append(csv(edge.sourceBlockId())).append(',')
                        .append(csv(edge.sourceGroupId())).append(',')
                        .append(csv(edge.sourceSignature().toString())).append('\n');
            }
        }
        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private static void exportDependencies(FeatureEffectCandidateResult result, Path outputFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("source_candidate_id,target_candidate_id,dependency_scope,relation_type,edge_count,")
                .append("cross_file_edge_count,source_group_id,target_group_id,source_signature,target_signature\n");
        for (CandidateDependency dependency : result.candidateDependencies()) {
            builder.append(csv(dependency.sourceCandidateId())).append(',')
                    .append(csv(dependency.targetCandidateId())).append(',')
                    .append(csv(dependency.dependencyScope().name())).append(',')
                    .append(csv(dependency.relationType().name())).append(',')
                    .append(dependency.edgeCount()).append(',')
                    .append(dependency.crossFileEdgeCount()).append(',')
                    .append(csv(dependency.sourceGroupId())).append(',')
                    .append(csv(dependency.targetGroupId())).append(',')
                    .append(csv(dependency.sourceSignature().toString())).append(',')
                    .append(csv(dependency.targetSignature().toString())).append('\n');
        }
        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private static void exportUnassignedBlocks(FeatureEffectCandidateResult result, Path outputFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("block_id,group_id,signature,file,branch_type,start_line,end_line,reason\n");
        for (UnassignedBlock block : result.unassignedBlocks()) {
            builder.append(csv(block.blockId())).append(',')
                    .append(csv(block.groupId())).append(',')
                    .append(csv(block.signature().toString())).append(',')
                    .append(csv(block.file().toString())).append(',')
                    .append(csv(block.branchType().name())).append(',')
                    .append(block.startLine()).append(',')
                    .append(block.endLine()).append(',')
                    .append(csv(block.reason())).append('\n');
        }
        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
    }

    public static Map<JavaRelationType, Long> internalEdgeCounts(FeatureEffectCandidate candidate) {
        Map<JavaRelationType, Long> counts = new TreeMap<>((left, right) -> left.name().compareTo(right.name()));
        for (DependencyEdge edge : candidate.internalEdges()) {
            counts.merge(edge.relationType(), 1L, Long::sum);
        }
        return counts;
    }

    private static String blockId(ConditionalBlock block) {
        return block.filePath() + ":" + block.directiveType().name() + ":" + block.startLine() + "-" + block.endLine();
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
