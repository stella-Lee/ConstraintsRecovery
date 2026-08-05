package spl.requiresanalysis;

import spl.entity.JavaEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EntityClassifier {
    private final EvidenceScoringConfig config;

    public EntityClassifier() {
        this(EvidenceScoringConfig.defaults());
    }

    public EntityClassifier(EvidenceScoringConfig config) {
        this.config = config;
    }

    public EntityClassification classify(JavaEntity entity) {
        List<String> tokens = tokens(entity);
        List<String> evidence = new ArrayList<>();
        EntityRole role = EntityRole.UNKNOWN;
        double domainScore = 0.2;
        double variabilityScore = 0.05;

        if (matches(tokens, config.utilityTokens())) {
            role = EntityRole.UTILITY;
            domainScore = 0.1;
            evidence.add("matched utility token");
        } else if (matches(tokens, config.infrastructureTokens())) {
            role = EntityRole.INFRASTRUCTURE;
            domainScore = 0.15;
            evidence.add("matched infrastructure token");
        } else if (matches(tokens, config.variabilityTokens())) {
            role = EntityRole.VARIABILITY_CONTROL;
            domainScore = 0.35;
            variabilityScore = 0.85;
            evidence.add("matched variability-control token");
        } else if (matches(tokens, config.domainCoreTokens())) {
            role = EntityRole.DOMAIN_CORE;
            domainScore = 0.9;
            evidence.add("matched domain-core token");
        } else if (matches(tokens, config.domainSupportTokens())) {
            role = EntityRole.DOMAIN_SUPPORT;
            domainScore = 0.65;
            evidence.add("matched domain-support token");
        } else if (matches(tokens, config.orchestrationTokens())) {
            role = EntityRole.APPLICATION_ORCHESTRATION;
            domainScore = 0.35;
            variabilityScore = 0.2;
            evidence.add("matched orchestration token");
        } else {
            evidence.add("no configured role token matched");
        }

        return new EntityClassification(
                entity.entityId(),
                displayName(entity),
                entity.entityType(),
                role,
                domainScore,
                variabilityScore,
                packageName(entity.qualifiedName()),
                entity.observedProducts(),
                String.join("; ", evidence)
        );
    }

    private List<String> tokens(JavaEntity entity) {
        String text = entity.simpleName();
        List<String> result = new ArrayList<>();
        for (String part : text.split("[^A-Za-z0-9]+")) {
            if (part.isBlank()) {
                continue;
            }
            for (String token : part.replaceAll("([a-z])([A-Z])", "$1 $2").split("\\s+")) {
                if (!token.isBlank()) {
                    result.add(token.toLowerCase(Locale.ROOT));
                }
            }
        }
        return result;
    }

    private boolean matches(List<String> tokens, java.util.Set<String> configuredTokens) {
        for (String token : tokens) {
            if (configuredTokens.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String displayName(JavaEntity entity) {
        return entity.qualifiedName() == null || entity.qualifiedName().isBlank()
                ? entity.simpleName()
                : entity.qualifiedName();
    }

    private String packageName(String qualifiedName) {
        if (qualifiedName == null) {
            return "";
        }
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot < 0 ? "" : qualifiedName.substring(0, lastDot);
    }
}
