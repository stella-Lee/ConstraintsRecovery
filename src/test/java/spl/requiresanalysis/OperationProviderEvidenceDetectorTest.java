package spl.requiresanalysis;

import org.junit.jupiter.api.Test;
import spl.ProductSignature;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperationProviderEvidenceDetectorTest {
    @Test
    void aggregatesProviderEvidenceByOperationAndProvider() {
        ProductSignature sourceSignature = new ProductSignature("P1");
        ProductSignature providerSignature = new ProductSignature("P1|P2");
        ProviderDataFlowEvidence first = evidence("PDE-0001", "R1", "pkg.Client.execute",
                "FIELD_DECLARED_TYPE_PROVIDER_USE", "FIELD_REFERENCE", sourceSignature, providerSignature);
        ProviderDataFlowEvidence second = evidence("PDE-0002", "R2", "pkg.Client.execute",
                "CONSTRUCTOR_PROVIDER_USE", "CONSTRUCTOR_CALL", sourceSignature, providerSignature);

        OperationProviderEvidenceResult result = new OperationProviderEvidenceDetector().detect(
                new ProviderDataFlowEvidenceResult(2, 0, 0, 0, 0, List.of(first, second))
        );

        assertEquals(1, result.evidence().size());
        OperationProviderEvidence operation = result.evidence().get(0);
        assertEquals("pkg.Client.execute@E1", operation.operationKey());
        assertEquals(2, operation.supportingRelationCount());
        assertEquals("CONSTRUCTOR_PROVIDER_USE|FIELD_DECLARED_TYPE_PROVIDER_USE", operation.evidenceKinds());
        assertEquals("CONSTRUCTOR_CALL|FIELD_REFERENCE", operation.relationTypes());
    }

    @Test
    void keepsDifferentProvidersSeparate() {
        ProductSignature sourceSignature = new ProductSignature("P1");
        ProviderDataFlowEvidence first = evidence("PDE-0001", "R1", "pkg.Client.execute",
                "METHOD_DECLARING_TYPE_PROVIDER_USE", "METHOD_CALL", sourceSignature,
                new ProductSignature("P1|P2"), "ProviderA", "pkg.ProviderA");
        ProviderDataFlowEvidence second = evidence("PDE-0002", "R2", "pkg.Client.execute",
                "METHOD_DECLARING_TYPE_PROVIDER_USE", "METHOD_CALL", sourceSignature,
                new ProductSignature("P1|P3"), "ProviderB", "pkg.ProviderB");

        OperationProviderEvidenceResult result = new OperationProviderEvidenceDetector().detect(
                new ProviderDataFlowEvidenceResult(2, 0, 0, 0, 0, List.of(first, second))
        );

        assertEquals(2, result.evidence().size());
    }

    private ProviderDataFlowEvidence evidence(String id, String relationId, String sourceName,
                                              String evidenceKind, String relationType,
                                              ProductSignature sourceSignature,
                                              ProductSignature providerSignature) {
        return evidence(id, relationId, sourceName, evidenceKind, relationType, sourceSignature,
                providerSignature, "Provider", "pkg.Provider");
    }

    private ProviderDataFlowEvidence evidence(String id, String relationId, String sourceName,
                                              String evidenceKind, String relationType,
                                              ProductSignature sourceSignature,
                                              ProductSignature providerSignature,
                                              String providerSimpleName,
                                              String providerName) {
        return new ProviderDataFlowEvidence(
                id,
                evidenceKind,
                "E1",
                sourceName,
                "METHOD",
                "B1",
                "G1",
                sourceSignature,
                Path.of("asset1"),
                10,
                "E2-" + providerSimpleName,
                providerName,
                "CLASS",
                "B2",
                "G2",
                providerSignature,
                Path.of("asset2"),
                20,
                "E3",
                "pkg.Client.field",
                "FIELD",
                relationId,
                relationType,
                "code",
                "SOURCE_SUBSET_OF_TARGET",
                List.of("P1"),
                "RECALL_EXPANSION_ONLY_NOT_DIRECT_REQUIRES;DATAFLOW_LITE"
        );
    }
}
