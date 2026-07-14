package spl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

public final class ResultExporter {
    public void exportTsv(Collection<ConditionalBlock> blocks, Path outputFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("file\tdirective\tstartLine\tendLine\tnestingDepth\tproductIds\n");
        for (ConditionalBlock block : blocks) {
            appendBlock(builder, block);
        }
        Path parent = outputFile.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private static void appendBlock(StringBuilder builder, ConditionalBlock block) {
        builder.append(block.filePath())
                .append('\t')
                .append(block.directiveType().token())
                .append('\t')
                .append(block.startLine())
                .append('\t')
                .append(block.endLine())
                .append('\t')
                .append(block.nestingDepth())
                .append('\t')
                .append(String.join("|", block.signature().productIds()))
                .append('\n');
        for (ConditionalBlock child : block.children()) {
            appendBlock(builder, child);
        }
    }
}
