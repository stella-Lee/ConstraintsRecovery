package spl.feature;

import java.util.Objects;

public record TokenEvidence(
        String token,
        TokenProvenance provenance,
        String sourceEntityId
) {
    public TokenEvidence {
        token = Objects.requireNonNull(token, "token");
        provenance = Objects.requireNonNull(provenance, "provenance");
        sourceEntityId = sourceEntityId == null ? "" : sourceEntityId;
    }
}
