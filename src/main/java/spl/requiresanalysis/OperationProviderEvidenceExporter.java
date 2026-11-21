package spl.requiresanalysis;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;

public final class OperationProviderEvidenceExporter {
    public void export(OperationProviderEvidenceResult result, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(
                outputDirectory.resolve("operation_provider_evidence.csv"), StandardCharsets.UTF_8))) {
            writer.println(String.join(",",
                    "evidence_id",
                    "operation_key",
                    "operation_name",
                    "source_signature",
                    "source_group_ids",
                    "source_block_ids",
                    "source_entity_ids",
                    "source_entity_names",
                    "provider_entity_id",
                    "provider_entity_name",
                    "provider_entity_type",
                    "provider_block_id",
                    "provider_group_id",
                    "provider_signature",
                    "provider_file",
                    "provider_line",
                    "evidence_kinds",
                    "relation_types",
                    "relation_ids",
                    "supporting_relation_count",
                    "observed_products",
                    "product_set_relation",
                    "caution_reasons"
            ));
            for (OperationProviderEvidence evidence : result.evidence()) {
                writer.println(csv(
                        evidence.evidenceId(),
                        evidence.operationKey(),
                        evidence.operationName(),
                        evidence.sourceSignature().toString(),
                        evidence.sourceGroupIds(),
                        evidence.sourceBlockIds(),
                        evidence.sourceEntityIds(),
                        evidence.sourceEntityNames(),
                        evidence.providerEntityId(),
                        evidence.providerEntityName(),
                        evidence.providerEntityType(),
                        evidence.providerBlockId(),
                        evidence.providerGroupId(),
                        evidence.providerSignature().toString(),
                        evidence.providerFile().toString(),
                        Integer.toString(evidence.providerLine()),
                        evidence.evidenceKinds(),
                        evidence.relationTypes(),
                        evidence.relationIds(),
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
