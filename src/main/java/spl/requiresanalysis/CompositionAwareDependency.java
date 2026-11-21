package spl.requiresanalysis;

import java.util.List;
import java.util.Objects;

public record CompositionAwareDependency(
        String dependencyId,
        String sourceBlockId,
        String targetBlockId,
        String sourceSignature,
        String targetSignature,
        String sourceMethod,
        String targetMethod,
        String wrapperBaseName,
        String ignoredWrapperSuffix,
        String dependencyKind,
        String evidenceType,
        boolean usedForRequiresScoring,
        List<String> observedProducts,
        List<String> cautionReasons
) {
    public CompositionAwareDependency {
        dependencyId = Objects.requireNonNull(dependencyId, "dependencyId");
        sourceBlockId = sourceBlockId == null ? "" : sourceBlockId;
        targetBlockId = targetBlockId == null ? "" : targetBlockId;
        sourceSignature = sourceSignature == null ? "" : sourceSignature;
        targetSignature = targetSignature == null ? "" : targetSignature;
        sourceMethod = sourceMethod == null ? "" : sourceMethod;
        targetMethod = targetMethod == null ? "" : targetMethod;
        wrapperBaseName = wrapperBaseName == null ? "" : wrapperBaseName;
        ignoredWrapperSuffix = ignoredWrapperSuffix == null ? "" : ignoredWrapperSuffix;
        dependencyKind = Objects.requireNonNull(dependencyKind, "dependencyKind");
        evidenceType = Objects.requireNonNull(evidenceType, "evidenceType");
        observedProducts = List.copyOf(Objects.requireNonNull(observedProducts, "observedProducts"));
        cautionReasons = List.copyOf(Objects.requireNonNull(cautionReasons, "cautionReasons"));
    }
}
