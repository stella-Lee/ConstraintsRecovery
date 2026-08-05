package spl.alternative;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class AlternativeCandidateExporter {
    public void export(AlternativeCandidateResult result, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        List<String> lines = new ArrayList<>();
        lines.add("feature_condition_a,feature_condition_b,product_signature_a,product_signature_b,confidence,evidence_categories,supporting_block_pairs,supporting_entity_pairs,explanation");
        result.candidates().stream()
                .sorted(Comparator.comparing(AlternativeCandidate::confidence)
                        .thenComparing(AlternativeCandidate::featureConditionA)
                        .thenComparing(AlternativeCandidate::featureConditionB))
                .forEach(candidate -> lines.add(csv(
                        candidate.featureConditionA(),
                        candidate.featureConditionB(),
                        candidate.productSignatureA(),
                        candidate.productSignatureB(),
                        candidate.confidence(),
                        candidate.evidenceCategories().stream()
                                .map(Enum::name)
                                .sorted()
                                .collect(Collectors.joining("|")),
                        String.join(" | ", candidate.supportingBlockPairs()),
                        String.join(" | ", candidate.supportingEntityPairs()),
                        candidate.notes()
                )));
        Files.write(outputDirectory.resolve("alternative_candidates.csv"), lines, StandardCharsets.UTF_8);
    }

    private String csv(Object... values) {
        return java.util.Arrays.stream(values)
                .map(value -> value == null ? "" : value.toString())
                .map(this::escape)
                .collect(Collectors.joining(","));
    }

    private String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
