package spl.feature;

import spl.dependency.DependencyEdge;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelationType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class FeatureEvidenceProfileBuilder {
    private static final int TOP_ENTITY_COUNT = 5;
    private static final Comparator<JavaEntity> ENTITY_ORDER =
            Comparator.comparing((JavaEntity entity) -> entity.sourceFile().toString())
                    .thenComparingInt(JavaEntity::startLine)
                    .thenComparing(JavaEntity::entityId);
    private static final Comparator<EntityImportance> IMPORTANCE_ORDER =
            Comparator.comparingInt(EntityImportance::importanceScore).reversed()
                    .thenComparingInt(EntityImportance::degree).reversed()
                    .thenComparing(item -> item.entity().sourceFile().toString())
                    .thenComparingInt(item -> item.entity().startLine())
                    .thenComparing(item -> item.entity().entityId());

    public FeatureEvidenceProfileResult build(SemanticAggregationResult semanticResult) {
        Objects.requireNonNull(semanticResult, "semanticResult");
        List<FeatureEvidenceProfile> profiles = semanticResult.candidates().stream()
                .map(candidate -> profile(candidate, semanticResult.evidenceByComponentId()))
                .sorted(Comparator.comparing(FeatureEvidenceProfile::componentId))
                .toList();
        return new FeatureEvidenceProfileResult(profiles);
    }

    private static FeatureEvidenceProfile profile(AggregatedFeatureEffectCandidate candidate,
                                                  Map<String, ComponentEvidence> evidenceByComponentId) {
        Map<String, JavaEntity> entitiesById = candidate.memberEntities().stream()
                .collect(Collectors.toMap(JavaEntity::entityId, entity -> entity, (left, right) -> left));
        Map<String, DegreeCounter> degreeByEntity = new HashMap<>();
        candidate.memberEntities().forEach(entity -> degreeByEntity.put(entity.entityId(), new DegreeCounter()));
        for (DependencyEdge edge : candidate.internalEdges()) {
            degreeByEntity.computeIfAbsent(edge.sourceEntityId(), ignored -> new DegreeCounter()).outgoing++;
            degreeByEntity.computeIfAbsent(edge.targetEntityId(), ignored -> new DegreeCounter()).incoming++;
        }
        for (DependencyEdge edge : candidate.incomingEdges()) {
            degreeByEntity.computeIfAbsent(edge.targetEntityId(), ignored -> new DegreeCounter()).incoming++;
        }
        for (DependencyEdge edge : candidate.outgoingEdges()) {
            degreeByEntity.computeIfAbsent(edge.sourceEntityId(), ignored -> new DegreeCounter()).outgoing++;
        }

        List<EntityImportance> ranked = candidate.memberEntities().stream()
                .sorted(ENTITY_ORDER)
                .map(entity -> toImportance(entity, degreeByEntity.getOrDefault(entity.entityId(), new DegreeCounter())))
                .sorted(IMPORTANCE_ORDER)
                .toList();
        List<EntityImportance> rankedWithRanks = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            EntityImportance item = ranked.get(index);
            rankedWithRanks.add(new EntityImportance(item.entity(), index + 1, item.degree(),
                    item.incomingDegree(), item.outgoingDegree(), item.importanceScore()));
        }

        Map<String, Integer> tokenFrequencies = new TreeMap<>();
        Map<String, TreeSet<String>> origins = new TreeMap<>();
        for (String componentId : candidate.structuralComponentIds()) {
            ComponentEvidence evidence = evidenceByComponentId.get(componentId);
            if (evidence == null) {
                continue;
            }
            evidence.tokenFrequencies().forEach((token, count) -> tokenFrequencies.merge(token, count, Integer::sum));
            for (TokenEvidence token : evidence.tokenEvidence()) {
                origins.computeIfAbsent(token.token(), ignored -> new TreeSet<>())
                        .add(token.provenance() + ":" + entityName(entitiesById.get(token.sourceEntityId())));
            }
        }
        Map<String, List<String>> tokenOrigins = origins.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> new ArrayList<>(entry.getValue()),
                        (left, right) -> left, TreeMap::new));

        Map<JavaRelationType, Long> internalTypes = candidate.internalEdges().stream()
                .collect(Collectors.groupingBy(DependencyEdge::relationType,
                        () -> new TreeMap<>(Comparator.comparing(Enum::name)), Collectors.counting()));
        List<String> topTargets = topEndpointNames(candidate.internalEdges(), true, entitiesById);
        List<String> topSources = topEndpointNames(candidate.internalEdges(), false, entitiesById);
        List<String> representativeTokens = topTokens(tokenFrequencies, 8);
        String concept = implementationConcept(representativeTokens);
        List<String> evidence = conceptEvidence(representativeTokens, rankedWithRanks, topTargets);

        return new FeatureEvidenceProfile(
                candidate.candidateId(),
                candidate.signatureGroupId(),
                candidate.productSignature(),
                observedProducts(candidate),
                candidate.involvedFiles(),
                candidate.memberBlockIds(),
                rankedWithRanks.stream().limit(TOP_ENTITY_COUNT).toList(),
                byType(rankedWithRanks, JavaEntityType.CLASS),
                byType(rankedWithRanks, JavaEntityType.INTERFACE),
                byType(rankedWithRanks, JavaEntityType.METHOD),
                byType(rankedWithRanks, JavaEntityType.CONSTRUCTOR),
                byType(rankedWithRanks, JavaEntityType.FIELD),
                tokenFrequencies,
                tokenOrigins,
                internalTypes,
                topTargets,
                topSources,
                packages(candidate),
                classes(candidate),
                methods(candidate),
                candidate.memberEntities().size(),
                candidate.memberBlockIds().size(),
                candidate.involvedFiles().size(),
                candidate.internalEdges().size(),
                candidate.incomingEdges().size(),
                candidate.outgoingEdges().size(),
                (int) candidate.internalEdges().stream().filter(DependencyEdge::crossFile).count(),
                (int) rankedWithRanks.stream().filter(item -> item.degree() == 0).count(),
                concept,
                evidence
        );
    }

    private static EntityImportance toImportance(JavaEntity entity, DegreeCounter counter) {
        int degree = counter.incoming + counter.outgoing;
        int score = degree * 2 + counter.incoming + counter.outgoing;
        return new EntityImportance(entity, 0, degree, counter.incoming, counter.outgoing, score);
    }

    private static List<EntityImportance> byType(List<EntityImportance> ranked, JavaEntityType type) {
        return ranked.stream()
                .filter(item -> item.entity().entityType() == type)
                .limit(TOP_ENTITY_COUNT)
                .toList();
    }

    private static List<String> topEndpointNames(List<DependencyEdge> edges, boolean target,
                                                 Map<String, JavaEntity> entitiesById) {
        Map<String, Long> counts = edges.stream()
                .collect(Collectors.groupingBy(edge -> target ? edge.targetEntityId() : edge.sourceEntityId(),
                        TreeMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(8)
                .map(entry -> entityName(entitiesById.get(entry.getKey())) + ":" + entry.getValue())
                .toList();
    }

    private static List<String> topTokens(Map<String, Integer> tokenFrequencies, int limit) {
        return tokenFrequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .toList();
    }

    private static String implementationConcept(List<String> representativeTokens) {
        List<String> tokens = representativeTokens.stream()
                .map(token -> token.contains(":") ? token.substring(0, token.indexOf(':')) : token)
                .limit(3)
                .toList();
        if (tokens.isEmpty()) {
            return "Unlabeled implementation cluster";
        }
        if (tokens.size() == 1) {
            return capitalize(tokens.get(0)) + " implementation cluster";
        }
        return capitalize(tokens.get(0)) + "-" + tokens.get(1) + " implementation cluster";
    }

    private static List<String> conceptEvidence(List<String> tokens, List<EntityImportance> ranked,
                                                List<String> targets) {
        List<String> evidence = new ArrayList<>();
        if (!tokens.isEmpty()) {
            evidence.add("tokens=" + String.join("|", tokens));
        }
        if (!ranked.isEmpty()) {
            evidence.add("entities=" + ranked.stream().limit(3)
                    .map(item -> entityName(item.entity()))
                    .collect(Collectors.joining("|")));
        }
        if (!targets.isEmpty()) {
            evidence.add("dependencyTargets=" + String.join("|", targets.stream().limit(3).toList()));
        }
        evidence.add("NOTE: descriptive implementation concept only; not a recovered feature name");
        return evidence;
    }

    private static List<String> observedProducts(AggregatedFeatureEffectCandidate candidate) {
        TreeSet<String> products = new TreeSet<>();
        candidate.memberEntities().forEach(entity -> products.addAll(entity.observedProducts()));
        if (products.isEmpty()) {
            products.addAll(candidate.productSignature().productIds());
        }
        return new ArrayList<>(products);
    }

    private static List<String> packages(AggregatedFeatureEffectCandidate candidate) {
        return candidate.memberEntities().stream()
                .map(JavaEntity::qualifiedName)
                .filter(Objects::nonNull)
                .map(FeatureEvidenceProfileBuilder::packageName)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private static List<String> classes(AggregatedFeatureEffectCandidate candidate) {
        return candidate.memberEntities().stream()
                .filter(entity -> entity.entityType() == JavaEntityType.CLASS
                        || entity.entityType() == JavaEntityType.INTERFACE)
                .map(FeatureEvidenceProfileBuilder::entityName)
                .distinct()
                .sorted()
                .toList();
    }

    private static List<String> methods(AggregatedFeatureEffectCandidate candidate) {
        return candidate.memberEntities().stream()
                .filter(entity -> entity.entityType() == JavaEntityType.METHOD
                        || entity.entityType() == JavaEntityType.CONSTRUCTOR)
                .map(FeatureEvidenceProfileBuilder::entityName)
                .distinct()
                .sorted()
                .toList();
    }

    private static String packageName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot < 0 ? "" : qualifiedName.substring(0, lastDot);
    }

    private static String entityName(JavaEntity entity) {
        if (entity == null) {
            return "<external>";
        }
        return entity.qualifiedName() == null ? entity.simpleName() : entity.qualifiedName();
    }

    private static String capitalize(String token) {
        return token.isBlank() ? token : token.substring(0, 1).toUpperCase() + token.substring(1);
    }

    private static final class DegreeCounter {
        private int incoming;
        private int outgoing;
    }
}
