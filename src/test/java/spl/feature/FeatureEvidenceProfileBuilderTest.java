package spl.feature;

import org.junit.jupiter.api.Test;
import spl.ProductSignature;
import spl.dependency.DependencyEdge;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelationType;
import spl.entity.ResolutionStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureEvidenceProfileBuilderTest {
    @Test
    void buildsProfileWithRankedEntitiesTokensAndDependencies() {
        JavaEntity service = entity("E1", JavaEntityType.CLASS, "ServiceController");
        JavaEntity method = entity("E2", JavaEntityType.METHOD, "ServiceController.setService");
        DependencyEdge edge = edge(service, method);
        AggregatedFeatureEffectCandidate candidate = new AggregatedFeatureEffectCandidate(
                "AFEC-G1-01",
                "G1",
                new ProductSignature("Enterprise"),
                CommonalityClassification.VARIABLE_SIGNATURE,
                List.of("FEC-G1-01"),
                List.of(service, method),
                Set.of("block-1"),
                List.of(Path.of("a/Service.java")),
                List.of(edge),
                List.of(),
                List.of(),
                List.of("service", "control"),
                List.of(),
                "Service control implementation cluster",
                EvidenceConfidence.HIGH,
                CandidateConfidence.HIGH,
                List.of()
        );
        ComponentEvidence evidence = new ComponentEvidence(
                "FEC-G1-01",
                Map.of("service", 3, "control", 1),
                List.of("service", "control"),
                Set.of("Service", "Control"),
                Set.of("service", "control"),
                Set.of("demo"),
                Set.of("ServiceController"),
                Set.of(Path.of("a/Service.java")),
                Set.of("block-1"),
                Set.of("E2"),
                Set.of("E2"),
                Set.of(),
                Set.of(),
                List.of(new TokenEvidence("service", TokenProvenance.METHOD_NAME, "E2"))
        );

        FeatureEvidenceProfileResult result = new FeatureEvidenceProfileBuilder().build(
                new SemanticAggregationResult(List.of(candidate), Map.of("FEC-G1-01", evidence),
                        List.of(), List.of(), List.of())
        );

        FeatureEvidenceProfile profile = result.profiles().get(0);
        assertEquals("AFEC-G1-01", profile.componentId());
        assertEquals(2, profile.entityCount());
        assertEquals(1, profile.internalEdgeCount());
        assertEquals("service", profile.tokenFrequencies().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow()
                .getKey());
        assertTrue(profile.implementationConcept().contains("Service"));
        assertTrue(profile.representativeEntities().get(0).importanceScore() > 0);
    }

    private static JavaEntity entity(String id, JavaEntityType type, String qualifiedName) {
        return new JavaEntity(id, type, qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1),
                qualifiedName, Path.of("a/Service.java"), 10, 12, "block-1", "G1",
                new ProductSignature("Enterprise"), List.of("Enterprise"), ResolutionStatus.RESOLVED_INTERNAL);
    }

    private static DependencyEdge edge(JavaEntity source, JavaEntity target) {
        return new DependencyEdge("D1", source.entityId(), target.entityId(), JavaRelationType.METHOD_CALL,
                source.sourceFile(), source.startLine(), target.sourceFile(), source.containingBlockId(),
                source.signatureGroupId(), source.effectiveProductSignature(), target.signatureGroupId(),
                target.effectiveProductSignature(), false, List.of("Enterprise"), ResolutionStatus.RESOLVED_INTERNAL);
    }
}
