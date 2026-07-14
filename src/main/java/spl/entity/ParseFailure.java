package spl.entity;

import java.nio.file.Path;
import java.util.Objects;

public record ParseFailure(
        Path sourceFile,
        String product,
        int line,
        String message,
        boolean extractionSkipped
) {
    public ParseFailure {
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        product = Objects.requireNonNull(product, "product");
        message = Objects.requireNonNull(message, "message");
    }
}
