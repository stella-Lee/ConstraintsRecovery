package spl.requiresanalysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class OperationProviderEvidenceDetector {
    public OperationProviderEvidenceResult detect(ProviderDataFlowEvidenceResult providerDataFlowResult) {
        Map<OperationProviderKey, Bucket> buckets = new LinkedHashMap<>();
        for (ProviderDataFlowEvidence evidence : providerDataFlowResult.evidence()) {
            if (!"METHOD".equals(evidence.sourceEntityType()) && !"CONSTRUCTOR".equals(evidence.sourceEntityType())) {
                continue;
            }
            String operationKey = evidence.sourceEntityName() + "@" + evidence.sourceEntityId();
            OperationProviderKey key = new OperationProviderKey(
                    evidence.sourceEntityId(), evidence.sourceBlockId(), evidence.providerEntityId());
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

    private record OperationProviderKey(String sourceEntityId, String sourceUnitId, String providerEntityId) {}

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
                    operationName(first.sourceEntityName()),
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
