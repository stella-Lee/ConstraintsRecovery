package spl.requiresanalysis;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;

public final class ProviderDataFlowEvidenceExporter {
    public void export(ProviderDataFlowEvidenceResult result, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(
                outputDirectory.resolve("provider_dataflow_evidence.csv"), StandardCharsets.UTF_8))) {
            writer.println(String.join(",",
                    "evidence_id",
                    "evidence_kind",
                    "source_entity_id",
                    "source_entity_name",
                    "source_entity_type",
                    "source_block_id",
                    "source_group_id",
                    "source_signature",
                    "source_file",
                    "source_line",
                    "provider_entity_id",
                    "provider_entity_name",
                    "provider_entity_type",
                    "provider_block_id",
                    "provider_group_id",
                    "provider_signature",
                    "provider_file",
                    "provider_line",
                    "intermediate_entity_id",
                    "intermediate_entity_name",
                    "intermediate_entity_type",
                    "relation_id",
                    "relation_type",
                    "code_snippet",
                    "product_set_relation",
                    "observed_products",
                    "caution_reasons"
            ));
            for (ProviderDataFlowEvidence evidence : result.evidence()) {
                writer.println(csv(
                        evidence.evidenceId(),
                        evidence.evidenceKind(),
                        evidence.sourceEntityId(),
                        evidence.sourceEntityName(),
                        evidence.sourceEntityType(),
                        evidence.sourceBlockId(),
                        evidence.sourceGroupId(),
                        evidence.sourceSignature().toString(),
                        evidence.sourceFile().toString(),
                        Integer.toString(evidence.sourceLine()),
                        evidence.providerEntityId(),
                        evidence.providerEntityName(),
                        evidence.providerEntityType(),
                        evidence.providerBlockId(),
                        evidence.providerGroupId(),
                        evidence.providerSignature().toString(),
                        evidence.providerFile().toString(),
                        Integer.toString(evidence.providerLine()),
                        evidence.intermediateEntityId(),
                        evidence.intermediateEntityName(),
                        evidence.intermediateEntityType(),
                        evidence.relationId(),
                        evidence.relationType(),
                        evidence.codeSnippet(),
                        evidence.productSetRelation(),
                        String.join("|", evidence.observedProducts()),
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
