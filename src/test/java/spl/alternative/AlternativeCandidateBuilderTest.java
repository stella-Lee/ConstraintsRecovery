package spl.alternative;

import org.junit.jupiter.api.Test;
import spl.ConditionalBlock;
import spl.ProductSignature;
import spl.dependency.DependencyGraph;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;
import spl.entity.ResolutionStatus;
import spl.grouping.SignatureGroup;
import spl.requiresanalysis.RequiresEvidenceBuilder;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlternativeCandidateBuilderTest {
    @Test
    void siblingBranchesWithSameEntitySignatureProduceHighCandidate() throws Exception {
        ConditionalBlock fifo = block(ConditionalBlock.DirectiveType.IF, 1, 3, "FIFO");
        ConditionalBlock shortest = block(ConditionalBlock.DirectiveType.ELIF, 4, 6, "ShortestPath");
        List<ConditionalBlock> roots = List.of(fifo, shortest);
        JavaEntity left = method("E1", "select", "Scheduler.select()", RequiresEvidenceBuilder.blockId(fifo));
        JavaEntity right = method("E2", "select", "Scheduler.select()", RequiresEvidenceBuilder.blockId(shortest));

        AlternativeCandidateResult result = builder().build(roots, groups(fifo, shortest), List.of(left, right),
                new DependencyGraph(List.of(), List.of(), List.of()));

        assertEquals(1, result.candidates().size());
        AlternativeCandidate candidate = result.candidates().get(0);
        assertEquals(AlternativeConfidence.HIGH, candidate.confidence());
        assertTrue(candidate.evidenceCategories().contains(AlternativeEvidenceCategory.SIBLING_BRANCH));
        assertTrue(candidate.evidenceCategories().contains(AlternativeEvidenceCategory.SAME_ENTITY_SIGNATURE));
    }

    @Test
    void disjointSignatureAloneDoesNotCreateCandidate() throws Exception {
        ConditionalBlock left = block(Path.of("assetA.java"), ConditionalBlock.DirectiveType.IF, 1, 3, "A");
        ConditionalBlock end = block(Path.of("assetA.java"), ConditionalBlock.DirectiveType.ENDIF, 4, 4, "");
        ConditionalBlock right = block(Path.of("assetB.java"), ConditionalBlock.DirectiveType.IF, 10, 12, "B");

        AlternativeCandidateResult result = builder().build(List.of(left, end, right), groups(left, right), List.of(),
                new DependencyGraph(List.of(), List.of(), List.of()));

        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void aggregatesByNormalizedConditionPair() throws Exception {
        ConditionalBlock a1 = block(ConditionalBlock.DirectiveType.IF, 1, 3, "A");
        ConditionalBlock b1 = block(ConditionalBlock.DirectiveType.ELIF, 4, 6, "B");
        ConditionalBlock end1 = block(ConditionalBlock.DirectiveType.ENDIF, 7, 7, "");
        ConditionalBlock b2 = block(ConditionalBlock.DirectiveType.IF, 20, 22, "B");
        ConditionalBlock a2 = block(ConditionalBlock.DirectiveType.ELIF, 23, 25, "A");
        ConditionalBlock end2 = block(ConditionalBlock.DirectiveType.ENDIF, 26, 26, "");
        JavaEntity e1 = method("E1", "sortQueue", "Scheduler.sortQueue()", RequiresEvidenceBuilder.blockId(a1));
        JavaEntity e2 = method("E2", "sortQueue", "Scheduler.sortQueue()", RequiresEvidenceBuilder.blockId(b1));
        JavaEntity e3 = method("E3", "sortQueue", "Scheduler.sortQueue()", RequiresEvidenceBuilder.blockId(b2));
        JavaEntity e4 = method("E4", "sortQueue", "Scheduler.sortQueue()", RequiresEvidenceBuilder.blockId(a2));

        AlternativeCandidateResult result = builder().build(List.of(a1, b1, end1, b2, a2, end2),
                groups(a1, b1, b2, a2),
                List.of(e1, e2, e3, e4), new DependencyGraph(List.of(), List.of(), List.of()));

        assertEquals(1, result.candidates().size());
        assertEquals(4, result.candidates().get(0).supportingBlockPairs().size());
    }

    private AlternativeCandidateBuilder builder() {
        return new AlternativeCandidateBuilder();
    }

    private List<SignatureGroup> groups(ConditionalBlock... blocks) {
        return List.of(new SignatureGroup("G1", new ProductSignature("A"), List.of(blocks)));
    }

    private JavaEntity method(String id, String simpleName, String qualifiedName, String blockId) {
        return new JavaEntity(id, JavaEntityType.METHOD, simpleName, qualifiedName, Path.of("asset.java"),
                2, 2, blockId, "G1", new ProductSignature("A"), List.of("A"), ResolutionStatus.RESOLVED_INTERNAL);
    }

    private ConditionalBlock block(ConditionalBlock.DirectiveType type, int startLine, int endLine, String signature)
            throws Exception {
        return block(Path.of("asset.java"), type, startLine, endLine, signature);
    }

    private ConditionalBlock block(Path file, ConditionalBlock.DirectiveType type, int startLine, int endLine,
                                   String signature) throws Exception {
        ConditionalBlock block = new ConditionalBlock(file, type, startLine, 0,
                new ProductSignature(signature));
        Method setEndLine = ConditionalBlock.class.getDeclaredMethod("setEndLine", int.class);
        setEndLine.setAccessible(true);
        setEndLine.invoke(block, endLine);
        return block;
    }
}
