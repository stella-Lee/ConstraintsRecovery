package spl.entity;

import spl.ProductSignature;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProductProjectionGenerator {
    private static final Pattern DIRECTIVE_PATTERN =
            Pattern.compile("^\\s*//\\s*#(if|elif|else|endif)\\b\\s*(.*)$");
    private static final Pattern VARIANT_CODE_PATTERN =
            Pattern.compile("^(\\s*)//@(.*)$");

    public ProductProjection generate(Path sourceFile, String source, String product) {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(product, "product");

        String[] lines = source.split("\\R", -1);
        int lineCount = source.endsWith("\n") || source.endsWith("\r") ? lines.length - 1 : lines.length;
        List<String> projectedLines = new ArrayList<>(lineCount);
        ArrayDeque<Frame> stack = new ArrayDeque<>();

        for (int index = 0; index < lineCount; index++) {
            String line = lines[index];
            Matcher matcher = DIRECTIVE_PATTERN.matcher(line);
            if (matcher.matches()) {
                handleDirective(matcher.group(1), matcher.group(2).trim(), product, stack);
                projectedLines.add("");
            } else {
                projectedLines.add(projectLine(line, isActive(stack)));
            }
        }

        return new ProductProjection(sourceFile, product, String.join(System.lineSeparator(), projectedLines), lineCount);
    }

    private static String projectLine(String line, boolean active) {
        if (!active) {
            return "";
        }
        Matcher variantCode = VARIANT_CODE_PATTERN.matcher(line);
        if (variantCode.matches()) {
            String restored = variantCode.group(2);
            if (restored.matches("[A-Z][A-Za-z0-9_]*(\\s*\\(.*)?")) {
                return variantCode.group(1) + "@" + restored;
            }
            return variantCode.group(1) + restored;
        }
        return line;
    }

    private static void handleDirective(String directive, String expression, String product, ArrayDeque<Frame> stack) {
        switch (directive) {
            case "if" -> {
                boolean parentActive = isActive(stack);
                boolean branchMatches = containsProduct(expression, product);
                stack.push(new Frame(parentActive, branchMatches, parentActive && branchMatches));
            }
            case "elif" -> {
                if (!stack.isEmpty()) {
                    Frame frame = stack.peek();
                    boolean branchMatches = containsProduct(expression, product);
                    frame.currentActive = frame.parentActive && !frame.branchTaken && branchMatches;
                    frame.branchTaken = frame.branchTaken || branchMatches;
                }
            }
            case "else" -> {
                if (!stack.isEmpty()) {
                    Frame frame = stack.peek();
                    frame.currentActive = frame.parentActive && !frame.branchTaken;
                    frame.branchTaken = true;
                }
            }
            case "endif" -> {
                if (!stack.isEmpty()) {
                    stack.pop();
                }
            }
            default -> {
            }
        }
    }

    private static boolean isActive(ArrayDeque<Frame> stack) {
        return stack.isEmpty() || stack.peek().currentActive;
    }

    private static boolean containsProduct(String expression, String product) {
        return new ProductSignature(expression).productIds().contains(product);
    }

    private static final class Frame {
        private final boolean parentActive;
        private boolean branchTaken;
        private boolean currentActive;

        private Frame(boolean parentActive, boolean branchTaken, boolean currentActive) {
            this.parentActive = parentActive;
            this.branchTaken = branchTaken;
            this.currentActive = currentActive;
        }
    }
}
