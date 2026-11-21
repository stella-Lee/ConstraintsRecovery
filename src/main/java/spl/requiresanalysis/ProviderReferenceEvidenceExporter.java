package spl.requiresanalysis;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;

public final class ProviderReferenceEvidenceExporter {
    public void export(ProviderReferenceEvidenceResult result, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(
                outputDirectory.resolve("provider_reference_evidence.csv"), StandardCharsets.UTF_8))) {
            writer.println(String.join(",",
                    "evidence_id",
                    "evidence_type",
                    "source_entity_id",
                    "source_entity_name",
                    "source_entity_type",
                    "source_block_id",
                    "source_group_id",
                    "source_signature",
                    "source_file",
                    "source_line",
                    "target_entity_id",
                    "target_entity_name",
                    "target_entity_type",
                    "target_block_id",
                    "target_group_id",
                    "target_signature",
                    "target_file",
                    "target_line",
                    "referenced_type",
                    "product_set_relation",
                    "observed_products",
                    "caution_reasons"
            ));
            for (ProviderReferenceEvidence evidence : result.evidence()) {
                writer.println(csv(
                        evidence.evidenceId(),
                        evidence.evidenceType(),
                        evidence.sourceEntityId(),
                        evidence.sourceEntityName(),
                        evidence.sourceEntityType(),
                        evidence.sourceBlockId(),
                        evidence.sourceGroupId(),
                        evidence.sourceSignature().toString(),
                        evidence.sourceFile().toString(),
                        Integer.toString(evidence.sourceLine()),
                        evidence.targetEntityId(),
                        evidence.targetEntityName(),
                        evidence.targetEntityType(),
                        evidence.targetBlockId(),
                        evidence.targetGroupId(),
                        evidence.targetSignature().toString(),
                        evidence.targetFile().toString(),
                        Integer.toString(evidence.targetLine()),
                        evidence.referencedType(),
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
