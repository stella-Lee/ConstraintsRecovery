package spl.feature;

import spl.ConditionalBlock;
import spl.ProductSignature;
import spl.dependency.DependencyEdge;
import spl.entity.JavaEntity;
import spl.grouping.SignatureGroup;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SemanticFeatureEffectAggregator {
    private static final Comparator<FeatureEffectCandidate> COMPONENT_ORDER =
            Comparator.comparing((FeatureEffectCandidate component) -> minFile(component).toString())
                    .thenComparingInt(SemanticFeatureEffectAggregator::minLine)
                    .thenComparing(FeatureEffectCandidate::candidateId);
    private static final Comparator<DependencyEdge> EDGE_ORDER =
            Comparator.comparing((DependencyEdge edge) -> edge.sourceFile().toString())
                    .thenComparingInt(DependencyEdge::sourceLine)
                    .thenComparing(edge -> edge.relationType().name())
                    .thenComparing(DependencyEdge::sourceEntityId)
                    .thenComparing(DependencyEdge::targetEntityId);
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^A-Za-z0-9]+");
    private static final Set<String> STANDARD_ANNOTATIONS = Set.of(
            "Override", "Deprecated", "SuppressWarnings", "FunctionalInterface", "SafeVarargs"
    );

    private final SemanticAggregationConfig config;

    public SemanticFeatureEffectAggregator() {
        this(SemanticAggregationConfig.defaults());
    }

    public SemanticFeatureEffectAggregator(SemanticAggregationConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public SemanticAggregationResult aggregate(FeatureEffectCandidateResult structuralResult,
                                               Collection<SignatureGroup> groups) {
        Objects.requireNonNull(structuralResult, "structuralResult");
        Objects.requireNonNull(groups, "groups");

        ProductSignature universe = completeProductUniverse(groups);
        Map<String, CommonalityClassification> commonalityByGroup = groups.stream()
                .collect(Collectors.toMap(SignatureGroup::groupId,
                        group -> classify(group.canonicalSignature(), universe)));
        Map<String, ComponentEvidence> evidenceById = new TreeMap<>();
        for (FeatureEffectCandidate component : structuralResult.candidates()) {
            evidenceById.put(component.candidateId(), extractEvidence(component));
        }

        Map<String, List<FeatureEffectCandidate>> byGroup = structuralResult.candidates().stream()
                .collect(Collectors.groupingBy(FeatureEffectCandidate::signatureGroupId));
        List<ComponentSimilarity> similarities = new ArrayList<>();
        List<ComponentMergeDecision> decisions = new ArrayList<>();
        List<AggregatedFeatureEffectCandidate> aggregated = new ArrayList<>();

        for (Map.Entry<String, List<FeatureEffectCandidate>> entry : byGroup.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            String groupId = entry.getKey();
            List<FeatureEffectCandidate> components = entry.getValue().stream()
                    .sorted(COMPONENT_ORDER)
                    .toList();
            Map<PairKey, ComponentSimilarity> pairSimilarities = new HashMap<>();
            Map<String, Set<String>> mergeAdjacency = new HashMap<>();
            components.forEach(component -> mergeAdjacency.put(component.candidateId(), new TreeSet<>()));

            for (int left = 0; left < components.size(); left++) {
                for (int right = left + 1; right < components.size(); right++) {
                    FeatureEffectCandidate a = components.get(left);
                    FeatureEffectCandidate b = components.get(right);
                    ComponentSimilarity similarity = similarity(a, b, evidenceById);
                    similarities.add(similarity);
                    pairSimilarities.put(PairKey.of(a.candidateId(), b.candidateId()), similarity);
                    ComponentMergeDecision decision = mergeDecision(a, b, similarity);
                    decisions.add(decision);
                    if (decision.decision() == MergeDecision.MERGED) {
                        mergeAdjacency.get(a.candidateId()).add(b.candidateId());
                        mergeAdjacency.get(b.candidateId()).add(a.candidateId());
                    }
                }
            }

            List<List<FeatureEffectCandidate>> clusters = completeLinkClusters(components, mergeAdjacency,
                    pairSimilarities);
            clusters = clusters.stream()
                    .sorted(Comparator.comparing(SemanticFeatureEffectAggregator::clusterSortKey))
                    .toList();
            int number = 1;
            for (List<FeatureEffectCandidate> cluster : clusters) {
                String candidateId = "AFEC-" + groupId + "-" + String.format("%02d", number++);
                aggregated.add(toAggregatedCandidate(candidateId, cluster,
                        commonalityByGroup.getOrDefault(groupId, CommonalityClassification.VARIABLE_SIGNATURE),
                        evidenceById, decisions));
            }
        }

        List<FeatureEffectCandidate> unassigned = aggregated.stream()
                .filter(candidate -> candidate.candidateConfidence() == CandidateConfidence.UNRESOLVED)
                .flatMap(candidate -> candidate.structuralComponentIds().stream())
                .map(id -> structuralResult.candidates().stream()
                        .filter(component -> component.candidateId().equals(id))
                        .findFirst()
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();

        return new SemanticAggregationResult(
                aggregated.stream().sorted(aggregatedOrder()).toList(),
                evidenceById,
                similarities.stream().sorted(similarityOrder()).toList(),
                decisions.stream().sorted(decisionOrder()).toList(),
                unassigned
        );
    }

    private ComponentEvidence extractEvidence(FeatureEffectCandidate component) {
        Map<String, Integer> frequencies = new TreeMap<>();
        TreeSet<String> rawTokens = new TreeSet<>();
        TreeSet<String> packages = new TreeSet<>();
        TreeSet<String> classes = new TreeSet<>();
        TreeSet<Path> files = new TreeSet<>(Comparator.comparing(Path::toString));
        TreeSet<String> blockIds = new TreeSet<>();
        TreeSet<String> internalTargets = new TreeSet<>();
        TreeSet<String> incomingSources = new TreeSet<>();
        TreeSet<String> outgoingTargets = new TreeSet<>();
        List<TokenEvidence> tokenEvidence = new ArrayList<>();

        for (JavaEntity entity : component.memberEntities()) {
            TokenProvenance provenance = switch (entity.entityType()) {
                case CLASS, INTERFACE, CONSTRUCTOR -> TokenProvenance.TYPE_NAME;
                case METHOD -> TokenProvenance.METHOD_NAME;
                case FIELD -> TokenProvenance.FIELD_NAME;
            };
            addTokens(entity.simpleName(), rawTokens, frequencies, tokenEvidence, provenance, entity.entityId());
            addTokens(entity.qualifiedName(), rawTokens, frequencies, tokenEvidence, TokenProvenance.IDENTIFIER,
                    entity.entityId());
            files.add(entity.sourceFile());
            if (entity.qualifiedName() != null) {
                packages.add(packageName(entity.qualifiedName()));
                classes.add(enclosingClassName(entity.qualifiedName()));
            }
        }
        for (ConditionalBlock block : component.memberBlocks()) {
            files.add(block.filePath());
            blockIds.add(blockId(block));
        }
        for (DependencyEdge edge : component.internalEdges()) {
            internalTargets.add(edge.targetEntityId());
            addTokens(edge.relationType().name(), rawTokens, frequencies, tokenEvidence, TokenProvenance.IDENTIFIER,
                    edge.sourceEntityId());
        }
        for (DependencyEdge edge : component.incomingEdges()) {
            incomingSources.add(edge.sourceEntityId());
        }
        for (DependencyEdge edge : component.outgoingEdges()) {
            outgoingTargets.add(edge.targetEntityId());
        }
        TreeSet<String> neighbors = new TreeSet<>();
        neighbors.addAll(internalTargets);
        neighbors.addAll(incomingSources);
        neighbors.addAll(outgoingTargets);
        List<String> representativeTokens = frequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(8)
                .map(Map.Entry::getKey)
                .toList();
        return new ComponentEvidence(component.candidateId(), frequencies, representativeTokens,
                rawTokens, new TreeSet<>(frequencies.keySet()), packages, classes, files, blockIds,
                neighbors, internalTargets, incomingSources, outgoingTargets, tokenEvidence);
    }

    private void addTokens(String text, Set<String> rawTokens, Map<String, Integer> frequencies,
                           List<TokenEvidence> tokenEvidence, TokenProvenance provenance, String sourceEntityId) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (provenance == TokenProvenance.COMMENT && !config.useComments()) {
            return;
        }
        if (provenance == TokenProvenance.CUSTOM_ANNOTATION && !config.useCustomAnnotations()) {
            return;
        }
        for (String fragment : TOKEN_SPLIT.split(text)) {
            if (STANDARD_ANNOTATIONS.contains(fragment)) {
                continue;
            }
            for (String token : splitIdentifier(fragment)) {
                if (token.isBlank()) {
                    continue;
                }
                if (STANDARD_ANNOTATIONS.contains(token)) {
                    continue;
                }
                rawTokens.add(token);
                String normalized = normalize(token);
                if (!normalized.isBlank() && !normalized.chars().allMatch(Character::isDigit)
                        && !config.stopWords().contains(normalized)) {
                    frequencies.merge(normalized, 1, Integer::sum);
                    tokenEvidence.add(new TokenEvidence(normalized, provenance, sourceEntityId));
                }
            }
        }
    }

    private static List<String> splitIdentifier(String fragment) {
        String spaced = fragment.replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .replace('_', ' ');
        return List.of(spaced.split("\\s+"));
    }

    private static String normalize(String token) {
        String normalized = token.toLowerCase();
        if (normalized.endsWith("ies") && normalized.length() > 4) {
            return normalized.substring(0, normalized.length() - 3) + "y";
        }
        if (normalized.endsWith("ing") && normalized.length() > 5) {
            return normalized.substring(0, normalized.length() - 3);
        }
        if (normalized.endsWith("s") && normalized.length() > 3) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private ComponentSimilarity similarity(FeatureEffectCandidate a, FeatureEffectCandidate b,
                                           Map<String, ComponentEvidence> evidenceById) {
        ComponentEvidence left = evidenceById.get(a.candidateId());
        ComponentEvidence right = evidenceById.get(b.candidateId());
        double lexical = weightedJaccard(left.tokenFrequencies(), right.tokenFrequencies());
        double context = average(
                jaccard(left.packages(), right.packages()),
                jaccard(left.classes(), right.classes()),
                jaccard(left.files(), right.files())
        );
        double neighborhood = jaccard(left.dependencyNeighbors(), right.dependencyNeighbors());
        double direct = directDependency(a, b);
        double block = jaccard(left.blockIds(), right.blockIds());
        double score = config.lexicalWeight() * lexical
                + config.contextualWeight() * context
                + config.neighborhoodWeight() * neighborhood
                + config.directDependencyWeight() * direct
                + config.blockEvidenceWeight() * block;
        List<String> support = support(lexical, context, neighborhood, direct, block);
        return new ComponentSimilarity(a.signatureGroupId(), a.candidateId(), b.candidateId(),
                lexical, context, neighborhood, direct, block, score,
                config.pairwiseMergeThreshold(), support.size(), support);
    }

    private ComponentMergeDecision mergeDecision(FeatureEffectCandidate a, FeatureEffectCandidate b,
                                                 ComponentSimilarity similarity) {
        if (!a.productSignature().equals(b.productSignature())) {
            return new ComponentMergeDecision(a.signatureGroupId(), a.candidateId(), b.candidateId(),
                    MergeDecision.NOT_MERGED, similarity.combinedScore(), similarity.supportingEvidence(),
                    "different product signatures");
        }
        if (!config.aggregateIsolatedEntities()
                && (a.classificationStatus() == CandidateClassificationStatus.ISOLATED_ENTITY
                || b.classificationStatus() == CandidateClassificationStatus.ISOLATED_ENTITY)) {
            return new ComponentMergeDecision(a.signatureGroupId(), a.candidateId(), b.candidateId(),
                    MergeDecision.NOT_MERGED, similarity.combinedScore(), similarity.supportingEvidence(),
                    "isolated aggregation disabled");
        }
        if (similarity.supportingEvidenceCount() < config.minimumEvidenceFamilies()) {
            return new ComponentMergeDecision(a.signatureGroupId(), a.candidateId(), b.candidateId(),
                    MergeDecision.NOT_MERGED, similarity.combinedScore(), similarity.supportingEvidence(),
                    "insufficient independent evidence families");
        }
        if (similarity.combinedScore() < config.pairwiseMergeThreshold()) {
            return new ComponentMergeDecision(a.signatureGroupId(), a.candidateId(), b.candidateId(),
                    MergeDecision.NOT_MERGED, similarity.combinedScore(), similarity.supportingEvidence(),
                    "combined score below threshold");
        }
        return new ComponentMergeDecision(a.signatureGroupId(), a.candidateId(), b.candidateId(),
                MergeDecision.MERGED, similarity.combinedScore(), similarity.supportingEvidence(),
                "threshold and evidence-family requirements satisfied");
    }

    private List<List<FeatureEffectCandidate>> completeLinkClusters(List<FeatureEffectCandidate> components,
                                                                    Map<String, Set<String>> adjacency,
                                                                    Map<PairKey, ComponentSimilarity> similarities) {
        Map<String, FeatureEffectCandidate> byId = components.stream()
                .collect(Collectors.toMap(FeatureEffectCandidate::candidateId, component -> component));
        Set<String> visited = new HashSet<>();
        List<List<FeatureEffectCandidate>> clusters = new ArrayList<>();
        for (FeatureEffectCandidate component : components) {
            if (!visited.add(component.candidateId())) {
                continue;
            }
            List<String> clusterIds = new ArrayList<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(component.candidateId());
            while (!queue.isEmpty()) {
                String id = queue.removeFirst();
                clusterIds.add(id);
                for (String neighbor : adjacency.getOrDefault(id, Set.of())) {
                    if (visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
            List<String> accepted = new ArrayList<>();
            for (String id : clusterIds.stream().sorted().toList()) {
                List<String> tentative = new ArrayList<>(accepted);
                tentative.add(id);
                if (accepted.isEmpty() || cohesive(tentative, similarities)) {
                    accepted.add(id);
                } else {
                    clusters.add(List.of(byId.get(id)));
                }
            }
            clusters.add(accepted.stream().map(byId::get).sorted(COMPONENT_ORDER).toList());
        }
        return clusters;
    }

    private boolean cohesive(List<String> ids, Map<PairKey, ComponentSimilarity> similarities) {
        if (ids.size() < 2) {
            return true;
        }
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                ComponentSimilarity similarity = similarities.get(PairKey.of(ids.get(i), ids.get(j)));
                if (similarity == null || similarity.combinedScore() < config.clusterCohesionThreshold()) {
                    return false;
                }
                sum += similarity.combinedScore();
                count++;
            }
        }
        return count == 0 || (sum / count) >= config.clusterCohesionThreshold();
    }

    private AggregatedFeatureEffectCandidate toAggregatedCandidate(String candidateId,
                                                                   List<FeatureEffectCandidate> components,
                                                                   CommonalityClassification commonality,
                                                                   Map<String, ComponentEvidence> evidenceById,
                                                                   List<ComponentMergeDecision> decisions) {
        TreeSet<String> componentIds = components.stream()
                .map(FeatureEffectCandidate::candidateId)
                .collect(Collectors.toCollection(TreeSet::new));
        List<JavaEntity> entities = components.stream()
                .flatMap(component -> component.memberEntities().stream())
                .sorted(Comparator.comparing((JavaEntity entity) -> entity.sourceFile().toString())
                        .thenComparingInt(JavaEntity::startLine)
                        .thenComparing(JavaEntity::entityId))
                .toList();
        TreeSet<String> blockIds = components.stream()
                .flatMap(component -> component.memberBlocks().stream())
                .map(SemanticFeatureEffectAggregator::blockId)
                .collect(Collectors.toCollection(TreeSet::new));
        TreeSet<Path> files = new TreeSet<>(Comparator.comparing(Path::toString));
        components.forEach(component -> files.addAll(component.involvedAssetFiles()));
        List<DependencyEdge> internal = components.stream()
                .flatMap(component -> component.internalEdges().stream())
                .sorted(EDGE_ORDER)
                .toList();
        List<DependencyEdge> incoming = components.stream()
                .flatMap(component -> component.incomingEdges().stream())
                .filter(edge -> !componentIds.contains(componentIdForEntity(edge.sourceEntityId(), components)))
                .sorted(EDGE_ORDER)
                .toList();
        List<DependencyEdge> outgoing = components.stream()
                .flatMap(component -> component.outgoingEdges().stream())
                .filter(edge -> !componentIds.contains(componentIdForEntity(edge.targetEntityId(), components)))
                .sorted(EDGE_ORDER)
                .toList();
        List<String> tokens = aggregateTokens(componentIds, evidenceById);
        String label = label(tokens);
        EvidenceConfidence labelConfidence = labelConfidence(tokens, components.size());
        CandidateConfidence candidateConfidence = candidateConfidence(components.size(), labelConfidence);
        List<ComponentMergeDecision> mergeEvidence = decisions.stream()
                .filter(decision -> componentIds.contains(decision.componentA())
                        && componentIds.contains(decision.componentB())
                        && decision.decision() == MergeDecision.MERGED)
                .toList();
        List<String> concerns = candidateConfidence == CandidateConfidence.UNRESOLVED
                ? List.of("insufficient evidence for semantic aggregation or descriptive label")
                : List.of();
        return new AggregatedFeatureEffectCandidate(candidateId,
                components.get(0).signatureGroupId(),
                components.get(0).productSignature(),
                commonality,
                new ArrayList<>(componentIds),
                entities,
                blockIds,
                new ArrayList<>(files),
                internal,
                incoming,
                outgoing,
                tokens,
                mergeEvidence,
                label,
                labelConfidence,
                candidateConfidence,
                concerns);
    }

    private String componentIdForEntity(String entityId, List<FeatureEffectCandidate> components) {
        return components.stream()
                .filter(component -> component.memberEntities().stream()
                        .anyMatch(entity -> entity.entityId().equals(entityId)))
                .map(FeatureEffectCandidate::candidateId)
                .findFirst()
                .orElse("");
    }

    private static List<String> aggregateTokens(Set<String> componentIds,
                                                Map<String, ComponentEvidence> evidenceById) {
        Map<String, Integer> frequencies = new TreeMap<>();
        for (String componentId : componentIds) {
            evidenceById.get(componentId).tokenFrequencies()
                    .forEach((token, count) -> frequencies.merge(token, count, Integer::sum));
        }
        return frequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(8)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static String label(List<String> tokens) {
        if (tokens.isEmpty()) {
            return "";
        }
        if (tokens.size() == 1) {
            return capitalize(tokens.get(0)) + " related code";
        }
        return capitalize(tokens.get(0)) + " " + tokens.get(1) + " behavior";
    }

    private static EvidenceConfidence labelConfidence(List<String> tokens, int componentCount) {
        if (tokens.isEmpty()) {
            return EvidenceConfidence.UNLABELED;
        }
        if (tokens.size() >= 3 && componentCount > 1) {
            return EvidenceConfidence.HIGH;
        }
        if (tokens.size() >= 2) {
            return EvidenceConfidence.MEDIUM;
        }
        return EvidenceConfidence.LOW;
    }

    private static CandidateConfidence candidateConfidence(int componentCount, EvidenceConfidence labelConfidence) {
        if (componentCount > 1 && (labelConfidence == EvidenceConfidence.HIGH
                || labelConfidence == EvidenceConfidence.MEDIUM)) {
            return CandidateConfidence.HIGH;
        }
        if (labelConfidence == EvidenceConfidence.MEDIUM) {
            return CandidateConfidence.MEDIUM;
        }
        if (labelConfidence == EvidenceConfidence.LOW) {
            return CandidateConfidence.LOW;
        }
        return CandidateConfidence.UNRESOLVED;
    }

    private List<String> support(double lexical, double context, double neighborhood, double direct, double block) {
        List<String> support = new ArrayList<>();
        if (lexical >= 0.20) {
            support.add("lexical");
        }
        if (context >= 0.30) {
            support.add("contextual");
        }
        if (neighborhood >= 0.20) {
            support.add("neighborhood");
        }
        if (direct > 0.0) {
            support.add("dependency");
        }
        if (block >= 0.20) {
            support.add("block");
        }
        return support;
    }

    private static double directDependency(FeatureEffectCandidate a, FeatureEffectCandidate b) {
        Set<String> aEntities = a.memberEntities().stream().map(JavaEntity::entityId).collect(Collectors.toSet());
        Set<String> bEntities = b.memberEntities().stream().map(JavaEntity::entityId).collect(Collectors.toSet());
        boolean connected = a.outgoingEdges().stream().anyMatch(edge -> bEntities.contains(edge.targetEntityId()))
                || b.outgoingEdges().stream().anyMatch(edge -> aEntities.contains(edge.targetEntityId()));
        return connected ? 1.0 : 0.0;
    }

    private static double weightedJaccard(Map<String, Integer> left, Map<String, Integer> right) {
        TreeSet<String> keys = new TreeSet<>();
        keys.addAll(left.keySet());
        keys.addAll(right.keySet());
        if (keys.isEmpty()) {
            return 0.0;
        }
        double min = 0.0;
        double max = 0.0;
        for (String key : keys) {
            min += Math.min(left.getOrDefault(key, 0), right.getOrDefault(key, 0));
            max += Math.max(left.getOrDefault(key, 0), right.getOrDefault(key, 0));
        }
        return max == 0.0 ? 0.0 : min / max;
    }

    private static double jaccard(Collection<?> left, Collection<?> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        HashSet<Object> union = new HashSet<>(left);
        union.addAll(right);
        HashSet<Object> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private static double average(double... values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return values.length == 0 ? 0.0 : sum / values.length;
    }

    private static ProductSignature completeProductUniverse(Collection<SignatureGroup> groups) {
        TreeSet<String> products = new TreeSet<>();
        groups.forEach(group -> products.addAll(group.canonicalSignature().productIds()));
        return new ProductSignature(String.join("|", products));
    }

    private static CommonalityClassification classify(ProductSignature signature, ProductSignature universe) {
        if (signature.equals(universe)) {
            return CommonalityClassification.FULL_COMMONALITY;
        }
        if (signature.productIds().size() > 1) {
            return CommonalityClassification.SUBGROUP_COMMONALITY;
        }
        return CommonalityClassification.VARIABLE_SIGNATURE;
    }

    private static String packageName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot < 0 ? "" : qualifiedName.substring(0, lastDot);
    }

    private static String enclosingClassName(String qualifiedName) {
        String[] parts = qualifiedName.split("\\.");
        return parts.length == 0 ? qualifiedName : parts[Math.max(0, parts.length - 2)];
    }

    private static Path minFile(FeatureEffectCandidate component) {
        return component.involvedAssetFiles().stream()
                .min(Comparator.comparing(Path::toString))
                .orElse(Path.of(""));
    }

    private static int minLine(FeatureEffectCandidate component) {
        return component.memberEntities().stream()
                .mapToInt(JavaEntity::startLine)
                .min()
                .orElse(Integer.MAX_VALUE);
    }

    private static String clusterSortKey(List<FeatureEffectCandidate> cluster) {
        return cluster.stream()
                .min(COMPONENT_ORDER)
                .map(component -> minFile(component) + "|" + minLine(component) + "|" + component.candidateId())
                .orElse("");
    }

    private static Comparator<AggregatedFeatureEffectCandidate> aggregatedOrder() {
        return Comparator.comparing(AggregatedFeatureEffectCandidate::signatureGroupId)
                .thenComparing(candidate -> candidate.involvedFiles().stream()
                        .map(Path::toString)
                        .min(String::compareTo)
                        .orElse(""))
                .thenComparing(candidate -> candidate.structuralComponentIds().isEmpty()
                        ? ""
                        : candidate.structuralComponentIds().get(0))
                .thenComparing(AggregatedFeatureEffectCandidate::candidateId);
    }

    private static Comparator<ComponentSimilarity> similarityOrder() {
        return Comparator.comparing(ComponentSimilarity::groupId)
                .thenComparing(ComponentSimilarity::componentA)
                .thenComparing(ComponentSimilarity::componentB);
    }

    private static Comparator<ComponentMergeDecision> decisionOrder() {
        return Comparator.comparing(ComponentMergeDecision::groupId)
                .thenComparing(ComponentMergeDecision::componentA)
                .thenComparing(ComponentMergeDecision::componentB);
    }

    private static String blockId(ConditionalBlock block) {
        return block.filePath() + ":" + block.directiveType().name() + ":" + block.startLine() + "-" + block.endLine();
    }

    private static String capitalize(String token) {
        return token.isBlank() ? token : token.substring(0, 1).toUpperCase() + token.substring(1);
    }

    private record PairKey(String left, String right) {
        private static PairKey of(String a, String b) {
            return a.compareTo(b) <= 0 ? new PairKey(a, b) : new PairKey(b, a);
        }
    }
}
