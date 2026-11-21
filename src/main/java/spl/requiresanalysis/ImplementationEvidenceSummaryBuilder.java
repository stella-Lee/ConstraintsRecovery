package spl.requiresanalysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ImplementationEvidenceSummaryBuilder {
    private static final Set<String> STOP_WORDS = Set.of(
            "com", "sleepycat", "je", "get", "set", "is", "add", "new", "run", "load", "stats",
            "stat", "impl", "base", "execute", "method", "field", "constructor", "object", "this"
    );
    private static final Pattern SPLIT_BOUNDARY = Pattern.compile(
            "(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[._$\\-\\s]+"
    );

    public ImplementationEvidenceSummaryResult build(SignatureProviderBundleEvidenceResult bundleResult) {
        List<SignatureProviderBundleEvidence> sortedBundles = bundleResult.evidence().stream()
                .sorted(Comparator.comparingInt(SignatureProviderBundleEvidence::supportingRelationCount).reversed()
                        .thenComparing(evidence -> evidence.sourceSignature().toString())
                        .thenComparing(evidence -> evidence.providerSignature().toString())
                        .thenComparing(SignatureProviderBundleEvidence::evidenceId))
                .toList();

        List<ImplementationEvidenceSummary> summaries = new ArrayList<>();
        for (SignatureProviderBundleEvidence bundle : sortedBundles) {
            summaries.add(toSummary("IES-" + String.format("%04d", summaries.size() + 1), bundle));
        }

        return new ImplementationEvidenceSummaryResult(bundleResult.evidence().size(), summaries.size(), summaries);
    }

    private ImplementationEvidenceSummary toSummary(String id, SignatureProviderBundleEvidence bundle) {
        return new ImplementationEvidenceSummary(
                id,
                bundle.evidenceId(),
                bundle.sourceSignature(),
                bundle.providerSignature(),
                importance(bundle),
                bundle.operationCount(),
                bundle.providerCount(),
                bundle.supportingRelationCount(),
                topSimpleNames(bundle.providerEntityNames(), 5),
                topSimpleNames(bundle.operationKeys(), 5),
                topTokens(bundle, 8),
                bundle.productSetRelation(),
                "implementation evidence only; not a confirmed requires constraint",
                bundle.cautionReasons()
        );
    }

    private String importance(SignatureProviderBundleEvidence bundle) {
        if (bundle.supportingRelationCount() >= 20 || bundle.operationCount() >= 6) {
            return "HIGH_EVIDENCE";
        }
        if (bundle.supportingRelationCount() >= 5 || bundle.operationCount() >= 2) {
            return "MEDIUM_EVIDENCE";
        }
        return "LOW_EVIDENCE";
    }

    private String topSimpleNames(String value, int limit) {
        return splitPipe(value).stream()
                .map(this::simpleName)
                .filter(token -> !token.isBlank())
                .distinct()
                .limit(limit)
                .collect(Collectors.joining("|"));
    }

    private String topTokens(SignatureProviderBundleEvidence bundle, int limit) {
        Map<String, Integer> frequencies = new LinkedHashMap<>();
        collectTokens(bundle.providerEntityNames(), frequencies);
        collectTokens(bundle.operationKeys(), frequencies);
        collectTokens(bundle.sourceEntityNames(), frequencies);
        return frequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining("|"));
    }

    private void collectTokens(String value, Map<String, Integer> frequencies) {
        for (String item : splitPipe(value)) {
            for (String part : SPLIT_BOUNDARY.split(item)) {
                String token = part.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (token.length() < 3 || STOP_WORDS.contains(token)) {
                    continue;
                }
                frequencies.merge(token, 1, Integer::sum);
            }
        }
    }

    private List<String> splitPipe(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return new TreeSet<>(List.of(value.split("\\|"))).stream().toList();
    }

    private String simpleName(String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        return dot >= 0 ? qualifiedName.substring(dot + 1) : qualifiedName;
    }
}
