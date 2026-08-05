package spl.requiresanalysis;

import java.util.Set;

public record EvidenceScoringConfig(
        Set<String> domainCoreTokens,
        Set<String> domainSupportTokens,
        Set<String> variabilityTokens,
        Set<String> orchestrationTokens,
        Set<String> infrastructureTokens,
        Set<String> utilityTokens,
        Set<String> loggingTokens,
        Set<String> frameworkCallbackTokens,
        double highDomainThreshold,
        double mediumDomainThreshold
) {
    public EvidenceScoringConfig {
        domainCoreTokens = Set.copyOf(domainCoreTokens);
        domainSupportTokens = Set.copyOf(domainSupportTokens);
        variabilityTokens = Set.copyOf(variabilityTokens);
        orchestrationTokens = Set.copyOf(orchestrationTokens);
        infrastructureTokens = Set.copyOf(infrastructureTokens);
        utilityTokens = Set.copyOf(utilityTokens);
        loggingTokens = Set.copyOf(loggingTokens);
        frameworkCallbackTokens = Set.copyOf(frameworkCallbackTokens);
    }

    public static EvidenceScoringConfig defaults() {
        return new EvidenceScoringConfig(
                Set.of("elevator", "door", "sensor", "floor", "payment", "reservation", "request",
                        "direction", "service", "disabled", "permission", "movement", "call", "queue",
                        "button", "position"),
                Set.of("state", "controller", "control", "comparator", "scheduler", "dispatcher",
                        "panel", "display", "model", "unit"),
                Set.of("feature", "registry", "factory", "plugin", "loader", "configuration", "config",
                        "variant", "product"),
                Set.of("main", "application", "app", "simulation", "orchestrator", "launcher"),
                Set.of("database", "logger", "log", "network", "repository", "dao", "jdbc", "http",
                        "socket", "stream", "file", "exception", "thread"),
                Set.of("util", "utils", "helper", "helpers", "stringutils", "collectionutils"),
                Set.of("log", "logger", "logging", "debug", "info", "warn", "error", "trace"),
                Set.of("listener", "callback", "actionperformed", "run", "paint", "update"),
                0.65,
                0.35
        );
    }
}
