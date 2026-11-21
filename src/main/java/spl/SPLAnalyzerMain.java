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
import spl.requiresanalysis.BranchCompletionObligationDetector;
import spl.requiresanalysis.CompositionAwareDependencyDetector;
import spl.requiresanalysis.CompositionAwareDependencyExporter;
import spl.requiresanalysis.CompositionAwareDependencyResult;
import spl.requiresanalysis.DisjunctiveImplementationEvidenceExporter;
import spl.requiresanalysis.DisjunctiveImplementationEvidenceResult;
import spl.requiresanalysis.EntityRole;
import spl.requiresanalysis.ProviderReferenceEvidenceDetector;
import spl.requiresanalysis.ProviderReferenceEvidenceExporter;
import spl.requiresanalysis.ProviderReferenceEvidenceResult;
import spl.requiresanalysis.ProviderDataFlowEvidenceDetector;
import spl.requiresanalysis.ProviderDataFlowEvidenceExporter;
import spl.requiresanalysis.ProviderDataFlowEvidenceResult;
import spl.requiresanalysis.OperationProviderEvidenceDetector;
import spl.requiresanalysis.OperationProviderEvidenceExporter;
import spl.requiresanalysis.OperationProviderEvidenceResult;
import spl.requiresanalysis.SignatureProviderBundleEvidenceDetector;
import spl.requiresanalysis.SignatureProviderBundleEvidenceExporter;
import spl.requiresanalysis.SignatureProviderBundleEvidence;
import spl.requiresanalysis.SignatureProviderBundleEvidenceResult;
import spl.requiresanalysis.ImplementationEvidenceSummary;
import spl.requiresanalysis.ImplementationEvidenceSummaryBuilder;
import spl.requiresanalysis.ImplementationEvidenceSummaryExporter;
import spl.requiresanalysis.ImplementationEvidenceSummaryResult;
import spl.requiresanalysis.AuditReportBuilder;
import spl.requiresanalysis.AuditReportExporter;
import spl.requiresanalysis.AuditReportResult;
import spl.requiresanalysis.RequiresEvidenceBuilder;
import spl.requiresanalysis.RequiresEvidenceExporter;
import spl.requiresanalysis.RequiresEvidenceResult;
import spl.requiresanalysis.RequiresRelevance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

public final class SPLAnalyzerMain {
    private static final Path OUTPUT_BASE_DIRECTORY = Path.of("output").toAbsolutePath().normalize();

    public static void main(String[] args) throws Exception {
        if (args.length > 1 || (args.length == 1 && args[0].startsWith("--"))) {
            System.err.println("Usage: java spl.SPLAnalyzerMain [asset-subdirectory]");
            return;
        }

        AssetProjectPathResolver pathResolver = new AssetProjectPathResolver();
        System.out.println("Base asset directory: " + pathResolver.baseDirectoryDisplay());
        String assetSubdirectory = args.length == 0 ? promptForAssetSubdirectory() : args[0];
        AssetProjectPathResolver.ResolvedAssetProject assetProject;
        try {
            assetProject = pathResolver.resolve(assetSubdirectory);
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            return;
        }
        System.out.println("Analyzing asset project: " + assetProject.displayPath());
        Path assetRelativePath = AssetProjectPathResolver.BASE_DIRECTORY.relativize(Path.of(assetProject.displayPath()));
        Path outputDirectory = OUTPUT_BASE_DIRECTORY.resolve(assetRelativePath);
        System.out.println("Output directory: " + outputDirectory);

        ProductSignatureExtractor extractor = new ProductSignatureExtractor();
        List<ConditionalBlock> blocks = extractor.extract(assetProject.normalizedPath());
        List<SignatureGroup> groups = new SignatureBlockGrouper().group(blocks);
        printGroups(groups);

        List<Path> assetFiles = new AssetFileScanner().scan(assetProject.normalizedPath());
        EntityExtractionResult entityResult = new JavaEntityExtractor().extract(groups, assetFiles);
        new EntityResultExporter().export(entityResult, outputDirectory);
        printEntitySummary(groups, entityResult);

        DependencyGraph graph = new CrossFileDependencyGraphBuilder().build(entityResult);
        new DependencyGraphExporter().export(graph, outputDirectory);
        printDependencySummary(graph);

        RequiresEvidenceResult requiresEvidence = new RequiresEvidenceBuilder().build(groups, entityResult, graph);
        new RequiresEvidenceExporter().export(requiresEvidence, outputDirectory);
        printRequiresEvidenceSummary(requiresEvidence, outputDirectory);
        CompositionAwareDependencyResult compositionResult =
                new CompositionAwareDependencyDetector().detect(requiresEvidence);
        new CompositionAwareDependencyExporter().export(compositionResult, outputDirectory);
        printCompositionAwareDependencySummary(compositionResult, outputDirectory);
        AuditReportResult auditResult = new AuditReportBuilder().build(requiresEvidence);
        AuditReportExporter auditExporter = new AuditReportExporter();
        auditExporter.export(auditResult, outputDirectory);
        auditExporter.exportExclusions(auditResult, entityResult.relations(), graph.unresolvedRelations(), outputDirectory);
        printAuditSummary(auditResult, outputDirectory);
        DisjunctiveImplementationEvidenceResult disjunctiveResult =
                new BranchCompletionObligationDetector().detectBranchCompletionObligations(blocks);
        new DisjunctiveImplementationEvidenceExporter().export(disjunctiveResult, outputDirectory);
        printDisjunctiveImplementationEvidenceSummary(disjunctiveResult, outputDirectory);
        ProviderReferenceEvidenceResult providerReferenceResult =
                new ProviderReferenceEvidenceDetector().detect(groups, entityResult);
        new ProviderReferenceEvidenceExporter().export(providerReferenceResult, outputDirectory);
        printProviderReferenceEvidenceSummary(providerReferenceResult, outputDirectory);
        ProviderDataFlowEvidenceResult providerDataFlowResult =
                new ProviderDataFlowEvidenceDetector().detect(groups, entityResult);
        new ProviderDataFlowEvidenceExporter().export(providerDataFlowResult, outputDirectory);
        printProviderDataFlowEvidenceSummary(providerDataFlowResult, outputDirectory);
        OperationProviderEvidenceResult operationProviderResult =
                new OperationProviderEvidenceDetector().detect(providerDataFlowResult);
        new OperationProviderEvidenceExporter().export(operationProviderResult, outputDirectory);
        printOperationProviderEvidenceSummary(operationProviderResult, outputDirectory);
        SignatureProviderBundleEvidenceResult signatureProviderBundleResult =
                new SignatureProviderBundleEvidenceDetector().detect(operationProviderResult);
        new SignatureProviderBundleEvidenceExporter().export(signatureProviderBundleResult, outputDirectory);
        printSignatureProviderBundleEvidenceSummary(signatureProviderBundleResult, outputDirectory);
        ImplementationEvidenceSummaryResult implementationSummaryResult =
                new ImplementationEvidenceSummaryBuilder().build(signatureProviderBundleResult);
        new ImplementationEvidenceSummaryExporter().export(implementationSummaryResult, outputDirectory);
        printImplementationEvidenceSummary(implementationSummaryResult, outputDirectory);
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

    private static void printRequiresEvidenceSummary(RequiresEvidenceResult result, Path outputDirectory) {
        System.out.println();
        System.out.println("Requires-analysis structural evidence:");
        System.out.println("Conditional blocks    : " + result.blocks().size());
        System.out.println("Block entities        : " + result.entities().size());
        System.out.println("Block dependencies    : " + result.dependencies().size());
        System.out.println("Entity classifications: " + result.classifications().size());
        System.out.println("Output files:");
        System.out.println("- " + outputDirectory.resolve("blocks.csv"));
        System.out.println("- " + outputDirectory.resolve("block-entities.csv"));
        System.out.println("- " + outputDirectory.resolve("block-dependencies.csv"));
        System.out.println("- " + outputDirectory.resolve("entity-classification.csv"));
        System.out.println("- " + outputDirectory.resolve("block-summary.csv"));

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

    private static void printAuditSummary(AuditReportResult result, Path outputDirectory) {
        System.out.println();
        System.out.println("Audited requires-analysis evidence:");
        System.out.println("Audited entities   : " + result.entityClassifications().size());
        var strictPairs = result.blockPairs().stream().filter(pair -> pair.eligibleForRequires()).toList();
        System.out.println("Direct relations examined: " + result.blockPairs().stream()
                .mapToInt(pair -> pair.occurrenceCount()).sum());
        System.out.println("Strict R relations       : " + strictPairs.stream()
                .mapToInt(pair -> pair.occurrenceCount()).sum());
        System.out.println("Strict R unit pairs      : " + strictPairs.size());
        System.out.println("Output files:");
        System.out.println("- " + outputDirectory.resolve("audited-entity-classification.csv"));
        System.out.println("- " + outputDirectory.resolve("requires_candidates.csv"));
        System.out.println("- " + outputDirectory.resolve("requires_candidate_evidence.csv"));
        System.out.println("- " + outputDirectory.resolve("requires_candidate_details.csv"));
        System.out.println("- " + outputDirectory.resolve("requires_exclusions.csv"));
        System.out.println("Requires relevance hypotheses:");
        Map<RequiresRelevance, Long> relevanceCounts = strictPairs.stream()
                .collect(Collectors.groupingBy(pair -> pair.requiresRelevance(), Collectors.counting()));
        for (RequiresRelevance relevance : RequiresRelevance.values()) {
            System.out.println("- " + relevance + ": " + relevanceCounts.getOrDefault(relevance, 0L));
        }
    }

    private static void printCompositionAwareDependencySummary(CompositionAwareDependencyResult result, Path outputDirectory) {
        System.out.println();
        System.out.println("Composition-aware dependency evidence:");
        System.out.println("Examined method calls : " + result.examinedMethodCalls());
        System.out.println("Wrapper delegations   : " + result.dependencies().size());
        System.out.println("Used for scoring      : false");
        System.out.println("Output files:");
        System.out.println("- " + outputDirectory.resolve("composition_dependencies.csv"));
        result.dependencies().stream()
                .limit(10)
                .forEach(dependency -> System.out.println("- " + dependency.dependencyId()
                        + " " + dependency.wrapperBaseName()
                        + " " + dependency.sourceSignature()
                        + " -> " + dependency.targetSignature()
                        + " suffixIgnored=" + dependency.ignoredWrapperSuffix()));
    }

    private static void printDisjunctiveImplementationEvidenceSummary(DisjunctiveImplementationEvidenceResult result, Path outputDirectory) {
        System.out.println();
        System.out.println("Disjunctive branch-completion implementation evidence:");
        System.out.println("Examined branch obligations: " + result.examinedBranchObligations());
        System.out.println("Candidates                 : " + result.candidates().size());
        System.out.println("Output files:");
        System.out.println("- " + outputDirectory.resolve("disjunctive_implementation_evidence.csv"));
        result.candidates().stream()
                .limit(10)
                .forEach(candidate -> System.out.printf(
                        "- %s %s -> %s method=%s location=%s%n",
                        candidate.candidateId(),
                        candidate.sourceSignature(),
                        String.join(" OR ", candidate.providerSignatureSet()),
                        candidate.evidenceMethod(),
                        candidate.evidenceLocation()
                ));
    }

    private static void printProviderReferenceEvidenceSummary(ProviderReferenceEvidenceResult result, Path outputDirectory) {
        System.out.println();
        System.out.println("Provider type-reference recall evidence:");
        System.out.println("Examined TYPE_REFERENCE relations: " + result.examinedTypeReferences());
        System.out.println("Recall evidence candidates     : " + result.evidence().size());
        System.out.println("Skipped unresolved references  : " + result.unresolvedReferences());
        System.out.println("Skipped external/non-provider  : " + result.externalReferences());
        System.out.println("Skipped common targets         : " + result.commonTargetReferences());
        System.out.println("Skipped non-subset signatures  : " + result.nonSubsetReferences());
        System.out.println("Output files:");
        System.out.println("- " + outputDirectory.resolve("provider_reference_evidence.csv"));
        result.evidence().stream()
                .limit(10)
                .forEach(evidence -> System.out.printf(
                        "- %s %s -> %s sourceSig=%s targetSig=%s line=%d%n",
                        evidence.evidenceId(),
                        evidence.sourceEntityName(),
                        evidence.targetEntityName(),
                        evidence.sourceSignature(),
                        evidence.targetSignature(),
                        evidence.sourceLine()
                ));
    }

    private static void printProviderDataFlowEvidenceSummary(ProviderDataFlowEvidenceResult result, Path outputDirectory) {
        System.out.println();
        System.out.println("Provider data-flow-lite recall evidence:");
        System.out.println("Examined relations          : " + result.examinedRelations());
        System.out.println("Recall evidence candidates : " + result.evidence().size());
        System.out.println("Skipped unresolved rels    : " + result.unresolvedRelations());
        System.out.println("Skipped unresolved provider: " + result.unresolvedProviders());
        System.out.println("Skipped common providers   : " + result.commonProviderTargets());
        System.out.println("Skipped non-subset rels    : " + result.nonSubsetRelations());
        System.out.println("Output files:");
        System.out.println("- " + outputDirectory.resolve("provider_dataflow_evidence.csv"));
        result.evidence().stream()
                .limit(10)
                .forEach(evidence -> System.out.printf(
                        "- %s %s %s -> %s sourceSig=%s providerSig=%s line=%d%n",
                        evidence.evidenceId(),
                        evidence.evidenceKind(),
                        evidence.sourceEntityName(),
                        evidence.providerEntityName(),
                        evidence.sourceSignature(),
                        evidence.providerSignature(),
                        evidence.sourceLine()
                ));
    }

    private static void printOperationProviderEvidenceSummary(OperationProviderEvidenceResult result, Path outputDirectory) {
        System.out.println();
        System.out.println("Operation-level provider recall evidence:");
        System.out.println("Input provider evidence : " + result.inputProviderEvidenceCount());
        System.out.println("Operation candidates    : " + result.operationCandidateCount());
        System.out.println("Output files:");
        System.out.println("- " + outputDirectory.resolve("operation_provider_evidence.csv"));
        result.evidence().stream()
                .limit(10)
                .forEach(evidence -> System.out.printf(
                        "- %s %s -> %s sourceSig=%s providerSig=%s support=%d%n",
                        evidence.evidenceId(),
                        evidence.operationKey(),
                        evidence.providerEntityName(),
                        evidence.sourceSignature(),
                        evidence.providerSignature(),
                        evidence.supportingRelationCount()
                ));
    }

    private static void printSignatureProviderBundleEvidenceSummary(SignatureProviderBundleEvidenceResult result, Path outputDirectory) {
        System.out.println();
        System.out.println("Signature-provider bundle recall evidence:");
        System.out.println("Input operation evidence: " + result.inputOperationEvidenceCount());
        System.out.println("Bundle candidates       : " + result.bundleCount());
        System.out.println("Output files:");
        System.out.println("- " + outputDirectory.resolve("signature_provider_bundle_evidence.csv"));
        result.evidence().stream()
                .sorted(Comparator.comparingInt(SignatureProviderBundleEvidence::supportingRelationCount).reversed()
                        .thenComparing(evidence -> evidence.sourceSignature().toString())
                        .thenComparing(evidence -> evidence.providerSignature().toString()))
                .limit(10)
                .forEach(evidence -> System.out.printf(
                        "- %s %s -> %s operations=%d providers=%d support=%d%n",
                        evidence.evidenceId(),
                        evidence.sourceSignature(),
                        evidence.providerSignature(),
                        evidence.operationCount(),
                        evidence.providerCount(),
                        evidence.supportingRelationCount()
                ));
    }

    private static void printImplementationEvidenceSummary(ImplementationEvidenceSummaryResult result, Path outputDirectory) {
        System.out.println();
        System.out.println("Concise implementation evidence summary:");
        System.out.println("Input bundles : " + result.inputBundleCount());
        System.out.println("Summary rows  : " + result.summaryCount());
        System.out.println("Output files:");
        System.out.println("- " + outputDirectory.resolve("implementation_evidence_summary.csv"));
        result.summaries().stream()
                .limit(10)
                .forEach(summary -> System.out.printf(
                        "- %s %s %s -> %s support=%d providers=%s tokens=%s%n",
                        summary.summaryId(),
                        summary.evidenceImportance(),
                        summary.sourceSignature(),
                        summary.providerSignature(),
                        summary.supportingRelationCount(),
                        summary.representativeProviders(),
                        summary.representativeTokens()
                ));
    }
}
