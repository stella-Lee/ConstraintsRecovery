package spl.requiresanalysis;

import spl.ConditionalBlock;
import spl.entity.JavaEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class RequiresEvidenceExporter {
    public void export(RequiresEvidenceResult result, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        exportBlocks(result, outputDirectory.resolve("blocks.csv"));
        exportBlockEntities(result, outputDirectory.resolve("block-entities.csv"));
        exportBlockDependencies(result, outputDirectory.resolve("block-dependencies.csv"));
        exportClassifications(result, outputDirectory.resolve("entity-classification.csv"));
        exportBlockSummaries(result, outputDirectory.resolve("block-summary.csv"));
    }

    private void exportBlocks(RequiresEvidenceResult result, Path outputFile) throws IOException {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("block_id,group_id,signature,file,branch_type,start_line,end_line,nesting_depth");
        for (ConditionalBlock block : result.blocks()) {
            String blockId = RequiresEvidenceBuilder.blockId(block);
            lines.add(csv(
                    blockId,
                    result.blockToGroupId().getOrDefault(blockId, ""),
                    block.signature().toString(),
                    block.filePath().toString(),
                    block.directiveType().name(),
                    block.startLine(),
                    block.endLine(),
                    block.nestingDepth()
            ));
        }
        Files.write(outputFile, lines, StandardCharsets.UTF_8);
    }

    private void exportBlockEntities(RequiresEvidenceResult result, Path outputFile) throws IOException {
        Map<String, EntityClassification> classifications = result.classifications().stream()
                .collect(Collectors.toMap(EntityClassification::entityId, classification -> classification));
        List<String> lines = new java.util.ArrayList<>();
        lines.add("block_id,group_id,signature,entity_id,entity_kind,qualified_name,entity_role,domain_score,variability_score,file,start_line,end_line,observed_product_set");
        result.entities().stream()
                .sorted(Comparator.comparing((JavaEntity entity) -> entity.containingBlockId())
                        .thenComparingInt(JavaEntity::startLine)
                        .thenComparing(JavaEntity::entityId))
                .forEach(entity -> {
                    EntityClassification classification = classifications.get(entity.entityId());
                    lines.add(csv(
                            entity.containingBlockId(),
                            entity.signatureGroupId(),
                            entity.effectiveProductSignature().toString(),
                            entity.entityId(),
                            entity.entityType().name(),
                            classification == null ? displayName(entity) : classification.qualifiedName(),
                            classification == null ? EntityRole.UNKNOWN : classification.entityRole(),
                            classification == null ? 0.0 : classification.domainScore(),
                            classification == null ? 0.0 : classification.variabilityScore(),
                            entity.sourceFile().toString(),
                            entity.startLine(),
                            entity.endLine(),
                            String.join("|", entity.observedProducts())
                    ));
                });
        Files.write(outputFile, lines, StandardCharsets.UTF_8);
    }

    private void exportBlockDependencies(RequiresEvidenceResult result, Path outputFile) throws IOException {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("relation_id,source_block_id,target_block_id,source_entity_id,target_entity_id,target_text,relation_type,source_file,source_line,source_group_id,target_group_id,source_signature,target_signature,source_entity_role,target_entity_role,dependency_strength,domain_relevance,requires_relevance,exclusion_reason,resolution_status,observed_product_set");
        for (DependencyEvidence dependency : result.dependencies()) {
            lines.add(csv(
                    dependency.relationId(),
                    dependency.sourceBlockId(),
                    dependency.targetBlockId(),
                    dependency.sourceEntityId(),
                    dependency.targetEntityId(),
                    dependency.targetText(),
                    dependency.relationType().name(),
                    dependency.sourceFile().toString(),
                    dependency.sourceLine(),
                    dependency.sourceGroupId(),
                    dependency.targetGroupId(),
                    dependency.sourceSignature(),
                    dependency.targetSignature(),
                    dependency.sourceEntityRole().name(),
                    dependency.targetEntityRole().name(),
                    format(dependency.dependencyStrength()),
                    format(dependency.domainRelevance()),
                    dependency.requiresRelevance().name(),
                    dependency.exclusionReason().name(),
                    dependency.resolutionStatus().name(),
                    String.join("|", dependency.observedProducts())
            ));
        }
        Files.write(outputFile, lines, StandardCharsets.UTF_8);
    }

    private void exportClassifications(RequiresEvidenceResult result, Path outputFile) throws IOException {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("entity_id,qualified_name,entity_kind,entity_role,domain_score,variability_score,package_name,observed_product_set,classification_evidence");
        result.classifications().stream()
                .sorted(Comparator.comparing(EntityClassification::entityId))
                .forEach(classification -> lines.add(csv(
                        classification.entityId(),
                        classification.qualifiedName(),
                        classification.entityKind().name(),
                        classification.entityRole().name(),
                        format(classification.domainScore()),
                        format(classification.variabilityScore()),
                        classification.packageName(),
                        String.join("|", classification.observedProductSet()),
                        classification.classificationEvidence()
                )));
        Files.write(outputFile, lines, StandardCharsets.UTF_8);
    }

    private void exportBlockSummaries(RequiresEvidenceResult result, Path outputFile) throws IOException {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("block_id,group_id,signature,file,branch_type,start_line,end_line,entity_count,incoming_dependency_count,outgoing_dependency_count,high_relevance_dependency_count,medium_relevance_dependency_count,low_relevance_dependency_count,dominant_entity_roles,observed_product_set");
        for (BlockSummary summary : result.blockSummaries()) {
            lines.add(csv(
                    summary.blockId(),
                    summary.groupId(),
                    summary.signature(),
                    summary.file(),
                    summary.branchType(),
                    summary.startLine(),
                    summary.endLine(),
                    summary.entityCount(),
                    summary.incomingDependencyCount(),
                    summary.outgoingDependencyCount(),
                    summary.highRelevanceDependencyCount(),
                    summary.mediumRelevanceDependencyCount(),
                    summary.lowRelevanceDependencyCount(),
                    summary.dominantEntityRoles().entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(entry -> entry.getKey() + ":" + entry.getValue())
                            .collect(Collectors.joining("|")),
                    String.join("|", summary.observedProductSet())
            ));
        }
        Files.write(outputFile, lines, StandardCharsets.UTF_8);
    }

    private String csv(Object... values) {
        return java.util.Arrays.stream(values)
                .map(value -> value == null ? "" : value.toString())
                .map(this::escape)
                .collect(Collectors.joining(","));
    }

    private String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private String displayName(JavaEntity entity) {
        return entity.qualifiedName() == null || entity.qualifiedName().isBlank()
                ? entity.simpleName()
                : entity.qualifiedName();
    }
}
