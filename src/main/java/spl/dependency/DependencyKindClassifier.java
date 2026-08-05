package spl.dependency;

import spl.entity.JavaRelationType;

public final class DependencyKindClassifier {
    private DependencyKindClassifier() {
    }

    public static boolean isImplementationDependency(JavaRelationType type) {
        return type == JavaRelationType.FIELD_REFERENCE
                || type == JavaRelationType.METHOD_CALL
                || type == JavaRelationType.CONSTRUCTOR_CALL;
    }

    public static boolean isStructuralDependency(JavaRelationType type) {
        return type == JavaRelationType.EXTENDS
                || type == JavaRelationType.IMPLEMENTS;
    }

    public static boolean isExcludedDependency(JavaRelationType type) {
        return type == JavaRelationType.TYPE_REFERENCE;
    }

    public static boolean isBlockGraphDependency(JavaRelationType type) {
        return isImplementationDependency(type) || isStructuralDependency(type);
    }

    public static DependencyCategory category(JavaRelationType type) {
        if (isImplementationDependency(type)) {
            return DependencyCategory.IMPLEMENTATION;
        }
        if (isStructuralDependency(type)) {
            return DependencyCategory.STRUCTURAL;
        }
        throw new IllegalArgumentException("Excluded dependency type has no graph category: " + type);
    }
}
