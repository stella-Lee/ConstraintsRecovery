package spl.requiresanalysis;

import java.util.List;
import java.util.Objects;

public record DisjunctiveImplementationEvidence(
        String candidateId,
        String candidateType,
        String sourceBlockId,
        String sourceSignature,
        List<String> providerBlockIds,
        List<String> providerSignatureSet,
        String evidenceType,
        String obligation,
        String evidenceMethod,
        String evidenceLocation,
        List<String> cautionReasons
) {
    public DisjunctiveImplementationEvidence {
        candidateId = Objects.requireNonNull(candidateId, "candidateId");
        candidateType = Objects.requireNonNull(candidateType, "candidateType");
        sourceBlockId = Objects.requireNonNull(sourceBlockId, "sourceBlockId");
        sourceSignature = Objects.requireNonNull(sourceSignature, "sourceSignature");
        providerBlockIds = List.copyOf(Objects.requireNonNull(providerBlockIds, "providerBlockIds"));
        providerSignatureSet = List.copyOf(Objects.requireNonNull(providerSignatureSet, "providerSignatureSet"));
        evidenceType = Objects.requireNonNull(evidenceType, "evidenceType");
        obligation = Objects.requireNonNull(obligation, "obligation");
        evidenceMethod = Objects.requireNonNull(evidenceMethod, "evidenceMethod");
        evidenceLocation = Objects.requireNonNull(evidenceLocation, "evidenceLocation");
        cautionReasons = List.copyOf(Objects.requireNonNull(cautionReasons, "cautionReasons"));
    }
}
