package spl.requiresanalysis;

import spl.ProductSignature;
import spl.entity.EntityExtractionResult;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelation;
import spl.entity.JavaRelationType;
import spl.entity.ResolutionStatus;
import spl.grouping.SignatureGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class ProviderReferenceEvidenceDetector {
    public ProviderReferenceEvidenceResult detect(Collection<SignatureGroup> groups,
                                                  EntityExtractionResult entityResult) {
        Map<String, JavaEntity> entitiesById = entityResult.entities().stream()
                .collect(Collectors.toMap(JavaEntity::entityId, entity -> entity, (left, right) -> left));
        Set<String> productUniverse = groups.stream()
                .flatMap(group -> group.canonicalSignature().productIds().stream())
                .collect(Collectors.toCollection(TreeSet::new));

        int examined = 0;
        int unresolved = 0;
        int external = 0;
        int commonTarget = 0;
        int nonSubset = 0;
        Map<String, ProviderReferenceEvidence> evidenceByKey = new LinkedHashMap<>();

        List<JavaRelation> sortedRelations = entityResult.relations().stream()
                .filter(relation -> relation.relationType() == JavaRelationType.TYPE_REFERENCE)
                .sorted(Comparator.comparing((JavaRelation relation) -> relation.sourceFile().toString())
                        .thenComparingInt(JavaRelation::sourceLine)
                        .thenComparing(JavaRelation::relationId))
                .toList();

        for (JavaRelation relation : sortedRelations) {
            examined++;
            if (relation.resolutionStatus() != ResolutionStatus.RESOLVED_INTERNAL
                    || relation.sourceEntityId() == null
                    || relation.targetEntityId() == null) {
                unresolved++;
                continue;
            }
            JavaEntity source = entitiesById.get(relation.sourceEntityId());
            JavaEntity target = entitiesById.get(relation.targetEntityId());
            if (source == null || target == null) {
                unresolved++;
                continue;
            }
            if (!isInternalProviderType(target)) {
                external++;
                continue;
            }
            if (sameSet(target.effectiveProductSignature(), productUniverse)) {
                commonTarget++;
                continue;
            }
            if (!properSubset(source.effectiveProductSignature(), target.effectiveProductSignature())) {
                nonSubset++;
                continue;
            }

            String key = source.containingBlockId() + "|"
                    + target.containingBlockId() + "|"
                    + source.entityId() + "|"
                    + target.entityId() + "|"
                    + relation.sourceFile() + "|"
                    + relation.sourceLine();
            evidenceByKey.putIfAbsent(key, toEvidence(evidenceByKey.size() + 1, relation, source, target));
        }

        return new ProviderReferenceEvidenceResult(
                examined,
                unresolved,
                external,
                commonTarget,
                nonSubset,
                evidenceByKey.values().stream()
                        .sorted(Comparator.comparing((ProviderReferenceEvidence evidence) -> evidence.sourceFile().toString())
                                .thenComparingInt(ProviderReferenceEvidence::sourceLine)
                                .thenComparing(ProviderReferenceEvidence::targetEntityName))
                        .toList()
        );
    }

    private ProviderReferenceEvidence toEvidence(int index, JavaRelation relation, JavaEntity source,
                                                 JavaEntity target) {
        return new ProviderReferenceEvidence(
                "PRE-" + String.format("%04d", index),
                source.entityId(),
                displayName(source),
                source.entityType().name(),
                source.containingBlockId(),
                source.signatureGroupId(),
                source.effectiveProductSignature(),
                source.sourceFile(),
                relation.sourceLine(),
                target.entityId(),
                displayName(target),
                target.entityType().name(),
                target.containingBlockId(),
                target.signatureGroupId(),
                target.effectiveProductSignature(),
                target.sourceFile(),
                target.startLine(),
                relation.unresolvedTargetText(),
                "SOURCE_SUBSET_OF_TARGET",
                sortedUnion(source.observedProducts(), relation.observedProducts()),
                "INTERNAL_VARIABILITY_PROVIDER_TYPE_REFERENCE",
                "RECALL_EXPANSION_ONLY_NOT_DIRECT_REQUIRES;TYPE_REFERENCE_USED_AS_SUPPORTING_EVIDENCE"
        );
    }

    private boolean isInternalProviderType(JavaEntity entity) {
        return entity.entityType() == JavaEntityType.CLASS || entity.entityType() == JavaEntityType.INTERFACE;
    }

    private boolean properSubset(ProductSignature source, ProductSignature target) {
        Set<String> sourceProducts = new TreeSet<>(source.productIds());
        Set<String> targetProducts = new TreeSet<>(target.productIds());
        return !sourceProducts.isEmpty()
                && !targetProducts.isEmpty()
                && !sourceProducts.equals(targetProducts)
                && targetProducts.containsAll(sourceProducts);
    }

    private boolean sameSet(ProductSignature signature, Set<String> products) {
        return new TreeSet<>(signature.productIds()).equals(products);
    }

    private List<String> sortedUnion(List<String> left, List<String> right) {
        Set<String> products = new TreeSet<>(left);
        products.addAll(right);
        return List.copyOf(products);
    }

    private String displayName(JavaEntity entity) {
        return entity.qualifiedName() == null || entity.qualifiedName().isBlank()
                ? entity.simpleName()
                : entity.qualifiedName();
    }
}
