package spl.feature;

import org.junit.jupiter.api.Test;
import spl.ConditionalBlock;
import spl.ProductSignature;
import spl.dependency.DependencyEdge;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelationType;
import spl.entity.ResolutionStatus;
import spl.grouping.SignatureGroup;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticFeatureEffectAggregatorTest {
    @Test
    void componentsWithStrongLexicalAndDependencyEvidenceAreMerged() {
        SignatureGroup group = group("G1", "Enterprise|Professional");
        JavaEntity serviceState = entity("E1", "ServiceState", "ServiceState", "a/Service.java", "G1", group);
        JavaEntity serviceButton = entity("E2", "ServiceButton", "ServiceButton", "a/Service.java", "G1", group);
        DependencyEdge edge = edge("D1", serviceState, serviceButton, JavaRelationType.METHOD_CALL);
        FeatureEffectCandidate first = component("FEC-G1-01", group, List.of(serviceState), List.of(), List.of(edge));
        FeatureEffectCandidate second = component("FEC-G1-02", group, List.of(serviceButton), List.of(edge), List.of());

        SemanticAggregationResult result = aggregate(List.of(first, second), List.of(group));

        assertEquals(1, result.candidates().size());
        assertEquals(List.of("FEC-G1-01", "FEC-G1-02"), result.candidates().get(0).structuralComponentIds());
        assertTrue(result.mergeDecisions().stream().anyMatch(decision -> decision.decision() == MergeDecision.MERGED));
    }

    @Test
    void componentsWithOnlySameSignatureAreNotMerged() {
        SignatureGroup group = group("G1", "Enterprise");
        FeatureEffectCandidate first = component("FEC-G1-01", group,
                List.of(entity("E1", "Door", "Door", "a/Door.java", "G1", group)), List.of(), List.of());
        FeatureEffectCandidate second = component("FEC-G1-02", group,
                List.of(entity("E2", "Queue", "Queue", "b/Queue.java", "G1", group)), List.of(), List.of());

        SemanticAggregationResult result = aggregate(List.of(first, second), List.of(group));

        assertEquals(2, result.candidates().size());
        assertTrue(result.mergeDecisions().stream().allMatch(decision -> decision.decision() == MergeDecision.NOT_MERGED));
    }

    @Test
    void componentsSharingOnlyTechnicalTokenAreNotMerged() {
        SignatureGroup group = group("G1", "Enterprise");
        FeatureEffectCandidate first = component("FEC-G1-01", group,
                List.of(entity("E1", "DoorManager", "DoorManager", "a/Door.java", "G1", group)), List.of(), List.of());
        FeatureEffectCandidate second = component("FEC-G1-02", group,
                List.of(entity("E2", "QueueManager", "QueueManager", "b/Queue.java", "G1", group)), List.of(), List.of());

        SemanticAggregationResult result = aggregate(List.of(first, second), List.of(group));

        assertEquals(2, result.candidates().size());
    }

    @Test
    void directDependencyPlusDomainTokenOverlapCanMergeIsolatedComponents() {
        SignatureGroup group = group("G1", "Enterprise");
        JavaEntity permissionModel = entity("E1", "FloorPermission", "FloorPermission", "a/Model.java", "G1", group);
        JavaEntity permissionView = entity("E2", "PermissionButton", "PermissionButton", "b/View.java", "G1", group);
        DependencyEdge edge = edge("D1", permissionModel, permissionView, JavaRelationType.FIELD_REFERENCE);
        FeatureEffectCandidate first = component("FEC-G1-01", group, List.of(permissionModel), List.of(), List.of(edge));
        FeatureEffectCandidate second = component("FEC-G1-02", group, List.of(permissionView), List.of(edge), List.of());

        SemanticAggregationResult result = aggregate(List.of(first, second), List.of(group));

        assertEquals(1, result.candidates().size());
        assertTrue(result.candidates().get(0).representativeTokens().contains("permission"));
    }

    @Test
    void componentsFromDifferentSignaturesAreNeverMerged() {
        SignatureGroup firstGroup = group("G1", "Enterprise");
        SignatureGroup secondGroup = group("G2", "Professional");
        FeatureEffectCandidate first = component("FEC-G1-01", firstGroup,
                List.of(entity("E1", "ServiceState", "ServiceState", "a/Service.java", "G1", firstGroup)), List.of(), List.of());
        FeatureEffectCandidate second = component("FEC-G2-01", secondGroup,
                List.of(entity("E2", "ServiceButton", "ServiceButton", "a/Service.java", "G2", secondGroup)), List.of(), List.of());

        SemanticAggregationResult result = aggregate(List.of(first, second), List.of(firstGroup, secondGroup));

        assertEquals(2, result.candidates().size());
    }

    @Test
    void commonalityIsClassifiedSeparately() {
        SignatureGroup common = group("G1", "Enterprise|Professional");
        SignatureGroup variable = group("G2", "Enterprise");
        FeatureEffectCandidate component = component("FEC-G1-01", common,
                List.of(entity("E1", "CommonControl", "CommonControl", "a/Common.java", "G1", common)), List.of(), List.of());

        SemanticAggregationResult result = aggregate(List.of(component), List.of(common, variable));

        assertEquals(CommonalityClassification.FULL_COMMONALITY,
                result.candidates().get(0).commonalityClassification());
    }

    @Test
    void candidateLabelGeneratedFromRepeatedDomainTokens() {
        SignatureGroup group = group("G1", "Enterprise");
        FeatureEffectCandidate component = component("FEC-G1-01", group,
                List.of(entity("E1", "RequestDirectionQueue", "RequestDirectionQueue", "a/Request.java", "G1", group)),
                List.of(), List.of());

        SemanticAggregationResult result = aggregate(List.of(component), List.of(group));

        assertFalse(result.candidates().get(0).suggestedLabel().isBlank());
        assertTrue(result.candidates().get(0).representativeTokens().contains("request"));
    }

    @Test
    void configurableThresholdsCanPreventMerge() {
        SignatureGroup group = group("G1", "Enterprise");
        JavaEntity firstEntity = entity("E1", "ServiceState", "ServiceState", "a/Service.java", "G1", group);
        JavaEntity secondEntity = entity("E2", "ServiceButton", "ServiceButton", "a/Service.java", "G1", group);
        DependencyEdge edge = edge("D1", firstEntity, secondEntity, JavaRelationType.METHOD_CALL);
        FeatureEffectCandidate first = component("FEC-G1-01", group, List.of(firstEntity), List.of(), List.of(edge));
        FeatureEffectCandidate second = component("FEC-G1-02", group, List.of(secondEntity), List.of(edge), List.of());
        SemanticAggregationConfig strict = new SemanticAggregationConfig(0.35, 0.20, 0.20, 0.15, 0.10,
                0.95, 2, 0.95, SemanticAggregationConfig.defaults().stopWords(), false, false, false, true, true);

        SemanticAggregationResult result = new SemanticFeatureEffectAggregator(strict)
                .aggregate(new FeatureEffectCandidateResult(List.of(first, second), List.of(), List.of()), List.of(group));

        assertEquals(2, result.candidates().size());
    }

    @Test
    void deterministicCandidateIdsLabelsAndOrdering() {
        SignatureGroup group = group("G1", "Enterprise");
        FeatureEffectCandidate second = component("FEC-G1-02", group,
                List.of(entity("E2", "RequestQueue", "RequestQueue", "b/Request.java", "G1", group)), List.of(), List.of());
        FeatureEffectCandidate first = component("FEC-G1-01", group,
                List.of(entity("E1", "DoorControl", "DoorControl", "a/Door.java", "G1", group)), List.of(), List.of());

        SemanticAggregationResult result = aggregate(List.of(second, first), List.of(group));

        assertEquals(List.of("AFEC-G1-01", "AFEC-G1-02"),
                result.candidates().stream().map(AggregatedFeatureEffectCandidate::candidateId).toList());
        assertEquals(result.candidates().stream().map(AggregatedFeatureEffectCandidate::suggestedLabel).toList(),
                result.candidates().stream().map(AggregatedFeatureEffectCandidate::suggestedLabel).toList());
    }

    @Test
    void emptyInputProducesEmptyResult() {
        SemanticAggregationResult result = aggregate(List.of(), List.of());

        assertTrue(result.candidates().isEmpty());
        assertTrue(result.similarities().isEmpty());
        assertTrue(result.mergeDecisions().isEmpty());
    }

    @Test
    void standardAnnotationsAndCommentsDoNotInfluenceTokensOrLabels() {
        SignatureGroup group = group("G1", "Enterprise");
        FeatureEffectCandidate component = component("FEC-G1-01", group,
                List.of(entity("E1", "OverrideDeprecatedDoor", "OverrideDeprecatedDoor", "a/Door.java", "G1", group)),
                List.of(), List.of());

        SemanticAggregationResult result = aggregate(List.of(component), List.of(group));

        ComponentEvidence evidence = result.evidenceByComponentId().get("FEC-G1-01");
        assertFalse(evidence.normalizedTokens().contains("override"));
        assertFalse(evidence.normalizedTokens().contains("deprecated"));
        assertTrue(evidence.tokenEvidence().stream()
                .noneMatch(token -> token.provenance() == TokenProvenance.COMMENT));
    }

    @Test
    void representativeTokensRecordSourceEntityAndProvenance() {
        SignatureGroup group = group("G1", "Enterprise");
        FeatureEffectCandidate component = component("FEC-G1-01", group,
                List.of(entity("E1", "FloorPermission", "FloorPermission", "a/Floor.java", "G1", group)),
                List.of(), List.of());

        SemanticAggregationResult result = aggregate(List.of(component), List.of(group));

        assertTrue(result.evidenceByComponentId().get("FEC-G1-01").tokenEvidence().stream()
                .anyMatch(token -> token.token().equals("floor")
                        && token.provenance() == TokenProvenance.TYPE_NAME
                        && token.sourceEntityId().equals("E1")));
    }

    private static SemanticAggregationResult aggregate(List<FeatureEffectCandidate> components,
                                                       List<SignatureGroup> groups) {
        return new SemanticFeatureEffectAggregator().aggregate(
                new FeatureEffectCandidateResult(components, List.of(), List.of()),
                groups
        );
    }

    private static FeatureEffectCandidate component(String id, SignatureGroup group, List<JavaEntity> entities,
                                                    List<DependencyEdge> incoming, List<DependencyEdge> outgoing) {
        return new FeatureEffectCandidate(
                id,
                group.groupId(),
                group.canonicalSignature(),
                entities,
                group.blocks(),
                List.of(),
                incoming,
                outgoing,
                entities.stream().map(JavaEntity::sourceFile).distinct().toList(),
                List.of("Enterprise"),
                entities.size() == 1 ? CandidateClassificationStatus.ISOLATED_ENTITY
                        : CandidateClassificationStatus.STRUCTURALLY_CONNECTED,
                Map.of(),
                Map.of(),
                entities.size(),
                0,
                entities.size() == 1 ? 1 : 0
        );
    }

    private static SignatureGroup group(String id, String signature) {
        return new SignatureGroup(id, new ProductSignature(signature),
                List.of(new ConditionalBlock(Path.of("a/Asset.java"), ConditionalBlock.DirectiveType.IF, 1, 0,
                        new ProductSignature(signature))));
    }

    private static JavaEntity entity(String id, String simpleName, String qualifiedName, String file,
                                     String groupId, SignatureGroup group) {
        return new JavaEntity(
                id,
                JavaEntityType.CLASS,
                simpleName,
                qualifiedName,
                Path.of(file),
                2,
                4,
                group.blocks().get(0).filePath() + ":IF:1-1",
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
}
