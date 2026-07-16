package spl.feature;

import java.util.List;
import java.util.Objects;

public record FeatureEvidenceProfileResult(List<FeatureEvidenceProfile> profiles) {
    public FeatureEvidenceProfileResult {
        profiles = List.copyOf(Objects.requireNonNull(profiles, "profiles"));
    }
}
