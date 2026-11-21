package spl.requiresanalysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spl.ConditionalBlock;
import spl.ProductSignature;
import spl.entity.EntityExtractionResult;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelation;
import spl.entity.JavaRelationType;
import spl.entity.ResolutionStatus;
import spl.grouping.SignatureGroup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderDataFlowEvidenceDetectorTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsConstructorProviderUse() throws IOException {
        Path file = sourceFile("class C { void run() { new Provider(); } }");
        JavaEntity source = entity("E1", JavaEntityType.METHOD, "pkg.Client.run", "B1", "G1",
                new ProductSignature("P1"), file, 1);
        JavaEntity provider = entity("E2", JavaEntityType.CLASS, "pkg.Provider", "B2", "G2",
                new ProductSignature("P1|P2"), file, 1);
        JavaEntity constructor = entity("E3", JavaEntityType.CONSTRUCTOR, "pkg.Provider.Provider", "B2", "G2",
                new ProductSignature("P1|P2"), file, 1);
        JavaRelation relation = relation("R1", source.entityId(), constructor.entityId(),
                JavaRelationType.CONSTRUCTOR_CALL, file, 1);

        ProviderDataFlowEvidenceResult result = new ProviderDataFlowEvidenceDetector().detect(
                groups(file, "P1|P2|P3"),
                new EntityExtractionResult(List.of(source, provider, constructor), List.of(relation), List.of(), 1, 1, 1)
        );

        assertEquals(1, result.evidence().size());
        assertEquals("CONSTRUCTOR_PROVIDER_USE", result.evidence().get(0).evidenceKind());
        assertEquals("pkg.Provider", result.evidence().get(0).providerEntityName());
    }

    @Test
    void detectsFieldDeclaredTypeProviderUse() throws IOException {
        Path file = sourceFile("private Provider provider;\nclass C { void run() { provider.use(); } }");
        JavaEntity source = entity("E1", JavaEntityType.METHOD, "pkg.Client.run", "B1", "G1",
                new ProductSignature("P1"), file, 2);
        JavaEntity provider = entity("E2", JavaEntityType.CLASS, "pkg.Provider", "B2", "G2",
                new ProductSignature("P1|P2"), file, 1);
        JavaEntity field = entity("E3", JavaEntityType.FIELD, "pkg.Client.provider", "B1", "G1",
                new ProductSignature("P1"), file, 1);
        JavaRelation relation = relation("R1", source.entityId(), field.entityId(),
                JavaRelationType.FIELD_REFERENCE, file, 2);

        ProviderDataFlowEvidenceResult result = new ProviderDataFlowEvidenceDetector().detect(
                groups(file, "P1|P2|P3"),
                new EntityExtractionResult(List.of(source, provider, field), List.of(relation), List.of(), 1, 1, 1)
        );

        assertEquals(1, result.evidence().size());
        assertEquals("FIELD_DECLARED_TYPE_PROVIDER_USE", result.evidence().get(0).evidenceKind());
    }

    @Test
    void skipsCommonProvider() throws IOException {
        Path file = sourceFile("class C { void run() { new Provider(); } }");
        JavaEntity source = entity("E1", JavaEntityType.METHOD, "pkg.Client.run", "B1", "G1",
                new ProductSignature("P1"), file, 1);
        JavaEntity provider = entity("E2", JavaEntityType.CLASS, "pkg.Provider", "B2", "G2",
                new ProductSignature("P1|P2|P3"), file, 1);
        JavaEntity constructor = entity("E3", JavaEntityType.CONSTRUCTOR, "pkg.Provider.Provider", "B2", "G2",
                new ProductSignature("P1|P2|P3"), file, 1);
        JavaRelation relation = relation("R1", source.entityId(), constructor.entityId(),
                JavaRelationType.CONSTRUCTOR_CALL, file, 1);

        ProviderDataFlowEvidenceResult result = new ProviderDataFlowEvidenceDetector().detect(
                groups(file, "P1|P2|P3"),
                new EntityExtractionResult(List.of(source, provider, constructor), List.of(relation), List.of(), 1, 1, 1)
        );

        assertEquals(0, result.evidence().size());
        assertEquals(1, result.commonProviderTargets());
    }

    private Path sourceFile(String text) throws IOException {
        Path file = tempDir.resolve("asset1");
        Files.writeString(file, text);
        return file;
    }

    private static List<SignatureGroup> groups(Path file, String universe) {
        ConditionalBlock block = new ConditionalBlock(file, ConditionalBlock.DirectiveType.IF, 1, 0,
                new ProductSignature(universe));
        return List.of(new SignatureGroup("G0", new ProductSignature(universe), List.of(block)));
    }

    private static JavaEntity entity(String id, JavaEntityType type, String name, String blockId, String groupId,
                                     ProductSignature signature, Path file, int line) {
        return new JavaEntity(id, type, name.substring(name.lastIndexOf('.') + 1), name, file, line, line, blockId,
                groupId, signature, signature.productIds(), ResolutionStatus.RESOLVED_INTERNAL);
    }

    private static JavaRelation relation(String id, String sourceId, String targetId, JavaRelationType type,
                                         Path file, int line) {
        return new JavaRelation(id, sourceId, targetId, "", type, file, line, "B1", "G1",
                new ProductSignature("P1"), List.of("P1"), ResolutionStatus.RESOLVED_INTERNAL);
    }
}
