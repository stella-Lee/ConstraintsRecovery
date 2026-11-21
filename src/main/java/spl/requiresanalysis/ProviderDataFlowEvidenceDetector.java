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
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ProviderDataFlowEvidenceDetector {
    private static final Set<JavaRelationType> SUPPORTED_RELATIONS = Set.of(
            JavaRelationType.CONSTRUCTOR_CALL,
            JavaRelationType.FIELD_REFERENCE,
            JavaRelationType.METHOD_CALL
    );
    private static final Pattern FIELD_TYPE_PATTERN = Pattern.compile(
            "^(?:@[\\w.]+\\s+)*(?:(?:public|protected|private|static|final|transient|volatile)\\s+)*([\\w.$<>\\[\\]?]+)\\s+\\w+.*"
    );

    public ProviderDataFlowEvidenceResult detect(Collection<SignatureGroup> groups,
                                                 EntityExtractionResult entityResult) {
        Map<String, JavaEntity> entitiesById = entityResult.entities().stream()
                .collect(Collectors.toMap(JavaEntity::entityId, entity -> entity, (left, right) -> left));
        ProviderIndex providerIndex = new ProviderIndex(entityResult.entities());
        Set<String> productUniverse = groups.stream()
                .flatMap(group -> group.canonicalSignature().productIds().stream())
                .collect(Collectors.toCollection(TreeSet::new));
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

            ProviderMatch provider = providerForRelation(relation, target, providerIndex, lineCache).orElse(null);
            if (provider == null) {
                unresolvedProviders++;
                continue;
            }
            if (sameSet(provider.entity().effectiveProductSignature(), productUniverse)) {
                commonProviders++;
                continue;
            }
            if (!properSubset(source.effectiveProductSignature(), provider.entity().effectiveProductSignature())) {
                nonSubset++;
                continue;
            }

            String key = relation.relationId() + "|" + source.entityId() + "|" + provider.entity().entityId() + "|"
                    + relation.sourceFile() + "|" + relation.sourceLine() + "|" + provider.kind();
            evidenceByKey.putIfAbsent(key, toEvidence(
                    "PDE-" + String.format("%04d", evidenceByKey.size() + 1),
                    provider.kind(),
                    relation,
                    source,
                    target,
                    provider.entity(),
                    sourceLine(relation.sourceFile(), relation.sourceLine(), lineCache)
            ));
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

    private Optional<ProviderMatch> providerForRelation(JavaRelation relation, JavaEntity target,
                                                       ProviderIndex providerIndex,
                                                       Map<Path, List<String>> lineCache) {
        return switch (relation.relationType()) {
            case CONSTRUCTOR_CALL -> providerForConstructor(target, providerIndex)
                    .map(provider -> new ProviderMatch("CONSTRUCTOR_PROVIDER_USE", provider));
            case FIELD_REFERENCE -> providerForField(target, providerIndex, lineCache)
                    .map(provider -> new ProviderMatch("FIELD_DECLARED_TYPE_PROVIDER_USE", provider));
            case METHOD_CALL -> providerForMethod(target, providerIndex)
                    .map(provider -> new ProviderMatch("METHOD_DECLARING_TYPE_PROVIDER_USE", provider));
            default -> Optional.empty();
        };
    }

    private Optional<JavaEntity> providerForConstructor(JavaEntity constructor, ProviderIndex providerIndex) {
        if (constructor.entityType() != JavaEntityType.CONSTRUCTOR) {
            return Optional.empty();
        }
        String name = displayName(constructor);
        int split = name.lastIndexOf('.');
        if (split < 0) {
            return providerIndex.bySimpleName(constructor.simpleName());
        }
        String declaringType = name.substring(0, split);
        return providerIndex.byQualifiedName(declaringType)
                .or(() -> providerIndex.bySimpleName(constructor.simpleName()));
    }

    private Optional<JavaEntity> providerForField(JavaEntity field, ProviderIndex providerIndex,
                                                  Map<Path, List<String>> lineCache) {
        if (field.entityType() != JavaEntityType.FIELD) {
            return Optional.empty();
        }
        String line = sourceLine(field.sourceFile(), field.startLine(), lineCache);
        String type = declaredFieldType(line).orElse("");
        return providerIndex.byTypeText(type);
    }

    private Optional<JavaEntity> providerForMethod(JavaEntity method, ProviderIndex providerIndex) {
        if (method.entityType() != JavaEntityType.METHOD && method.entityType() != JavaEntityType.CONSTRUCTOR) {
            return Optional.empty();
        }
        String name = displayName(method);
        int split = name.lastIndexOf('.');
        if (split < 0) {
            return Optional.empty();
        }
        return providerIndex.byQualifiedName(name.substring(0, split));
    }

    private Optional<String> declaredFieldType(String line) {
        String cleaned = line.strip();
        int assignment = cleaned.indexOf('=');
        if (assignment >= 0) {
            cleaned = cleaned.substring(0, assignment).strip();
        }
        Matcher matcher = FIELD_TYPE_PATTERN.matcher(cleaned);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(baseType(matcher.group(1)));
    }

    private String baseType(String type) {
        String value = type.strip();
        int generic = value.indexOf('<');
        if (generic >= 0) {
            value = value.substring(0, generic);
        }
        while (value.endsWith("[]")) {
            value = value.substring(0, value.length() - 2);
        }
        int dot = value.lastIndexOf('.');
        return dot >= 0 ? value.substring(dot + 1) : value;
    }

    private ProviderDataFlowEvidence toEvidence(String id, String kind, JavaRelation relation, JavaEntity source,
                                                JavaEntity intermediate, JavaEntity provider, String codeSnippet) {
        return new ProviderDataFlowEvidence(
                id,
                kind,
                source.entityId(),
                displayName(source),
                source.entityType().name(),
                source.containingBlockId(),
                source.signatureGroupId(),
                source.effectiveProductSignature(),
                source.sourceFile(),
                relation.sourceLine(),
                provider.entityId(),
                displayName(provider),
                provider.entityType().name(),
                provider.containingBlockId(),
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
                sortedUnion(source.observedProducts(), relation.observedProducts()),
                "RECALL_EXPANSION_ONLY_NOT_DIRECT_REQUIRES;DATAFLOW_LITE"
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

    private List<String> sortedUnion(List<String> left, List<String> right) {
        Set<String> products = new TreeSet<>(left);
        products.addAll(right);
        return List.copyOf(products);
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

    private record ProviderMatch(String kind, JavaEntity entity) {
    }

    private final class ProviderIndex {
        private final Map<String, JavaEntity> qualifiedProviders = new HashMap<>();
        private final Map<String, List<JavaEntity>> simpleProviders = new HashMap<>();

        private ProviderIndex(Collection<JavaEntity> entities) {
            for (JavaEntity entity : entities) {
                if (entity.entityType() != JavaEntityType.CLASS && entity.entityType() != JavaEntityType.INTERFACE) {
                    continue;
                }
                String qualified = displayName(entity);
                qualifiedProviders.putIfAbsent(qualified, entity);
                simpleProviders.computeIfAbsent(entity.simpleName(), ignored -> new ArrayList<>()).add(entity);
            }
        }

        private Optional<JavaEntity> byQualifiedName(String name) {
            return Optional.ofNullable(qualifiedProviders.get(name));
        }

        private Optional<JavaEntity> bySimpleName(String name) {
            List<JavaEntity> matches = simpleProviders.getOrDefault(name, List.of());
            return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
        }

        private Optional<JavaEntity> byTypeText(String typeText) {
            if (typeText.isBlank()) {
                return Optional.empty();
            }
            return byQualifiedName(typeText).or(() -> bySimpleName(baseType(typeText)));
        }
    }
}
