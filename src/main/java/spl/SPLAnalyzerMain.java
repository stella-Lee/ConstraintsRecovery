package spl;

import spl.entity.EntityExtractionResult;
import spl.entity.EntityResultExporter;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityExtractor;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelation;
import spl.entity.JavaRelationType;
import spl.entity.ResolutionStatus;
import spl.grouping.SignatureBlockGrouper;
import spl.grouping.SignatureGroup;
import spl.dependency.CrossFileDependencyGraphBuilder;
import spl.dependency.DependencyEdge;
import spl.dependency.DependencyGraph;
import spl.dependency.DependencyGraphExporter;
import spl.dependency.UnresolvedDependencyRelation;
import spl.requiresanalysis.DependencyEvidence;
import spl.requiresanalysis.EntityRole;
import spl.requiresanalysis.AuditReportBuilder;
import spl.requiresanalysis.AuditReportExporter;
import spl.requiresanalysis.AuditReportResult;
import spl.requiresanalysis.RequiresEvidenceBuilder;
import spl.requiresanalysis.RequiresEvidenceExporter;
import spl.requiresanalysis.RequiresEvidenceResult;
import spl.requiresanalysis.RequiresRelevance;
import spl.feature.CandidateClassificationStatus;
import spl.feature.CandidateDependencyScope;
import spl.feature.FeatureEffectCandidate;
import spl.feature.FeatureEffectCandidateBuilder;
import spl.feature.FeatureEffectCandidateExporter;
import spl.feature.FeatureEffectCandidateResult;
import spl.feature.FeatureEvidenceProfile;
import spl.feature.FeatureEvidenceProfileBuilder;
import spl.feature.FeatureEvidenceProfileExporter;
import spl.feature.FeatureEvidenceProfileResult;
import spl.feature.AggregatedFeatureEffectCandidate;
import spl.feature.CandidateConfidence;
import spl.feature.CommonalityClassification;
import spl.feature.EvidenceConfidence;
import spl.feature.SemanticAggregationExporter;
import spl.feature.SemanticAggregationResult;
import spl.feature.SemanticFeatureEffectAggregator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

public final class SPLAnalyzerMain {
    private static final Path OUTPUT_DIRECTORY = Path.of("D:\\SPL-Tool\\git\\ConstraintsRecovery\\output");

    public static void main(String[] args) throws Exception {
        CommandLine commandLine;
        try {
            commandLine = CommandLine.parse(args);
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            System.err.println("Usage: java spl.SPLAnalyzerMain [asset-subdirectory] [--candidate-details <candidate-id>]");
            return;
        }

        AssetProjectPathResolver pathResolver = new AssetProjectPathResolver();
        System.out.println("Base asset directory: " + pathResolver.baseDirectoryDisplay());
        String assetSubdirectory = commandLine.assetSubdirectory() == null
                ? promptForAssetSubdirectory()
                : commandLine.assetSubdirectory();
        AssetProjectPathResolver.ResolvedAssetProject assetProject;
        try {
            assetProject = pathResolver.resolve(assetSubdirectory);
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            return;
        }
        System.out.println("Analyzing asset project: " + assetProject.displayPath());
        System.out.println("Output directory: " + OUTPUT_DIRECTORY);

        ProductSignatureExtractor extractor = new ProductSignatureExtractor();
        List<ConditionalBlock> blocks = extractor.extract(assetProject.normalizedPath());
        List<SignatureGroup> groups = new SignatureBlockGrouper().group(blocks);
        printGroups(groups);

        List<Path> assetFiles = new AssetFileScanner().scan(assetProject.normalizedPath());
        EntityExtractionResult entityResult = new JavaEntityExtractor().extract(groups, assetFiles);
        new EntityResultExporter().export(entityResult, OUTPUT_DIRECTORY);
        printEntitySummary(groups, entityResult);

        DependencyGraph graph = new CrossFileDependencyGraphBuilder().build(entityResult);
        new DependencyGraphExporter().export(graph, OUTPUT_DIRECTORY);
        printDependencySummary(graph);

        RequiresEvidenceResult requiresEvidence = new RequiresEvidenceBuilder().build(groups, entityResult, graph);
        new RequiresEvidenceExporter().export(requiresEvidence, OUTPUT_DIRECTORY);
        printRequiresEvidenceSummary(requiresEvidence);
        AuditReportResult auditResult = new AuditReportBuilder().build(requiresEvidence);
        new AuditReportExporter().export(auditResult, OUTPUT_DIRECTORY);
        printAuditSummary(auditResult);
        if (commandLine.candidateDetailsId() != null) {
            System.out.println();
            System.out.println("--candidate-details is a legacy feature-candidate option and is not run in the requires-analysis default pipeline.");
        }
    }

    private static Path outputFile(String fileName) {
        return OUTPUT_DIRECTORY.resolve(fileName);
    }

    private static String promptForAssetSubdirectory() {
        System.out.print("Enter asset project subdirectory under assets: ");
        return new Scanner(System.in).nextLine();
    }

    private static void printGroups(List<SignatureGroup> groups) {
        for (int index = 0; index < groups.size(); index++) {
            SignatureGroup group = groups.get(index);
            if (index > 0) {
                System.out.println();
            }
            System.out.println("Group " + group.groupId());
            System.out.println("Signature : " + group.canonicalSignature());
            System.out.println("Blocks    : " + group.blocks().size());
        }
    }

    private static void printEntitySummary(List<SignatureGroup> groups, EntityExtractionResult result) {
        System.out.println();
        System.out.println("Parsed asset files: " + result.parsedAssetFileCount());
        System.out.println("Product projections attempted: " + result.projectionAttemptCount());
        System.out.println("Successful projections       : " + result.successfulProjectionCount());
        System.out.println("Failed projections           : " + result.parseFailures().size());
        for (var failure : result.parseFailures()) {
            System.out.printf(
                    "Parse failure: %s product %s line %d: %s%n",
                    failure.sourceFile(),
                    failure.product(),
                    failure.line(),
                    failure.message()
            );
        }

        System.out.println();
        System.out.println("Total entities by type:");
        printEntityCounts(result.entities());
        System.out.println("Total relations by type:");
        printRelationCounts(result.relations());
        printRepresentativeRelations("Representative field references", result.relations(), JavaRelationType.FIELD_REFERENCE);
        printRepresentativeRelations("Representative constructor calls", result.relations(), JavaRelationType.CONSTRUCTOR_CALL);
        printRepresentativeMergedEntities(result.entities());
        printRepresentativeMergedRelations(result.relations());

        Map<String, List<JavaEntity>> entitiesByGroup = result.entities().stream()
                .collect(Collectors.groupingBy(JavaEntity::signatureGroupId));
        Map<String, List<JavaRelation>> relationsByGroup = result.relations().stream()
                .collect(Collectors.groupingBy(JavaRelation::signatureGroupId));
        for (SignatureGroup group : groups) {
            System.out.println();
            System.out.println("Group " + group.groupId());
            System.out.println("Signature: " + group.canonicalSignature());
            System.out.println("Entities:");
            printEntityCounts(entitiesByGroup.getOrDefault(group.groupId(), List.of()));
            System.out.println("Relations:");
            printRelationCounts(relationsByGroup.getOrDefault(group.groupId(), List.of()));
        }
    }

    private static void printEntityCounts(Collection<JavaEntity> entities) {
        Map<JavaEntityType, Long> counts = entities.stream()
                .collect(Collectors.groupingBy(JavaEntity::entityType, Collectors.counting()));
        for (JavaEntityType type : JavaEntityType.values()) {
            System.out.println("- " + type + ": " + counts.getOrDefault(type, 0L));
        }
    }

    private static void printRelationCounts(Collection<JavaRelation> relations) {
        Map<JavaRelationType, Long> counts = relations.stream()
                .collect(Collectors.groupingBy(JavaRelation::relationType, Collectors.counting()));
        for (JavaRelationType type : JavaRelationType.values()) {
            System.out.println("- " + type + ": " + counts.getOrDefault(type, 0L));
        }
    }

    private static void printRepresentativeRelations(String title, Collection<JavaRelation> relations,
                                                     JavaRelationType relationType) {
        System.out.println();
        System.out.println(title + ":");
        relations.stream()
                .filter(relation -> relation.relationType() == relationType)
                .limit(10)
                .forEach(relation -> System.out.printf(
                        "- source=%s target=%s file=%s line=%d group=%s status=%s%n",
                        relation.sourceEntityId(),
                        relation.unresolvedTargetText(),
                        relation.sourceFile(),
                        relation.sourceLine(),
                        relation.signatureGroupId(),
                        relation.resolutionStatus()
                ));
    }

    private static void printRepresentativeMergedEntities(Collection<JavaEntity> entities) {
        System.out.println();
        System.out.println("Representative merged entities:");
        entities.stream()
                .filter(entity -> entity.observedProducts().size() > 1)
                .limit(5)
                .forEach(entity -> System.out.printf(
                        "- key=%s|%s|%s|%d-%d products=%s signature=%s group=%s%n",
                        entity.sourceFile(),
                        entity.entityType(),
                        entity.qualifiedName() == null ? entity.simpleName() : entity.qualifiedName(),
                        entity.startLine(),
                        entity.endLine(),
                        entity.observedProducts(),
                        entity.effectiveProductSignature(),
                        entity.signatureGroupId()
                ));
    }

    private static void printRepresentativeMergedRelations(Collection<JavaRelation> relations) {
        System.out.println();
        System.out.println("Representative merged relations:");
        relations.stream()
                .filter(relation -> relation.observedProducts().size() > 1)
                .limit(5)
                .forEach(relation -> System.out.printf(
                        "- key=%s|%d|%s|%s products=%s signature=%s group=%s%n",
                        relation.sourceFile(),
                        relation.sourceLine(),
                        relation.relationType(),
                        relation.targetEntityId() == null ? relation.unresolvedTargetText() : relation.targetEntityId(),
                        relation.observedProducts(),
                        relation.effectiveProductSignature(),
                        relation.signatureGroupId()
                ));
    }

    private static void printDependencySummary(DependencyGraph graph) {
        System.out.println();
        System.out.println("Dependency graph:");
        System.out.println("Graph nodes          : " + graph.nodes().size());
        System.out.println("Resolved graph edges : " + graph.edges().size());
        System.out.println("Debug unresolved rels: " + graph.unresolvedRelations().size());
        System.out.println("Implementation deps  : " + graph.statistics().implementationDependencyCount());
        System.out.println("Structural deps      : " + graph.statistics().structuralDependencyCount());
        System.out.println("Excluded deps removed: " + graph.statistics().excludedDependencyCount());
        System.out.println("External deps removed: " + graph.statistics().externalDependencyRemovedCount());
        System.out.println("Unresolved removed   : " + graph.statistics().unresolvedDependencyRemovedCount());
        System.out.println("Cross-file edges     : " + graph.edges().stream().filter(DependencyEdge::crossFile).count());
        System.out.println("Same-file edges      : " + graph.edges().stream().filter(edge -> !edge.crossFile()).count());
        System.out.println("External targets     : " + graph.unresolvedRelations().stream()
                .filter(relation -> relation.failureReason().equals("external target"))
                .count());
        System.out.println("Ambiguous relations  : " + graph.unresolvedRelations().stream()
                .filter(relation -> relation.resolutionStatus() == ResolutionStatus.AMBIGUOUS)
                .count());
        System.out.println("Missing source rels  : " + graph.unresolvedRelations().stream()
                .filter(relation -> relation.failureReason().equals("missing source entity"))
                .count());
        System.out.println("Invalid ctor targets : " + invalidConstructorTargetCount(graph));

        System.out.println("Edges by relation type:");
        Map<JavaRelationType, Long> byType = graph.edges().stream()
                .collect(Collectors.groupingBy(DependencyEdge::relationType, Collectors.counting()));
        for (JavaRelationType type : JavaRelationType.values()) {
            System.out.println("- " + type + ": " + byType.getOrDefault(type, 0L));
        }

        System.out.println("Resolved external relations by type:");
        Map<JavaRelationType, Long> externalByType = graph.unresolvedRelations().stream()
                .filter(relation -> relation.resolutionStatus() == ResolutionStatus.RESOLVED_EXTERNAL)
                .collect(Collectors.groupingBy(UnresolvedDependencyRelation::relationType, Collectors.counting()));
        for (JavaRelationType type : JavaRelationType.values()) {
            System.out.println("- " + type + ": " + externalByType.getOrDefault(type, 0L));
        }

        System.out.println("Edges by source SignatureGroup:");
        graph.edges().stream()
                .collect(Collectors.groupingBy(DependencyEdge::sourceGroupId, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> System.out.println("- " + entry.getKey() + ": " + entry.getValue()));

        System.out.println("Top SignatureGroup dependency pairs:");
        graph.edges().stream()
                .collect(Collectors.groupingBy(
                        edge -> edge.sourceGroupId() + " -> " + edge.targetGroupId(),
                        Collectors.groupingBy(DependencyEdge::relationType, Collectors.counting())
                ))
                .entrySet()
                .stream()
                .sorted(Comparator.<Map.Entry<String, Map<JavaRelationType, Long>>>comparingLong(
                        entry -> -entry.getValue().values().stream().mapToLong(Long::longValue).sum()
                ).thenComparing(Map.Entry::getKey))
                .limit(10)
                .forEach(entry -> {
                    System.out.println(entry.getKey());
                    entry.getValue().entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .forEach(typeCount -> System.out.println(typeCount.getKey() + ": " + typeCount.getValue()));
                });
    }

    private static long invalidConstructorTargetCount(DependencyGraph graph) {
        Map<String, JavaEntityType> nodeTypes = graph.nodes().stream()
                .collect(Collectors.toMap(node -> node.entityId(), node -> node.entityType()));
        return graph.edges().stream()
                .filter(edge -> edge.relationType() == JavaRelationType.CONSTRUCTOR_CALL)
                .filter(edge -> nodeTypes.get(edge.targetEntityId()) != JavaEntityType.CONSTRUCTOR)
                .count();
    }

    private static void printRequiresEvidenceSummary(RequiresEvidenceResult result) {
        System.out.println();
        System.out.println("Requires-analysis structural evidence:");
        System.out.println("Conditional blocks    : " + result.blocks().size());
        System.out.println("Block entities        : " + result.entities().size());
        System.out.println("Block dependencies    : " + result.dependencies().size());
        System.out.println("Entity classifications: " + result.classifications().size());
        System.out.println("Output files:");
        System.out.println("- " + outputFile("blocks.csv"));
        System.out.println("- " + outputFile("block-entities.csv"));
        System.out.println("- " + outputFile("block-dependencies.csv"));
        System.out.println("- " + outputFile("entity-classification.csv"));
        System.out.println("- " + outputFile("block-summary.csv"));

        System.out.println("Entity roles:");
        Map<EntityRole, Long> roleCounts = result.classifications().stream()
                .collect(Collectors.groupingBy(classification -> classification.entityRole(), Collectors.counting()));
        for (EntityRole role : EntityRole.values()) {
            System.out.println("- " + role + ": " + roleCounts.getOrDefault(role, 0L));
        }

        System.out.println("Requires relevance evidence levels:");
        Map<RequiresRelevance, Long> relevanceCounts = result.dependencies().stream()
                .collect(Collectors.groupingBy(DependencyEvidence::requiresRelevance, Collectors.counting()));
        for (RequiresRelevance relevance : RequiresRelevance.values()) {
            System.out.println("- " + relevance + ": " + relevanceCounts.getOrDefault(relevance, 0L));
        }

        System.out.println("Top blocks by outgoing dependency evidence:");
        result.blockSummaries().stream()
                .sorted(Comparator.<spl.requiresanalysis.BlockSummary>comparingInt(
                        summary -> -summary.outgoingDependencyCount()
                ).thenComparing(summary -> summary.file())
                        .thenComparingInt(summary -> summary.startLine()))
                .limit(10)
                .forEach(summary -> System.out.printf(
                        "- %s group=%s entities=%d outgoing=%d high=%d medium=%d low=%d roles=%s%n",
                        summary.blockId(),
                        summary.groupId(),
                        summary.entityCount(),
                        summary.outgoingDependencyCount(),
                        summary.highRelevanceDependencyCount(),
                        summary.mediumRelevanceDependencyCount(),
                        summary.lowRelevanceDependencyCount(),
                        summary.dominantEntityRoles()
                ));
    }

    private static void printAuditSummary(AuditReportResult result) {
        System.out.println();
        System.out.println("Audited requires-analysis evidence:");
        System.out.println("Audited entities   : " + result.entityClassifications().size());
        System.out.println("Audited block pairs: " + result.blockPairs().size());
        System.out.println("Output files:");
        System.out.println("- " + outputFile("audited-entity-classification.csv"));
        System.out.println("- " + outputFile("requires_candidates.csv"));
        System.out.println("- " + outputFile("requires_candidate_evidence.csv"));
        System.out.println("- " + outputFile("requires_candidate_details.csv"));
        System.out.println("Requires relevance hypotheses:");
        Map<RequiresRelevance, Long> relevanceCounts = result.blockPairs().stream()
                .collect(Collectors.groupingBy(pair -> pair.requiresRelevance(), Collectors.counting()));
        for (RequiresRelevance relevance : RequiresRelevance.values()) {
            System.out.println("- " + relevance + ": " + relevanceCounts.getOrDefault(relevance, 0L));
        }
    }

    private static void printFeatureEffectSummary(List<SignatureGroup> groups,
                                                  FeatureEffectCandidateResult result) {
        System.out.println();
        System.out.println("Feature-effect candidates:");
        System.out.println("SignatureGroups           : " + groups.size());
        System.out.println("Total candidates          : " + result.candidates().size());
        System.out.println("Isolated candidates       : " + result.candidates().stream()
                .filter(candidate -> candidate.classificationStatus() == CandidateClassificationStatus.ISOLATED_ENTITY)
                .count());
        System.out.println("Block-only candidates     : " + result.candidates().stream()
                .filter(candidate -> candidate.classificationStatus() == CandidateClassificationStatus.BLOCK_ONLY)
                .count());
        System.out.println("Unassigned entities       : 0");
        System.out.println("Unassigned blocks         : " + result.unassignedBlocks().size());
        System.out.println("Cross-file candidates     : " + result.candidates().stream()
                .filter(candidate -> candidate.involvedAssetFiles().size() > 1)
                .count());
        System.out.println("Candidate dependencies    : " + result.candidateDependencies().stream()
                .filter(dependency -> dependency.dependencyScope() != CandidateDependencyScope.INTERNAL_CANDIDATE)
                .mapToInt(dependency -> dependency.edgeCount())
                .sum());

        System.out.println("Candidates per SignatureGroup:");
        Map<String, Long> candidatesByGroup = result.candidates().stream()
                .collect(Collectors.groupingBy(FeatureEffectCandidate::signatureGroupId, Collectors.counting()));
        for (SignatureGroup group : groups) {
            System.out.println("- " + group.groupId() + ": " + candidatesByGroup.getOrDefault(group.groupId(), 0L));
        }

        System.out.println("Entity-count distribution:");
        printDistribution(result.candidates().stream()
                .map(candidate -> candidate.memberEntities().size())
                .toList());
        System.out.println("Block-count distribution:");
        printDistribution(result.candidates().stream()
                .map(candidate -> candidate.memberBlocks().size())
                .toList());

        System.out.println("Top ten largest candidates:");
        result.candidates().stream()
                .sorted(Comparator.<FeatureEffectCandidate>comparingInt(
                        candidate -> -candidate.memberEntities().size()
                ).thenComparing(FeatureEffectCandidate::candidateId))
                .limit(10)
                .forEach(candidate -> {
                    System.out.printf(
                            "%s group=%s signature=%s entities=%d blocks=%d files=%d%n",
                            candidate.candidateId(),
                            candidate.signatureGroupId(),
                            candidate.productSignature(),
                            candidate.memberEntities().size(),
                            candidate.memberBlocks().size(),
                            candidate.involvedAssetFiles().size()
                    );
                    FeatureEffectCandidateExporter.internalEdgeCounts(candidate)
                            .forEach((type, count) -> System.out.println("- " + type + ": " + count));
                });
    }

    private static void printDistribution(List<Integer> values) {
        values.stream()
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> System.out.println("- " + entry.getKey() + ": " + entry.getValue()));
    }

    private static void printSemanticAggregationSummary(SemanticAggregationResult result) {
        System.out.println();
        System.out.println("Semantic aggregation:");
        System.out.println("Structural components       : " + result.evidenceByComponentId().size());
        System.out.println("Aggregated candidates       : " + result.candidates().size());
        System.out.println("Merged components           : " + result.candidates().stream()
                .filter(candidate -> candidate.structuralComponentIds().size() > 1)
                .mapToInt(candidate -> candidate.structuralComponentIds().size())
                .sum());
        System.out.println("Unassigned components       : " + result.unassignedComponents().size());
        System.out.println("Unlabeled candidates        : " + result.candidates().stream()
                .filter(candidate -> candidate.labelConfidence() == EvidenceConfidence.UNLABELED)
                .count());
        System.out.println("Full commonality candidates : " + countCommonality(result, CommonalityClassification.FULL_COMMONALITY));
        System.out.println("Subgroup commonality cand.  : " + countCommonality(result, CommonalityClassification.SUBGROUP_COMMONALITY));
        System.out.println("Variable-signature cand.    : " + countCommonality(result, CommonalityClassification.VARIABLE_SIGNATURE));

        System.out.println("Aggregated candidates per SignatureGroup:");
        result.candidates().stream()
                .collect(Collectors.groupingBy(AggregatedFeatureEffectCandidate::signatureGroupId, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> System.out.println("- " + entry.getKey() + ": " + entry.getValue()));

        System.out.println("Label confidence distribution:");
        for (EvidenceConfidence confidence : EvidenceConfidence.values()) {
            long count = result.candidates().stream()
                    .filter(candidate -> candidate.labelConfidence() == confidence)
                    .count();
            System.out.println("- " + confidence + ": " + count);
        }
        System.out.println("Candidate confidence distribution:");
        for (CandidateConfidence confidence : CandidateConfidence.values()) {
            long count = result.candidates().stream()
                    .filter(candidate -> candidate.candidateConfidence() == confidence)
                    .count();
            System.out.println("- " + confidence + ": " + count);
        }

        System.out.println("Top ten aggregated candidates by entity count:");
        result.candidates().stream()
                .sorted(Comparator.<AggregatedFeatureEffectCandidate>comparingInt(
                        candidate -> -candidate.memberEntities().size()
                ).thenComparing(AggregatedFeatureEffectCandidate::candidateId))
                .limit(10)
                .forEach(candidate -> System.out.printf(
                        "%s signature=%s label=%s confidence=%s components=%d entities=%d files=%d tokens=%s evidence=%s%n",
                        candidate.candidateId(),
                        candidate.productSignature(),
                        candidate.suggestedLabel().isBlank() ? "<unlabeled>" : candidate.suggestedLabel(),
                        candidate.candidateConfidence(),
                        candidate.structuralComponentIds().size(),
                        candidate.memberEntities().size(),
                        candidate.involvedFiles().size(),
                        candidate.representativeTokens(),
                        candidate.aggregationDecisions().stream()
                                .flatMap(decision -> decision.supportingEvidence().stream())
                                .distinct()
                                .toList()
                ));

        System.out.println("Top ten highest-confidence labels:");
        result.candidates().stream()
                .filter(candidate -> candidate.labelConfidence() != EvidenceConfidence.UNLABELED)
                .sorted(Comparator.comparing((AggregatedFeatureEffectCandidate candidate) -> candidate.labelConfidence().ordinal())
                        .thenComparing(AggregatedFeatureEffectCandidate::candidateId))
                .limit(10)
                .forEach(candidate -> System.out.printf(
                        "%s label=%s labelConfidence=%s tokens=%s entities=%s%n",
                        candidate.candidateId(),
                        candidate.suggestedLabel(),
                        candidate.labelConfidence(),
                        candidate.representativeTokens(),
                        candidate.memberEntities().stream()
                                .limit(3)
                                .map(entity -> entity.qualifiedName() == null ? entity.simpleName() : entity.qualifiedName())
                                .toList()
                ));
    }

    private static long countCommonality(SemanticAggregationResult result, CommonalityClassification classification) {
        return result.candidates().stream()
                .filter(candidate -> candidate.commonalityClassification() == classification)
                .count();
    }

    private static void printFeatureEvidenceProfileSummary(FeatureEvidenceProfileResult result) {
        System.out.println();
        System.out.println("Feature evidence profiles:");
        System.out.println("Profiles: " + result.profiles().size());
        System.out.println("Top 20 implementation clusters:");
        result.profiles().stream()
                .sorted(Comparator.<FeatureEvidenceProfile>comparingInt(FeatureEvidenceProfile::entityCount)
                        .reversed()
                        .thenComparing(FeatureEvidenceProfile::componentId))
                .limit(20)
                .forEach(profile -> System.out.printf(
                        "%s signature=%s concept=%s entities=%s tokens=%s dependencies=%s%n",
                        profile.componentId(),
                        profile.signature(),
                        profile.implementationConcept(),
                        profile.representativeEntities().stream()
                                .limit(3)
                                .map(item -> item.entity().qualifiedName() == null
                                        ? item.entity().simpleName()
                                        : item.entity().qualifiedName())
                                .toList(),
                        profile.tokenFrequencies().entrySet().stream()
                                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                                        .thenComparing(Map.Entry::getKey))
                                .limit(5)
                                .map(entry -> entry.getKey() + ":" + entry.getValue())
                                .toList(),
                        profile.internalDependencyTypes()
                ));
    }

    private static void printCandidateDetails(FeatureEffectCandidateResult result,
                                              SemanticAggregationResult semanticResult,
                                              String candidateId) {
        if (candidateId.startsWith("AFEC-")) {
            printAggregatedCandidateDetails(semanticResult, candidateId);
            return;
        }
        FeatureEffectCandidate candidate = result.candidates().stream()
                .filter(item -> item.candidateId().equals(candidateId))
                .findFirst()
                .orElse(null);
        if (candidate == null) {
            System.out.println("Candidate not found: " + candidateId);
            return;
        }
        System.out.println();
        System.out.println("Candidate details: " + candidate.candidateId());
        System.out.println("Group: " + candidate.signatureGroupId());
        System.out.println("Signature: " + candidate.productSignature());
        System.out.println("Classification: " + candidate.classificationStatus());
        System.out.println("Entities:");
        candidate.memberEntities().forEach(entity -> System.out.printf(
                "- %s %s %s:%d-%d block=%s%n",
                entity.entityId(),
                entity.qualifiedName() == null ? entity.simpleName() : entity.qualifiedName(),
                entity.sourceFile(),
                entity.startLine(),
                entity.endLine(),
                entity.containingBlockId()
        ));
        System.out.println("Blocks:");
        candidate.memberBlocks().forEach(block -> System.out.printf(
                "- %s %s:%d-%d%n",
                block.directiveType(),
                block.filePath(),
                block.startLine(),
                block.endLine()
        ));
        System.out.println("Internal dependencies:");
        candidate.internalEdges().forEach(edge -> System.out.printf(
                "- %s %s -> %s line=%d%n",
                edge.relationType(),
                edge.sourceEntityId(),
                edge.targetEntityId(),
                edge.sourceLine()
        ));
        System.out.println("Incoming dependencies: " + candidate.incomingEdges().size());
        System.out.println("Outgoing dependencies: " + candidate.outgoingEdges().size());
    }

    private static void printAggregatedCandidateDetails(SemanticAggregationResult result, String candidateId) {
        AggregatedFeatureEffectCandidate candidate = result.candidates().stream()
                .filter(item -> item.candidateId().equals(candidateId))
                .findFirst()
                .orElse(null);
        if (candidate == null) {
            System.out.println("Candidate not found: " + candidateId);
            return;
        }
        System.out.println();
        System.out.println("Aggregated candidate details: " + candidate.candidateId());
        System.out.println("Label: " + (candidate.suggestedLabel().isBlank() ? "<unlabeled>" : candidate.suggestedLabel()));
        System.out.println("Label confidence: " + candidate.labelConfidence());
        System.out.println("Candidate confidence: " + candidate.candidateConfidence());
        System.out.println("Components: " + candidate.structuralComponentIds());
        System.out.println("Representative tokens: " + candidate.representativeTokens());
        System.out.println("Entities:");
        candidate.memberEntities().forEach(entity -> System.out.printf(
                "- %s %s %s:%d-%d%n",
                entity.entityId(),
                entity.qualifiedName() == null ? entity.simpleName() : entity.qualifiedName(),
                entity.sourceFile(),
                entity.startLine(),
                entity.endLine()
        ));
        System.out.println("Merge decisions:");
        candidate.aggregationDecisions().forEach(decision -> System.out.printf(
                "- %s + %s score=%.3f evidence=%s reason=%s%n",
                decision.componentA(),
                decision.componentB(),
                decision.combinedScore(),
                decision.supportingEvidence(),
                decision.reason()
        ));
        System.out.println("Incoming dependencies: " + candidate.incomingEdges().size());
        System.out.println("Outgoing dependencies: " + candidate.outgoingEdges().size());
    }

    private record CommandLine(String assetSubdirectory, String candidateDetailsId) {
        private static CommandLine parse(String[] args) {
            List<String> positional = new ArrayList<>();
            String candidateDetailsId = null;
            for (int index = 0; index < args.length; index++) {
                if ("--candidate-details".equals(args[index])) {
                    if (index + 1 >= args.length) {
                        throw new IllegalArgumentException("--candidate-details requires a candidate id.");
                    }
                    candidateDetailsId = args[++index];
                } else {
                    positional.add(args[index]);
                }
            }
            if (positional.size() > 1) {
                throw new IllegalArgumentException("Only one asset subdirectory may be supplied.");
            }
            return new CommandLine(positional.isEmpty() ? null : positional.get(0), candidateDetailsId);
        }
    }
}
