package spl.requiresanalysis;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;

public final class SignatureProviderBundleEvidenceExporter {
    public void export(SignatureProviderBundleEvidenceResult result, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(
                outputDirectory.resolve("signature_provider_bundle_evidence.csv"), StandardCharsets.UTF_8))) {
            writer.println(String.join(",",
                    "evidence_id",
                    "source_signature",
                    "provider_signature",
                    "source_group_ids",
                    "source_block_ids",
                    "source_entity_names",
                    "operation_keys",
                    "provider_entity_names",
                    "provider_group_ids",
                    "provider_block_ids",
                    "evidence_kinds",
                    "relation_types",
                    "operation_count",
                    "provider_count",
                    "supporting_relation_count",
                    "observed_products",
                    "product_set_relation",
                    "caution_reasons"
            ));
            for (SignatureProviderBundleEvidence evidence : result.evidence()) {
                writer.println(csv(
                        evidence.evidenceId(),
                        evidence.sourceSignature().toString(),
                        evidence.providerSignature().toString(),
                        evidence.sourceGroupIds(),
                        evidence.sourceBlockIds(),
                        evidence.sourceEntityNames(),
                        evidence.operationKeys(),
                        evidence.providerEntityNames(),
                        evidence.providerGroupIds(),
                        evidence.providerBlockIds(),
                        evidence.evidenceKinds(),
                        evidence.relationTypes(),
                        Integer.toString(evidence.operationCount()),
                        Integer.toString(evidence.providerCount()),
                        Integer.toString(evidence.supportingRelationCount()),
                        String.join("|", evidence.observedProducts()),
                        evidence.productSetRelation(),
                        evidence.cautionReasons()
                ));
            }
        }
    }

    private String csv(String... values) {
        StringJoiner joiner = new StringJoiner(",");
        for (String value : values) {
            joiner.add(escape(value));
        }
        return joiner.toString();
    }

    private String escape(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
