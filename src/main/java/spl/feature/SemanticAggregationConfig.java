package spl.feature;

import java.util.Set;
import java.util.TreeSet;

public record SemanticAggregationConfig(
        double lexicalWeight,
        double contextualWeight,
        double neighborhoodWeight,
        double directDependencyWeight,
        double blockEvidenceWeight,
        double pairwiseMergeThreshold,
        int minimumEvidenceFamilies,
        double clusterCohesionThreshold,
        Set<String> stopWords,
        boolean useStringLiterals,
        boolean useComments,
        boolean useCustomAnnotations,
        boolean aggregateIsolatedEntities,
        boolean processFullCommonality
) {
    public SemanticAggregationConfig {
        stopWords = Set.copyOf(stopWords);
    }

    public static SemanticAggregationConfig defaults() {
        return new SemanticAggregationConfig(
                0.35,
                0.20,
                0.20,
                0.15,
                0.10,
                0.42,
                2,
                0.42,
                defaultStopWords(),
                false,
                false,
                false,
                true,
                true
        );
    }

    private static Set<String> defaultStopWords() {
        TreeSet<String> words = new TreeSet<>();
        words.addAll(Set.of(
                "get", "set", "is", "add", "remove", "create", "new", "run", "main",
                "value", "data", "object", "listener", "action", "handler", "manager",
                "util", "impl", "java", "lang", "void", "int", "boolean", "string",
                "class", "interface", "method", "field", "constructor", "core", "examples",
                "example", "de", "ovgu", "featureide", "asset", "reference", "controller",
                "model", "ui", "sim", "unit", "composite", "serial", "version", "uid",
                "btn", "lbl", "txt", "window", "elevator"
        ));
        return words;
    }
}
