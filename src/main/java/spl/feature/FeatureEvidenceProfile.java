package spl.feature;

import spl.ProductSignature;
import spl.entity.JavaRelationType;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record FeatureEvidenceProfile(
        String componentId,
        String groupId,
        ProductSignature signature,
        List<String> observedProducts,
        List<Path> sourceFiles,
        Set<String> sourceBlocks,
        List<EntityImportance> representativeEntities,
        List<EntityImportance> topClasses,
        List<EntityImportance> topInterfaces,
        List<EntityImportance> topMethods,
        List<EntityImportance> topConstructors,
        List<EntityImportance> topFields,
        Map<String, Integer> tokenFrequencies,
        Map<String, List<String>> tokenOrigins,
        Map<JavaRelationType, Long> internalDependencyTypes,
        List<String> topDependencyTargets,
        List<String> topDependencySources,
        List<String> packages,
        List<String> classes,
        List<String> methods,
        int entityCount,
        int blockCount,
        int fileCount,
        int internalEdgeCount,
        int incomingEdgeCount,
        int outgoingEdgeCount,
        int crossFileEdgeCount,
        int isolatedEntityCount,
        String implementationConcept,
        List<String> conceptEvidence
) {
    public FeatureEvidenceProfile {
        componentId = Objects.requireNonNull(componentId, "componentId");
        groupId = Objects.requireNonNull(groupId, "groupId");
        signature = Objects.requireNonNull(signature, "signature");
        observedProducts = List.copyOf(Objects.requireNonNull(observedProducts, "observedProducts"));
        sourceFiles = List.copyOf(Objects.requireNonNull(sourceFiles, "sourceFiles"));
        sourceBlocks = Set.copyOf(Objects.requireNonNull(sourceBlocks, "sourceBlocks"));
        representativeEntities = List.copyOf(Objects.requireNonNull(representativeEntities, "representativeEntities"));
        topClasses = List.copyOf(Objects.requireNonNull(topClasses, "topClasses"));
        topInterfaces = List.copyOf(Objects.requireNonNull(topInterfaces, "topInterfaces"));
        topMethods = List.copyOf(Objects.requireNonNull(topMethods, "topMethods"));
        topConstructors = List.copyOf(Objects.requireNonNull(topConstructors, "topConstructors"));
        topFields = List.copyOf(Objects.requireNonNull(topFields, "topFields"));
        tokenFrequencies = Map.copyOf(Objects.requireNonNull(tokenFrequencies, "tokenFrequencies"));
        tokenOrigins = Map.copyOf(Objects.requireNonNull(tokenOrigins, "tokenOrigins"));
        internalDependencyTypes = Map.copyOf(Objects.requireNonNull(internalDependencyTypes, "internalDependencyTypes"));
        topDependencyTargets = List.copyOf(Objects.requireNonNull(topDependencyTargets, "topDependencyTargets"));
        topDependencySources = List.copyOf(Objects.requireNonNull(topDependencySources, "topDependencySources"));
        packages = List.copyOf(Objects.requireNonNull(packages, "packages"));
        classes = List.copyOf(Objects.requireNonNull(classes, "classes"));
        methods = List.copyOf(Objects.requireNonNull(methods, "methods"));
        implementationConcept = Objects.requireNonNull(implementationConcept, "implementationConcept");
        conceptEvidence = List.copyOf(Objects.requireNonNull(conceptEvidence, "conceptEvidence"));
    }
}
