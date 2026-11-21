package spl.requiresanalysis;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;

public final class ImplementationEvidenceSummaryExporter {
    public void export(ImplementationEvidenceSummaryResult result, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(
                outputDirectory.resolve("implementation_evidence_summary.csv"), StandardCharsets.UTF_8))) {
            writer.println(String.join(",",
                    "summary_id",
                    "source_evidence_id",
                    "source_signature",
                    "provider_signature",
                    "evidence_importance",
                    "operation_count",
                    "provider_count",
                    "supporting_relation_count",
                    "representative_providers",
                    "representative_operations",
                    "representative_tokens",
                    "product_set_relation",
                    "interpretation_note",
                    "caution_reasons"
            ));
            for (ImplementationEvidenceSummary summary : result.summaries()) {
                writer.println(csv(
                        summary.summaryId(),
                        summary.sourceEvidenceId(),
                        summary.sourceSignature().toString(),
                        summary.providerSignature().toString(),
                        summary.evidenceImportance(),
                        Integer.toString(summary.operationCount()),
                        Integer.toString(summary.providerCount()),
                        Integer.toString(summary.supportingRelationCount()),
                        summary.representativeProviders(),
                        summary.representativeOperations(),
                        summary.representativeTokens(),
                        summary.productSetRelation(),
                        summary.interpretationNote(),
                        summary.cautionReasons()
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
