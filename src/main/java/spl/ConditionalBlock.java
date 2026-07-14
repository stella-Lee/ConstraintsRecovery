package spl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ConditionalBlock {
    public enum DirectiveType {
        IF("#if"),
        ELIF("#elif"),
        ELSE("#else"),
        ENDIF("#endif");

        private final String token;

        DirectiveType(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    private final Path filePath;
    private final DirectiveType directiveType;
    private final int startLine;
    private final int nestingDepth;
    private final ProductSignature signature;
    private final List<ConditionalBlock> children = new ArrayList<>();
    private int endLine;

    public ConditionalBlock(Path filePath, DirectiveType directiveType, int startLine,
                            int nestingDepth, ProductSignature signature) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.directiveType = Objects.requireNonNull(directiveType, "directiveType");
        this.startLine = startLine;
        this.endLine = startLine;
        this.nestingDepth = nestingDepth;
        this.signature = signature == null ? new ProductSignature("") : signature;
    }

    public Path filePath() {
        return filePath;
    }

    public DirectiveType directiveType() {
        return directiveType;
    }

    public int startLine() {
        return startLine;
    }

    public int endLine() {
        return endLine;
    }

    public int nestingDepth() {
        return nestingDepth;
    }

    public ProductSignature signature() {
        return signature;
    }

    public List<ConditionalBlock> children() {
        return Collections.unmodifiableList(children);
    }

    void addChild(ConditionalBlock child) {
        children.add(Objects.requireNonNull(child, "child"));
    }

    List<ConditionalBlock> mutableChildren() {
        return children;
    }

    void setEndLine(int endLine) {
        this.endLine = Math.max(startLine, endLine);
    }
}
