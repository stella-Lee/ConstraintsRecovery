package spl.grouping;

import spl.ConditionalBlock;
import spl.ProductSignature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

public final class SignatureBlockGrouper {
    private static final Comparator<ConditionalBlock> BLOCK_ORDER =
            Comparator.comparing((ConditionalBlock block) -> block.filePath().toString())
                    .thenComparingInt(ConditionalBlock::startLine);

    public List<SignatureGroup> group(Collection<ConditionalBlock> blocks) {
        Objects.requireNonNull(blocks, "blocks");
        Map<SignatureKey, List<ConditionalBlock>> groups = new TreeMap<>();
        for (ConditionalBlock block : blocks) {
            collectBlock(block, groups);
        }

        List<SignatureGroup> result = new ArrayList<>();
        int groupNumber = 1;
        for (Map.Entry<SignatureKey, List<ConditionalBlock>> entry : groups.entrySet()) {
            List<ConditionalBlock> orderedBlocks = entry.getValue().stream()
                    .sorted(BLOCK_ORDER)
                    .toList();
            result.add(new SignatureGroup(
                    "G" + groupNumber,
                    entry.getKey().toProductSignature(),
                    orderedBlocks
            ));
            groupNumber++;
        }
        return result;
    }

    private static void collectBlock(ConditionalBlock block, Map<SignatureKey, List<ConditionalBlock>> groups) {
        Objects.requireNonNull(block, "block");
        if (isActualConditionalBranch(block) && !block.signature().isEmpty()) {
            groups.computeIfAbsent(SignatureKey.from(block.signature()), ignored -> new ArrayList<>())
                    .add(block);
        }
        for (ConditionalBlock child : block.children()) {
            collectBlock(child, groups);
        }
    }

    private static boolean isActualConditionalBranch(ConditionalBlock block) {
        return block.directiveType() == ConditionalBlock.DirectiveType.IF
                || block.directiveType() == ConditionalBlock.DirectiveType.ELIF
                || block.directiveType() == ConditionalBlock.DirectiveType.ELSE;
    }

    private record SignatureKey(List<String> productIds) implements Comparable<SignatureKey> {
        private SignatureKey {
            productIds = List.copyOf(productIds);
        }

        private static SignatureKey from(ProductSignature signature) {
            TreeSet<String> canonicalIds = new TreeSet<>(signature.productIds());
            return new SignatureKey(new ArrayList<>(canonicalIds));
        }

        private ProductSignature toProductSignature() {
            return new ProductSignature(String.join("|", productIds));
        }

        @Override
        public int compareTo(SignatureKey other) {
            int commonSize = Math.min(productIds.size(), other.productIds.size());
            for (int index = 0; index < commonSize; index++) {
                int comparison = productIds.get(index).compareTo(other.productIds.get(index));
                if (comparison != 0) {
                    return comparison;
                }
            }
            return Integer.compare(productIds.size(), other.productIds.size());
        }
    }
}
