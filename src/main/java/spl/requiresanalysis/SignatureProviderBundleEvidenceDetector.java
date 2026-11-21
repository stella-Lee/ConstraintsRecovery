package spl.requiresanalysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class SignatureProviderBundleEvidenceDetector {
    public SignatureProviderBundleEvidenceResult detect(OperationProviderEvidenceResult operationResult) {
        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (OperationProviderEvidence evidence : operationResult.evidence()) {
            String key = evidence.sourceSignature() + "->" + evidence.providerSignature();
            buckets.computeIfAbsent(key, ignored -> new Bucket()).add(evidence);
        }

        List<SignatureProviderBundleEvidence> evidence = new ArrayList<>();
        List<Bucket> sorted = buckets.values().stream()
                .sorted(Comparator.comparing(Bucket::sourceSignatureText)
                        .thenComparing(Bucket::providerSignatureText))
                .toList();
        for (Bucket bucket : sorted) {
            evidence.add(bucket.toEvidence("SPB-" + String.format("%04d", evidence.size() + 1)));
        }

        return new SignatureProviderBundleEvidenceResult(
                operationResult.evidence().size(),
                evidence.size(),
                evidence
        );
    }

    private static final class Bucket {
        private final List<OperationProviderEvidence> members = new ArrayList<>();

        private void add(OperationProviderEvidence evidence) {
            members.add(evidence);
        }

        private String sourceSignatureText() {
            return members.get(0).sourceSignature().toString();
        }

        private String providerSignatureText() {
            return members.get(0).providerSignature().toString();
        }

        private SignatureProviderBundleEvidence toEvidence(String id) {
            OperationProviderEvidence first = members.get(0);
            return new SignatureProviderBundleEvidence(
                    id,
                    first.sourceSignature(),
                    first.providerSignature(),
                    joined(OperationProviderEvidence::sourceGroupIds),
                    joined(OperationProviderEvidence::sourceBlockIds),
                    joined(OperationProviderEvidence::sourceEntityNames),
                    joined(OperationProviderEvidence::operationKey),
                    joined(OperationProviderEvidence::providerEntityName),
                    joined(OperationProviderEvidence::providerGroupId),
                    joined(OperationProviderEvidence::providerBlockId),
                    joined(OperationProviderEvidence::evidenceKinds),
                    joined(OperationProviderEvidence::relationTypes),
                    distinctCount(OperationProviderEvidence::operationKey),
                    distinctCount(OperationProviderEvidence::providerEntityName),
                    members.stream().mapToInt(OperationProviderEvidence::supportingRelationCount).sum(),
                    observedProducts(),
                    first.productSetRelation(),
                    "RECALL_EXPANSION_ONLY_NOT_DIRECT_REQUIRES;SIGNATURE_PROVIDER_BUNDLE"
            );
        }

        private int distinctCount(ValueExtractor extractor) {
            return members.stream()
                    .map(extractor::value)
                    .flatMap(value -> split(value).stream())
                    .collect(Collectors.toCollection(TreeSet::new))
                    .size();
        }

        private String joined(ValueExtractor extractor) {
            return members.stream()
                    .map(extractor::value)
                    .flatMap(value -> split(value).stream())
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toCollection(TreeSet::new))
                    .stream()
                    .collect(Collectors.joining("|"));
        }

        private Set<String> split(String value) {
            if (value == null || value.isBlank()) {
                return Set.of();
            }
            return Set.of(value.split("\\|"));
        }

        private List<String> observedProducts() {
            Set<String> products = new TreeSet<>();
            for (OperationProviderEvidence member : members) {
                products.addAll(member.observedProducts());
            }
            return List.copyOf(products);
        }
    }

    @FunctionalInterface
    private interface ValueExtractor {
        String value(OperationProviderEvidence evidence);
    }
}
