package spl.requiresanalysis;

import java.util.List;
import java.util.Objects;

public record SignatureProviderBundleEvidenceResult(
        int inputOperationEvidenceCount,
        int bundleCount,
        List<SignatureProviderBundleEvidence> evidence
) {
    public SignatureProviderBundleEvidenceResult {
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    }
}
