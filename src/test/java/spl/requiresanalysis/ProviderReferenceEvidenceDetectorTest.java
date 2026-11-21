package spl.requiresanalysis;

import org.junit.jupiter.api.Test;
import spl.ConditionalBlock;
import spl.ProductSignature;
import spl.entity.EntityExtractionResult;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelation;
import spl.entity.JavaRelationType;
import spl.entity.ResolutionStatus;
import spl.grouping.SignatureGroup;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderReferenceEvidenceDetectorTest {
    private static final Path FILE = Path.of("assets/sample/asset1");

    @Test
    void detectsInternalVariabilityProviderTypeReference() {
        JavaEntity source = entity("E1", JavaEntityType.METHOD, "Client.run", "B1", "G1",
                new ProductSignature("P1"), 20);
        JavaEntity target = entity("E2", JavaEntityType.CLASS, "Provider", "B2", "G2",
                new ProductSignature("P1|P2"), 5);
        JavaRelation relation = relation("R1", source.entityId(), target.entityId(), "Provider");

        ProviderReferenceEvidenceResult result = new ProviderReferenceEvidenceDetector().detect(
                groups("P1|P2|P3"),
                new EntityExtractionResult(List.of(source, target), List.of(relation), List.of(), 1, 1, 1)
        );

        assertEquals(1, result.examinedTypeReferences());
        assertEquals(1, result.evidence().size());
        assertEquals("INTERNAL_VARIABILITY_PROVIDER_TYPE_REFERENCE", result.evidence().get(0).evidenceType());
        assertEquals("SOURCE_SUBSET_OF_TARGET", result.evidence().get(0).productSetRelation());
    }

    @Test
    void skipsCommonProviderTargets() {
        JavaEntity source = entity("E1", JavaEntityType.METHOD, "Client.run", "B1", "G1",
                new ProductSignature("P1"), 20);
        JavaEntity target = entity("E2", JavaEntityType.CLASS, "CommonProvider", "B2", "G2",
                new ProductSignature("P1|P2|P3"), 5);
        JavaRelation relation = relation("R1", source.entityId(), target.entityId(), "CommonProvider");

        ProviderReferenceEvidenceResult result = new ProviderReferenceEvidenceDetector().detect(
                groups("P1|P2|P3"),
                new EntityExtractionResult(List.of(source, target), List.of(relation), List.of(), 1, 1, 1)
        );

        assertEquals(0, result.evidence().size());
        assertEquals(1, result.commonTargetReferences());
    }

    @Test
    void skipsEqualSignatures() {
        JavaEntity source = entity("E1", JavaEntityType.METHOD, "Client.run", "B1", "G1",
                new ProductSignature("P1|P2"), 20);
        JavaEntity target = entity("E2", JavaEntityType.CLASS, "Provider", "B2", "G2",
                new ProductSignature("P1|P2"), 5);
        JavaRelation relation = relation("R1", source.entityId(), target.entityId(), "Provider");

        ProviderReferenceEvidenceResult result = new ProviderReferenceEvidenceDetector().detect(
                groups("P1|P2|P3"),
                new EntityExtractionResult(List.of(source, target), List.of(relation), List.of(), 1, 1, 1)
        );

        assertEquals(0, result.evidence().size());
        assertEquals(1, result.nonSubsetReferences());
    }

    private static List<SignatureGroup> groups(String universe) {
        ConditionalBlock block = new ConditionalBlock(FILE, ConditionalBlock.DirectiveType.IF, 1, 0,
                new ProductSignature(universe));
        return List.of(new SignatureGroup("G0", new ProductSignature(universe), List.of(block)));
    }

    private static JavaEntity entity(String id, JavaEntityType type, String name, String blockId, String groupId,
                                     ProductSignature signature, int line) {
        return new JavaEntity(id, type, name.substring(name.lastIndexOf('.') + 1), name, FILE, line, line, blockId,
                groupId, signature, signature.productIds(), ResolutionStatus.RESOLVED_INTERNAL);
    }

    private static JavaRelation relation(String id, String sourceId, String targetId, String targetText) {
        return new JavaRelation(id, sourceId, targetId, targetText, JavaRelationType.TYPE_REFERENCE, FILE, 20,
                "B1", "G1", new ProductSignature("P1"), List.of("P1"), ResolutionStatus.RESOLVED_INTERNAL);
    }
}
