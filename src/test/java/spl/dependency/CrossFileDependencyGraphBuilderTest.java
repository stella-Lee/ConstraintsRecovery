package spl.dependency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spl.ConditionalBlock;
import spl.ProductSignature;
import spl.ProductSignatureExtractor;
import spl.entity.EntityExtractionResult;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityExtractor;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelation;
import spl.entity.JavaRelationType;
import spl.entity.ParseFailure;
import spl.entity.ResolutionStatus;
import spl.grouping.SignatureBlockGrouper;
import spl.grouping.SignatureGroup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossFileDependencyGraphBuilderTest {
    @TempDir
    Path tempDir;

    private final CrossFileDependencyGraphBuilder builder = new CrossFileDependencyGraphBuilder();

    @Test
    void buildsSameFileMethodCallEdge() {
        EntityExtractionResult result = result(
                List.of(entity("E1", "a/A.java", JavaEntityType.METHOD, "caller", "A.caller", "G1"),
                        entity("E2", "a/A.java", JavaEntityType.METHOD, "target", "A.target", "G1")),
                List.of(relation("R1", "E1", "E2", "target", JavaRelationType.METHOD_CALL, "a/A.java", 10, "G1"))
        );

        DependencyGraph graph = builder.build(result);

        assertEquals(2, graph.nodes().size());
        assertEquals(1, graph.edges().size());
        assertFalse(graph.edges().get(0).crossFile());
    }

    @Test
    void buildsCrossFileMethodCallEdge() {
        EntityExtractionResult result = result(
                List.of(entity("E1", "a/A.java", JavaEntityType.METHOD, "caller", "A.caller", "G1"),
                        entity("E2", "b/B.java", JavaEntityType.METHOD, "target", "B.target", "G2")),
                List.of(relation("R1", "E1", "E2", "target", JavaRelationType.METHOD_CALL, "a/A.java", 10, "G1"))
        );

        DependencyGraph graph = builder.build(result);

        assertEquals(1, graph.edges().size());
        assertTrue(graph.edges().get(0).crossFile());
        assertEquals("G1", graph.edges().get(0).sourceGroupId());
        assertEquals("G2", graph.edges().get(0).targetGroupId());
    }

    @Test
    void supportsConstructorTypeFieldExtendsAndImplementsEdges() {
        JavaEntity source = entity("E1", "a/A.java", JavaEntityType.CLASS, "A", "A", "G1");
        List<JavaEntity> entities = List.of(
                source,
                entity("E2", "a/B.java", JavaEntityType.CONSTRUCTOR, "B", "B.B", "G2"),
                entity("E3", "a/C.java", JavaEntityType.CLASS, "C", "C", "G3"),
                entity("E4", "a/A.java", JavaEntityType.FIELD, "field", "A.field", "G1"),
                entity("E5", "a/Base.java", JavaEntityType.CLASS, "Base", "Base", "G4"),
                entity("E6", "a/Api.java", JavaEntityType.INTERFACE, "Api", "Api", "G5")
        );
        List<JavaRelation> relations = List.of(
                relation("R1", "E1", "E2", "B", JavaRelationType.CONSTRUCTOR_CALL, "a/A.java", 4, "G1"),
                relation("R2", "E1", "E3", "C", JavaRelationType.TYPE_REFERENCE, "a/A.java", 5, "G1"),
                relation("R3", "E1", "E4", "field", JavaRelationType.FIELD_REFERENCE, "a/A.java", 6, "G1"),
                relation("R4", "E1", "E5", "Base", JavaRelationType.EXTENDS, "a/A.java", 1, "G1"),
                relation("R5", "E1", "E6", "Api", JavaRelationType.IMPLEMENTS, "a/A.java", 1, "G1")
        );

        DependencyGraph graph = builder.build(result(entities, relations));

        assertEquals(5, graph.edges().size());
        assertEquals(5, graph.edges().stream().map(DependencyEdge::relationType).distinct().count());
    }

    @Test
    void unresolvedAndExternalTargetsDoNotBecomeEdges() {
        JavaEntity source = entity("E1", "a/A.java", JavaEntityType.METHOD, "caller", "A.caller", "G1");
        JavaRelation unresolved = relation("R1", "E1", null, "missing", JavaRelationType.METHOD_CALL, "a/A.java", 10, "G1");
        JavaRelation external = new JavaRelation(
                "R2", "E1", null, "java.lang.String", JavaRelationType.TYPE_REFERENCE,
                Path.of("a/A.java"), 11, "B1", "G1", new ProductSignature("Enterprise"),
                List.of("Enterprise"), ResolutionStatus.RESOLVED_EXTERNAL
        );

        DependencyGraph graph = builder.build(result(List.of(source), List.of(unresolved, external)));

        assertTrue(graph.edges().isEmpty());
        assertEquals(2, graph.unresolvedRelations().size());
        assertTrue(graph.unresolvedRelations().stream().anyMatch(relation -> relation.failureReason().equals("external target")));
    }

    @Test
    void constructorCallCannotResolveToInterface() {
        JavaEntity source = entity("E1", "a/A.java", JavaEntityType.METHOD, "caller", "A.caller", "G1");
        JavaEntity api = entity("E2", "a/Api.java", JavaEntityType.INTERFACE, "Api", "Api", "G1");
        JavaRelation badConstructor = relation("R1", "E1", "E2", "Api",
                JavaRelationType.CONSTRUCTOR_CALL, "a/A.java", 8, "G1");

        DependencyGraph graph = builder.build(result(List.of(source, api), List.of(badConstructor)));

        assertTrue(graph.edges().isEmpty());
        assertEquals("invalid constructor target type", graph.unresolvedRelations().get(0).failureReason());
    }

    @Test
    void suppressesDuplicateRelationsAndAccumulatesProducts() {
        List<JavaEntity> entities = List.of(
                entity("E1", "a/A.java", JavaEntityType.METHOD, "caller", "A.caller", "G1"),
                entity("E2", "a/A.java", JavaEntityType.METHOD, "target", "A.target", "G1")
        );
        JavaRelation first = relation("R1", "E1", "E2", "target", JavaRelationType.METHOD_CALL, "a/A.java", 10, "G1",
                List.of("Enterprise"));
        JavaRelation second = relation("R2", "E1", "E2", "target", JavaRelationType.METHOD_CALL, "a/A.java", 10, "G1",
                List.of("Professional"));

        DependencyGraph graph = builder.build(result(entities, List.of(first, second)));

        assertEquals(1, graph.edges().size());
        assertEquals(List.of("Enterprise", "Professional"), graph.edges().get(0).observedProducts());
    }

    @Test
    void createsEmptyGraphAndKeepsIsolatedEntities() {
        DependencyGraph empty = builder.build(result(List.of(), List.of()));
        assertTrue(empty.nodes().isEmpty());
        assertTrue(empty.edges().isEmpty());

        DependencyGraph isolated = builder.build(result(
                List.of(entity("E1", "a/A.java", JavaEntityType.CLASS, "A", "A", "G1")),
                List.of()
        ));
        assertEquals(1, isolated.nodes().size());
        assertTrue(isolated.edges().isEmpty());
    }

    @Test
    void deterministicEdgeIdsAndOrdering() {
        List<JavaEntity> entities = List.of(
                entity("E1", "b/B.java", JavaEntityType.METHOD, "b", "B.b", "G1"),
                entity("E2", "a/A.java", JavaEntityType.METHOD, "a", "A.a", "G1"),
                entity("E3", "a/A.java", JavaEntityType.METHOD, "c", "A.c", "G1")
        );
        List<JavaRelation> relations = List.of(
                relation("R1", "E1", "E2", "a", JavaRelationType.METHOD_CALL, "b/B.java", 20, "G1"),
                relation("R2", "E2", "E3", "c", JavaRelationType.METHOD_CALL, "a/A.java", 10, "G1")
        );

        DependencyGraph graph = builder.build(result(entities, relations));

        assertEquals(List.of("D1", "D2"), graph.edges().stream().map(DependencyEdge::edgeId).toList());
        assertEquals(Path.of("a/A.java"), graph.edges().get(0).sourceFile());
    }

    @Test
    void integrationWithTwoProductMarkedFiles() throws Exception {
        Path caller = tempDir.resolve("Caller.java");
        Files.writeString(caller, """
                //#if Enterprise|Professional
                class Caller {
                    void run() {
                        new Target().call();
                    }
                }
                //#endif
                """);
        Path target = tempDir.resolve("Target.java");
        Files.writeString(target, """
                //#if Enterprise|Professional
                class Target {
                    void call() {
                    }
                }
                //#endif
                """);

        ProductSignatureExtractor extractor = new ProductSignatureExtractor();
        List<ConditionalBlock> blocks = extractor.extractFromFiles(List.of(caller, target));
        List<SignatureGroup> groups = new SignatureBlockGrouper().group(blocks);
        EntityExtractionResult extraction = new JavaEntityExtractor().extract(groups, List.of(caller, target));
        DependencyGraph graph = builder.build(extraction);

        assertTrue(graph.nodes().size() >= 4);
        assertTrue(graph.edges().stream().anyMatch(DependencyEdge::crossFile));
    }

    private static EntityExtractionResult result(List<JavaEntity> entities, List<JavaRelation> relations) {
        return new EntityExtractionResult(entities, relations, List.of(), 1, 1, 1);
    }

    private static JavaEntity entity(String id, String file, JavaEntityType type, String simpleName,
                                     String qualifiedName, String groupId) {
        return new JavaEntity(
                id,
                type,
                simpleName,
                qualifiedName,
                Path.of(file),
                1,
                2,
                "B1",
                groupId,
                new ProductSignature("Enterprise"),
                List.of("Enterprise"),
                ResolutionStatus.RESOLVED_INTERNAL
        );
    }

    private static JavaRelation relation(String id, String sourceId, String targetId, String targetText,
                                         JavaRelationType type, String file, int line, String groupId) {
        return relation(id, sourceId, targetId, targetText, type, file, line, groupId, List.of("Enterprise"));
    }

    private static JavaRelation relation(String id, String sourceId, String targetId, String targetText,
                                         JavaRelationType type, String file, int line, String groupId,
                                         List<String> products) {
        return new JavaRelation(
                id,
                sourceId,
                targetId,
                targetText,
                type,
                Path.of(file),
                line,
                "B1",
                groupId,
                new ProductSignature("Enterprise"),
                products,
                targetId == null ? ResolutionStatus.UNRESOLVED : ResolutionStatus.RESOLVED_INTERNAL
        );
    }
}
