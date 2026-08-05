package spl.requiresanalysis;

public enum DependencyExclusionReason {
    NONE,
    STANDARD_LIBRARY,
    EXTERNAL_LIBRARY,
    UTILITY_ONLY,
    LOGGING_ONLY,
    FRAMEWORK_CALLBACK,
    COMMON_INFRASTRUCTURE,
    INTRA_BLOCK,
    UNKNOWN_TARGET
}
