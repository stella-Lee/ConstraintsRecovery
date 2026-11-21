package spl.requiresanalysis;

import org.junit.jupiter.api.Test;
import spl.ConditionalBlock;
import spl.ProductSignature;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelationType;
import spl.entity.ResolutionStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CompositionAwareDependencyDetectorTest {
    @Test
    void detectsWrappeeDelegationWithoutUsingSuffixForScoring() {
        JavaEntity source = entity("E1", "hook390", "pkg.INCompressor.hook390");
        JavaEntity target = entity("E2", "hook390__wrappee__Derivative_Verifier_INCompressor",
                "pkg.INCompressor.hook390__wrappee__Derivative_Verifier_INCompressor");
        DependencyEvidence dependency = dependency("D1", source.entityId(), target.entityId(), JavaRelationType.METHOD_CALL);

        CompositionAwareDependencyResult result = new CompositionAwareDependencyDetector().detect(result(
                List.of(source, target),
                List.of(dependency)
        ));

        assertEquals(1, result.dependencies().size());
        CompositionAwareDependency composition = result.dependencies().get(0);
        assertEquals("VARIABILITY_WRAPPER_DELEGATION", composition.evidenceType());
        assertEquals("hook390", composition.wrapperBaseName());
        assertEquals("Derivative_Verifier_INCompressor", composition.ignoredWrapperSuffix());
        assertFalse(composition.usedForRequiresScoring());
        assertEquals(List.of(
                "WRAPPEE_SUFFIX_NOT_INTERPRETED_AS_FEATURE_NAME",
                "COMPOSITION_AWARE_EVIDENCE_ONLY",
                "NOT_USED_FOR_REQUIRES_SCORING"
        ), composition.cautionReasons());
    }

    @Test
    void ignoresOrdinaryMethodCalls() {
        JavaEntity source = entity("E1", "hook390", "pkg.INCompressor.hook390");
        JavaEntity target = entity("E2", "compress", "pkg.INCompressor.compress");
        DependencyEvidence dependency = dependency("D1", source.entityId(), target.entityId(), JavaRelationType.METHOD_CALL);

        CompositionAwareDependencyResult result = new CompositionAwareDependencyDetector().detect(result(
                List.of(source, target),
                List.of(dependency)
        ));

        assertEquals(0, result.dependencies().size());
    }

    @Test
    void ignoresWrappeeSuffixWhenBaseNameDoesNotMatchSourceMethod() {
        JavaEntity source = entity("E1", "other", "pkg.INCompressor.other");
        JavaEntity target = entity("E2", "hook390__wrappee__Derivative_Verifier_INCompressor",
                "pkg.INCompressor.hook390__wrappee__Derivative_Verifier_INCompressor");
        DependencyEvidence dependency = dependency("D1", source.entityId(), target.entityId(), JavaRelationType.METHOD_CALL);

        CompositionAwareDependencyResult result = new CompositionAwareDependencyDetector().detect(result(
                List.of(source, target),
                List.of(dependency)
        ));

        assertEquals(0, result.dependencies().size());
    }

    private RequiresEvidenceResult result(List<JavaEntity> entities, List<DependencyEvidence> dependencies) {
        return new RequiresEvidenceResult(
                List.of(block()),
                entities,
                dependencies,
                List.of(),
                List.of(),
                Map.of("asset:IF:1-10", "G1")
        );
    }

    private ConditionalBlock block() {
        return new ConditionalBlock(Path.of("asset"), ConditionalBlock.DirectiveType.IF, 1, 0,
                new ProductSignature("P1|P2"));
    }

    private JavaEntity entity(String id, String simpleName, String qualifiedName) {
        return new JavaEntity(
                id,
                JavaEntityType.METHOD,
                simpleName,
                qualifiedName,
                Path.of("asset"),
                1,
                2,
                "asset:IF:1-10",
                "G1",
                new ProductSignature("P1|P2"),
                List.of("P1", "P2"),
                ResolutionStatus.RESOLVED_INTERNAL
        );
    }

    private DependencyEvidence dependency(String id, String sourceEntityId, String targetEntityId,
                                          JavaRelationType relationType) {
        return new DependencyEvidence(
                id,
                "asset:IF:1-10",
                "asset:IF:1-10",
                sourceEntityId,
                targetEntityId,
                "",
                relationType,
                Path.of("asset"),
                5,
                "G1",
                "G1",
                "P1|P2",
                "P1|P2",
                EntityRole.DOMAIN_SUPPORT,
                EntityRole.DOMAIN_SUPPORT,
                1.0,
                1.0,
                RequiresRelevance.LOW,
                DependencyExclusionReason.NONE,
                ResolutionStatus.RESOLVED_INTERNAL,
                List.of("P1", "P2")
        );
    }
}
