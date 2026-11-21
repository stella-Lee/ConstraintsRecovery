package spl.requiresanalysis;

import spl.ProductSignature;
import spl.entity.EntityExtractionResult;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelation;
import spl.entity.JavaRelationType;
import spl.entity.ResolutionStatus;
import spl.grouping.SignatureGroup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class ProviderDataFlowEvidenceDetector {
    private static final Set<JavaRelationType> SUPPORTED_RELATIONS = Set.of(
            JavaRelationType.CONSTRUCTOR_CALL,
            JavaRelationType.FIELD_REFERENCE,
            JavaRelationType.METHOD_CALL
    );
    public ProviderDataFlowEvidenceResult detect(Collection<SignatureGroup> groups,
                                                 EntityExtractionResult entityResult) {
        return detect(entityResult, new ProductSignature(groups.stream()
                .flatMap(group -> group.canonicalSignature().productIds().stream())
                .distinct().collect(Collectors.joining("|"))));
    }

    public ProviderDataFlowEvidenceResult detect(EntityExtractionResult entityResult, ProductSignature universe) {
        Map<String, JavaEntity> entitiesById = entityResult.entities().stream()
                .collect(Collectors.toMap(JavaEntity::entityId, entity -> entity, (left, right) -> left));
        ProviderIndex providerIndex = new ProviderIndex(entityResult.entities());
        Set<String> productUniverse = Set.copyOf(universe.productIds());
        Map<Path, List<String>> lineCache = new HashMap<>();

        int examined = 0;
        int unresolvedRelations = 0;
        int unresolvedProviders = 0;
        int commonProviders = 0;
        int nonSubset = 0;
        Map<String, ProviderDataFlowEvidence> evidenceByKey = new LinkedHashMap<>();

        List<JavaRelation> sortedRelations = entityResult.relations().stream()
                .filter(relation -> SUPPORTED_RELATIONS.contains(relation.relationType()))
                .sorted(Comparator.comparing((JavaRelation relation) -> relation.sourceFile().toString())
                        .thenComparingInt(JavaRelation::sourceLine)
                        .thenComparing(JavaRelation::relationId))
                .toList();

        for (JavaRelation relation : sortedRelations) {
            examined++;
            if (relation.resolutionStatus() != ResolutionStatus.RESOLVED_INTERNAL
                    || relation.sourceEntityId() == null
                    || relation.targetEntityId() == null) {
                unresolvedRelations++;
                continue;
            }
            JavaEntity source = entitiesById.get(relation.sourceEntityId());
            JavaEntity target = entitiesById.get(relation.targetEntityId());
            if (source == null || target == null) {
                unresolvedRelations++;
                continue;
            }

            Map<JavaEntity, List<String>> observations = new LinkedHashMap<>();
            for (String product : relation.observedProducts()) {
                JavaEntity provider = providerForProduct(relation.relationType(), target, product, providerIndex);
                if (provider != null) {
                    observations.computeIfAbsent(provider, ignored -> new ArrayList<>()).add(product);
                }
            }
            if (observations.isEmpty()) {
                unresolvedProviders++;
            }
            for (var match : observations.entrySet()) {
                JavaEntity provider = match.getKey();
                if (sameSet(provider.effectiveProductSignature(), productUniverse)) {
                    commonProviders++;
                } else if (!properSubset(relation.effectiveProductSignature(), provider.effectiveProductSignature())) {
                    nonSubset++;
                } else {
                    String key = relation.relationId() + "|" + provider.entityId();
                    evidenceByKey.putIfAbsent(key, toEvidence(
                            "PDE-" + String.format("%04d", evidenceByKey.size() + 1),
                            providerKind(relation.relationType()), relation, source, target, provider,
                            sourceLine(relation.sourceFile(), relation.sourceLine(), lineCache), match.getValue()));
                }
            }
        }

        return new ProviderDataFlowEvidenceResult(
                examined,
                unresolvedRelations,
                unresolvedProviders,
                commonProviders,
                nonSubset,
                evidenceByKey.values().stream()
                        .sorted(Comparator.comparing((ProviderDataFlowEvidence evidence) -> evidence.sourceFile().toString())
                                .thenComparingInt(ProviderDataFlowEvidence::sourceLine)
                                .thenComparing(ProviderDataFlowEvidence::providerEntityName)
                                .thenComparing(ProviderDataFlowEvidence::evidenceId))
                        .toList()
        );
    }

    private String providerKind(JavaRelationType kind) {
        return switch (kind) {
            case CONSTRUCTOR_CALL -> "CONSTRUCTOR_PROVIDER_USE";
            case FIELD_REFERENCE -> "FIELD_DECLARED_TYPE_PROVIDER_USE";
            case METHOD_CALL -> "METHOD_DECLARING_TYPE_PROVIDER_USE";
            default -> throw new IllegalArgumentException("Unsupported provider relation " + kind);
        };
    }

    private JavaEntity providerForProduct(JavaRelationType kind, JavaEntity target, String product,
                                          ProviderIndex providerIndex) {
        if (kind == JavaRelationType.FIELD_REFERENCE) {
            return providerIndex.find(target.declaredFieldTypes().get(product), product, null);
        }
        JavaEntityType expected = kind == JavaRelationType.METHOD_CALL
                ? JavaEntityType.METHOD : JavaEntityType.CONSTRUCTOR;
        if (target.entityType() != expected) {
            return null;
        }
        return providerIndex.find(target.declaringTypeName(), product, target);
    }

    private ProviderDataFlowEvidence toEvidence(String id, String kind, JavaRelation relation, JavaEntity source,
                                                JavaEntity intermediate, JavaEntity provider, String codeSnippet,
                                                List<String> observedProducts) {
        return new ProviderDataFlowEvidence(
                id,
                kind,
                source.entityId(),
                displayName(source),
                source.entityType().name(),
                AuditReportBuilder.unitId(relation.containingBlockId(), relation.sourceFile()),
                relation.signatureGroupId(),
                relation.effectiveProductSignature(),
                relation.sourceFile(),
                relation.sourceLine(),
                provider.entityId(),
                displayName(provider),
                provider.entityType().name(),
                AuditReportBuilder.unitId(provider.containingBlockId(), provider.sourceFile()),
                provider.signatureGroupId(),
                provider.effectiveProductSignature(),
                provider.sourceFile(),
                provider.startLine(),
                intermediate.entityId(),
                displayName(intermediate),
                intermediate.entityType().name(),
                relation.relationId(),
                relation.relationType().name(),
                codeSnippet,
                "SOURCE_SUBSET_OF_TARGET",
                observedProducts.stream().distinct().sorted().toList(),
                "RECALL_EXPANSION_ONLY_NOT_DIRECT_REQUIRES;"
                        + (source.entityType() == JavaEntityType.FIELD ? "FIELD_INITIALIZER"
                        : source.entityType() == JavaEntityType.METHOD || source.entityType() == JavaEntityType.CONSTRUCTOR
                        ? "OPERATION_BODY" : "TYPE_CONTEXT")
        );
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

    private String sourceLine(Path file, int line, Map<Path, List<String>> lineCache) {
        if (line <= 0) {
            return "";
        }
        List<String> lines = lineCache.computeIfAbsent(file, this::readLines);
        return line <= lines.size() ? lines.get(line - 1).strip() : "";
    }

    private List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException exception) {
            return List.of();
        }
    }

    private String displayName(JavaEntity entity) {
        return entity.qualifiedName() == null || entity.qualifiedName().isBlank()
                ? entity.simpleName()
                : entity.qualifiedName();
    }

    private final class ProviderIndex {
        private final Map<String, List<JavaEntity>> byName = new HashMap<>();

        private ProviderIndex(Collection<JavaEntity> entities) {
            for (JavaEntity entity : entities) {
                if (entity.entityType() == JavaEntityType.CLASS || entity.entityType() == JavaEntityType.INTERFACE) {
                    byName.computeIfAbsent(displayName(entity), ignored -> new ArrayList<>()).add(entity);
                }
            }
        }

        private JavaEntity find(String qualifiedName, String product, JavaEntity member) {
            List<JavaEntity> matches = byName.getOrDefault(qualifiedName, List.of()).stream()
                    .filter(entity -> entity.observedProducts().contains(product))
                    .filter(entity -> member == null || entity.sourceFile().equals(member.sourceFile())
                            && entity.startLine() <= member.startLine() && entity.endLine() >= member.endLine())
                    .toList();
            return matches.size() == 1 ? matches.get(0) : null;
        }
    }
}
