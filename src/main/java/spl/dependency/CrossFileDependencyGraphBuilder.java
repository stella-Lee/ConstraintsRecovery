package spl.dependency;

import spl.entity.EntityExtractionResult;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelation;
import spl.entity.JavaRelationType;
import spl.entity.ResolutionStatus;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

public final class CrossFileDependencyGraphBuilder {
    private static final Comparator<DependencyNode> NODE_ORDER =
            Comparator.comparing((DependencyNode node) -> node.sourceFile().toString())
                    .thenComparingInt(DependencyNode::startLine)
                    .thenComparing(node -> node.entityType().name())
                    .thenComparing(node -> node.qualifiedName() == null ? node.simpleName() : node.qualifiedName());
    private static final Comparator<DependencyEdge> EDGE_ORDER =
            Comparator.comparing((DependencyEdge edge) -> edge.sourceFile().toString())
                    .thenComparingInt(DependencyEdge::sourceLine)
                    .thenComparing(edge -> edge.relationType().name())
                    .thenComparing(DependencyEdge::sourceEntityId)
                    .thenComparing(DependencyEdge::targetEntityId);
    private static final Comparator<UnresolvedDependencyRelation> UNRESOLVED_ORDER =
            Comparator.comparing((UnresolvedDependencyRelation relation) -> relation.sourceFile().toString())
                    .thenComparingInt(UnresolvedDependencyRelation::sourceLine)
                    .thenComparing(relation -> relation.relationType().name())
                    .thenComparing(relation -> relation.sourceEntityId() == null ? "" : relation.sourceEntityId())
                    .thenComparing(relation -> relation.targetText() == null ? "" : relation.targetText());

    public DependencyGraph build(EntityExtractionResult extractionResult) {
        Objects.requireNonNull(extractionResult, "extractionResult");
        Map<String, JavaEntity> entitiesById = new HashMap<>();
        List<DependencyNode> nodes = new ArrayList<>();
        for (JavaEntity entity : extractionResult.entities()) {
            entitiesById.put(entity.entityId(), entity);
            nodes.add(DependencyNode.from(entity));
        }

        Map<EdgeKey, EdgeAccumulator> edgeAccumulators = new LinkedHashMap<>();
        List<UnresolvedDependencyRelation> unresolvedRelations = new ArrayList<>();
        int excludedDependencyCount = 0;
        int externalDependencyRemovedCount = 0;
        int unresolvedDependencyRemovedCount = 0;
        for (JavaRelation relation : extractionResult.relations()) {
            if (!DependencyKindClassifier.isBlockGraphDependency(relation.relationType())) {
                excludedDependencyCount++;
                continue;
            }
            DependencyResolutionResult resolution = resolve(relation, entitiesById);
            if (!resolution.resolved()) {
                unresolvedRelations.add(resolution.unresolvedRelation());
                if (resolution.unresolvedRelation().resolutionStatus() == ResolutionStatus.RESOLVED_EXTERNAL) {
                    externalDependencyRemovedCount++;
                } else {
                    unresolvedDependencyRemovedCount++;
                }
                continue;
            }
            DependencyEdge edge = resolution.edge();
            edgeAccumulators.computeIfAbsent(EdgeKey.from(edge), ignored -> new EdgeAccumulator(edge))
                    .addProducts(edge.observedProducts());
        }

        List<DependencyEdge> edges = new ArrayList<>();
        int edgeNumber = 1;
        for (EdgeAccumulator accumulator : edgeAccumulators.values().stream()
                .sorted(Comparator.comparing(EdgeAccumulator::sortKey))
                .toList()) {
            edges.add(accumulator.toEdge("D" + edgeNumber));
            edgeNumber++;
        }

        DependencyGraphStatistics statistics = new DependencyGraphStatistics(
                (int) edges.stream()
                        .filter(edge -> DependencyKindClassifier.isImplementationDependency(edge.relationType()))
                        .count(),
                (int) edges.stream()
                        .filter(edge -> DependencyKindClassifier.isStructuralDependency(edge.relationType()))
                        .count(),
                excludedDependencyCount,
                externalDependencyRemovedCount,
                unresolvedDependencyRemovedCount
        );

        return new DependencyGraph(
                nodes.stream().sorted(NODE_ORDER).toList(),
                edges.stream().sorted(EDGE_ORDER).toList(),
                unresolvedRelations.stream().sorted(UNRESOLVED_ORDER).toList(),
                statistics
        );
    }

    private static DependencyResolutionResult resolve(JavaRelation relation, Map<String, JavaEntity> entitiesById) {
        if (relation.sourceEntityId() == null || relation.sourceEntityId().isBlank()) {
            return new DependencyResolutionResult(relation, null,
                    UnresolvedDependencyRelation.from(relation, "missing source entity"));
        }
        JavaEntity source = entitiesById.get(relation.sourceEntityId());
        if (source == null) {
            return new DependencyResolutionResult(relation, null,
                    UnresolvedDependencyRelation.from(relation, "source entity not found"));
        }
        if (relation.targetEntityId() == null || relation.targetEntityId().isBlank()) {
            String reason = switch (relation.resolutionStatus()) {
                case RESOLVED_EXTERNAL -> "external target";
                case AMBIGUOUS -> "ambiguous target";
                case UNRESOLVED -> "unresolved target";
                case RESOLVED_INTERNAL -> "missing internal target";
            };
            return new DependencyResolutionResult(relation, null,
                    UnresolvedDependencyRelation.from(relation, reason));
        }
        JavaEntity target = entitiesById.get(relation.targetEntityId());
        if (target == null) {
            return new DependencyResolutionResult(relation, null,
                    UnresolvedDependencyRelation.from(relation, "target entity not found"));
        }
        if (relation.relationType() == JavaRelationType.CONSTRUCTOR_CALL
                && target.entityType() != JavaEntityType.CONSTRUCTOR) {
            return new DependencyResolutionResult(relation, null,
                    UnresolvedDependencyRelation.from(relation, "invalid constructor target type"));
        }

        boolean crossFile = !normalize(source.sourceFile()).equals(normalize(target.sourceFile()));
        DependencyEdge edge = new DependencyEdge(
                "D0",
                source.entityId(),
                target.entityId(),
                relation.relationType(),
                relation.sourceFile(),
                relation.sourceLine(),
                target.sourceFile(),
                relation.containingBlockId(),
                relation.signatureGroupId(),
                relation.effectiveProductSignature(),
                target.signatureGroupId(),
                target.effectiveProductSignature(),
                crossFile,
                relation.observedProducts(),
                ResolutionStatus.RESOLVED_INTERNAL
        );
        return new DependencyResolutionResult(relation, edge, null);
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private record EdgeKey(String sourceEntityId, String targetEntityId, String relationType,
                           String sourceFile, int sourceLine) {
        private static EdgeKey from(DependencyEdge edge) {
            return new EdgeKey(
                    edge.sourceEntityId(),
                    edge.targetEntityId(),
                    edge.relationType().name(),
                    edge.sourceFile().toString(),
                    edge.sourceLine()
            );
        }
    }

    private static final class EdgeAccumulator {
        private final DependencyEdge edge;
        private final TreeSet<String> observedProducts = new TreeSet<>();

        private EdgeAccumulator(DependencyEdge edge) {
            this.edge = edge;
        }

        private void addProducts(List<String> products) {
            observedProducts.addAll(products);
        }

        private String sortKey() {
            return edge.sourceFile() + "|" + edge.sourceLine() + "|" + edge.relationType()
                    + "|" + edge.sourceEntityId() + "|" + edge.targetEntityId();
        }

        private DependencyEdge toEdge(String edgeId) {
            return new DependencyEdge(
                    edgeId,
                    edge.sourceEntityId(),
                    edge.targetEntityId(),
                    edge.relationType(),
                    edge.sourceFile(),
                    edge.sourceLine(),
                    edge.targetFile(),
                    edge.sourceBlockId(),
                    edge.sourceGroupId(),
                    edge.sourceSignature(),
                    edge.targetGroupId(),
                    edge.targetSignature(),
                    edge.crossFile(),
                    new ArrayList<>(observedProducts),
                    edge.resolutionStatus()
            );
        }
    }
}
