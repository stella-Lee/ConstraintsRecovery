package spl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetProjectPathResolverTest {
    @TempDir
    Path repositoryRoot;

    @Test
    void resolvesSubdirectoryUnderAssets() throws Exception {
        Files.createDirectories(repositoryRoot.resolve("assets").resolve("elevator"));
        AssetProjectPathResolver resolver = new AssetProjectPathResolver(repositoryRoot);

        AssetProjectPathResolver.ResolvedAssetProject resolved = resolver.resolve("elevator");

        assertEquals("assets", resolver.baseDirectoryDisplay());
        assertEquals("assets" + repositoryRoot.getFileSystem().getSeparator() + "elevator", resolved.displayPath());
        assertEquals(repositoryRoot.resolve("assets").resolve("elevator").toAbsolutePath().normalize(),
                resolved.normalizedPath());
    }

    @Test
    void rejectsAbsolutePathInput() throws Exception {
        Files.createDirectories(repositoryRoot.resolve("assets").resolve("elevator"));
        AssetProjectPathResolver resolver = new AssetProjectPathResolver(repositoryRoot);

        Path absolutePath = repositoryRoot.resolve("assets").resolve("elevator").toAbsolutePath();

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(absolutePath.toString()));
    }

    @Test
    void rejectsParentTraversal() throws Exception {
        Files.createDirectories(repositoryRoot.resolve("assets").resolve("elevator"));
        AssetProjectPathResolver resolver = new AssetProjectPathResolver(repositoryRoot);

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("../elevator"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("elevator/../outside"));
    }

    @Test
    void rejectsMissingPathAndFiles() throws Exception {
        Path assets = Files.createDirectories(repositoryRoot.resolve("assets"));
        Files.writeString(assets.resolve("not-directory"), "content");
        AssetProjectPathResolver resolver = new AssetProjectPathResolver(repositoryRoot);

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("missing"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("not-directory"));
    }

    @Test
    void normalizedPathStaysInsideAssets() throws Exception {
        Files.createDirectories(repositoryRoot.resolve("assets").resolve("elevator"));
        AssetProjectPathResolver resolver = new AssetProjectPathResolver(repositoryRoot);

        AssetProjectPathResolver.ResolvedAssetProject resolved = resolver.resolve("./elevator");

        assertTrue(resolved.normalizedPath().startsWith(repositoryRoot.resolve("assets").toAbsolutePath().normalize()));
        assertEquals("assets" + repositoryRoot.getFileSystem().getSeparator() + "elevator", resolved.displayPath());
    }
}
