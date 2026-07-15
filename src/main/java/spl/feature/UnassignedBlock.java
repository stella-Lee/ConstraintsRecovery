package spl.feature;

import spl.ConditionalBlock;
import spl.ProductSignature;

import java.nio.file.Path;
import java.util.Objects;

public record UnassignedBlock(
        String blockId,
        String groupId,
        ProductSignature signature,
        Path file,
        ConditionalBlock.DirectiveType branchType,
        int startLine,
        int endLine,
        String reason
) {
    public UnassignedBlock {
        blockId = Objects.requireNonNull(blockId, "blockId");
        groupId = Objects.requireNonNull(groupId, "groupId");
        signature = Objects.requireNonNull(signature, "signature");
        file = Objects.requireNonNull(file, "file");
        branchType = Objects.requireNonNull(branchType, "branchType");
        reason = Objects.requireNonNull(reason, "reason");
    }
}
