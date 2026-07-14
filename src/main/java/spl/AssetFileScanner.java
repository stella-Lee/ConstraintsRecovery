package spl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class AssetFileScanner {
    public List<Path> scan(Path assetPath) throws IOException {
        Objects.requireNonNull(assetPath, "assetPath");
        if (!Files.exists(assetPath)) {
            throw new IOException("Asset path does not exist: " + assetPath);
        }
        if (Files.isRegularFile(assetPath)) {
            return List.of(assetPath.toAbsolutePath().normalize());
        }
        try (Stream<Path> paths = Files.walk(assetPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }
}
