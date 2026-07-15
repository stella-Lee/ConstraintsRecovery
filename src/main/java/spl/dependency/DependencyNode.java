package spl.dependency;

import spl.ProductSignature;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record DependencyNode(
        String entityId,
        JavaEntityType entityType,
        String simpleName,
        String qualifiedName,
        Path sourceFile,
        int startLine,
        int endLine,
        ProductSignature effectiveProductSignature,
        String signatureGroupId,
        List<String> observedProducts
) {
    public DependencyNode {
        entityId = Objects.requireNonNull(entityId, "entityId");
        entityType = Objects.requireNonNull(entityType, "entityType");
        simpleName = Objects.requireNonNull(simpleName, "simpleName");
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        effectiveProductSignature = Objects.requireNonNull(effectiveProductSignature, "effectiveProductSignature");
        signatureGroupId = Objects.requireNonNull(signatureGroupId, "signatureGroupId");
        observedProducts = List.copyOf(Objects.requireNonNull(observedProducts, "observedProducts"));
    }

    public static DependencyNode from(JavaEntity entity) {
        return new DependencyNode(
                entity.entityId(),
                entity.entityType(),
                entity.simpleName(),
                entity.qualifiedName(),
                entity.sourceFile(),
                entity.startLine(),
                entity.endLine(),
                entity.effectiveProductSignature(),
                entity.signatureGroupId(),
                entity.observedProducts()
        );
    }
}
