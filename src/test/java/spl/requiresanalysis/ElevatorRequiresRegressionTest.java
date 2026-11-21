package spl.requiresanalysis;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spl.AssetFileScanner;
import spl.ProductSignatureExtractor;
import spl.dependency.CrossFileDependencyGraphBuilder;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityExtractor;
import spl.entity.JavaRelation;
import spl.entity.JavaRelationType;
import spl.grouping.SignatureBlockGrouper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static spl.entity.JavaRelationType.CONSTRUCTOR_CALL;
import static spl.entity.JavaRelationType.FIELD_REFERENCE;

class ElevatorRequiresRegressionTest {
    private static List<JavaRelation> relations;
    private static Map<String, JavaEntity> declarations;
    private static List<AuditedBlockPair> pairs;
    private static ProviderDataFlowEvidenceResult expansion;

    @BeforeAll
    static void analyzeElevator() throws Exception {
        Path elevator = Path.of("assets/elevator");
        var groups = new SignatureBlockGrouper().group(new ProductSignatureExtractor().extract(elevator));
        var extracted = new JavaEntityExtractor().extract(groups, new AssetFileScanner().scan(elevator));
        relations = extracted.relations();
        declarations = extracted.entities().stream().collect(Collectors.toMap(JavaEntity::entityId, Function.identity()));
        var graph = new CrossFileDependencyGraphBuilder().build(extracted);
        pairs = new AuditReportBuilder().build(new RequiresEvidenceBuilder().build(groups, extracted, graph)).blockPairs();
        expansion = new ProviderDataFlowEvidenceDetector().detect(groups, extracted);
    }

    @Test
    void comparesAgainstTheDeclaredControllerFieldButResolvesSuperToItsConstructor() {
        Set<Integer> comparisonLines = Set.of(112, 114, 115, 133, 135, 136);
        assertEquals(comparisonLines, ofKind(FIELD_REFERENCE).stream()
                .filter(relation -> at(relation, "asset10", comparisonLines) && targetAt(relation, "asset10", 78))
                .map(JavaRelation::sourceLine).collect(Collectors.toSet()));
        assertEquals(1, pairs.stream().filter(AuditedBlockPair::eligibleForRequires)
                .filter(pair -> pair.evidenceRows().stream().filter(row -> row.targetLine() == 78)
                        .map(RequiresCandidateEvidenceRow::sourceLine).collect(Collectors.toSet())
                        .containsAll(comparisonLines)).count());
        assertTrue(ofKind(FIELD_REFERENCE).stream().noneMatch(relation -> at(relation, "asset10", Set.of(102, 125))));
        assertEquals(Set.of(102, 125), ofKind(CONSTRUCTOR_CALL).stream()
                .filter(relation -> at(relation, "asset10", Set.of(102, 125)) && targetAt(relation, "asset10", 79))
                .map(JavaRelation::sourceLine).collect(Collectors.toSet()));
    }

    @Test
    void excludesEqualSignatureConstructorAndQueueDependenciesWithoutMergingDeclarations() {
        assertRequestConstructorUse("asset3", 298);
        assertRequestConstructorUse("asset7", 241);
        assertRequestConstructorUse("asset7", 245);
        assertEqualSignatureUse("asset1", 153, "asset1", 38, FIELD_REFERENCE);
        assertEqualSignatureUse("asset1", 153, "asset1", 39, FIELD_REFERENCE);
        assertEqualSignatureUse("asset1", 156, "asset1", 37, FIELD_REFERENCE);
        assertEqualSignatureUse("asset1", 156, "asset1", 39, FIELD_REFERENCE);
    }

    @Test
    void keepsLiftedProvidersAndSingleProductUsesInExpansionOnly() {
        var strict = pairs.stream().filter(AuditedBlockPair::eligibleForRequires).toList();
        assertTrue(strict.stream().allMatch(pair -> pair.sourceProductSet().contains("|")
                && pair.targetProductSet().contains("|")));
        assertTrue(strict.stream().noneMatch(pair -> pair.dependencyKinds().contains("FIELD_ACCESS_LIFTED_TO_TYPE")));
        var triggerUses = expansion.evidence().stream()
                .filter(row -> row.sourceFile().endsWith("asset5") && Set.of(67, 81, 89).contains(row.sourceLine()))
                .filter(row -> row.relationType().equals("FIELD_REFERENCE")
                        && row.intermediateEntityName().endsWith(".triggerListener")
                        && row.providerEntityName().endsWith(".ITriggerListener"))
                .toList();
        assertEquals(Set.of(67, 81, 89), triggerUses.stream()
                .map(ProviderDataFlowEvidence::sourceLine).collect(Collectors.toSet()));
        assertTrue(triggerUses.stream().anyMatch(row -> row.sourceSignature().productIds().size() == 1));

        var initializers = expansion.evidence().stream().filter(row -> row.sourceEntityType().equals("FIELD")).toList();
        assertFalse(initializers.isEmpty());
        assertTrue(initializers.stream().allMatch(row -> row.cautionReasons().contains("FIELD_INITIALIZER")));
        var operations = new OperationProviderEvidenceDetector().detect(expansion);
        assertEquals(expansion.evidence().size() - initializers.size(), operations.evidence().stream()
                .mapToInt(OperationProviderEvidence::supportingRelationCount).sum());
    }

    private static void assertRequestConstructorUse(String source, int line) {
        assertEqualSignatureUse(source, line, "asset10", 28, CONSTRUCTOR_CALL);
        assertTrue(ofKind(CONSTRUCTOR_CALL).stream().noneMatch(relation ->
                at(relation, source, Set.of(line)) && targetAt(relation, "asset10", 24)));
    }

    private static void assertEqualSignatureUse(String source, int line, String target, int declaration,
                                               JavaRelationType kind) {
        assertTrue(ofKind(kind).stream().anyMatch(relation ->
                at(relation, source, Set.of(line)) && targetAt(relation, target, declaration)));
        assertTrue(pairs.stream()
                .filter(pair -> pair.candidateStatus() == RequiresCandidateStatus.SAME_SIGNATURE_DIRECTED_DEPENDENCY)
                .filter(pair -> pair.sourceProductSet().equals("Enterprise|Professional")
                        && pair.targetProductSet().equals("Enterprise|Professional"))
                .flatMap(pair -> pair.evidenceRows().stream())
                .anyMatch(row -> Path.of(row.sourceFile()).endsWith(source) && row.sourceLine() == line
                        && Path.of(row.targetFile()).endsWith(target) && row.targetLine() == declaration
                        && row.dependencyKind().equals(kind.name())));
    }

    private static List<JavaRelation> ofKind(JavaRelationType kind) {
        return relations.stream().filter(relation -> relation.relationType() == kind).toList();
    }

    private static boolean at(JavaRelation relation, String asset, Set<Integer> lines) {
        return relation.sourceFile().endsWith(asset) && lines.contains(relation.sourceLine());
    }

    private static boolean targetAt(JavaRelation relation, String asset, int line) {
        JavaEntity target = declarations.get(relation.targetEntityId());
        return target != null && target.sourceFile().endsWith(asset) && target.startLine() == line;
    }
}
