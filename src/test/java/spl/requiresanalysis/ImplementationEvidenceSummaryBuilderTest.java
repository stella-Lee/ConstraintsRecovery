package spl.requiresanalysis;

import org.junit.jupiter.api.Test;
import spl.ProductSignature;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplementationEvidenceSummaryBuilderTest {
    @Test
    void buildsConciseSummaryFromSignatureProviderBundle() {
        SignatureProviderBundleEvidence bundle = new SignatureProviderBundleEvidence(
                "SPB-0001",
                new ProductSignature("P1"),
                new ProductSignature("P1|P2"),
                "G1",
                "B1",
                "pkg.Client.invokeCompressor|pkg.Client.shutdownINCompressor",
                "pkg.Client.invokeCompressor|pkg.Client.shutdownINCompressor",
                "com.sleepycat.je.incomp.INCompressor",
                "G2",
                "B2",
                "METHOD_DECLARING_TYPE_PROVIDER_USE",
                "METHOD_CALL",
                2,
                1,
                12,
                List.of("P1"),
                "SOURCE_SUBSET_OF_TARGET",
                "RECALL_EXPANSION_ONLY_NOT_DIRECT_REQUIRES"
        );

        ImplementationEvidenceSummaryResult result = new ImplementationEvidenceSummaryBuilder().build(
                new SignatureProviderBundleEvidenceResult(1, 1, List.of(bundle))
        );

        assertEquals(1, result.summaries().size());
        ImplementationEvidenceSummary summary = result.summaries().get(0);
        assertEquals("MEDIUM_EVIDENCE", summary.evidenceImportance());
        assertEquals("INCompressor", summary.representativeProviders());
        assertTrue(summary.representativeTokens().contains("compressor"));
    }

    @Test
    void sortsBySupportingRelationCountDescending() {
        SignatureProviderBundleEvidence small = bundle("SPB-0001", "P1", "P1|P2", 1);
        SignatureProviderBundleEvidence large = bundle("SPB-0002", "P2", "P1|P2", 20);

        ImplementationEvidenceSummaryResult result = new ImplementationEvidenceSummaryBuilder().build(
                new SignatureProviderBundleEvidenceResult(2, 2, List.of(small, large))
        );

        assertEquals("SPB-0002", result.summaries().get(0).sourceEvidenceId());
    }

    private SignatureProviderBundleEvidence bundle(String id, String source, String provider, int support) {
        return new SignatureProviderBundleEvidence(
                id,
                new ProductSignature(source),
                new ProductSignature(provider),
                "G1",
                "B1",
                "pkg.Client.operation",
                "pkg.Client.operation",
                "pkg.Provider",
                "G2",
                "B2",
                "METHOD_DECLARING_TYPE_PROVIDER_USE",
                "METHOD_CALL",
                1,
                1,
                support,
                List.of(source),
                "SOURCE_SUBSET_OF_TARGET",
                "RECALL_EXPANSION_ONLY_NOT_DIRECT_REQUIRES"
        );
    }
}
