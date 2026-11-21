package spl.requiresanalysis;

import org.junit.jupiter.api.Test;
import spl.ProductSignature;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SignatureProviderBundleEvidenceDetectorTest {
    @Test
    void keepsProviderIdentitiesSeparateWhenSignaturesMatch() {
        ProductSignature source = new ProductSignature("P1");
        ProductSignature provider = new ProductSignature("P1|P2");
        OperationProviderEvidence first = evidence("OPE-0001", "pkg.A.execute", "pkg.ProviderA", source, provider, 2);
        OperationProviderEvidence second = evidence("OPE-0002", "pkg.B.execute", "pkg.ProviderB", source, provider, 3);

        SignatureProviderBundleEvidenceResult result = new SignatureProviderBundleEvidenceDetector().detect(
                new OperationProviderEvidenceResult(2, 2, List.of(first, second))
        );

        assertEquals(2, result.evidence().size());
        assertEquals(5, result.evidence().stream().mapToInt(SignatureProviderBundleEvidence::supportingRelationCount).sum());
        assertEquals(List.of(1, 1), result.evidence().stream().map(SignatureProviderBundleEvidence::providerCount).toList());
    }

    @Test
    void keepsDifferentSignaturePairsSeparate() {
        OperationProviderEvidence first = evidence("OPE-0001", "pkg.A.execute", "pkg.ProviderA",
                new ProductSignature("P1"), new ProductSignature("P1|P2"), 1);
        OperationProviderEvidence second = evidence("OPE-0002", "pkg.B.execute", "pkg.ProviderA",
                new ProductSignature("P2"), new ProductSignature("P1|P2"), 1);

        SignatureProviderBundleEvidenceResult result = new SignatureProviderBundleEvidenceDetector().detect(
                new OperationProviderEvidenceResult(2, 2, List.of(first, second))
        );

        assertEquals(2, result.evidence().size());
    }

    private OperationProviderEvidence evidence(String id, String operationKey, String providerName,
                                               ProductSignature source, ProductSignature provider, int support) {
        return new OperationProviderEvidence(
                id,
                operationKey,
                operationKey.substring(operationKey.lastIndexOf('.') + 1),
                source,
                "G1",
                "B1",
                "E1",
                operationKey,
                providerName,
                providerName,
                "CLASS",
                "B2",
                "G2",
                provider,
                Path.of("asset2"),
                20,
                "METHOD_DECLARING_TYPE_PROVIDER_USE",
                "METHOD_CALL",
                "R1",
                support,
                List.of("P1"),
                "SOURCE_SUBSET_OF_TARGET",
                "RECALL_EXPANSION_ONLY_NOT_DIRECT_REQUIRES;OPERATION_LEVEL_AGGREGATE"
        );
    }
}
