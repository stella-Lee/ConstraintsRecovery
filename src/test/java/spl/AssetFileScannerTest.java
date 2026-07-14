package spl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetFileScannerTest {
    @TempDir
    Path tempDir;

    @Test
    void scansMultipleAssetFilesInStableOrder() throws Exception {
        Path nested = Files.createDirectories(tempDir.resolve("nested"));
        Path second = Files.writeString(tempDir.resolve("b.java"), "class B {}");
        Path first = Files.writeString(nested.resolve("a.java"), "class A {}");

        List<Path> files = new AssetFileScanner().scan(tempDir);

        assertEquals(List.of(second.toAbsolutePath().normalize(), first.toAbsolutePath().normalize()), files);
    }

    @Test
    void returnsSingleFileWhenAssetPathIsAFile() throws Exception {
        Path file = Files.writeString(tempDir.resolve("single.java"), "class Single {}");

        List<Path> files = new AssetFileScanner().scan(file);

        assertEquals(List.of(file.toAbsolutePath().normalize()), files);
    }
}
