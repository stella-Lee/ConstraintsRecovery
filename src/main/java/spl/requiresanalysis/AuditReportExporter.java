package spl.requiresanalysis;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class AuditReportExporter {
    public void export(AuditReportResult result, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        exportEntities(result, outputDirectory.resolve("audited-entity-classification.csv"));
        exportCandidateDetails(result, outputDirectory.resolve("requires_candidate_details.csv"));
        exportCompactCandidates(result, outputDirectory.resolve("requires_candidates.csv"));
        exportCandidateEvidence(result, outputDirectory.resolve("requires_candidate_evidence.csv"));
    }

    private void exportEntities(AuditReportResult result, Path outputFile) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("entity_id,qualified_name,entity_kind,declaring_type,package_name,return_or_field_type,semantic_role,architectural_role,domain_relevance,variability_relevance,classification_confidence,name_evidence,declaring_type_evidence,type_signature_evidence,package_evidence,usage_context_evidence,dependency_context_evidence,positive_evidence,negative_evidence,previous_role,role_changed,change_reason");
        for (AuditedEntityClassification classification : result.entityClassifications()) {
            lines.add(csv(
                    classification.entityId(),
                    classification.qualifiedName(),
                    classification.entityKind(),
                    classification.declaringType(),
                    classification.packageName(),
                    classification.returnOrFieldType(),
                    classification.semanticRole(),
                    classification.architecturalRole(),
                    classification.domainRelevance(),
                    classification.variabilityRelevance(),
                    classification.classificationConfidence(),
                    classification.nameEvidence(),
                    classification.declaringTypeEvidence(),
                    classification.typeSignatureEvidence(),
                    classification.packageEvidence(),
                    classification.usageContextEvidence(),
                    classification.dependencyContextEvidence(),
                    classification.positiveEvidence(),
                    classification.negativeEvidence(),
                    classification.previousRole(),
                    classification.roleChanged(),
                    classification.changeReason()
            ));
        }
        write(outputFile, lines);
    }

    private void exportCandidateDetails(AuditReportResult result, Path outputFile) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("candidate_id,source_block_id,target_block_id,source_product_set,target_product_set,product_set_relation,dependency_kinds,candidate_category,method_call_count,constructor_call_count,field_reference_count,extends_count,implements_count,distinct_source_entity_count,distinct_target_entity_count,distinct_source_block_count,distinct_target_block_count,distinct_file_count,referenced_entities,merged_dependency_count,structural_strength,domain_relevance,product_set_support,direction_support,same_feature_possibility,shared_support_possibility,common_context_possibility,resolution_confidence,interpretation_categories,requires_relevance,relevance_reasons,caution_reasons,exclusion_reasons,target_presence_scope,target_entity_count,referenced_target_entity_count,target_scope_coarseness,target_scope_quality,referenced_target_entities,referenced_target_methods,referenced_target_fields,target_declaration_ranges,primary_target_entity,primary_target_operation,primary_target_declaration,primary_target_presence_scope,primary_target_feature_specificity,primary_selection_reason,primary_evidence_relation_id,primary_evidence_source_entity,primary_evidence_source_entity_type,primary_evidence_target_entity_type,primary_evidence_dependency_kind,primary_evidence_source_file,primary_evidence_source_line,alternative_target_entities,common_target_entities,feature_specific_target_entities,evidence_snippets");
        for (int index = 0; index < result.blockPairs().size(); index++) {
            AuditedBlockPair pair = result.blockPairs().get(index);
            lines.add(csv(
                    candidateId(index),
                    pair.sourceBlockId(),
                    pair.targetBlockId(),
                    pair.sourceProductSet(),
                    pair.targetProductSet(),
                    pair.productSetRelation(),
                    pair.dependencyKinds(),
                    pair.candidateCategory(),
                    pair.methodCallCount(),
                    pair.constructorCallCount(),
                    pair.fieldReferenceCount(),
                    pair.extendsCount(),
                    pair.implementsCount(),
                    pair.distinctSourceEntityCount(),
                    pair.distinctTargetEntityCount(),
                    pair.distinctSourceBlockCount(),
                    pair.distinctTargetBlockCount(),
                    pair.distinctFileCount(),
                    pair.referencedEntities(),
                    pair.mergedDependencyCount(),
                    pair.structuralStrength(),
                    pair.domainRelevance(),
                    pair.productSetSupport(),
                    pair.directionSupport(),
                    pair.sameFeaturePossibility(),
                    pair.sharedSupportPossibility(),
                    pair.commonContextPossibility(),
                    pair.resolutionConfidence(),
                    pair.interpretationCategories(),
                    pair.requiresRelevance(),
                    pair.relevanceReasons(),
                    pair.cautionReasons(),
                    pair.exclusionReasons(),
                    pair.targetPresenceScope(),
                    pair.targetEntityCount(),
                    pair.directlyReferencedTargetEntityCount(),
                    String.format(java.util.Locale.ROOT, "%.3f", pair.targetScopeCoarseness()),
                    pair.targetScopeQuality(),
                    pair.referencedTargetEntities(),
                    pair.referencedTargetMethods(),
                    pair.referencedTargetFields(),
                    pair.targetDeclarationRanges(),
                    pair.primaryTargetEntity(),
                    pair.primaryTargetOperation(),
                    pair.primaryTargetDeclaration(),
                    pair.primaryTargetPresenceScope(),
                    pair.primaryTargetFeatureSpecificity(),
                    pair.primarySelectionReason(),
                    pair.primaryEvidenceRelationId(),
                    pair.primaryEvidenceSourceEntity(),
                    pair.primaryEvidenceSourceEntityType(),
                    pair.primaryEvidenceTargetEntityType(),
                    pair.primaryEvidenceDependencyKind(),
                    pair.primaryEvidenceSourceFile(),
                    pair.primaryEvidenceSourceLine(),
                    pair.alternativeTargetEntities(),
                    pair.commonTargetEntities(),
                    pair.featureSpecificTargetEntities(),
                    pair.evidenceSnippets()
            ));
        }
        write(outputFile, lines);
    }

    private void exportCompactCandidates(AuditReportResult result, Path outputFile) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("candidate_id,source_entity,source_entity_type,target_entity,target_entity_type,dependency_kind,source_block_id,target_block_id,source_asset_id,target_asset_id,same_asset,source_is_outermost,target_is_outermost,cross_asset,source_products,target_products,signature_relation,occurrence_count,distinct_source_line_count,cross_file,cross_class,eligible_for_requires,candidate_status,exclusion_reason,requires_manual_inspection,confidence,primary_evidence_id");
        List<AuditedBlockPair> primaryPairs = result.blockPairs();
        for (int index = 0; index < primaryPairs.size(); index++) {
            AuditedBlockPair pair = primaryPairs.get(index);
            validatePrimaryEvidence(pair);
            lines.add(csv(
                    candidateId(index),
                    valueOrUnresolved(pair.sourceEntity()),
                    pair.sourceEntityType(),
                    valueOrUnresolved(pair.targetEntity()),
                    pair.targetEntityType(),
                    pair.dependencyKind(),
                    pair.sourceBlockId(),
                    pair.targetBlockId(),
                    pair.sourceAssetId(),
                    pair.targetAssetId(),
                    pair.sameAsset(),
                    pair.sourceIsOutermost(),
                    pair.targetIsOutermost(),
                    pair.crossAsset(),
                    pair.sourceProductSet(),
                    pair.targetProductSet(),
                    pair.signatureRelation(),
                    pair.occurrenceCount(),
                    pair.distinctSourceLineCount(),
                    pair.crossFile(),
                    pair.crossClass(),
                    pair.eligibleForRequires(),
                    pair.candidateStatus(),
                    candidateExclusionReason(pair),
                    pair.requiresManualInspection(),
                    pair.confidence(),
                    pair.primaryEvidenceId()
            ));
        }
        write(outputFile, lines);
    }

    private void exportCandidateEvidence(AuditReportResult result, Path outputFile) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("evidence_id,candidate_id,source_file,source_line,source_entity,source_entity_type,source_block_id,source_asset_id,source_is_outermost,target_file,target_line,target_entity,target_entity_type,target_block_id,target_asset_id,target_is_outermost,dependency_kind,code_snippet,source_products,target_products");
        for (int index = 0; index < result.blockPairs().size(); index++) {
            String candidateId = candidateId(index);
            for (RequiresCandidateEvidenceRow evidence : result.blockPairs().get(index).evidenceRows()) {
                lines.add(csv(
                        evidence.evidenceId(),
                        candidateId,
                        evidence.sourceFile(),
                        evidence.sourceLine(),
                        evidence.sourceEntity(),
                        evidence.sourceEntityType(),
                        evidence.sourceBlockId(),
                        evidence.sourceAssetId(),
                        evidence.sourceIsOutermost(),
                        evidence.targetFile(),
                        evidence.targetLine(),
                        evidence.targetEntity(),
                        evidence.targetEntityType(),
                        evidence.targetBlockId(),
                        evidence.targetAssetId(),
                        evidence.targetIsOutermost(),
                        evidence.dependencyKind(),
                        evidence.codeSnippet(),
                        evidence.sourceProducts(),
                        evidence.targetProducts()
                ));
            }
        }
        write(outputFile, lines);
    }

    private String candidateId(int index) {
        return "RC-" + String.format(Locale.ROOT, "%04d", index + 1);
    }

    private String valueOrUnresolved(String value) {
        return value == null || value.isBlank() ? "UNRESOLVED" : value;
    }

    private String candidateExclusionReason(AuditedBlockPair pair) {
        return switch (pair.candidateStatus()) {
            case ELIGIBLE_REQUIRES_EVIDENCE -> "";
            case COMMON_TARGET_DEPENDENCY -> "TARGET_PRESENT_IN_ALL_PRODUCTS";
            case INTRA_ASSET_ROOT_DEPENDENCY -> "SAME_ASSET_ROOT_CONTEXT";
            case INTRA_ASSET_MANDATORY_TARGET -> "TARGET_IS_SAME_ASSET_OUTERMOST_BLOCK";
            case MANDATORY_SOURCE_TO_VARIABLE_TARGET -> "OUTERMOST_SOURCE_DEPENDS_ON_NESTED_TARGET";
            case SAME_SIGNATURE_DIRECTED_DEPENDENCY -> "DIRECTION_NOT_IDENTIFIABLE";
            case INTRA_ENTITY, SELF_DEPENDENCY -> "SELF_DEPENDENCY";
            case INTRA_CLASS -> "INTRA_CLASS";
            case INTRA_BLOCK -> "INTRA_BLOCK";
            case SIGNATURE_INCONSISTENT -> "SIGNATURE_INCONSISTENT";
            case UNRESOLVED_ENTITY -> "UNRESOLVED_ENTITY";
            case INVALID_DEPENDENCY_KIND -> "INVALID_DEPENDENCY_KIND";
            case UNRESOLVED_BLOCK_SCOPE -> "UNRESOLVED_BLOCK_SCOPE";
        };
    }

    private String evidenceLocation(AuditedBlockPair pair) {
        BlockLocation source = BlockLocation.fromSourceLocation(pair.primaryEvidenceSourceFile(),
                pair.primaryEvidenceSourceLine());
        if (source == null) {
            source = BlockLocation.fromBlockId(pair.sourceBlockId());
        }
        BlockLocation target = BlockLocation.fromDeclaration(pair.primaryTargetDeclaration());
        if (target == null) {
            target = BlockLocation.fromBlockId(pair.targetBlockId());
        }
        if (source == null && target == null) {
            return "";
        }
        if (source == null) {
            return target.format();
        }
        if (target == null) {
            return source.format();
        }
        if (source.fileName().equals(target.fileName())) {
            return source.format() + " -> " + target.lineRange();
        }
        return source.format() + " -> " + target.format();
    }

    private String judgmentReason(AuditedBlockPair pair, String relationType, String sourceFeature, String targetFeature) {
        List<String> reasons = new ArrayList<>();
        if (pair.primarySelectionReason().contains("DIRECTLY_REFERENCED_BY_SOURCE_BLOCK")
                && !pair.featureSpecificTargetEntities().isBlank()) {
            reasons.add("DIRECT_FEATURE_DEPENDENCY");
        }
        if (pair.primarySelectionReason().contains("COMPILE_TIME_NECESSITY")) {
            reasons.add("COMPILE_TIME_NECESSITY");
        }
        if (pair.productSetRelation().name().equals("SOURCE_SUBSET_OF_TARGET")) {
            reasons.add("SOURCE_SET_SUBSET");
        }
        if ("FEATURE_SPECIFIC".equals(pair.primaryTargetFeatureSpecificity())) {
            reasons.add("TARGET_FEATURE_RESOLVED");
        } else {
            reasons.add("TARGET_FEATURE_UNRESOLVED");
        }
        if ("COMMON".equals(pair.primaryTargetFeatureSpecificity())) {
            reasons.add("COMMON_TARGET_ONLY");
        }
        if ("UNRESOLVED".equals(sourceFeature)) {
            reasons.add("SOURCE_FEATURE_UNRESOLVED");
        }
        if ("UNRESOLVED".equals(targetFeature)) {
            reasons.add("TARGET_FEATURE_UNRESOLVED");
        }
        if (pair.resolutionConfidence().name().equals("LOW") || pair.resolutionConfidence().name().equals("UNKNOWN")) {
            reasons.add("AMBIGUOUS_RESOLUTION");
        }
        if (pair.primaryEvidenceRelationId().isBlank() || pair.featureSpecificTargetEntities().isBlank()) {
            reasons.add("NO_DIRECT_DEPENDENCY");
        }
        if ("UNRESOLVED".equals(relationType)) {
            reasons.add("FEATURE_BOUNDARY_AMBIGUOUS");
            reasons.add("INSUFFICIENT_SEMANTIC_DISTINCTION");
            reasons.add("RELATION_TYPE_UNRESOLVED");
        }
        return reasons.stream().distinct().collect(Collectors.joining("|"));
    }

    private void validatePrimaryEvidence(AuditedBlockPair pair) {
        if (pair.candidateStatus() == RequiresCandidateStatus.INVALID_DEPENDENCY_KIND
                || pair.candidateStatus() == RequiresCandidateStatus.UNRESOLVED_ENTITY) {
            return;
        }
        String kind = pair.dependencyKind();
        if (kind == null || kind.isBlank()) {
            if (pair.requiresRelevance() == RequiresRelevance.HIGH) {
                throw new IllegalStateException("HIGH candidate has no primary evidence relation: "
                        + pair.sourceBlockId() + " -> " + pair.targetBlockId());
            }
            return;
        }
        boolean valid = switch (kind) {
            case "EXTENDS" -> "CLASS".equals(pair.sourceEntityType())
                    && "CLASS".equals(pair.targetEntityType());
            case "IMPLEMENTS" -> "CLASS".equals(pair.sourceEntityType())
                    && "INTERFACE".equals(pair.targetEntityType());
            case "FIELD_REFERENCE" -> "FIELD".equals(pair.targetEntityType())
                    && ("METHOD".equals(pair.sourceEntityType())
                    || "CONSTRUCTOR".equals(pair.sourceEntityType()));
            case "METHOD_CALL" -> "METHOD".equals(pair.targetEntityType())
                    && ("METHOD".equals(pair.sourceEntityType())
                    || "CONSTRUCTOR".equals(pair.sourceEntityType()));
            case "CONSTRUCTOR_CALL" -> "CONSTRUCTOR".equals(pair.targetEntityType())
                    && ("METHOD".equals(pair.sourceEntityType())
                    || "CONSTRUCTOR".equals(pair.sourceEntityType()));
            case "TYPE_REFERENCE" -> !pair.sourceEntityType().isBlank()
                    && ("CLASS".equals(pair.targetEntityType())
                    || "INTERFACE".equals(pair.targetEntityType()));
            default -> false;
        };
        if (!valid) {
            throw new IllegalStateException("Invalid primary evidence " + kind + " from "
                    + pair.sourceEntityType() + " to " + pair.targetEntityType()
                    + " for " + pair.sourceBlockId() + " -> " + pair.targetBlockId());
        }
    }

    private void write(Path outputFile, List<String> lines) throws IOException {
        try {
            Files.write(outputFile, lines, StandardCharsets.UTF_8);
        } catch (FileSystemException exception) {
            Files.write(outputFile.resolveSibling(outputFile.getFileName() + ".new"), lines, StandardCharsets.UTF_8);
        }
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

    private record BlockLocation(String fileName, String lineRange) {
        static BlockLocation fromBlockId(String blockId) {
            if (blockId == null || blockId.isBlank()) {
                return null;
            }
            int lastSeparator = Math.max(blockId.lastIndexOf('\\'), blockId.lastIndexOf('/'));
            String tail = lastSeparator >= 0 ? blockId.substring(lastSeparator + 1) : blockId;
            String[] parts = tail.split(":");
            if (parts.length < 3) {
                return null;
            }
            return new BlockLocation(parts[0], parts[2]);
        }

        static BlockLocation fromDeclaration(String declaration) {
            if (declaration == null || declaration.isBlank()) {
                return null;
            }
            int at = declaration.lastIndexOf('@');
            if (at < 0 || at + 1 >= declaration.length()) {
                return null;
            }
            String location = declaration.substring(at + 1);
            int lineSeparator = location.lastIndexOf(':');
            if (lineSeparator < 0 || lineSeparator + 1 >= location.length()) {
                return null;
            }
            Path file = Path.of(location.substring(0, lineSeparator));
            return new BlockLocation(file.getFileName().toString(), location.substring(lineSeparator + 1));
        }

        static BlockLocation fromSourceLocation(String sourceFile, int line) {
            if (sourceFile == null || sourceFile.isBlank() || line < 0) {
                return null;
            }
            return new BlockLocation(Path.of(sourceFile).getFileName().toString(), Integer.toString(line));
        }

        String format() {
            return fileName + ":" + lineRange;
        }
    }
}
