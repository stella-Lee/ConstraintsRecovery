package spl.requiresanalysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class DisjunctiveImplementationEvidenceExporter {
    public void export(DisjunctiveImplementationEvidenceResult result, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        var lines = new java.util.ArrayList<String>();
        lines.add("candidate_id,candidate_type,source_block_id,source_signature,provider_block_ids,provider_signature_set,evidence_type,obligation,evidence_method,evidence_location,caution_reasons");
        for (DisjunctiveImplementationEvidence candidate : result.candidates()) {
            lines.add(csv(
                    candidate.candidateId(),
                    candidate.candidateType(),
                    candidate.sourceBlockId(),
                    candidate.sourceSignature(),
                    String.join(";", candidate.providerBlockIds()),
                    String.join(" OR ", candidate.providerSignatureSet()),
                    candidate.evidenceType(),
                    candidate.obligation(),
                    candidate.evidenceMethod(),
                    candidate.evidenceLocation(),
                    String.join("|", candidate.cautionReasons())
            ));
        }
        Files.write(outputDirectory.resolve("disjunctive_implementation_evidence.csv"), lines, StandardCharsets.UTF_8);
    }

    private String csv(Object... values) {
        return Arrays.stream(values)
                .map(value -> value == null ? "" : value.toString())
                .map(this::escape)
                .collect(Collectors.joining(","));
    }

    private String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"");
        }
        return value;
    }
}
