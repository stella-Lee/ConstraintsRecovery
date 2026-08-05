package spl.requiresanalysis;

import spl.ConditionalBlock;
import spl.dependency.DependencyEdge;
import spl.dependency.DependencyGraph;
import spl.dependency.DependencyKindClassifier;
import spl.entity.EntityExtractionResult;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelationType;
import spl.entity.ResolutionStatus;
import spl.grouping.SignatureGroup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class RequiresEvidenceBuilder {
    private final EntityClassifier entityClassifier;
    private final DependencyEvidenceScorer dependencyScorer;

    public RequiresEvidenceBuilder() {
        this(new EntityClassifier(), new DependencyEvidenceScorer());
    }

    public RequiresEvidenceBuilder(EntityClassifier entityClassifier, DependencyEvidenceScorer dependencyScorer) {
        this.entityClassifier = Objects.requireNonNull(entityClassifier, "entityClassifier");
        this.dependencyScorer = Objects.requireNonNull(dependencyScorer, "dependencyScorer");
    }

    public RequiresEvidenceResult build(List<SignatureGroup> groups, EntityExtractionResult entityResult,
                                        DependencyGraph graph) {
        List<ConditionalBlock> blocks = groups.stream()
                .flatMap(group -> group.blocks().stream())
                .sorted(Comparator.comparing((ConditionalBlock block) -> block.filePath().toString())
                        .thenComparingInt(ConditionalBlock::startLine)
                        .thenComparing(block -> block.directiveType().name()))
                .toList();
        Map<String, String> blockToGroup = new LinkedHashMap<>();
        for (SignatureGroup group : groups) {
            for (ConditionalBlock block : group.blocks()) {
                blockToGroup.put(blockId(block), group.groupId());
            }
        }

        List<JavaEntity> sortedEntities = entityResult.entities().stream()
                .sorted(Comparator.comparing((JavaEntity entity) -> entity.sourceFile().toString())
                        .thenComparingInt(JavaEntity::startLine)
                        .thenComparing(entity -> entity.entityType().name())
                        .thenComparing(entity -> displayName(entity)))
                .toList();
        Map<String, JavaEntity> entitiesById = sortedEntities.stream()
                .collect(Collectors.toMap(JavaEntity::entityId, entity -> entity, (left, right) -> left, LinkedHashMap::new));
        Map<String, EntityClassification> classificationsById = new LinkedHashMap<>();
        for (JavaEntity entity : sortedEntities) {
            EntityClassification classification = entityClassifier.classify(entity);
            classificationsById.put(entity.entityId(), classification);
        }

        List<DependencyEvidence> dependencies = new ArrayList<>();
        graph.edges().stream()
                .filter(edge -> DependencyKindClassifier.isBlockGraphDependency(edge.relationType()))
                .sorted(Comparator.comparing((DependencyEdge edge) -> edge.sourceFile().toString())
                        .thenComparingInt(DependencyEdge::sourceLine)
                        .thenComparing(edge -> edge.relationType().name())
                        .thenComparing(DependencyEdge::sourceEntityId)
                        .thenComparing(DependencyEdge::targetEntityId))
                .map(edge -> dependencyScorer.score(edge, entitiesById, classificationsById))
                .forEach(dependencies::add);
        dependencies = mergeSameImplementationFieldReferences(dependencies, entitiesById);

        List<BlockSummary> summaries = buildSummaries(blocks, sortedEntities, dependencies, classificationsById, blockToGroup);
        return new RequiresEvidenceResult(
                blocks,
                sortedEntities,
                dependencies.stream().sorted(dependencyComparator()).toList(),
                List.copyOf(classificationsById.values()),
                summaries,
                blockToGroup
        );
    }

    private List<DependencyEvidence> mergeSameImplementationFieldReferences(List<DependencyEvidence> dependencies,
                                                                            Map<String, JavaEntity> entitiesById) {
        return dependencies.stream()
                .map(dependency -> sameImplementationFieldReference(dependency, entitiesById)
                        ? asInternalImplementationReference(dependency)
                        : dependency)
                .toList();
    }

    private boolean sameImplementationFieldReference(DependencyEvidence dependency,
                                                     Map<String, JavaEntity> entitiesById) {
        if (dependency.relationType() != JavaRelationType.FIELD_REFERENCE
                || dependency.resolutionStatus() != ResolutionStatus.RESOLVED_INTERNAL
                || dependency.sourceBlockId().isBlank()
                || dependency.targetBlockId().isBlank()
                || dependency.sourceBlockId().equals(dependency.targetBlockId())) {
            return false;
        }
        JavaEntity source = entitiesById.get(dependency.sourceEntityId());
        JavaEntity target = entitiesById.get(dependency.targetEntityId());
        if (source == null || target == null || target.entityType() != JavaEntityType.FIELD) {
            return false;
        }
        String sourceOwner = declaringType(source);
        String targetOwner = declaringType(target);
        return !sourceOwner.isBlank() && sourceOwner.equals(targetOwner);
    }

    private DependencyEvidence asInternalImplementationReference(DependencyEvidence dependency) {
        return new DependencyEvidence(
                dependency.relationId(),
                dependency.sourceBlockId(),
                dependency.targetBlockId(),
                dependency.sourceEntityId(),
                dependency.targetEntityId(),
                dependency.targetText(),
                dependency.relationType(),
                dependency.sourceFile(),
                dependency.sourceLine(),
                dependency.sourceGroupId(),
                dependency.targetGroupId(),
                dependency.sourceSignature(),
                dependency.targetSignature(),
                dependency.sourceEntityRole(),
                dependency.targetEntityRole(),
                dependency.dependencyStrength(),
                0.2,
                RequiresRelevance.LOW,
                DependencyExclusionReason.INTRA_BLOCK,
                dependency.resolutionStatus(),
                dependency.observedProducts()
        );
    }

    public static String blockId(ConditionalBlock block) {
        return block.filePath() + ":" + block.directiveType().name() + ":" + block.startLine() + "-" + block.endLine();
    }

    private List<BlockSummary> buildSummaries(List<ConditionalBlock> blocks, List<JavaEntity> entities,
                                              List<DependencyEvidence> dependencies,
                                              Map<String, EntityClassification> classificationsById,
                                              Map<String, String> blockToGroup) {
        Map<String, List<JavaEntity>> entitiesByBlock = entities.stream()
                .collect(Collectors.groupingBy(JavaEntity::containingBlockId));
        Map<String, List<DependencyEvidence>> outgoingByBlock = dependencies.stream()
                .collect(Collectors.groupingBy(DependencyEvidence::sourceBlockId));
        Map<String, List<DependencyEvidence>> incomingByBlock = dependencies.stream()
                .filter(dependency -> !dependency.targetBlockId().isBlank())
                .collect(Collectors.groupingBy(DependencyEvidence::targetBlockId));

        List<BlockSummary> summaries = new ArrayList<>();
        for (ConditionalBlock block : blocks) {
            String blockId = blockId(block);
            List<JavaEntity> blockEntities = entitiesByBlock.getOrDefault(blockId, List.of());
            List<DependencyEvidence> outgoing = outgoingByBlock.getOrDefault(blockId, List.of());
            List<DependencyEvidence> incoming = incomingByBlock.getOrDefault(blockId, List.of());
            Map<EntityRole, Long> roleCounts = blockEntities.stream()
                    .map(entity -> classificationsById.get(entity.entityId()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.groupingBy(EntityClassification::entityRole, TreeMap::new, Collectors.counting()));
            List<String> products = blockEntities.stream()
                    .flatMap(entity -> entity.observedProducts().stream())
                    .distinct()
                    .sorted()
                    .toList();
            summaries.add(new BlockSummary(
                    blockId,
                    blockToGroup.getOrDefault(blockId, ""),
                    block.signature().toString(),
                    block.filePath().toString(),
                    block.directiveType().name(),
                    block.startLine(),
                    block.endLine(),
                    blockEntities.size(),
                    incoming.size(),
                    outgoing.size(),
                    countRelevance(outgoing, RequiresRelevance.HIGH),
                    countRelevance(outgoing, RequiresRelevance.MEDIUM),
                    countRelevance(outgoing, RequiresRelevance.LOW),
                    roleCounts,
                    products
            ));
        }
        return summaries;
    }

    private int countRelevance(List<DependencyEvidence> dependencies, RequiresRelevance relevance) {
        return (int) dependencies.stream()
                .filter(dependency -> dependency.requiresRelevance() == relevance)
                .count();
    }

    private Comparator<DependencyEvidence> dependencyComparator() {
        return Comparator.comparing((DependencyEvidence dependency) -> dependency.sourceFile().toString())
                .thenComparingInt(DependencyEvidence::sourceLine)
                .thenComparing(dependency -> dependency.relationType().name())
                .thenComparing(DependencyEvidence::sourceEntityId)
                .thenComparing(dependency -> dependency.targetEntityId().isBlank()
                        ? dependency.targetText()
                        : dependency.targetEntityId());
    }

    private static String displayName(JavaEntity entity) {
        return entity.qualifiedName() == null || entity.qualifiedName().isBlank()
                ? entity.simpleName()
                : entity.qualifiedName();
    }

    private static String declaringType(JavaEntity entity) {
        String qualifiedName = entity.qualifiedName();
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return "";
        }
        String suffix = "." + entity.simpleName();
        if (!qualifiedName.endsWith(suffix)) {
            return "";
        }
        return qualifiedName.substring(0, qualifiedName.length() - suffix.length());
    }
}
