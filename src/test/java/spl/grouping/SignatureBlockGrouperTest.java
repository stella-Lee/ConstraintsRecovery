package spl.grouping;

import org.junit.jupiter.api.Test;
import spl.ConditionalBlock;
import spl.ProductSignatureExtractor;
import spl.ProductSignature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureBlockGrouperTest {
    private final SignatureBlockGrouper grouper = new SignatureBlockGrouper();

    @Test
    void groupsIdenticalSignatures() {
        ConditionalBlock first = block("asset1", 3, "Enterprise|Professional");
        ConditionalBlock second = block("asset2", 9, "Enterprise|Professional");

        List<SignatureGroup> groups = grouper.group(List.of(first, second));

        assertEquals(1, groups.size());
        assertEquals("Enterprise|Professional", groups.get(0).canonicalSignature().rawExpression());
        assertEquals(List.of(first, second), groups.get(0).blocks());
    }

    @Test
    void groupsDifferentProductOrderingBySetEquality() {
        ConditionalBlock first = block("asset1", 3, "Enterprise|Professional");
        ConditionalBlock second = block("asset2", 9, "Professional|Enterprise");

        List<SignatureGroup> groups = grouper.group(List.of(first, second));

        assertEquals(1, groups.size());
        assertEquals("Enterprise|Professional", groups.get(0).canonicalSignature().rawExpression());
        assertEquals(2, groups.get(0).blocks().size());
    }

    @Test
    void separatesDifferentSignatures() {
        ConditionalBlock first = block("asset1", 3, "Enterprise");
        ConditionalBlock second = block("asset2", 9, "Starter");

        List<SignatureGroup> groups = grouper.group(List.of(first, second));

        assertEquals(2, groups.size());
        assertEquals("Enterprise", groups.get(0).canonicalSignature().rawExpression());
        assertEquals("Starter", groups.get(1).canonicalSignature().rawExpression());
    }

    @Test
    void preservesDuplicateBlocks() {
        ConditionalBlock duplicate = block("asset1", 3, "Enterprise");

        List<SignatureGroup> groups = grouper.group(List.of(duplicate, duplicate));

        assertEquals(1, groups.size());
        assertEquals(2, groups.get(0).blocks().size());
        assertEquals(List.of(duplicate, duplicate), groups.get(0).blocks());
    }

    @Test
    void returnsEmptyGroupsForEmptyInput() {
        assertTrue(grouper.group(List.of()).isEmpty());
    }

    @Test
    void excludesEndifBlocksAndEmptySignaturesFromGrouping() {
        ConditionalBlock branch = block("asset1", 3, "Enterprise");
        ConditionalBlock end = new ConditionalBlock(
                Path.of("assets").resolve("elevator").resolve("asset1"),
                ConditionalBlock.DirectiveType.ENDIF,
                8,
                0,
                new ProductSignature("")
        );

        List<SignatureGroup> groups = grouper.group(List.of(branch, end));

        assertEquals(1, groups.size());
        assertEquals(List.of(branch), groups.get(0).blocks());
    }

    @Test
    void codeAfterNestedEndifDoesNotCreateEmptySignatureGroup() throws Exception {
        Path file = Files.createTempFile("nested-after-endif", ".asset");
        Files.writeString(file, """
                //#if Enterprise|Professional
                //#if Professional
                int nested;
                //#endif
                int afterNested;
                //#endif
                """);

        List<ConditionalBlock> blocks = new ProductSignatureExtractor().extractFromFile(file);
        List<SignatureGroup> groups = grouper.group(blocks);

        assertEquals(2, groups.size());
        assertEquals("Enterprise|Professional", groups.get(0).canonicalSignature().rawExpression());
        assertEquals("Professional", groups.get(1).canonicalSignature().rawExpression());
    }

    @Test
    void producesDeterministicGroupAndBlockOrdering() {
        ConditionalBlock starter = block("z-asset", 20, "Starter");
        ConditionalBlock enterpriseLate = block("b-asset", 30, "Professional|Enterprise");
        ConditionalBlock enterpriseEarly = block("a-asset", 10, "Enterprise|Professional");
        ConditionalBlock basic = block("c-asset", 1, "HomeBasic|Starter");

        List<SignatureGroup> groups = grouper.group(List.of(starter, enterpriseLate, enterpriseEarly, basic));

        assertEquals(List.of("G1", "G2", "G3"), groups.stream().map(SignatureGroup::groupId).toList());
        assertEquals("Enterprise|Professional", groups.get(0).canonicalSignature().rawExpression());
        assertEquals("HomeBasic|Starter", groups.get(1).canonicalSignature().rawExpression());
        assertEquals("Starter", groups.get(2).canonicalSignature().rawExpression());
        assertEquals(List.of(enterpriseEarly, enterpriseLate), groups.get(0).blocks());
    }

    private static ConditionalBlock block(String fileName, int startLine, String signature) {
        return new ConditionalBlock(
                Path.of("assets").resolve("elevator").resolve(fileName),
                ConditionalBlock.DirectiveType.IF,
                startLine,
                0,
                new ProductSignature(signature)
        );
    }
}
