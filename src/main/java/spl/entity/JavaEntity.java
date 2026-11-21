package spl.entity;

import spl.ProductSignature;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record JavaEntity(
        String entityId,
        JavaEntityType entityType,
        String simpleName,
        String qualifiedName,
        Path sourceFile,
        int startLine,
        int endLine,
        String containingBlockId,
        String signatureGroupId,
        ProductSignature effectiveProductSignature,
        List<String> observedProducts,
        ResolutionStatus resolutionStatus,
        Map<String, String> declaredFieldTypes,
        String declaringTypeName
) {
    public JavaEntity(String entityId, JavaEntityType entityType, String simpleName, String qualifiedName,
                      Path sourceFile, int startLine, int endLine, String containingBlockId,
                      String signatureGroupId, ProductSignature effectiveProductSignature,
                      List<String> observedProducts, ResolutionStatus resolutionStatus) {
        this(entityId, entityType, simpleName, qualifiedName, sourceFile, startLine, endLine, containingBlockId,
                signatureGroupId, effectiveProductSignature, observedProducts, resolutionStatus, Map.of());
    }

    public JavaEntity(String entityId, JavaEntityType entityType, String simpleName, String qualifiedName,
                      Path sourceFile, int startLine, int endLine, String containingBlockId,
                      String signatureGroupId, ProductSignature effectiveProductSignature,
                      List<String> observedProducts, ResolutionStatus resolutionStatus,
                      Map<String, String> declaredFieldTypes) {
        this(entityId, entityType, simpleName, qualifiedName, sourceFile, startLine, endLine, containingBlockId,
                signatureGroupId, effectiveProductSignature, observedProducts, resolutionStatus, declaredFieldTypes,
                memberNamePrefix(entityType, qualifiedName));
    }

    private static String memberNamePrefix(JavaEntityType type, String qualifiedName) {
        if (type == JavaEntityType.CLASS || type == JavaEntityType.INTERFACE || qualifiedName == null) {
            return "";
        }
        int separator = qualifiedName.lastIndexOf('.');
        return separator < 0 ? "" : qualifiedName.substring(0, separator);
    }

    public JavaEntity {
        entityId = Objects.requireNonNull(entityId, "entityId");
        entityType = Objects.requireNonNull(entityType, "entityType");
        simpleName = Objects.requireNonNull(simpleName, "simpleName");
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        containingBlockId = Objects.requireNonNull(containingBlockId, "containingBlockId");
        signatureGroupId = Objects.requireNonNull(signatureGroupId, "signatureGroupId");
        effectiveProductSignature = Objects.requireNonNull(effectiveProductSignature, "effectiveProductSignature");
        observedProducts = List.copyOf(Objects.requireNonNull(observedProducts, "observedProducts"));
        resolutionStatus = Objects.requireNonNull(resolutionStatus, "resolutionStatus");
        declaredFieldTypes = Map.copyOf(declaredFieldTypes);
        declaringTypeName = Objects.requireNonNull(declaringTypeName, "declaringTypeName");
    }
}
