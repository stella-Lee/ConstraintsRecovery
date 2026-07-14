package spl.grouping;

import spl.ConditionalBlock;
import spl.ProductSignature;

import java.util.List;
import java.util.Objects;

public record SignatureGroup(
        String groupId,
        ProductSignature canonicalSignature,
        List<ConditionalBlock> blocks
) {
    public SignatureGroup {
        groupId = Objects.requireNonNull(groupId, "groupId");
        canonicalSignature = Objects.requireNonNull(canonicalSignature, "canonicalSignature");
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
    }
}
