package spl.requiresanalysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class CompositionAwareDependencyExporter {
    public void export(CompositionAwareDependencyResult result, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        var lines = new java.util.ArrayList<String>();
        lines.add("dependency_id,source_block_id,target_block_id,source_signature,target_signature,source_method,target_method,wrapper_base_name,ignored_wrapper_suffix,dependency_kind,evidence_type,used_for_requires_scoring,observed_product_set,caution_reasons");
        for (CompositionAwareDependency dependency : result.dependencies()) {
            lines.add(csv(
                    dependency.dependencyId(),
                    dependency.sourceBlockId(),
                    dependency.targetBlockId(),
                    dependency.sourceSignature(),
                    dependency.targetSignature(),
                    dependency.sourceMethod(),
                    dependency.targetMethod(),
                    dependency.wrapperBaseName(),
                    dependency.ignoredWrapperSuffix(),
                    dependency.dependencyKind(),
                    dependency.evidenceType(),
                    dependency.usedForRequiresScoring(),
                    String.join("|", dependency.observedProducts()),
                    String.join("|", dependency.cautionReasons())
            ));
        }
        Files.write(outputDirectory.resolve("composition_dependencies.csv"), lines, StandardCharsets.UTF_8);
    }

    private String csv(Object... values) {
        return Arrays.stream(values)
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
