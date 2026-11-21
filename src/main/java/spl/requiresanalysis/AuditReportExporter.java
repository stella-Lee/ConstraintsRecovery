package spl.requiresanalysis;

import spl.dependency.DependencyKindClassifier;
import spl.dependency.UnresolvedDependencyRelation;
import spl.entity.JavaRelation;
import spl.entity.ResolutionStatus;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class AuditReportExporter {
    public void export(AuditReportResult result, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        exportEntities(result, outputDirectory.resolve("audited-entity-classification.csv"));
        AuditReportResult strict = new AuditReportResult(result.entityClassifications(),
                result.blockPairs().stream().filter(AuditedBlockPair::eligibleForRequires).toList());
        exportCandidateDetails(strict, outputDirectory.resolve("requires_candidate_details.csv"));
        exportCompactCandidates(strict, outputDirectory.resolve("requires_candidates.csv"));
        exportCandidateEvidence(strict, outputDirectory.resolve("requires_candidate_evidence.csv"));
    }

    public void exportExclusions(AuditReportResult result, List<JavaRelation> relations,
                                 List<UnresolvedDependencyRelation> unresolvedRelations,
                                 Path outputDirectory) throws IOException {
        Map<String, String> failures = unresolvedRelations.stream().collect(Collectors.toMap(
                UnresolvedDependencyRelation::relationId, UnresolvedDependencyRelation::failureReason));
        List<String> lines = new ArrayList<>();
        lines.add("evidence_id,source_file,source_line,source_entity,target_entity,dependency_kind,source_unit,target_unit,source_products,target_products,resolution_status,reason,source_column,target_file,target_line,resolution_detail");
        for (AuditedBlockPair pair : result.blockPairs()) {
            if (!pair.eligibleForRequires()) {
                for (RequiresCandidateEvidenceRow row : pair.evidenceRows()) {
                    lines.add(csv(row.evidenceId(), row.sourceFile(), row.sourceLine(), row.sourceEntity(),
                            row.targetEntity(), row.dependencyKind(), pair.sourceBlockId(), pair.targetBlockId(),
                            row.sourceProducts(), row.targetProducts(), "RESOLVED_INTERNAL",
                            candidateExclusionReason(pair), row.sourceColumn(), row.targetFile(), row.targetLine(), ""));
                }
            }
        }
        for (JavaRelation relation : relations) {
            var kind = relation.relationType();
            var status = relation.resolutionStatus();
            String reason;
            if (DependencyKindClassifier.isStructuralDependency(kind)) {
                reason = "STRUCTURAL_DEPENDENCY";
            } else if (!DependencyKindClassifier.isImplementationDependency(kind)) {
                reason = "UNSUPPORTED_RELATION_KIND";
            } else if (status != ResolutionStatus.RESOLVED_INTERNAL) {
                reason = status.name();
            } else if (failures.containsKey(relation.relationId())) {
                reason = "INCOMPLETE_INTERNAL_RELATION";
            } else {
                continue;
            }
            lines.add(csv(relation.relationId(), relation.sourceFile(), relation.sourceLine(),
                    relation.sourceEntityId(), relation.unresolvedTargetText(), kind,
                    AuditReportBuilder.unitId(relation.containingBlockId(), relation.sourceFile()), "",
                    relation.effectiveProductSignature(), "", status, reason, relation.sourceColumn(), "", "",
                    failures.getOrDefault(relation.relationId(), "")));
        }
        write(outputDirectory.resolve("requires_exclusions.csv"), lines);
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
        lines.add("evidence_id,candidate_id,source_file,source_line,source_entity,source_entity_type,source_block_id,source_asset_id,source_is_outermost,target_file,target_line,target_entity,target_entity_type,target_block_id,target_asset_id,target_is_outermost,dependency_kind,code_snippet,source_products,target_products,source_column");
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
                        evidence.targetProducts(),
                        evidence.sourceColumn()
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
            case SINGLE_PRODUCT_SOURCE -> "SINGLE_PRODUCT_SOURCE";
            case SINGLE_PRODUCT_TARGET -> "SINGLE_PRODUCT_TARGET";
            case COMMON_TARGET_DEPENDENCY -> "TARGET_PRESENT_IN_ALL_PRODUCTS";
            case INTRA_ASSET_MANDATORY_TARGET -> "TARGET_IS_SAME_ASSET_OUTERMOST_BLOCK";
            case SAME_SIGNATURE_DIRECTED_DEPENDENCY -> "DIRECTION_NOT_IDENTIFIABLE";
            case INTRA_BLOCK -> "INTRA_BLOCK";
            case SIGNATURE_INCONSISTENT -> "SIGNATURE_INCONSISTENT";
        };
    }

    private void validatePrimaryEvidence(AuditedBlockPair pair) {
        String kind = pair.dependencyKind();
        if (kind == null || kind.isBlank()) {
            if (pair.requiresRelevance() == RequiresRelevance.HIGH) {
                throw new IllegalStateException("HIGH candidate has no primary evidence relation: "
                        + pair.sourceBlockId() + " -> " + pair.targetBlockId());
            }
            return;
        }
        boolean valid = switch (kind) {
            case "FIELD_REFERENCE" -> "FIELD".equals(pair.targetEntityType())
                    && ("METHOD".equals(pair.sourceEntityType())
                    || "CONSTRUCTOR".equals(pair.sourceEntityType()));
            case "METHOD_CALL" -> "METHOD".equals(pair.targetEntityType())
                    && ("METHOD".equals(pair.sourceEntityType())
                    || "CONSTRUCTOR".equals(pair.sourceEntityType()));
            case "CONSTRUCTOR_CALL" -> "CONSTRUCTOR".equals(pair.targetEntityType())
                    && ("METHOD".equals(pair.sourceEntityType())
                    || "CONSTRUCTOR".equals(pair.sourceEntityType()));
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
}
