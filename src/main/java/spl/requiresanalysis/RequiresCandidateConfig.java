package spl.requiresanalysis;

public record RequiresCandidateConfig(
        int repeatedFieldReferenceSourceEntities
) {
    public static RequiresCandidateConfig defaults() {
        return new RequiresCandidateConfig(2);
    }
}
