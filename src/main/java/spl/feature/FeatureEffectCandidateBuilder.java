package spl.feature;

import spl.ConditionalBlock;
import spl.ProductSignature;
import spl.dependency.DependencyEdge;
import spl.dependency.DependencyGraph;
import spl.dependency.DependencyKindClassifier;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelationType;
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
import java.util.stream.Collectors;

public final class FeatureEffectCandidateBuilder {
    private static final Comparator<JavaEntity> ENTITY_ORDER =
            Comparator.comparing((JavaEntity entity) -> entity.sourceFile().toString())
                    .thenComparingInt(JavaEntity::startLine)
                    .thenComparing(JavaEntity::entityId);
    private static final Comparator<ConditionalBlock> BLOCK_ORDER =
            Comparator.comparing((ConditionalBlock block) -> block.filePath().toString())
                    .thenComparingInt(ConditionalBlock::startLine)
                    .thenComparing(block -> block.directiveType().name());
    private static final Comparator<DependencyEdge> EDGE_ORDER =
            Comparator.comparing((DependencyEdge edge) -> edge.sourceFile().toString())
                    .thenComparingInt(DependencyEdge::sourceLine)
                    .thenComparing(edge -> edge.relationType().name())
                    .thenComparing(DependencyEdge::sourceEntityId)
                    .thenComparing(DependencyEdge::targetEntityId);

    public FeatureEffectCandidateResult build(Collection<SignatureGroup> groups,
                                              Collection<JavaEntity> entities,
                                              DependencyGraph graph) {
        Objects.requireNonNull(groups, "groups");
        Objects.requireNonNull(entities, "entities");
        Objects.requireNonNull(graph, "graph");

        Map<String, SignatureGroup> groupsById = groups.stream()
                .collect(Collectors.toMap(SignatureGroup::groupId, group -> group));
        Map<String, JavaEntity> entitiesById = entities.stream()
                .collect(Collectors.toMap(JavaEntity::entityId, entity -> entity));
        Map<String, List<JavaEntity>> entitiesByGroup = entities.stream()
                .collect(Collectors.groupingBy(JavaEntity::signatureGroupId));
        Map<String, List<DependencyEdge>> sameGroupEdges = graph.edges().stream()
                .filter(edge -> DependencyKindClassifier.isImplementationDependency(edge.relationType()))
                .filter(edge -> edge.sourceGroupId().equals(edge.targetGroupId()))
                .collect(Collectors.groupingBy(DependencyEdge::sourceGroupId));

        List<CandidateAccumulator> accumulators = new ArrayList<>();
        Map<String, CandidateAccumulator> accumulatorByEntityId = new HashMap<>();

        for (SignatureGroup group : groups.stream()
                .sorted(Comparator.comparing(SignatureGroup::groupId))
                .toList()) {
            List<JavaEntity> groupEntities = entitiesByGroup.getOrDefault(group.groupId(), List.of()).stream()
                    .sorted(ENTITY_ORDER)
                    .toList();
            List<DependencyEdge> groupEdges = sameGroupEdges.getOrDefault(group.groupId(), List.of()).stream()
                    .filter(edge -> entitiesById.containsKey(edge.sourceEntityId()))
                    .filter(edge -> entitiesById.containsKey(edge.targetEntityId()))
                    .sorted(EDGE_ORDER)
                    .toList();

            List<List<JavaEntity>> components = weakComponents(groupEntities, groupEdges);
            components = components.stream()
                    .sorted(Comparator.comparing(FeatureEffectCandidateBuilder::componentSortKey))
                    .toList();

            int number = 1;
            for (List<JavaEntity> component : components) {
                String candidateId = "FEC-" + group.groupId() + "-" + String.format("%02d", number++);
                CandidateAccumulator accumulator = new CandidateAccumulator(candidateId, group.groupId(),
                        group.canonicalSignature());
                component.forEach(accumulator::addEntity);
                for (DependencyEdge edge : groupEdges) {
                    if (accumulator.hasEntity(edge.sourceEntityId()) && accumulator.hasEntity(edge.targetEntityId())) {
                        accumulator.addInternalEdge(edge);
                    }
                }
                associateBlocks(group, accumulator, groupEdges);
                accumulators.add(accumulator);
                component.forEach(entity -> accumulatorByEntityId.put(entity.entityId(), accumulator));
            }

            Set<String> assignedBlockIds = accumulators.stream()
                    .filter(accumulator -> accumulator.groupId.equals(group.groupId()))
                    .flatMap(accumulator -> accumulator.blockIds.stream())
                    .collect(Collectors.toSet());
            for (ConditionalBlock block : group.blocks().stream().sorted(BLOCK_ORDER).toList()) {
                String blockId = blockId(block);
                if (!assignedBlockIds.contains(blockId)) {
                    String candidateId = "FEC-" + group.groupId() + "-" + String.format("%02d", number++);
                    CandidateAccumulator accumulator = CandidateAccumulator.blockOnly(candidateId, group.groupId(),
                            group.canonicalSignature());
                    accumulator.addBlock(block);
                    accumulators.add(accumulator);
                }
            }
        }

        for (DependencyEdge edge : graph.edges().stream()
                .filter(edge -> DependencyKindClassifier.isImplementationDependency(edge.relationType()))
                .sorted(EDGE_ORDER)
                .toList()) {
            CandidateAccumulator source = accumulatorByEntityId.get(edge.sourceEntityId());
            CandidateAccumulator target = accumulatorByEntityId.get(edge.targetEntityId());
            if (source == null || target == null || source == target) {
                continue;
            }
            source.addOutgoingEdge(edge);
            target.addIncomingEdge(edge);
        }

        List<FeatureEffectCandidate> candidates = accumulators.stream()
                .map(CandidateAccumulator::toCandidate)
                .sorted(candidateOrder())
                .toList();
        List<CandidateDependency> dependencies = candidateDependencies(candidates, accumulators);
        return new FeatureEffectCandidateResult(candidates, dependencies, List.of());
    }

    private static List<List<JavaEntity>> weakComponents(List<JavaEntity> entities, List<DependencyEdge> edges) {
        Map<String, JavaEntity> entitiesById = entities.stream()
                .collect(Collectors.toMap(JavaEntity::entityId, entity -> entity));
        Map<String, Set<String>> adjacency = new HashMap<>();
        entities.forEach(entity -> adjacency.put(entity.entityId(), new TreeSet<>()));
        for (DependencyEdge edge : edges) {
            if (adjacency.containsKey(edge.sourceEntityId()) && adjacency.containsKey(edge.targetEntityId())) {
                adjacency.get(edge.sourceEntityId()).add(edge.targetEntityId());
                adjacency.get(edge.targetEntityId()).add(edge.sourceEntityId());
            }
        }
        addContainmentAdjacency(entities, adjacency);

        Set<String> visited = new HashSet<>();
        List<List<JavaEntity>> components = new ArrayList<>();
        for (JavaEntity entity : entities) {
            if (visited.contains(entity.entityId())) {
                continue;
            }
            List<JavaEntity> component = new ArrayList<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(entity.entityId());
            visited.add(entity.entityId());
            while (!queue.isEmpty()) {
                String id = queue.removeFirst();
                component.add(entitiesById.get(id));
                for (String neighbor : adjacency.getOrDefault(id, Set.of())) {
                    if (visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
            components.add(component.stream().sorted(ENTITY_ORDER).toList());
        }
        return components;
    }

    private static void addContainmentAdjacency(List<JavaEntity> entities, Map<String, Set<String>> adjacency) {
        for (JavaEntity container : entities) {
            if (container.entityType() != JavaEntityType.CLASS && container.entityType() != JavaEntityType.INTERFACE) {
                continue;
            }
            String containerName = container.qualifiedName();
            if (containerName == null || containerName.isBlank()) {
                continue;
            }
            String prefix = containerName + ".";
            for (JavaEntity member : entities) {
                if (container.entityId().equals(member.entityId()) || member.qualifiedName() == null) {
                    continue;
                }
                if (member.qualifiedName().startsWith(prefix)
                        && container.sourceFile().toAbsolutePath().normalize()
                        .equals(member.sourceFile().toAbsolutePath().normalize())) {
                    adjacency.get(container.entityId()).add(member.entityId());
                    adjacency.get(member.entityId()).add(container.entityId());
                }
            }
        }
    }

    private static void associateBlocks(SignatureGroup group, CandidateAccumulator accumulator,
                                        List<DependencyEdge> groupEdges) {
        Set<String> sourceBlockIds = groupEdges.stream()
                .filter(edge -> accumulator.hasEntity(edge.sourceEntityId()))
                .map(DependencyEdge::sourceBlockId)
                .collect(Collectors.toSet());
        for (ConditionalBlock block : group.blocks().stream().sorted(BLOCK_ORDER).toList()) {
            String blockId = blockId(block);
            boolean containsEntity = accumulator.entities.stream()
                    .anyMatch(entity -> entity.containingBlockId().equals(blockId)
                            || contains(block, entity.sourceFile(), entity.startLine()));
            if (containsEntity || sourceBlockIds.contains(blockId)) {
                accumulator.addBlock(block);
            }
        }
    }

    private static boolean contains(ConditionalBlock block, Path file, int line) {
        return block.filePath().toAbsolutePath().normalize().equals(file.toAbsolutePath().normalize())
                && block.startLine() <= line
                && line <= block.endLine();
    }

    private static String componentSortKey(List<JavaEntity> entities) {
        return entities.stream()
                .min(ENTITY_ORDER)
                .map(entity -> entity.sourceFile() + "|" + entity.startLine() + "|" + entity.entityId())
                .orElse("");
    }

    private static Comparator<FeatureEffectCandidate> candidateOrder() {
        return Comparator.comparing(FeatureEffectCandidate::signatureGroupId)
                .thenComparing(candidate -> candidate.memberEntities().stream()
                        .map(entity -> entity.sourceFile().toString())
                        .min(String::compareTo)
                        .orElse(""))
                .thenComparingInt(candidate -> candidate.memberEntities().stream()
                        .mapToInt(JavaEntity::startLine)
                        .min()
                        .orElse(Integer.MAX_VALUE))
                .thenComparing(FeatureEffectCandidate::candidateId);
    }

    private static List<CandidateDependency> candidateDependencies(List<FeatureEffectCandidate> candidates,
                                                                   List<CandidateAccumulator> accumulators) {
        Map<String, FeatureEffectCandidate> candidatesById = candidates.stream()
                .collect(Collectors.toMap(FeatureEffectCandidate::candidateId, candidate -> candidate));
        Map<String, String> candidateIdByEntityId = new HashMap<>();
        for (FeatureEffectCandidate candidate : candidates) {
            candidate.memberEntities().forEach(entity -> candidateIdByEntityId.put(entity.entityId(), candidate.candidateId()));
        }

        Map<CandidateDependencyKey, CandidateDependencyCount> counts = new TreeMap<>();
        for (CandidateAccumulator accumulator : accumulators) {
            for (DependencyEdge edge : accumulator.internalEdges) {
                addDependency(counts, accumulator.candidateId, accumulator.candidateId,
                        CandidateDependencyScope.INTERNAL_CANDIDATE, edge, candidatesById);
            }
            for (DependencyEdge edge : accumulator.outgoingEdges) {
                String targetCandidateId = candidateIdByEntityId.get(edge.targetEntityId());
                if (targetCandidateId == null) {
                    continue;
                }
                FeatureEffectCandidate source = candidatesById.get(accumulator.candidateId);
                FeatureEffectCandidate target = candidatesById.get(targetCandidateId);
                CandidateDependencyScope scope = source.signatureGroupId().equals(target.signatureGroupId())
                        ? CandidateDependencyScope.INTRA_SIGNATURE_INTER_CANDIDATE
                        : CandidateDependencyScope.CROSS_SIGNATURE;
                addDependency(counts, accumulator.candidateId, targetCandidateId, scope, edge, candidatesById);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> entry.getKey().toDependency(entry.getValue(), candidatesById))
                .toList();
    }

    private static void addDependency(Map<CandidateDependencyKey, CandidateDependencyCount> counts,
                                      String sourceCandidateId, String targetCandidateId,
                                      CandidateDependencyScope scope, DependencyEdge edge,
                                      Map<String, FeatureEffectCandidate> candidatesById) {
        if (!candidatesById.containsKey(sourceCandidateId) || !candidatesById.containsKey(targetCandidateId)) {
            return;
        }
        CandidateDependencyKey key = new CandidateDependencyKey(sourceCandidateId, targetCandidateId,
                scope, edge.relationType());
        counts.computeIfAbsent(key, ignored -> new CandidateDependencyCount()).add(edge.crossFile());
    }

    private static String blockId(ConditionalBlock block) {
        return block.filePath() + ":" + block.directiveType().name() + ":" + block.startLine() + "-" + block.endLine();
    }

    private static final class CandidateAccumulator {
        private final String candidateId;
        private final String groupId;
        private final ProductSignature signature;
        private final boolean blockOnly;
        private final List<JavaEntity> entities = new ArrayList<>();
        private final List<ConditionalBlock> blocks = new ArrayList<>();
        private final Set<String> blockIds = new HashSet<>();
        private final List<DependencyEdge> internalEdges = new ArrayList<>();
        private final List<DependencyEdge> incomingEdges = new ArrayList<>();
        private final List<DependencyEdge> outgoingEdges = new ArrayList<>();

        private CandidateAccumulator(String candidateId, String groupId, ProductSignature signature) {
            this(candidateId, groupId, signature, false);
        }

        private CandidateAccumulator(String candidateId, String groupId, ProductSignature signature, boolean blockOnly) {
            this.candidateId = candidateId;
            this.groupId = groupId;
            this.signature = signature;
            this.blockOnly = blockOnly;
        }

        private static CandidateAccumulator blockOnly(String candidateId, String groupId, ProductSignature signature) {
            return new CandidateAccumulator(candidateId, groupId, signature, true);
        }

        private void addEntity(JavaEntity entity) {
            entities.add(entity);
        }

        private boolean hasEntity(String entityId) {
            return entities.stream().anyMatch(entity -> entity.entityId().equals(entityId));
        }

        private void addBlock(ConditionalBlock block) {
            if (blockIds.add(blockId(block))) {
                blocks.add(block);
            }
        }

        private void addInternalEdge(DependencyEdge edge) {
            internalEdges.add(edge);
        }

        private void addIncomingEdge(DependencyEdge edge) {
            incomingEdges.add(edge);
        }

        private void addOutgoingEdge(DependencyEdge edge) {
            outgoingEdges.add(edge);
        }

        private FeatureEffectCandidate toCandidate() {
            List<JavaEntity> orderedEntities = entities.stream().sorted(ENTITY_ORDER).toList();
            List<ConditionalBlock> orderedBlocks = blocks.stream().sorted(BLOCK_ORDER).toList();
            List<DependencyEdge> orderedInternal = internalEdges.stream().sorted(EDGE_ORDER).toList();
            List<DependencyEdge> orderedIncoming = incomingEdges.stream().sorted(EDGE_ORDER).toList();
            List<DependencyEdge> orderedOutgoing = outgoingEdges.stream().sorted(EDGE_ORDER).toList();
            TreeSet<Path> files = new TreeSet<>(Comparator.comparing(Path::toString));
            orderedEntities.forEach(entity -> files.add(entity.sourceFile()));
            orderedBlocks.forEach(block -> files.add(block.filePath()));
            TreeSet<String> products = new TreeSet<>();
            orderedEntities.forEach(entity -> products.addAll(entity.observedProducts()));
            if (products.isEmpty()) {
                products.addAll(signature.productIds());
            }
            Map<JavaEntityType, Long> entityCounts = orderedEntities.stream()
                    .collect(Collectors.groupingBy(JavaEntity::entityType, () -> new TreeMap<>(
                            Comparator.comparing(Enum::name)), Collectors.counting()));
            Map<JavaRelationType, Long> edgeCounts = orderedInternal.stream()
                    .collect(Collectors.groupingBy(DependencyEdge::relationType, () -> new TreeMap<>(
                            Comparator.comparing(Enum::name)), Collectors.counting()));
            CandidateClassificationStatus status = classification(orderedEntities, orderedInternal);
            return new FeatureEffectCandidate(
                    candidateId,
                    groupId,
                    signature,
                    orderedEntities,
                    orderedBlocks,
                    orderedInternal,
                    orderedIncoming,
                    orderedOutgoing,
                    new ArrayList<>(files),
                    new ArrayList<>(products),
                    status,
                    entityCounts,
                    edgeCounts,
                    orderedEntities.size(),
                    (int) orderedInternal.stream().filter(DependencyEdge::crossFile).count(),
                    status == CandidateClassificationStatus.ISOLATED_ENTITY ? 1 : 0
            );
        }

        private CandidateClassificationStatus classification(List<JavaEntity> orderedEntities,
                                                             List<DependencyEdge> orderedInternal) {
            if (blockOnly) {
                return CandidateClassificationStatus.BLOCK_ONLY;
            }
            if (orderedEntities.size() == 1 && orderedInternal.isEmpty()) {
                return CandidateClassificationStatus.ISOLATED_ENTITY;
            }
            if (!orderedEntities.isEmpty()) {
                return CandidateClassificationStatus.STRUCTURALLY_CONNECTED;
            }
            return CandidateClassificationStatus.UNCLASSIFIED;
        }
    }

    private record CandidateDependencyKey(
            String sourceCandidateId,
            String targetCandidateId,
            CandidateDependencyScope scope,
            JavaRelationType relationType
    ) implements Comparable<CandidateDependencyKey> {
        @Override
        public int compareTo(CandidateDependencyKey other) {
            int comparison = sourceCandidateId.compareTo(other.sourceCandidateId);
            if (comparison != 0) {
                return comparison;
            }
            comparison = targetCandidateId.compareTo(other.targetCandidateId);
            if (comparison != 0) {
                return comparison;
            }
            comparison = scope.name().compareTo(other.scope.name());
            if (comparison != 0) {
                return comparison;
            }
            return relationType.name().compareTo(other.relationType.name());
        }

        private CandidateDependency toDependency(CandidateDependencyCount count,
                                                 Map<String, FeatureEffectCandidate> candidatesById) {
            FeatureEffectCandidate source = candidatesById.get(sourceCandidateId);
            FeatureEffectCandidate target = candidatesById.get(targetCandidateId);
            return new CandidateDependency(
                    sourceCandidateId,
                    targetCandidateId,
                    scope,
                    relationType,
                    count.edgeCount,
                    count.crossFileEdgeCount,
                    source.signatureGroupId(),
                    target.signatureGroupId(),
                    source.productSignature(),
                    target.productSignature()
            );
        }
    }

    private static final class CandidateDependencyCount {
        private int edgeCount;
        private int crossFileEdgeCount;

        private void add(boolean crossFile) {
            edgeCount++;
            if (crossFile) {
                crossFileEdgeCount++;
            }
        }
    }
}
