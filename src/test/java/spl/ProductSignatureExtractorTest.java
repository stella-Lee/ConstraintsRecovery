package spl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductSignatureExtractorTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsProductIdBranchesAndEndDirective() throws Exception {
        Path file = tempDir.resolve("Example.java");
        Files.writeString(file, """
                class Example {
                //#if P1 | P2
                int a;
                //#elif P3
                int b;
                //#else
                int c;
                //#endif
                }
                """);

        List<ConditionalBlock> blocks = new ProductSignatureExtractor().extractFromFile(file);

        assertEquals(4, blocks.size());
        assertEquals(ConditionalBlock.DirectiveType.IF, blocks.get(0).directiveType());
        assertEquals(List.of("P1", "P2"), blocks.get(0).signature().productIds());
        assertEquals(2, blocks.get(0).startLine());
        assertEquals(3, blocks.get(0).endLine());
        assertEquals(0, blocks.get(0).nestingDepth());

        assertEquals(ConditionalBlock.DirectiveType.ELIF, blocks.get(1).directiveType());
        assertEquals(List.of("P3"), blocks.get(1).signature().productIds());
        assertEquals(4, blocks.get(1).startLine());
        assertEquals(5, blocks.get(1).endLine());

        assertEquals(ConditionalBlock.DirectiveType.ELSE, blocks.get(2).directiveType());
        assertTrue(blocks.get(2).signature().isEmpty());
        assertEquals(6, blocks.get(2).startLine());
        assertEquals(7, blocks.get(2).endLine());

        assertEquals(ConditionalBlock.DirectiveType.ENDIF, blocks.get(3).directiveType());
        assertEquals(8, blocks.get(3).startLine());
        assertEquals(8, blocks.get(3).endLine());
    }

    @Test
    void preservesNestedConditionalStructure() throws Exception {
        Path file = tempDir.resolve("Nested.java");
        Files.writeString(file, """
                //#if OUTER
                int a;
                //#if INNER_A|INNER_B
                int b;
                //#endif
                //#endif
                """);

        List<ConditionalBlock> blocks = new ProductSignatureExtractor().extractFromFile(file);

        assertEquals(2, blocks.size());
        ConditionalBlock outer = blocks.get(0);
        assertEquals(ConditionalBlock.DirectiveType.IF, outer.directiveType());
        assertEquals(0, outer.nestingDepth());
        assertEquals(List.of("OUTER"), outer.signature().productIds());
        assertEquals(2, outer.children().size());

        ConditionalBlock inner = outer.children().get(0);
        assertEquals(ConditionalBlock.DirectiveType.IF, inner.directiveType());
        assertEquals(1, inner.nestingDepth());
        assertEquals(List.of("INNER_A", "INNER_B"), inner.signature().productIds());
        assertEquals(3, inner.startLine());
        assertEquals(4, inner.endLine());

        ConditionalBlock innerEnd = outer.children().get(1);
        assertEquals(ConditionalBlock.DirectiveType.ENDIF, innerEnd.directiveType());
        assertEquals(1, innerEnd.nestingDepth());
    }
}
