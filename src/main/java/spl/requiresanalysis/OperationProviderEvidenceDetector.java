package spl.requiresanalysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class OperationProviderEvidenceDetector {
    private static final Pattern WRAPPEE_SUFFIX = Pattern.compile("(__wrappee__).*");
    private static final Pattern HOOK_PREFIX = Pattern.compile("^hook\\d+(__wrappee__.*)?$");

    public OperationProviderEvidenceResult detect(ProviderDataFlowEvidenceResult providerDataFlowResult) {
        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (ProviderDataFlowEvidence evidence : providerDataFlowResult.evidence()) {
            String operationKey = operationKey(evidence.sourceEntityName());
            String key = operationKey + "|" + evidence.sourceSignature() + "|" + evidence.providerEntityId();
            buckets.computeIfAbsent(key, ignored -> new Bucket(operationKey)).add(evidence);
        }

        List<OperationProviderEvidence> result = new ArrayList<>();
        List<Bucket> sortedBuckets = buckets.values().stream()
                .sorted(Comparator.comparing(Bucket::firstProviderName)
                        .thenComparing(Bucket::operationKey)
                        .thenComparing(Bucket::firstSourceSignature))
                .toList();
        for (Bucket bucket : sortedBuckets) {
            result.add(bucket.toEvidence("OPE-" + String.format("%04d", result.size() + 1)));
        }

        return new OperationProviderEvidenceResult(
                providerDataFlowResult.evidence().size(),
                result.size(),
                result
        );
    }

    private String operationKey(String qualifiedOperationName) {
        String normalized = qualifiedOperationName == null ? "" : qualifiedOperationName.strip();
        if (normalized.isBlank()) {
            return "UNKNOWN_OPERATION";
        }
        int lastDot = normalized.lastIndexOf('.');
        String simple = lastDot >= 0 ? normalized.substring(lastDot + 1) : normalized;
        simple = WRAPPEE_SUFFIX.matcher(simple).replaceFirst("$1");
        if (HOOK_PREFIX.matcher(simple).matches()) {
            simple = "hook";
        }
        int declaringTypeEnd = normalized.lastIndexOf('.');
        if (declaringTypeEnd < 0) {
            return simple;
        }
        String declaringType = normalized.substring(0, declaringTypeEnd);
        return declaringType + "." + simple;
    }

    private static final class Bucket {
        private final String operationKey;
        private final List<ProviderDataFlowEvidence> members = new ArrayList<>();

        private Bucket(String operationKey) {
            this.operationKey = operationKey;
        }

        private void add(ProviderDataFlowEvidence evidence) {
            members.add(evidence);
        }

        private String operationKey() {
            return operationKey;
        }

        private String firstProviderName() {
            return members.get(0).providerEntityName();
        }

        private String firstSourceSignature() {
            return members.get(0).sourceSignature().toString();
        }

        private OperationProviderEvidence toEvidence(String id) {
            ProviderDataFlowEvidence first = members.get(0);
            return new OperationProviderEvidence(
                    id,
                    operationKey,
                    operationName(operationKey),
                    first.sourceSignature(),
                    joined(ProviderDataFlowEvidence::sourceGroupId),
                    joined(ProviderDataFlowEvidence::sourceBlockId),
                    joined(ProviderDataFlowEvidence::sourceEntityId),
                    joined(ProviderDataFlowEvidence::sourceEntityName),
                    first.providerEntityId(),
                    first.providerEntityName(),
                    first.providerEntityType(),
                    first.providerBlockId(),
                    first.providerGroupId(),
                    first.providerSignature(),
                    first.providerFile(),
                    first.providerLine(),
                    joined(ProviderDataFlowEvidence::evidenceKind),
                    joined(ProviderDataFlowEvidence::relationType),
                    joined(ProviderDataFlowEvidence::relationId),
                    members.size(),
                    observedProducts(),
                    first.productSetRelation(),
                    "RECALL_EXPANSION_ONLY_NOT_DIRECT_REQUIRES;OPERATION_LEVEL_AGGREGATE"
            );
        }

        private String operationName(String key) {
            int dot = key.lastIndexOf('.');
            return dot >= 0 ? key.substring(dot + 1) : key;
        }

        private String joined(ValueExtractor extractor) {
            return members.stream()
                    .map(extractor::value)
                    .filter(value -> value != null && !value.isBlank())
                    .collect(Collectors.toCollection(TreeSet::new))
                    .stream()
                    .collect(Collectors.joining("|"));
        }

        private List<String> observedProducts() {
            Set<String> products = new TreeSet<>();
            for (ProviderDataFlowEvidence member : members) {
                products.addAll(member.observedProducts());
            }
            return List.copyOf(products);
        }
    }

    @FunctionalInterface
    private interface ValueExtractor {
        String value(ProviderDataFlowEvidence evidence);
    }
}
