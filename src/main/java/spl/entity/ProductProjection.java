package spl.entity;

import java.nio.file.Path;
import java.util.Objects;

public record ProductProjection(Path sourceFile, String product, String source, int lineCount) {
    public ProductProjection {
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        product = Objects.requireNonNull(product, "product");
        source = Objects.requireNonNull(source, "source");
    }
}
