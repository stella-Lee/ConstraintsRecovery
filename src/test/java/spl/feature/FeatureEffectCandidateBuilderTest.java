package spl.feature;

import org.junit.jupiter.api.Test;
import spl.ConditionalBlock;
import spl.ProductSignature;
import spl.dependency.DependencyEdge;
import spl.dependency.DependencyGraph;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelationType;
import spl.entity.ResolutionStatus;
import spl.grouping.SignatureGroup;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureEffectCandidateBuilderTest {
    private final FeatureEffectCandidateBuilder builder = new FeatureEffectCandidateBuilder();

    @Test
    void oneSignatureGroupContainingOneConnectedComponent() {
        SignatureGroup group = group("G1", block("a/A.java", "G1", 1));
        JavaEntity caller = entity("E1", JavaEntityType.METHOD, "A.call", "a/A.java", 2, "G1", group);
        JavaEntity target = entity("E2", JavaEntityType.METHOD, "A.target", "a/A.java", 4, "G1", group);
        DependencyGraph graph = graph(edge("D1", caller, target, JavaRelationType.METHOD_CALL));

        FeatureEffectCandidateResult result = builder.build(List.of(group), List.of(caller, target), graph);

        assertEquals(1, result.candidates().size());
        assertEquals("FEC-G1-01", result.candidates().get(0).candidateId());
        assertEquals(2, result.candidates().get(0).memberEntities().size());
        assertEquals(1, result.candidates().get(0).internalEdges().size());
    }

    @Test
    void oneSignatureGroupContainingTwoDisconnectedComponents() {
        SignatureGroup group = group("G1", block("a/A.java", "G1", 1));
        JavaEntity first = entity("E1", JavaEntityType.METHOD, "A.first", "a/A.java", 2, "G1", group);
        JavaEntity second = entity("E2", JavaEntityType.METHOD, "A.second", "a/A.java", 8, "G1", group);

        FeatureEffectCandidateResult result = builder.build(List.of(group), List.of(first, second), graph());

        assertEquals(2, result.candidates().size());
        assertTrue(result.candidates().stream()
                .allMatch(candidate -> candidate.classificationStatus() == CandidateClassificationStatus.ISOLATED_ENTITY));
    }

    @Test
    void connectsSameSignatureEntitiesByImplementationRelationTypesOnly() {
        SignatureGroup group = group("G1", block("a/A.java", "G1", 1));
        JavaEntity classEntity = entity("E1", JavaEntityType.CLASS, "A", "a/A.java", 1, "G1", group);
        JavaEntity field = entity("E2", JavaEntityType.FIELD, "A.value", "a/A.java", 2, "G1", group);
        JavaEntity base = entity("E3", JavaEntityType.CLASS, "Base", "b/Base.java", 1, "G1", group);
        DependencyGraph graph = graph(
                edge("D1", classEntity, field, JavaRelationType.FIELD_REFERENCE),
                edge("D2", classEntity, base, JavaRelationType.EXTENDS)
        );

        FeatureEffectCandidateResult result = builder.build(List.of(group), List.of(classEntity, field, base), graph);

        assertEquals(2, result.candidates().size());
        assertTrue(result.candidates().stream()
                .anyMatch(candidate -> candidate.memberEntities().size() == 2
                        && candidate.internalEdges().stream()
                        .allMatch(edge -> edge.relationType() == JavaRelationType.FIELD_REFERENCE)));
        assertTrue(result.candidates().stream()
                .anyMatch(candidate -> candidate.memberEntities().size() == 1
                        && candidate.memberEntities().get(0).entityId().equals(base.entityId())));
    }

    @Test
    void declarationContainmentConnectsClassMembersOnlyAsComponentRelation() {
        SignatureGroup group = group("G1", block("a/A.java", "G1", 1));
        JavaEntity clazz = entity("E1", JavaEntityType.CLASS, "A", "a/A.java", 1, "G1", group);
        JavaEntity method = entity("E2", JavaEntityType.METHOD, "A.run", "a/A.java", 2, "G1", group);

        FeatureEffectCandidateResult result = builder.build(List.of(group), List.of(clazz, method), graph());

        assertEquals(1, result.candidates().size());
        assertTrue(result.candidates().get(0).internalEdges().isEmpty());
    }

    @Test
    void blockContainingNoEntityBecomesBlockOnlyCandidate() {
        SignatureGroup group = group("G1", block("a/A.java", "G1", 1));

        FeatureEffectCandidateResult result = builder.build(List.of(group), List.of(), graph());

        assertEquals(1, result.candidates().size());
        assertEquals(CandidateClassificationStatus.BLOCK_ONLY, result.candidates().get(0).classificationStatus());
        assertEquals(1, result.candidates().get(0).memberBlocks().size());
        assertTrue(result.unassignedBlocks().isEmpty());
    }

    @Test
    void oneBlockCanBeAssociatedWithMultipleDisconnectedCandidates() {
        ConditionalBlock block = block("a/A.java", "G1", 1);
        SignatureGroup group = group("G1", block);
        JavaEntity first = entity("E1", JavaEntityType.METHOD, "A.first", "a/A.java", 1, "G1", group);
        JavaEntity second = entity("E2", JavaEntityType.METHOD, "A.second", "a/A.java", 1, "G1", group);

        FeatureEffectCandidateResult result = builder.build(List.of(group), List.of(first, second), graph());

        assertEquals(2, result.candidates().size());
        assertTrue(result.candidates().stream().allMatch(candidate -> candidate.memberBlocks().size() == 1));
    }

    @Test
    void crossSignatureEdgeDoesNotMergeCandidatesButCreatesDependency() {
        SignatureGroup firstGroup = group("G1", block("a/A.java", "G1", 1));
        SignatureGroup secondGroup = group("G2", block("b/B.java", "G2", 1));
        JavaEntity first = entity("E1", JavaEntityType.METHOD, "A.run", "a/A.java", 1, "G1", firstGroup);
        JavaEntity second = entity("E2", JavaEntityType.METHOD, "B.run", "b/B.java", 1, "G2", secondGroup);

        FeatureEffectCandidateResult result = builder.build(List.of(firstGroup, secondGroup), List.of(first, second),
                graph(edge("D1", first, second, JavaRelationType.METHOD_CALL)));

        assertEquals(2, result.candidates().size());
        assertTrue(result.candidateDependencies().stream()
                .anyMatch(dependency -> dependency.dependencyScope() == CandidateDependencyScope.CROSS_SIGNATURE));
    }

    @Test
    void unresolvedAndExternalRelationsDoNotMergeCandidates() {
        SignatureGroup group = group("G1", block("a/A.java", "G1", 1));
        JavaEntity first = entity("E1", JavaEntityType.METHOD, "A.first", "a/A.java", 1, "G1", group);
        JavaEntity second = entity("E2", JavaEntityType.METHOD, "A.second", "a/A.java", 2, "G1", group);

        FeatureEffectCandidateResult result = builder.build(List.of(group), List.of(first, second),
                new DependencyGraph(List.of(), List.of(), List.of()));

        assertEquals(2, result.candidates().size());
    }

    @Test
    void deterministicCandidateIdsOrderingAndDuplicateEdgesDoNotChangeMembership() {
        SignatureGroup group = group("G1", block("a/A.java", "G1", 1));
        JavaEntity first = entity("E1", JavaEntityType.METHOD, "A.first", "a/A.java", 1, "G1", group);
        JavaEntity second = entity("E2", JavaEntityType.METHOD, "A.second", "a/A.java", 2, "G1", group);

        FeatureEffectCandidateResult result = builder.build(List.of(group), List.of(second, first),
                graph(edge("D1", first, second, JavaRelationType.METHOD_CALL),
                        edge("D2", first, second, JavaRelationType.METHOD_CALL)));

        assertEquals(List.of("FEC-G1-01"), result.candidates().stream()
                .map(FeatureEffectCandidate::candidateId)
                .toList());
        assertEquals(2, result.candidates().get(0).memberEntities().size());
    }

    @Test
    void emptySignatureGroupAndEmptyDependencyGraphAreHandled() {
        SignatureGroup group = new SignatureGroup("G1", new ProductSignature("Enterprise"), List.of());

        FeatureEffectCandidateResult result = builder.build(List.of(group), List.of(), graph());

        assertTrue(result.candidates().isEmpty());
        assertTrue(result.candidateDependencies().isEmpty());
        assertTrue(result.unassignedBlocks().isEmpty());
    }

    private static DependencyGraph graph(DependencyEdge... edges) {
        return new DependencyGraph(List.of(), List.of(edges), List.of());
    }

    private static SignatureGroup group(String groupId, ConditionalBlock block) {
        return new SignatureGroup(groupId, new ProductSignature("Enterprise"), List.of(block));
    }

    private static ConditionalBlock block(String file, String groupId, int startLine) {
        return new ConditionalBlock(Path.of(file), ConditionalBlock.DirectiveType.IF, startLine, 0,
                new ProductSignature("Enterprise"));
    }

    private static JavaEntity entity(String id, JavaEntityType type, String qualifiedName, String file, int line,
                                     String groupId, SignatureGroup group) {
        ConditionalBlock block = group.blocks().isEmpty() ? block(file, groupId, line) : group.blocks().get(0);
        String simpleName = qualifiedName.contains(".")
                ? qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
                : qualifiedName;
        return new JavaEntity(
                id,
                type,
                simpleName,
                qualifiedName,
                Path.of(file),
                line,
                line,
                blockId(block),
                groupId,
                group.canonicalSignature(),
                List.of("Enterprise"),
                ResolutionStatus.RESOLVED_INTERNAL
        );
    }

    private static DependencyEdge edge(String id, JavaEntity source, JavaEntity target, JavaRelationType type) {
        return new DependencyEdge(
                id,
                source.entityId(),
                target.entityId(),
                type,
                source.sourceFile(),
                source.startLine(),
                target.sourceFile(),
                source.containingBlockId(),
                source.signatureGroupId(),
                source.effectiveProductSignature(),
                target.signatureGroupId(),
                target.effectiveProductSignature(),
                !source.sourceFile().equals(target.sourceFile()),
                List.of("Enterprise"),
                ResolutionStatus.RESOLVED_INTERNAL
        );
    }

    private static String blockId(ConditionalBlock block) {
        return block.filePath() + ":" + block.directiveType().name() + ":" + block.startLine() + "-" + block.endLine();
    }
}
