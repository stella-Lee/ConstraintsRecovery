package spl;

import spl.ConditionalBlock.DirectiveType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProductSignatureExtractor {
    private static final Pattern DIRECTIVE_PATTERN =
            Pattern.compile("^\\s*//\\s*#(if|elif|else|endif)\\b\\s*(.*)$");

    private final AssetFileScanner scanner;

    public ProductSignatureExtractor() {
        this(new AssetFileScanner());
    }

    public ProductSignatureExtractor(AssetFileScanner scanner) {
        this.scanner = Objects.requireNonNull(scanner, "scanner");
    }

    public List<ConditionalBlock> extract(Path assetPath) throws IOException {
        return extractFromFiles(scanner.scan(assetPath));
    }

    public List<ConditionalBlock> extractFromFiles(Collection<Path> files) throws IOException {
        List<ConditionalBlock> blocks = new ArrayList<>();
        for (Path file : files) {
            blocks.addAll(extractFromFile(file));
        }
        return blocks;
    }

    public List<ConditionalBlock> extractFromFile(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Path normalizedFile = file.toAbsolutePath().normalize();
        List<ConditionalBlock> roots = new ArrayList<>();
        ArrayDeque<ConditionalFrame> stack = new ArrayDeque<>();

        for (int index = 0; index < lines.size(); index++) {
            int lineNumber = index + 1;
            Matcher matcher = DIRECTIVE_PATTERN.matcher(lines.get(index));
            if (!matcher.matches()) {
                continue;
            }

            DirectiveType type = parseDirectiveType(matcher.group(1));
            String expression = matcher.group(2).trim();
            switch (type) {
                case IF -> openIf(normalizedFile, lineNumber, expression, roots, stack);
                case ELIF -> switchBranch(normalizedFile, DirectiveType.ELIF, lineNumber, expression, stack);
                case ELSE -> switchBranch(normalizedFile, DirectiveType.ELSE, lineNumber, "", stack);
                case ENDIF -> closeIf(normalizedFile, lineNumber, stack);
            }
        }

        int lastLine = lines.size();
        for (ConditionalFrame frame : stack) {
            frame.currentBranch().setEndLine(lastLine);
        }
        return roots;
    }

    private static void openIf(Path file, int lineNumber, String expression,
                               List<ConditionalBlock> roots, ArrayDeque<ConditionalFrame> stack) {
        List<ConditionalBlock> target = currentChildren(roots, stack);
        ConditionalBlock block = new ConditionalBlock(
                file,
                DirectiveType.IF,
                lineNumber,
                stack.size(),
                new ProductSignature(expression)
        );
        target.add(block);
        stack.push(new ConditionalFrame(target, block, stack.size()));
    }

    private static void switchBranch(Path file, DirectiveType type, int lineNumber, String expression,
                                     ArrayDeque<ConditionalFrame> stack) {
        if (stack.isEmpty()) {
            return;
        }
        ConditionalFrame frame = stack.peek();
        frame.currentBranch().setEndLine(lineNumber - 1);
        ConditionalBlock block = new ConditionalBlock(
                file,
                type,
                lineNumber,
                frame.depth(),
                new ProductSignature(expression)
        );
        frame.siblings().add(block);
        frame.setCurrentBranch(block);
    }

    private static void closeIf(Path file, int lineNumber, ArrayDeque<ConditionalFrame> stack) {
        if (stack.isEmpty()) {
            return;
        }
        ConditionalFrame frame = stack.pop();
        frame.currentBranch().setEndLine(lineNumber - 1);
        ConditionalBlock endBlock = new ConditionalBlock(
                file,
                DirectiveType.ENDIF,
                lineNumber,
                frame.depth(),
                new ProductSignature("")
        );
        frame.siblings().add(endBlock);
    }

    private static List<ConditionalBlock> currentChildren(List<ConditionalBlock> roots,
                                                          ArrayDeque<ConditionalFrame> stack) {
        if (stack.isEmpty()) {
            return roots;
        }
        return stack.peek().currentBranch().mutableChildren();
    }

    private static DirectiveType parseDirectiveType(String token) {
        return DirectiveType.valueOf(token.toUpperCase(Locale.ROOT));
    }

    private static final class ConditionalFrame {
        private final List<ConditionalBlock> siblings;
        private final int depth;
        private ConditionalBlock currentBranch;

        private ConditionalFrame(List<ConditionalBlock> siblings, ConditionalBlock currentBranch, int depth) {
            this.siblings = siblings;
            this.currentBranch = currentBranch;
            this.depth = depth;
        }

        private List<ConditionalBlock> siblings() {
            return siblings;
        }

        private ConditionalBlock currentBranch() {
            return currentBranch;
        }

        private void setCurrentBranch(ConditionalBlock currentBranch) {
            this.currentBranch = currentBranch;
        }

        private int depth() {
            return depth;
        }
    }

}
