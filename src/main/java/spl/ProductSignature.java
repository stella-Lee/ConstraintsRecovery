package spl;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class ProductSignature {
    private final String rawExpression;
    private final List<String> productIds;

    public ProductSignature(String rawExpression) {
        this.rawExpression = rawExpression == null ? "" : rawExpression.trim();
        this.productIds = parseProductIds(this.rawExpression);
    }

    private static List<String> parseProductIds(String expression) {
        if (expression.isBlank()) {
            return List.of();
        }
        return Arrays.stream(expression.split("\\|"))
                .map(String::trim)
                .filter(productId -> !productId.isEmpty())
                .toList();
    }

    public String rawExpression() {
        return rawExpression;
    }

    public List<String> productIds() {
        return productIds;
    }

    public boolean isEmpty() {
        return productIds.isEmpty();
    }

    @Override
    public String toString() {
        return rawExpression;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProductSignature that)) {
            return false;
        }
        return rawExpression.equals(that.rawExpression) && productIds.equals(that.productIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawExpression, productIds);
    }
}
