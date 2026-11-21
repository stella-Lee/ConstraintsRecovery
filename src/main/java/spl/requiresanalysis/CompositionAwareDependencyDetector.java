package spl.requiresanalysis;

import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelationType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class CompositionAwareDependencyDetector {
    private static final String WRAPPEE_MARKER = "__wrappee__";

    public CompositionAwareDependencyResult detect(RequiresEvidenceResult result) {
        Objects.requireNonNull(result, "result");
        Map<String, JavaEntity> entitiesById = result.entities().stream()
                .collect(Collectors.toMap(JavaEntity::entityId, entity -> entity, (left, right) -> left));
        List<CompositionAwareDependency> dependencies = new ArrayList<>();
        int examined = 0;
        for (DependencyEvidence dependency : result.dependencies()) {
            if (dependency.relationType() != JavaRelationType.METHOD_CALL) {
                continue;
            }
            examined++;
            JavaEntity source = entitiesById.get(dependency.sourceEntityId());
            JavaEntity target = entitiesById.get(dependency.targetEntityId());
            if (!isMethodLike(source) || !isMethodLike(target)) {
                continue;
            }
            WrappeeName wrappeeName = parseWrappeeName(target.simpleName());
            if (wrappeeName == null || !source.simpleName().equals(wrappeeName.baseName())) {
                continue;
            }
            dependencies.add(new CompositionAwareDependency(
                    "",
                    dependency.sourceBlockId(),
                    dependency.targetBlockId(),
                    dependency.sourceSignature(),
                    dependency.targetSignature(),
                    displayName(source),
                    displayName(target),
                    wrappeeName.baseName(),
                    wrappeeName.ignoredSuffix(),
                    dependency.relationType().name(),
                    "VARIABILITY_WRAPPER_DELEGATION",
                    false,
                    dependency.observedProducts(),
                    List.of(
                            "WRAPPEE_SUFFIX_NOT_INTERPRETED_AS_FEATURE_NAME",
                            "COMPOSITION_AWARE_EVIDENCE_ONLY",
                            "NOT_USED_FOR_REQUIRES_SCORING"
                    )
            ));
        }
        return new CompositionAwareDependencyResult(number(dependencies), examined);
    }

    private static List<CompositionAwareDependency> number(List<CompositionAwareDependency> dependencies) {
        List<CompositionAwareDependency> ordered = dependencies.stream()
                .sorted(Comparator.comparing(CompositionAwareDependency::sourceBlockId)
                        .thenComparing(CompositionAwareDependency::targetBlockId)
                        .thenComparing(CompositionAwareDependency::sourceMethod)
                        .thenComparing(CompositionAwareDependency::targetMethod))
                .toList();
        List<CompositionAwareDependency> numbered = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            CompositionAwareDependency dependency = ordered.get(index);
            numbered.add(new CompositionAwareDependency(
                    "CAD-" + String.format(java.util.Locale.ROOT, "%04d", index + 1),
                    dependency.sourceBlockId(),
                    dependency.targetBlockId(),
                    dependency.sourceSignature(),
                    dependency.targetSignature(),
                    dependency.sourceMethod(),
                    dependency.targetMethod(),
                    dependency.wrapperBaseName(),
                    dependency.ignoredWrapperSuffix(),
                    dependency.dependencyKind(),
                    dependency.evidenceType(),
                    dependency.usedForRequiresScoring(),
                    dependency.observedProducts(),
                    dependency.cautionReasons()
            ));
        }
        return numbered;
    }

    private static boolean isMethodLike(JavaEntity entity) {
        return entity != null
                && (entity.entityType() == JavaEntityType.METHOD || entity.entityType() == JavaEntityType.CONSTRUCTOR);
    }

    private static WrappeeName parseWrappeeName(String simpleName) {
        int markerIndex = simpleName.indexOf(WRAPPEE_MARKER);
        if (markerIndex <= 0) {
            return null;
        }
        String baseName = simpleName.substring(0, markerIndex);
        String suffix = simpleName.substring(markerIndex + WRAPPEE_MARKER.length());
        if (baseName.isBlank() || suffix.isBlank()) {
            return null;
        }
        return new WrappeeName(baseName, suffix);
    }

    private static String displayName(JavaEntity entity) {
        return entity.qualifiedName() == null || entity.qualifiedName().isBlank()
                ? entity.simpleName()
                : entity.qualifiedName();
    }

    private record WrappeeName(String baseName, String ignoredSuffix) {
    }
}
