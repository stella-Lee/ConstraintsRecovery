package spl.entity;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductProjectionGeneratorTest {
    private final ProductProjectionGenerator generator = new ProductProjectionGenerator();

    @Test
    void projectsIfElifBranchesForSelectedProduct() {
        String source = """
                class Sample {
                //#if Enterprise
                int enterprise;
                //#elif Professional
                int professional;
                //#endif
                }
                """;

        ProductProjection projection = generator.generate(Path.of("asset"), source, "Professional");

        assertBlank(projection, 2);
        assertBlank(projection, 3);
        assertContainsLine(projection, 5, "int professional;");
    }

    @Test
    void projectsElseBranchWhenNoEarlierBranchMatches() {
        String source = """
                class Sample {
                //#if Enterprise
                int enterprise;
                //#else
                int starter;
                //#endif
                }
                """;

        ProductProjection projection = generator.generate(Path.of("asset"), source, "Starter");

        assertBlank(projection, 3);
        assertContainsLine(projection, 5, "int starter;");
    }

    @Test
    void handlesNestedConditionalsAndCodeAfterNestedEndif() {
        String source = """
                class Sample {
                //#if Enterprise|Professional
                int shared;
                //#if Enterprise
                int enterprise;
                //#endif
                int afterNested;
                //#endif
                }
                """;

        ProductProjection projection = generator.generate(Path.of("asset"), source, "Professional");

        assertContainsLine(projection, 3, "int shared;");
        assertBlank(projection, 5);
        assertContainsLine(projection, 7, "int afterNested;");
    }

    @Test
    void preservesOriginalLineCount() {
        String source = """
                //#if Enterprise
                class A {
                }
                //#endif
                """;

        ProductProjection projection = generator.generate(Path.of("asset"), source, "Enterprise");

        assertEquals(4, projection.lineCount());
        assertEquals(4, projection.source().split("\\R", -1).length);
    }

    @Test
    void supportsConditionalImportsAndTypeFragments() {
        String source = """
                //#if Enterprise
                import java.util.List;
                //#endif
                class Sample
                //#if Enterprise
                        extends Base
                //#endif
                {
                }
                """;

        ProductProjection projection = generator.generate(Path.of("asset"), source, "Enterprise");

        assertContainsLine(projection, 2, "import java.util.List;");
        assertContainsLine(projection, 6, "extends Base");
    }

    private static void assertBlank(ProductProjection projection, int lineNumber) {
        assertTrue(lines(projection)[lineNumber - 1].isBlank());
    }

    private static void assertContainsLine(ProductProjection projection, int lineNumber, String expected) {
        assertEquals(expected, lines(projection)[lineNumber - 1].trim());
    }

    private static String[] lines(ProductProjection projection) {
        return projection.source().split("\\R", -1);
    }
}
