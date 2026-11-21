package spl.entity;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public record ProductProjection(Path sourceFile, String product, String source, int lineCount,
                                Map<Integer, Integer> restoredColumnOffsets) {
    public ProductProjection(Path sourceFile, String product, String source, int lineCount) {
        this(sourceFile, product, source, lineCount, Map.of());
    }

    int originalColumn(int line, int projectedColumn) {
        return projectedColumn + restoredColumnOffsets.getOrDefault(line, 0);
    }

    public ProductProjection {
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        product = Objects.requireNonNull(product, "product");
        source = Objects.requireNonNull(source, "source");
        restoredColumnOffsets = Map.copyOf(restoredColumnOffsets);
    }
}
