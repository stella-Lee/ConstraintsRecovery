package spl.feature;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ComponentEvidence(
        String componentId,
        Map<String, Integer> tokenFrequencies,
        List<String> representativeTokens,
        Set<String> rawTokens,
        Set<String> normalizedTokens,
        Set<String> packages,
        Set<String> classes,
        Set<Path> files,
        Set<String> blockIds,
        Set<String> dependencyNeighbors,
        Set<String> internalTargets,
        Set<String> incomingSources,
        Set<String> outgoingTargets
) {
    public ComponentEvidence {
        componentId = Objects.requireNonNull(componentId, "componentId");
        tokenFrequencies = Map.copyOf(Objects.requireNonNull(tokenFrequencies, "tokenFrequencies"));
        representativeTokens = List.copyOf(Objects.requireNonNull(representativeTokens, "representativeTokens"));
        rawTokens = Set.copyOf(Objects.requireNonNull(rawTokens, "rawTokens"));
        normalizedTokens = Set.copyOf(Objects.requireNonNull(normalizedTokens, "normalizedTokens"));
        packages = Set.copyOf(Objects.requireNonNull(packages, "packages"));
        classes = Set.copyOf(Objects.requireNonNull(classes, "classes"));
        files = Set.copyOf(Objects.requireNonNull(files, "files"));
        blockIds = Set.copyOf(Objects.requireNonNull(blockIds, "blockIds"));
        dependencyNeighbors = Set.copyOf(Objects.requireNonNull(dependencyNeighbors, "dependencyNeighbors"));
        internalTargets = Set.copyOf(Objects.requireNonNull(internalTargets, "internalTargets"));
        incomingSources = Set.copyOf(Objects.requireNonNull(incomingSources, "incomingSources"));
        outgoingTargets = Set.copyOf(Objects.requireNonNull(outgoingTargets, "outgoingTargets"));
    }
}
