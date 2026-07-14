package spl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class AssetProjectPathResolver {
    public static final Path BASE_DIRECTORY = Path.of("assets");

    private final Path normalizedBaseDirectory;

    public AssetProjectPathResolver() {
        this(Path.of(""));
    }

    public AssetProjectPathResolver(Path repositoryRoot) {
        Path normalizedRepositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot")
                .toAbsolutePath()
                .normalize();
        this.normalizedBaseDirectory = normalizedRepositoryRoot.resolve(BASE_DIRECTORY)
                .toAbsolutePath()
                .normalize();
    }

    public String baseDirectoryDisplay() {
        return BASE_DIRECTORY.toString();
    }

    public ResolvedAssetProject resolve(String subdirectoryInput) {
        String trimmedInput = Objects.requireNonNull(subdirectoryInput, "subdirectoryInput").trim();
        if (trimmedInput.isEmpty()) {
            throw new IllegalArgumentException("Asset project subdirectory must not be empty.");
        }

        Path inputPath = Path.of(trimmedInput);
        if (inputPath.isAbsolute()) {
            throw new IllegalArgumentException("Absolute paths are not allowed. Enter a subdirectory under assets.");
        }
        if (containsParentTraversal(inputPath)) {
            throw new IllegalArgumentException("Parent traversal is not allowed. Enter a subdirectory under assets.");
        }

        Path normalizedResolvedPath = normalizedBaseDirectory.resolve(inputPath)
                .toAbsolutePath()
                .normalize();
        if (!normalizedResolvedPath.startsWith(normalizedBaseDirectory)) {
            throw new IllegalArgumentException("Asset project path must stay under assets.");
        }
        if (!Files.exists(normalizedResolvedPath)) {
            throw new IllegalArgumentException("Asset project does not exist: " + displayPath(inputPath));
        }
        if (!Files.isDirectory(normalizedResolvedPath)) {
            throw new IllegalArgumentException("Asset project is not a directory: " + displayPath(inputPath));
        }

        return new ResolvedAssetProject(normalizedResolvedPath, displayPath(inputPath));
    }

    private static boolean containsParentTraversal(Path path) {
        for (Path part : path) {
            if ("..".equals(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static String displayPath(Path subdirectory) {
        return BASE_DIRECTORY.resolve(subdirectory).normalize().toString();
    }

    public record ResolvedAssetProject(Path normalizedPath, String displayPath) {
    }
}
