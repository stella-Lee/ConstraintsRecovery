package spl.requiresanalysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spl.ConditionalBlock;
import spl.ProductSignature;
import spl.dependency.DependencyEdge;
import spl.dependency.DependencyGraph;
import spl.dependency.UnresolvedDependencyRelation;
import spl.entity.EntityExtractionResult;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelationType;
import spl.entity.ResolutionStatus;
import spl.grouping.SignatureGroup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequiresEvidenceBuilderTest {
    @Test
    void classifiesDomainAndInfrastructureEntities() {
        EntityClassifier classifier = new EntityClassifier();

        EntityClassification elevator = classifier.classify(entity("E1", JavaEntityType.CLASS,
                "Elevator", "spl.Elevator", "B1"));
        EntityClassification logger = classifier.classify(entity("E2", JavaEntityType.FIELD,
                "logger", "spl.Logger", "B1"));

        assertEquals(EntityRole.DOMAIN_CORE, elevator.entityRole());
        assertEquals(EntityRole.INFRASTRUCTURE, logger.entityRole());
        assertTrue(elevator.domainScore() > logger.domainScore());
    }

    @Test
    void buildsBlockCenteredDependencyEvidenceAndExcludesTypeReferences() {
        ConditionalBlock sourceBlock = block("assets/elevator/asset1", ConditionalBlock.DirectiveType.IF,
                10, 30, "Enterprise");
        ConditionalBlock targetBlock = block("assets/elevator/asset2", ConditionalBlock.DirectiveType.IF,
                5, 20, "Enterprise");
        JavaEntity source = entity(RequiresEvidenceBuilder.blockId(sourceBlock), JavaEntityType.METHOD,
                "moveToFloor", "spl.Elevator.moveToFloor", RequiresEvidenceBuilder.blockId(sourceBlock));
        JavaEntity target = entity(RequiresEvidenceBuilder.blockId(targetBlock), JavaEntityType.METHOD,
                "requestFloor", "spl.Floor.requestFloor", RequiresEvidenceBuilder.blockId(targetBlock));

        DependencyEdge edge = new DependencyEdge(
                "D1",
                source.entityId(),
                target.entityId(),
                JavaRelationType.METHOD_CALL,
                source.sourceFile(),
                12,
                target.sourceFile(),
                source.containingBlockId(),
                "G1",
                new ProductSignature("Enterprise"),
                "G1",
                new ProductSignature("Enterprise"),
                true,
                List.of("Enterprise"),
                ResolutionStatus.RESOLVED_INTERNAL
        );
        UnresolvedDependencyRelation unresolved = new UnresolvedDependencyRelation(
                "U1",
                JavaRelationType.TYPE_REFERENCE,
                source.entityId(),
                "java.util.List",
                source.sourceFile(),
                13,
                source.containingBlockId(),
                "G1",
                new ProductSignature("Enterprise"),
                ResolutionStatus.RESOLVED_EXTERNAL,
                "external target",
                List.of("Enterprise")
        );

        RequiresEvidenceResult result = new RequiresEvidenceBuilder().build(
                List.of(new SignatureGroup("G1", new ProductSignature("Enterprise"), List.of(sourceBlock, targetBlock))),
                new EntityExtractionResult(List.of(source, target), List.of(), List.of(), 2, 2, 2),
                new DependencyGraph(List.of(), List.of(edge), List.of(unresolved))
        );

        assertEquals(2, result.blocks().size());
        assertEquals(2, result.entities().size());
        assertEquals(1, result.dependencies().size());
        assertTrue(result.dependencies().stream()
                .noneMatch(dependency -> dependency.relationType() == JavaRelationType.TYPE_REFERENCE));
        assertTrue(result.dependencies().stream()
                .anyMatch(dependency -> dependency.requiresRelevance() == RequiresRelevance.MEDIUM
                        || dependency.requiresRelevance() == RequiresRelevance.HIGH));
    }

    @Test
    void marksIntraBlockDependenciesAsLowRelevanceEvidence() {
        ConditionalBlock block = block("assets/elevator/asset1", ConditionalBlock.DirectiveType.IF,
                10, 30, "Enterprise");
        JavaEntity source = entity("E1", JavaEntityType.METHOD, "move", "spl.Elevator.move",
                RequiresEvidenceBuilder.blockId(block));
        JavaEntity target = entity("E2", JavaEntityType.FIELD, "floor", "spl.Elevator.floor",
                RequiresEvidenceBuilder.blockId(block));
        DependencyEdge edge = new DependencyEdge(
                "D1",
                source.entityId(),
                target.entityId(),
                JavaRelationType.FIELD_REFERENCE,
                source.sourceFile(),
                15,
                target.sourceFile(),
                source.containingBlockId(),
                "G1",
                new ProductSignature("Enterprise"),
                "G1",
                new ProductSignature("Enterprise"),
                false,
                List.of("Enterprise"),
                ResolutionStatus.RESOLVED_INTERNAL
        );

        RequiresEvidenceResult result = new RequiresEvidenceBuilder().build(
                List.of(new SignatureGroup("G1", new ProductSignature("Enterprise"), List.of(block))),
                new EntityExtractionResult(List.of(source, target), List.of(), List.of(), 1, 1, 1),
                new DependencyGraph(List.of(), List.of(edge), List.of())
        );

        DependencyEvidence dependency = result.dependencies().get(0);
        assertEquals(DependencyExclusionReason.INTRA_BLOCK, dependency.exclusionReason());
        assertEquals(RequiresRelevance.LOW, dependency.requiresRelevance());
    }

    @Test
    void treatsSameDeclaringTypeFieldReferenceAcrossBlocksAsInternalImplementationReference() {
        ConditionalBlock fieldBlock = block("assets/elevator/asset1", ConditionalBlock.DirectiveType.IF,
                10, 12, "Enterprise");
        ConditionalBlock useBlock = block("assets/elevator/asset1", ConditionalBlock.DirectiveType.IF,
                20, 30, "Enterprise");
        JavaEntity field = entity("E1", JavaEntityType.FIELD, "floor", "spl.Elevator.floor",
                RequiresEvidenceBuilder.blockId(fieldBlock));
        JavaEntity method = entity("E2", JavaEntityType.METHOD, "move", "spl.Elevator.move",
                RequiresEvidenceBuilder.blockId(useBlock));
        DependencyEdge edge = new DependencyEdge(
                "D1",
                method.entityId(),
                field.entityId(),
                JavaRelationType.FIELD_REFERENCE,
                method.sourceFile(),
                24,
                field.sourceFile(),
                method.containingBlockId(),
                "G1",
                new ProductSignature("Enterprise"),
                "G1",
                new ProductSignature("Enterprise"),
                false,
                List.of("Enterprise"),
                ResolutionStatus.RESOLVED_INTERNAL
        );

        RequiresEvidenceResult result = new RequiresEvidenceBuilder().build(
                List.of(new SignatureGroup("G1", new ProductSignature("Enterprise"), List.of(fieldBlock, useBlock))),
                new EntityExtractionResult(List.of(field, method), List.of(), List.of(), 1, 1, 1),
                new DependencyGraph(List.of(), List.of(edge), List.of())
        );

        DependencyEvidence dependency = result.dependencies().get(0);
        assertEquals(DependencyExclusionReason.INTRA_BLOCK, dependency.exclusionReason());
        assertEquals(RequiresRelevance.LOW, dependency.requiresRelevance());
        assertEquals(RequiresEvidenceBuilder.blockId(fieldBlock), dependency.targetBlockId());
        assertEquals("G1", dependency.targetGroupId());
        assertEquals("Enterprise", dependency.targetSignature());
        AuditReportResult audit = new AuditReportBuilder().build(result);
        assertEquals(1, audit.blockPairs().size());
        assertEquals(RequiresCandidateStatus.COMMON_TARGET_DEPENDENCY, audit.blockPairs().get(0).candidateStatus());
    }

    @Test
    void exportsRequestedCsvArtifacts(@TempDir Path tempDir) throws Exception {
        ConditionalBlock block = block("assets/elevator/asset1", ConditionalBlock.DirectiveType.IF,
                1, 8, "Starter");
        JavaEntity entity = entity("E1", JavaEntityType.CLASS, "Elevator", "spl.Elevator",
                RequiresEvidenceBuilder.blockId(block));
        RequiresEvidenceResult result = new RequiresEvidenceBuilder().build(
                List.of(new SignatureGroup("G1", new ProductSignature("Starter"), List.of(block))),
                new EntityExtractionResult(List.of(entity), List.of(), List.of(), 1, 1, 1),
                new DependencyGraph(List.of(), List.of(), List.of())
        );

        new RequiresEvidenceExporter().export(result, tempDir);

        assertTrue(Files.exists(tempDir.resolve("blocks.csv")));
        assertTrue(Files.exists(tempDir.resolve("block-entities.csv")));
        assertTrue(Files.exists(tempDir.resolve("block-dependencies.csv")));
        assertTrue(Files.exists(tempDir.resolve("entity-classification.csv")));
        assertTrue(Files.exists(tempDir.resolve("block-summary.csv")));
        assertTrue(Files.readString(tempDir.resolve("entity-classification.csv")).contains("entity_role"));
    }

    private ConditionalBlock block(String file, ConditionalBlock.DirectiveType type, int start, int end,
                                   String signature) {
        return new ConditionalBlock(Path.of(file), type, start, 1, new ProductSignature(signature));
    }

    private JavaEntity entity(String entityId, JavaEntityType type, String simpleName, String qualifiedName,
                              String containingBlockId) {
        return new JavaEntity(
                entityId,
                type,
                simpleName,
                qualifiedName,
                Path.of("assets/elevator/asset1"),
                12,
                20,
                containingBlockId,
                "G1",
                new ProductSignature("Enterprise"),
                List.of("Enterprise"),
                ResolutionStatus.RESOLVED_INTERNAL
        );
    }
}
