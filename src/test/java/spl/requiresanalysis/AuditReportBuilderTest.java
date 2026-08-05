package spl.requiresanalysis;

import org.junit.jupiter.api.Test;
import spl.ConditionalBlock;
import spl.ProductSignature;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelationType;
import spl.entity.ResolutionStatus;

import java.nio.file.Path;
import java.nio.file.Files;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditReportBuilderTest {
    private static final String SOURCE_BLOCK_ID = "asset3:IF:1-1";
    private static final String TARGET_BLOCK_ID = "asset7:IF:2-2";

    @TempDir
    Path tempDir;

    @Test
    void classifiesRequestAndGetFloorAsDomainModelAndOperation() {
        AuditReportResult result = buildResult(
                List.of(
                        entity("E1", JavaEntityType.CLASS, "Request",
                                "de.ovgu.featureide.examples.elevator.core.controller.Request", SOURCE_BLOCK_ID),
                        entity("E2", JavaEntityType.METHOD, "getFloor",
                                "de.ovgu.featureide.examples.elevator.core.controller.Request.getFloor", SOURCE_BLOCK_ID)
                ),
                List.of()
        );
        Map<String, AuditedEntityClassification> byName = bySimpleName(result);

        assertEquals(SemanticRole.DOMAIN_MODEL, byName.get("Request").semanticRole());
        assertEquals(ArchitecturalRole.CONTROLLER, byName.get("Request").architecturalRole());
        assertEquals(EvidenceLevel.HIGH, byName.get("Request").domainRelevance());
        assertEquals(SemanticRole.DOMAIN_OPERATION, byName.get("getFloor").semanticRole());
        assertEquals(EvidenceLevel.HIGH, byName.get("getFloor").domainRelevance());
    }

    @Test
    void classifiesDomainUiDialogAndGetterThroughContext() {
        AuditReportResult result = buildResult(
                List.of(
                        entity("E1", JavaEntityType.CLASS, "FloorChooseDialog",
                                "de.ovgu.featureide.examples.elevator.ui.FloorChooseDialog", SOURCE_BLOCK_ID),
                        entity("E2", JavaEntityType.METHOD, "getSelectedFloors",
                                "de.ovgu.featureide.examples.elevator.ui.FloorChooseDialog.getSelectedFloors", SOURCE_BLOCK_ID)
                ),
                List.of()
        );
        Map<String, AuditedEntityClassification> byName = bySimpleName(result);

        assertEquals(ArchitecturalRole.UI, byName.get("FloorChooseDialog").architecturalRole());
        assertEquals(SemanticRole.DOMAIN_SUPPORT, byName.get("FloorChooseDialog").semanticRole());
        assertTrue(byName.get("FloorChooseDialog").domainRelevance() == EvidenceLevel.MEDIUM
                || byName.get("FloorChooseDialog").domainRelevance() == EvidenceLevel.HIGH);
        assertEquals(SemanticRole.DOMAIN_OPERATION, byName.get("getSelectedFloors").semanticRole());
        assertEquals(EvidenceLevel.HIGH, byName.get("getSelectedFloors").domainRelevance());
    }

    @Test
    void classifiesResetUpAndResetDownThroughDeclaringTypeAsMediumUiDomainOperations() {
        AuditReportResult result = buildResult(
                List.of(
                        entity("E1", JavaEntityType.METHOD, "resetUp",
                                "de.ovgu.featureide.examples.elevator.ui.FloorComposite.resetUp", SOURCE_BLOCK_ID),
                        entity("E2", JavaEntityType.METHOD, "resetDown",
                                "de.ovgu.featureide.examples.elevator.ui.FloorComposite.resetDown", SOURCE_BLOCK_ID)
                ),
                List.of()
        );
        Map<String, AuditedEntityClassification> byName = bySimpleName(result);

        assertEquals(SemanticRole.DOMAIN_OPERATION, byName.get("resetUp").semanticRole());
        assertEquals(EvidenceLevel.MEDIUM, byName.get("resetUp").domainRelevance());
        assertEquals(SemanticRole.DOMAIN_OPERATION, byName.get("resetDown").semanticRole());
        assertEquals(EvidenceLevel.MEDIUM, byName.get("resetDown").domainRelevance());
    }

    @Test
    void separatesUiStateAndOrchestrationFacadeFromDomainCore() {
        AuditReportResult result = buildResult(
                List.of(
                        entity("E1", JavaEntityType.FIELD, "listInnerElevatorControls",
                                "de.ovgu.featureide.examples.elevator.ui.MainWindow.listInnerElevatorControls", SOURCE_BLOCK_ID),
                        entity("E2", JavaEntityType.FIELD, "sim",
                                "de.ovgu.featureide.examples.elevator.ui.MainWindow.sim", SOURCE_BLOCK_ID)
                ),
                List.of(dependency("D1", "E2", "E3", JavaRelationType.TYPE_REFERENCE, SOURCE_BLOCK_ID, TARGET_BLOCK_ID,
                        "SimulationUnit", ResolutionStatus.RESOLVED_INTERNAL))
        );
        Map<String, AuditedEntityClassification> byName = bySimpleName(result);

        assertEquals(SemanticRole.DOMAIN_STATE, byName.get("listInnerElevatorControls").semanticRole());
        assertEquals(ArchitecturalRole.UI, byName.get("listInnerElevatorControls").architecturalRole());
        assertEquals(EvidenceLevel.MEDIUM, byName.get("listInnerElevatorControls").domainRelevance());
        assertEquals(SemanticRole.DOMAIN_FACADE, byName.get("sim").semanticRole());
        assertEquals(ArchitecturalRole.ORCHESTRATION, byName.get("sim").architecturalRole());
        assertTrue(byName.get("sim").domainRelevance() == EvidenceLevel.MEDIUM
                || byName.get("sim").domainRelevance() == EvidenceLevel.UNKNOWN);
    }

    @Test
    void equalProductSetsDoNotProduceRequiresCandidates() {
        JavaEntity source = entity("E1", JavaEntityType.METHOD, "onRequestFinished",
                "de.ovgu.featureide.examples.elevator.ui.MainWindow.onRequestFinished", SOURCE_BLOCK_ID);
        JavaEntity target = entity("E2", JavaEntityType.METHOD, "resetUp",
                "de.ovgu.featureide.examples.elevator.ui.FloorComposite.resetUp", TARGET_BLOCK_ID);
        DependencyEvidence dependency = dependency("D1", source.entityId(), target.entityId(),
                JavaRelationType.METHOD_CALL, SOURCE_BLOCK_ID, TARGET_BLOCK_ID, "", ResolutionStatus.RESOLVED_INTERNAL);

        AuditReportResult result = buildResult(List.of(source, target), List.of(dependency));

        assertEquals(1, result.blockPairs().size());
        assertEquals(RequiresCandidateStatus.SAME_SIGNATURE_DIRECTED_DEPENDENCY,
                result.blockPairs().get(0).candidateStatus());
    }

    @Test
    void sameProductSetDifferentEntityPairsCreateSeparateCandidates() throws Exception {
        ConditionalBlock sourceBlock = block("asset1", ConditionalBlock.DirectiveType.IF, 10, 20,
                "Enterprise");
        ConditionalBlock targetBlock = block("asset2", ConditionalBlock.DirectiveType.IF, 30, 40,
                "Enterprise|Professional");
        String sourceBlockId = RequiresEvidenceBuilder.blockId(sourceBlock);
        String targetBlockId = RequiresEvidenceBuilder.blockId(targetBlock);
        JavaEntity sourceA = entity("S1", JavaEntityType.METHOD, "sourceA", "spl.Source.sourceA",
                Path.of("asset1"), 11, 12, sourceBlockId, "G1", "Enterprise");
        JavaEntity sourceB = entity("S2", JavaEntityType.METHOD, "sourceB", "spl.Source.sourceB",
                Path.of("asset1"), 13, 14, sourceBlockId, "G1", "Enterprise");
        JavaEntity targetA = entity("T1", JavaEntityType.METHOD, "targetA", "spl.Target.targetA",
                Path.of("asset2"), 31, 32, targetBlockId, "G2", "Enterprise|Professional");
        JavaEntity targetB = entity("T2", JavaEntityType.METHOD, "targetB", "spl.Target.targetB",
                Path.of("asset2"), 33, 34, targetBlockId, "G2", "Enterprise|Professional");
        RequiresEvidenceResult evidence = evidence(List.of(sourceBlock, targetBlock),
                List.of(sourceA, sourceB, targetA, targetB),
                List.of(
                        dependency("D1", sourceA.entityId(), targetA.entityId(), JavaRelationType.METHOD_CALL,
                                sourceBlockId, targetBlockId, Path.of("asset1"), 12, "G1", "G2",
                                "Enterprise", "Enterprise|Professional"),
                        dependency("D2", sourceB.entityId(), targetB.entityId(), JavaRelationType.METHOD_CALL,
                                sourceBlockId, targetBlockId, Path.of("asset1"), 14, "G1", "G2",
                                "Enterprise", "Enterprise|Professional")
                ));

        AuditReportResult result = new AuditReportBuilder().build(evidence);

        assertEquals(2, result.blockPairs().size());
        assertTrue(result.blockPairs().stream().allMatch(pair -> pair.occurrenceCount() == 1));
    }

    @Test
    void sameEntityPairRepeatedCallsCreateOneCandidateWithMultipleEvidenceRows() throws Exception {
        ConditionalBlock sourceBlock = block("asset1", ConditionalBlock.DirectiveType.IF, 10, 20,
                "Enterprise");
        ConditionalBlock targetBlock = block("asset2", ConditionalBlock.DirectiveType.IF, 30, 40,
                "Enterprise|Professional");
        String sourceBlockId = RequiresEvidenceBuilder.blockId(sourceBlock);
        String targetBlockId = RequiresEvidenceBuilder.blockId(targetBlock);
        JavaEntity source = entity("S1", JavaEntityType.METHOD, "source", "spl.Source.source",
                Path.of("asset1"), 11, 12, sourceBlockId, "G1", "Enterprise");
        JavaEntity target = entity("T1", JavaEntityType.METHOD, "target", "spl.Target.target",
                Path.of("asset2"), 31, 32, targetBlockId, "G2", "Enterprise|Professional");
        RequiresEvidenceResult evidence = evidence(List.of(sourceBlock, targetBlock), List.of(source, target),
                List.of(
                        dependency("D1", source.entityId(), target.entityId(), JavaRelationType.METHOD_CALL,
                                sourceBlockId, targetBlockId, Path.of("asset1"), 12, "G1", "G2",
                                "Enterprise", "Enterprise|Professional"),
                        dependency("D2", source.entityId(), target.entityId(), JavaRelationType.METHOD_CALL,
                                sourceBlockId, targetBlockId, Path.of("asset1"), 13, "G1", "G2",
                                "Enterprise", "Enterprise|Professional")
                ));

        AuditReportResult result = new AuditReportBuilder().build(evidence);

        assertEquals(1, result.blockPairs().size());
        assertEquals(2, result.blockPairs().get(0).occurrenceCount());
        assertEquals(2, result.blockPairs().get(0).evidenceRows().size());
        assertEquals("METHOD_CALL", result.blockPairs().get(0).dependencyKind());
    }

    @Test
    void sameAssetNestedSourceToOutermostTargetIsMandatoryTargetContext() throws Exception {
        AuditReportResult result = singleMethodCallCandidate(
                block("asset10", ConditionalBlock.DirectiveType.IF, 20, 25, 1, "Enterprise"),
                block("asset10", ConditionalBlock.DirectiveType.IF, 1, 100, 0, "Enterprise|Professional"),
                "Enterprise",
                "Enterprise|Professional");

        AuditedBlockPair candidate = result.blockPairs().get(0);
        assertEquals(RequiresCandidateStatus.INTRA_ASSET_MANDATORY_TARGET, candidate.candidateStatus());
        assertFalse(candidate.eligibleForRequires());
        assertTrue(candidate.sameAsset());
        assertFalse(candidate.sourceIsOutermost());
        assertTrue(candidate.targetIsOutermost());
    }

    @Test
    void sameAssetOutermostSourceToNestedTargetRequiresManualInspection() throws Exception {
        AuditReportResult result = singleMethodCallCandidate(
                block("asset10", ConditionalBlock.DirectiveType.IF, 1, 100, 0, "Enterprise"),
                block("asset10", ConditionalBlock.DirectiveType.IF, 20, 25, 1, "Enterprise|Professional"),
                "Enterprise",
                "Enterprise|Professional");

        AuditedBlockPair candidate = result.blockPairs().get(0);
        assertEquals(RequiresCandidateStatus.MANDATORY_SOURCE_TO_VARIABLE_TARGET, candidate.candidateStatus());
        assertFalse(candidate.eligibleForRequires());
        assertTrue(candidate.requiresManualInspection());
    }

    @Test
    void sameAssetOutermostToOutermostIsRootDependency() throws Exception {
        AuditReportResult result = singleMethodCallCandidate(
                block("asset10", ConditionalBlock.DirectiveType.IF, 1, 10, 0, "Enterprise"),
                block("asset10", ConditionalBlock.DirectiveType.IF, 20, 30, 0, "Enterprise|Professional"),
                "Enterprise",
                "Enterprise|Professional");

        AuditedBlockPair candidate = result.blockPairs().get(0);
        assertEquals(RequiresCandidateStatus.INTRA_ASSET_ROOT_DEPENDENCY, candidate.candidateStatus());
        assertFalse(candidate.eligibleForRequires());
    }

    @Test
    void crossAssetOutermostTargetIsNotExcludedForOutermostAlone() throws Exception {
        AuditReportResult result = singleMethodCallCandidate(
                block("assetA", ConditionalBlock.DirectiveType.IF, 20, 25, 1, "Enterprise"),
                block("assetB", ConditionalBlock.DirectiveType.IF, 1, 100, 0, "Enterprise|Professional"),
                "Enterprise",
                "Enterprise|Professional");

        AuditedBlockPair candidate = result.blockPairs().get(0);
        assertEquals(RequiresCandidateStatus.ELIGIBLE_REQUIRES_EVIDENCE, candidate.candidateStatus());
        assertTrue(candidate.eligibleForRequires());
        assertFalse(candidate.sameAsset());
        assertTrue(candidate.crossAsset());
        assertTrue(candidate.targetIsOutermost());
    }

    @Test
    void sourceSubsetOfTargetIsFilteredAsTautologicalEvenWithFeatureSpecificTarget() throws Exception {
        ConditionalBlock sourceBlock = block("asset10", ConditionalBlock.DirectiveType.IF, 105, 144,
                1, "Enterprise|Professional");
        ConditionalBlock broadTarget = block("asset10", ConditionalBlock.DirectiveType.IF, 8, 145,
                "Enterprise|HomeBasic|HomePremium|Professional|Ultimate");
        ConditionalBlock nestedTarget = block("asset10", ConditionalBlock.DirectiveType.IF, 77, 81,
                1, "Enterprise|Professional|Ultimate");
        ConditionalBlock universe = block("asset10", ConditionalBlock.DirectiveType.IF, 200, 200, "Starter");
        String sourceBlockId = RequiresEvidenceBuilder.blockId(sourceBlock);
        String broadTargetId = RequiresEvidenceBuilder.blockId(broadTarget);
        String nestedTargetId = RequiresEvidenceBuilder.blockId(nestedTarget);
        JavaEntity source = entity("S1", JavaEntityType.METHOD, "compareDirectional",
                "de.ovgu.featureide.examples.elevator.core.controller.Request.UpComparator.compareDirectional",
                sourceBlockId);
        JavaEntity getFloor = entity("T1", JavaEntityType.METHOD, "getFloor",
                "de.ovgu.featureide.examples.elevator.core.controller.Request.getFloor",
                broadTargetId);
        JavaEntity controller = entity("T2", JavaEntityType.FIELD, "controller",
                "de.ovgu.featureide.examples.elevator.core.controller.Request.RequestComparator.controller",
                nestedTargetId);
        JavaEntity getDirection = entity("T3", JavaEntityType.METHOD, "getDirection",
                "de.ovgu.featureide.examples.elevator.core.controller.Request.getDirection",
                nestedTargetId);
        List<DependencyEvidence> dependencies = List.of(
                dependency("D1", source.entityId(), getFloor.entityId(), JavaRelationType.METHOD_CALL,
                        sourceBlockId, broadTargetId, "", ResolutionStatus.RESOLVED_INTERNAL),
                dependency("D2", source.entityId(), controller.entityId(), JavaRelationType.FIELD_REFERENCE,
                        sourceBlockId, nestedTargetId, "", ResolutionStatus.RESOLVED_INTERNAL),
                dependency("D3", source.entityId(), getDirection.entityId(), JavaRelationType.METHOD_CALL,
                        sourceBlockId, nestedTargetId, "", ResolutionStatus.RESOLVED_INTERNAL)
        );
        RequiresEvidenceResult evidence = new RequiresEvidenceResult(
                List.of(sourceBlock, broadTarget, nestedTarget, universe),
                List.of(source, getFloor, controller, getDirection),
                dependencies,
                List.of(new EntityClassifier().classify(source), new EntityClassifier().classify(getFloor),
                        new EntityClassifier().classify(controller), new EntityClassifier().classify(getDirection)),
                List.of(),
                Map.of(sourceBlockId, "G5", broadTargetId, "G3", nestedTargetId, "G6")
        );

        AuditReportResult result = new AuditReportBuilder().build(evidence);
        assertTrue(result.blockPairs().stream()
                .anyMatch(pair -> pair.candidateStatus() == RequiresCandidateStatus.INTRA_ASSET_MANDATORY_TARGET
                        && pair.dependencyKind().equals("METHOD_CALL")));
    }

    @Test
    void commonTargetDoesNotProduceRequiresCandidate() throws Exception {
        ConditionalBlock sourceBlock = block("asset3", ConditionalBlock.DirectiveType.IF, 269, 272,
                "Enterprise|Professional");
        ConditionalBlock broadTarget = block("asset10", ConditionalBlock.DirectiveType.IF, 8, 145,
                "Enterprise|HomeBasic|HomePremium|Professional|Ultimate");
        ConditionalBlock nestedTarget = block("asset10", ConditionalBlock.DirectiveType.IF, 77, 81,
                "Enterprise|Professional|Ultimate");
        ConditionalBlock universe = block("asset10", ConditionalBlock.DirectiveType.IF, 200, 200, "Starter");
        String sourceBlockId = RequiresEvidenceBuilder.blockId(sourceBlock);
        String targetBlockId = RequiresEvidenceBuilder.blockId(broadTarget);
        JavaEntity source = entity("S1", JavaEntityType.METHOD, "actionPerformed",
                "de.ovgu.featureide.examples.elevator.ui.MainWindow.actionPerformed", sourceBlockId);
        JavaEntity request = entity("T1", JavaEntityType.CLASS, "Request",
                "de.ovgu.featureide.examples.elevator.core.controller.Request", targetBlockId);
        DependencyEvidence dependency = dependency("D1", source.entityId(), request.entityId(),
                JavaRelationType.CONSTRUCTOR_CALL, sourceBlockId, targetBlockId, "", ResolutionStatus.RESOLVED_INTERNAL);
        RequiresEvidenceResult evidence = new RequiresEvidenceResult(
                List.of(sourceBlock, broadTarget, nestedTarget, universe),
                List.of(source, request),
                List.of(dependency),
                List.of(new EntityClassifier().classify(source), new EntityClassifier().classify(request)),
                List.of(),
                Map.of(sourceBlockId, "G5", targetBlockId, "G3")
        );

        AuditReportResult result = new AuditReportBuilder().build(evidence);
        assertEquals(1, result.blockPairs().size());
        assertEquals(RequiresCandidateStatus.INVALID_DEPENDENCY_KIND, result.blockPairs().get(0).candidateStatus());
    }

    @Test
    void dependenciesInvolvingCommonBlocksDoNotProduceRequiresCandidates() throws Exception {
        ConditionalBlock commonBlock = block("asset1", ConditionalBlock.DirectiveType.IF, 1, 20,
                "Enterprise|Professional|Starter");
        ConditionalBlock variableBlock = block("asset2", ConditionalBlock.DirectiveType.IF, 5, 10,
                "Enterprise");
        ConditionalBlock universeContributor = block("asset3", ConditionalBlock.DirectiveType.IF, 1, 1,
                "Professional|Starter");
        String commonBlockId = RequiresEvidenceBuilder.blockId(commonBlock);
        String variableBlockId = RequiresEvidenceBuilder.blockId(variableBlock);
        JavaEntity source = entity("S1", JavaEntityType.METHOD, "run",
                "spl.Common.run", commonBlockId);
        JavaEntity target = entity("T1", JavaEntityType.METHOD, "feature",
                "spl.Feature.feature", variableBlockId);
        DependencyEvidence dependency = dependency("D1", source.entityId(), target.entityId(),
                JavaRelationType.METHOD_CALL, commonBlockId, variableBlockId, "", ResolutionStatus.RESOLVED_INTERNAL);
        RequiresEvidenceResult evidence = new RequiresEvidenceResult(
                List.of(commonBlock, variableBlock, universeContributor),
                List.of(source, target),
                List.of(dependency),
                List.of(new EntityClassifier().classify(source), new EntityClassifier().classify(target)),
                List.of(),
                Map.of(commonBlockId, "G1", variableBlockId, "G2")
        );

        AuditReportResult result = new AuditReportBuilder().build(evidence);

        assertEquals(1, result.blockPairs().size());
        assertEquals(RequiresCandidateStatus.SIGNATURE_INCONSISTENT, result.blockPairs().get(0).candidateStatus());
    }

    @Test
    void compactCandidateUsesPrimaryEvidenceRelationInsteadOfFirstAggregatedKind() throws Exception {
        ConditionalBlock sourceConstructorBlock = block("asset10", ConditionalBlock.DirectiveType.IF, 27, 31,
                1, "Enterprise|Professional");
        ConditionalBlock extendsSourceBlock = block("asset10", ConditionalBlock.DirectiveType.IF, 99, 102,
                1, "Enterprise|Professional");
        ConditionalBlock broadTargetBlock = block("asset10", ConditionalBlock.DirectiveType.IF, 8, 145,
                "Enterprise|HomeBasic|HomePremium|Professional|Ultimate");
        ConditionalBlock nestedTargetBlock = block("asset10", ConditionalBlock.DirectiveType.IF, 19, 20,
                1, "Enterprise|Professional");
        ConditionalBlock universeContributor = block("asset10", ConditionalBlock.DirectiveType.IF, 200, 200,
                "Starter");
        String sourceConstructorBlockId = RequiresEvidenceBuilder.blockId(sourceConstructorBlock);
        String extendsSourceBlockId = RequiresEvidenceBuilder.blockId(extendsSourceBlock);
        String broadTargetBlockId = RequiresEvidenceBuilder.blockId(broadTargetBlock);
        String nestedTargetBlockId = RequiresEvidenceBuilder.blockId(nestedTargetBlock);

        JavaEntity constructor = entity("E1", JavaEntityType.CONSTRUCTOR, "Request",
                "de.ovgu.featureide.examples.elevator.core.controller.Request.Request",
                Path.of("asset10"), 28, 31, sourceConstructorBlockId, "G5",
                "Enterprise|Professional");
        JavaEntity floor = entity("E2", JavaEntityType.FIELD, "floor",
                "de.ovgu.featureide.examples.elevator.core.controller.Request.floor",
                Path.of("asset10"), 18, 18, broadTargetBlockId, "G3",
                "Enterprise|HomeBasic|HomePremium|Professional|Ultimate");
        JavaEntity direction = entity("E3", JavaEntityType.FIELD, "direction",
                "de.ovgu.featureide.examples.elevator.core.controller.Request.direction",
                Path.of("asset10"), 20, 20, nestedTargetBlockId, "G5",
                "Enterprise|Professional");
        JavaEntity downComparator = entity("E4", JavaEntityType.CLASS, "DownComparator",
                "de.ovgu.featureide.examples.elevator.core.controller.Request.DownComparator",
                Path.of("asset10"), 100, 102, extendsSourceBlockId, "G5",
                "Enterprise|Professional");
        JavaEntity requestComparator = entity("E5", JavaEntityType.CLASS, "RequestComparator",
                "de.ovgu.featureide.examples.elevator.core.controller.Request.RequestComparator",
                Path.of("asset10"), 76, 98, broadTargetBlockId, "G3",
                "Enterprise|HomeBasic|HomePremium|Professional|Ultimate");
        List<DependencyEvidence> dependencies = List.of(
                dependency("D1", constructor.entityId(), floor.entityId(), JavaRelationType.FIELD_REFERENCE,
                        sourceConstructorBlockId, broadTargetBlockId, Path.of("asset10"), 29,
                        "G5", "G3", "Enterprise|Professional",
                        "Enterprise|HomeBasic|HomePremium|Professional|Ultimate"),
                dependency("D2", constructor.entityId(), direction.entityId(), JavaRelationType.FIELD_REFERENCE,
                        sourceConstructorBlockId, nestedTargetBlockId, Path.of("asset10"), 30,
                        "G5", "G5", "Enterprise|Professional", "Enterprise|Professional"),
                dependency("D3", downComparator.entityId(), requestComparator.entityId(), JavaRelationType.EXTENDS,
                        extendsSourceBlockId, broadTargetBlockId, Path.of("asset10"), 100,
                        "G5", "G3", "Enterprise|Professional",
                        "Enterprise|HomeBasic|HomePremium|Professional|Ultimate")
        );
        RequiresEvidenceResult evidence = new RequiresEvidenceResult(
                List.of(sourceConstructorBlock, extendsSourceBlock, broadTargetBlock, nestedTargetBlock,
                        universeContributor),
                List.of(constructor, floor, direction, downComparator, requestComparator),
                dependencies,
                List.of(new EntityClassifier().classify(constructor), new EntityClassifier().classify(floor),
                        new EntityClassifier().classify(direction), new EntityClassifier().classify(downComparator),
                        new EntityClassifier().classify(requestComparator)),
                List.of(),
                Map.of(sourceConstructorBlockId, "G5", extendsSourceBlockId, "G5", broadTargetBlockId, "G3",
                        nestedTargetBlockId, "G5")
        );

        AuditReportResult result = new AuditReportBuilder().build(evidence);
        new AuditReportExporter().export(result, tempDir);

        List<String> compactRows = Files.readAllLines(tempDir.resolve("requires_candidates.csv"));
        String candidate = compactRows.stream()
                .filter(row -> row.contains("Request.direction"))
                .findFirst()
                .orElseThrow();

        assertTrue(candidate.contains("Request.Request"));
        assertTrue(candidate.contains("Request.direction"));
        assertTrue(candidate.contains("FIELD_REFERENCE"));
        assertTrue(candidate.contains("INTRA_CLASS"));
        assertFalse(candidate.contains(",EXTENDS,"));
        assertFalse(candidate.contains("EXTENDS|FIELD_REFERENCE"));
    }

    private AuditReportResult buildResult(List<JavaEntity> entities, List<DependencyEvidence> dependencies) {
        ConditionalBlock sourceBlock = new ConditionalBlock(Path.of("asset3"), ConditionalBlock.DirectiveType.IF,
                1, 0, new ProductSignature("Enterprise|Professional"));
        ConditionalBlock targetBlock = new ConditionalBlock(Path.of("asset7"), ConditionalBlock.DirectiveType.IF,
                2, 0, new ProductSignature("Enterprise|Professional"));
        ConditionalBlock otherProductBlock = new ConditionalBlock(Path.of("asset9"), ConditionalBlock.DirectiveType.IF,
                3, 0, new ProductSignature("Starter"));
        List<EntityClassification> previous = entities.stream()
                .map(entity -> new EntityClassifier().classify(entity))
                .toList();
        return new AuditReportBuilder().build(new RequiresEvidenceResult(
                List.of(sourceBlock, targetBlock, otherProductBlock),
                entities,
                dependencies,
                previous,
                List.of(),
                Map.of(SOURCE_BLOCK_ID, "G1", TARGET_BLOCK_ID, "G1")
        ));
    }

    private RequiresEvidenceResult evidence(List<ConditionalBlock> blocks, List<JavaEntity> entities,
                                            List<DependencyEvidence> dependencies) {
        Map<String, String> blockGroups = blocks.stream()
                .collect(Collectors.toMap(RequiresEvidenceBuilder::blockId, block -> "G" + block.startLine()));
        return new RequiresEvidenceResult(
                blocks,
                entities,
                dependencies,
                entities.stream().map(entity -> new EntityClassifier().classify(entity)).toList(),
                List.of(),
                blockGroups
        );
    }

    private AuditReportResult singleMethodCallCandidate(ConditionalBlock sourceBlock, ConditionalBlock targetBlock,
                                                        String sourceSignature, String targetSignature) {
        String sourceBlockId = RequiresEvidenceBuilder.blockId(sourceBlock);
        String targetBlockId = RequiresEvidenceBuilder.blockId(targetBlock);
        JavaEntity source = entity("S1", JavaEntityType.METHOD, "source", "spl.Source.source",
                sourceBlock.filePath(), sourceBlock.startLine() + 1, sourceBlock.startLine() + 1,
                sourceBlockId, "GS", sourceSignature);
        JavaEntity target = entity("T1", JavaEntityType.METHOD, "target", "spl.Target.target",
                targetBlock.filePath(), targetBlock.startLine() + 1, targetBlock.startLine() + 1,
                targetBlockId, "GT", targetSignature);
        ConditionalBlock universeContributor;
        try {
            universeContributor = block("assetUniverse", ConditionalBlock.DirectiveType.IF, 1, 1, 0, "Starter");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        return new AuditReportBuilder().build(evidence(
                List.of(sourceBlock, targetBlock, universeContributor),
                List.of(source, target),
                List.of(dependency("D1", source.entityId(), target.entityId(), JavaRelationType.METHOD_CALL,
                        sourceBlockId, targetBlockId, sourceBlock.filePath(), source.startLine(),
                        "GS", "GT", sourceSignature, targetSignature))
        ));
    }

    private JavaEntity entity(String id, JavaEntityType type, String simpleName, String qualifiedName, String blockId) {
        return new JavaEntity(
                id,
                type,
                simpleName,
                qualifiedName,
                Path.of("asset3"),
                10,
                12,
                blockId,
                "G1",
                new ProductSignature("Enterprise|Professional"),
                List.of("Enterprise", "Professional"),
                ResolutionStatus.RESOLVED_INTERNAL
        );
    }

    private JavaEntity entity(String id, JavaEntityType type, String simpleName, String qualifiedName, Path file,
                              int startLine, int endLine, String blockId, String groupId, String signature) {
        return new JavaEntity(
                id,
                type,
                simpleName,
                qualifiedName,
                file,
                startLine,
                endLine,
                blockId,
                groupId,
                new ProductSignature(signature),
                List.of(signature.split("\\|")),
                ResolutionStatus.RESOLVED_INTERNAL
        );
    }

    private DependencyEvidence dependency(String id, String sourceEntityId, String targetEntityId,
                                          JavaRelationType type, String sourceBlockId, String targetBlockId,
                                          String targetText, ResolutionStatus status) {
        return new DependencyEvidence(
                id,
                sourceBlockId,
                targetBlockId,
                sourceEntityId,
                targetEntityId,
                targetText,
                type,
                Path.of("asset3"),
                10,
                "G1",
                "G1",
                "Enterprise|Professional",
                "Enterprise|Professional",
                EntityRole.UNKNOWN,
                EntityRole.UNKNOWN,
                0.75,
                0.5,
                RequiresRelevance.MEDIUM,
                DependencyExclusionReason.NONE,
                status,
                List.of("Enterprise", "Professional")
        );
    }

    private DependencyEvidence dependency(String id, String sourceEntityId, String targetEntityId,
                                          JavaRelationType type, String sourceBlockId, String targetBlockId,
                                          Path sourceFile, int sourceLine, String sourceGroupId, String targetGroupId,
                                          String sourceSignature, String targetSignature) {
        return new DependencyEvidence(
                id,
                sourceBlockId,
                targetBlockId,
                sourceEntityId,
                targetEntityId,
                "",
                type,
                sourceFile,
                sourceLine,
                sourceGroupId,
                targetGroupId,
                sourceSignature,
                targetSignature,
                EntityRole.DOMAIN_CORE,
                EntityRole.DOMAIN_CORE,
                0.75,
                0.8,
                RequiresRelevance.HIGH,
                DependencyExclusionReason.NONE,
                ResolutionStatus.RESOLVED_INTERNAL,
                List.of(sourceSignature.split("\\|"))
        );
    }

    private ConditionalBlock block(String file, ConditionalBlock.DirectiveType type, int start, int end,
                                   String signature) throws Exception {
        return block(file, type, start, end, 0, signature);
    }

    private ConditionalBlock block(String file, ConditionalBlock.DirectiveType type, int start, int end, int depth,
                                   String signature) throws Exception {
        ConditionalBlock block = new ConditionalBlock(Path.of(file), type, start, depth, new ProductSignature(signature));
        Method setEndLine = ConditionalBlock.class.getDeclaredMethod("setEndLine", int.class);
        setEndLine.setAccessible(true);
        setEndLine.invoke(block, end);
        return block;
    }

    private Map<String, AuditedEntityClassification> bySimpleName(AuditReportResult result) {
        return result.entityClassifications().stream()
                .collect(Collectors.toMap(classification -> {
                    String[] parts = classification.qualifiedName().split("\\.");
                    return parts[parts.length - 1];
                }, Function.identity()));
    }
}
