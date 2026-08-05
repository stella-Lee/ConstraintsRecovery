package spl.requiresanalysis;

import spl.ConditionalBlock;
import spl.dependency.DependencyKindClassifier;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelationType;
import spl.entity.ResolutionStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class AuditReportBuilder {
    private final RequiresCandidateConfig candidateConfig;

    public AuditReportBuilder() {
        this(RequiresCandidateConfig.defaults());
    }

    public AuditReportBuilder(RequiresCandidateConfig candidateConfig) {
        this.candidateConfig = Objects.requireNonNull(candidateConfig, "candidateConfig");
    }

    public AuditReportResult build(RequiresEvidenceResult result) {
        Map<String, JavaEntity> entitiesById = result.entities().stream()
                .collect(Collectors.toMap(JavaEntity::entityId, entity -> entity, (left, right) -> left, LinkedHashMap::new));
        Map<String, List<JavaEntity>> entitiesByBlock = result.entities().stream()
                .collect(Collectors.groupingBy(JavaEntity::containingBlockId));
        Map<String, EntityClassification> previousById = result.classifications().stream()
                .collect(Collectors.toMap(EntityClassification::entityId, classification -> classification));
        Map<String, List<DependencyEvidence>> dependenciesBySource = result.dependencies().stream()
                .collect(Collectors.groupingBy(DependencyEvidence::sourceEntityId));
        Map<String, List<DependencyEvidence>> dependenciesByTarget = result.dependencies().stream()
                .filter(dependency -> !dependency.targetEntityId().isBlank())
                .collect(Collectors.groupingBy(DependencyEvidence::targetEntityId));

        Map<String, AuditedEntityClassification> auditedById = new LinkedHashMap<>();
        result.entities().stream()
                .sorted(Comparator.comparing((JavaEntity entity) -> entity.sourceFile().toString())
                        .thenComparingInt(JavaEntity::startLine)
                        .thenComparing(JavaEntity::entityId))
                .forEach(entity -> auditedById.put(entity.entityId(), classify(
                        entity,
                        previousById.get(entity.entityId()),
                        dependenciesBySource.getOrDefault(entity.entityId(), List.of()),
                        dependenciesByTarget.getOrDefault(entity.entityId(), List.of()),
                        entitiesById
                )));

        List<AuditedBlockPair> pairs = buildPairs(result, auditedById, entitiesById, entitiesByBlock);
        return new AuditReportResult(List.copyOf(auditedById.values()), pairs);
    }

    private AuditedEntityClassification classify(JavaEntity entity, EntityClassification previous,
                                                 List<DependencyEvidence> outgoing,
                                                 List<DependencyEvidence> incoming,
                                                 Map<String, JavaEntity> entitiesById) {
        String qualifiedName = displayName(entity);
        String declaringType = declaringType(entity);
        String packageName = packageName(qualifiedName);
        String typeSignature = typeSignature(outgoing, entitiesById);
        Set<String> nameTokens = tokens(entity.simpleName());
        Set<String> declaringTokens = tokens(declaringType);
        Set<String> typeTokens = tokens(typeSignature);
        Set<String> packageTokens = tokens(packageName);
        Set<String> dependencyTokens = dependencyTokens(outgoing, incoming, entitiesById);

        ArchitecturalRole architecture = architecturalRole(packageTokens, declaringTokens, nameTokens);
        SemanticRole semantic = semanticRole(entity, nameTokens, declaringTokens, typeTokens, dependencyTokens,
                architecture, previous == null ? EntityRole.UNKNOWN : previous.entityRole());
        EvidenceLevel domain = domainRelevance(semantic, architecture, nameTokens, declaringTokens, typeTokens,
                dependencyTokens);
        EvidenceLevel variability = variabilityRelevance(nameTokens, declaringTokens, packageTokens);
        ClassificationConfidence confidence = confidence(semantic, domain, declaringTokens, typeTokens, dependencyTokens);

        List<String> positive = new ArrayList<>();
        List<String> negative = new ArrayList<>();
        if (!nameTokens.isEmpty()) {
            positive.add("name tokens=" + String.join("|", nameTokens));
        }
        if (hasDomainConcept(declaringTokens)) {
            positive.add("declaring type contains domain concept");
        }
        if (hasDomainConcept(typeTokens)) {
            positive.add("type signature contains domain concept");
        }
        if (architecture == ArchitecturalRole.UI) {
            positive.add("UI architecture retained independently from domain relevance");
        }
        if (semantic == SemanticRole.UNKNOWN) {
            negative.add("no contextual semantic role identified");
        }
        if (genericGetter(entity.simpleName(), nameTokens, declaringTokens, typeTokens)) {
            negative.add("getter has no domain target concept");
        }

        EntityRole previousRole = previous == null ? EntityRole.UNKNOWN : previous.entityRole();
        boolean roleChanged = previousRole != legacyEquivalent(semantic, domain);
        String changeReason = roleChanged
                ? "audited role separates semantic, architectural, and relevance dimensions"
                : "audited role is consistent with baseline role";

        return new AuditedEntityClassification(
                entity.entityId(),
                qualifiedName,
                entity.entityType(),
                declaringType,
                packageName,
                typeSignature,
                semantic,
                architecture,
                domain,
                variability,
                confidence,
                evidence("name", nameTokens),
                evidence("declaring", declaringTokens),
                evidence("type", typeTokens),
                evidence("package", packageTokens),
                usageEvidence(entity, incoming, outgoing),
                evidence("dependency", dependencyTokens),
                String.join("; ", positive),
                String.join("; ", negative),
                previousRole,
                roleChanged,
                changeReason
        );
    }

    private List<AuditedBlockPair> buildPairs(RequiresEvidenceResult result,
                                              Map<String, AuditedEntityClassification> auditedById,
                                              Map<String, JavaEntity> entitiesById,
                                              Map<String, List<JavaEntity>> entitiesByBlock) {
        Map<String, ConditionalBlock> blockById = result.blocks().stream()
                .collect(Collectors.toMap(RequiresEvidenceBuilder::blockId, block -> block));
        Set<String> universe = result.blocks().stream()
                .flatMap(block -> block.signature().productIds().stream())
                .collect(Collectors.toCollection(TreeMapSet::new));

        List<DependencyEvidence> graphDependencies = result.dependencies().stream()
                .filter(dependency -> DependencyKindClassifier.isBlockGraphDependency(dependency.relationType()))
                .filter(dependency -> !dependency.targetBlockId().isBlank())
                .filter(dependency -> dependency.resolutionStatus() == ResolutionStatus.RESOLVED_INTERNAL)
                .filter(dependency -> {
                    ConditionalBlock source = blockById.get(dependency.sourceBlockId());
                    ConditionalBlock target = blockById.get(dependency.targetBlockId());
                    return source != null && target != null;
                })
                .toList();

        Map<String, List<DependencyEvidence>> byPair = graphDependencies.stream()
                .collect(Collectors.groupingBy(
                        dependency -> candidateKey(dependency, entitiesById),
                        TreeMap::new,
                        Collectors.toList()
                ));

        List<AuditedBlockPair> pairs = new ArrayList<>();
        for (List<DependencyEvidence> rows : byPair.values()) {
            rows = rows.stream().sorted(Comparator.comparingInt(DependencyEvidence::sourceLine)
                    .thenComparing(row -> row.relationType().name())
                    .thenComparing(DependencyEvidence::targetEntityId)).toList();
            DependencyEvidence first = rows.get(0);
            ConditionalBlock sourceBlock = blockById.get(first.sourceBlockId());
            ConditionalBlock targetBlock = blockById.get(first.targetBlockId());
            JavaEntity sourceEntity = entitiesById.get(first.sourceEntityId());
            JavaEntity targetEntity = entitiesById.get(first.targetEntityId());
            Set<String> sourceProducts = products(sourceBlock);
            Set<String> targetProducts = products(targetBlock);
            ProductSetRelation relation = productSetRelation(sourceProducts, targetProducts);
            String signatureRelation = signatureRelation(relation);
            boolean commonSource = sameSet(sourceProducts, universe);
            boolean commonTarget = sameSet(targetProducts, universe);
            boolean equivalentPresence = relation == ProductSetRelation.EQUAL;
            EvidenceCounts counts = evidenceCounts(rows);
            EvidenceLevel structural = structuralStrength(rows);
            EvidenceLevel domain = pairDomainRelevance(rows, auditedById);
            EvidenceLevel productSupport = productSetSupport(relation);
            EvidenceLevel direction = directionSupport(relation);
            EvidenceLevel sameFeature = sameFeaturePossibility(first, relation);
            EvidenceLevel sharedSupport = sharedSupportPossibility(rows, auditedById);
            EvidenceLevel commonContext = commonContextPossibility(rows, auditedById, entitiesById);
            EvidenceLevel resolution = resolutionConfidence(rows);
            Set<InterpretationCategory> categories = categories(first, structural, domain, relation,
                    sameFeature, sharedSupport, commonContext, rows);
            TargetLocalization localization = targetLocalization(first.targetBlockId(), rows, graphDependencies,
                    entitiesById, entitiesByBlock, blockById);
            categories = refineCommonOnlyCategories(categories, localization);
            boolean syntacticallyTrivial = equivalentPresence || syntacticallyContained(sourceBlock, targetBlock);
            RequiresCandidateStatus status = candidateStatus(first, sourceBlock, targetBlock, sourceEntity,
                    targetEntity, relation, commonTarget);
            RequiresCandidateCategory category = candidateCategory(status);
            RequiresRelevance requires = requiresRelevance(status);
            EvidenceLevel confidence = confidence(status, rows);
            String sourceName = displayName(sourceEntity);
            String targetName = displayName(targetEntity);
            String sourceType = sourceEntity == null ? "" : sourceEntity.entityType().name();
            String targetType = targetEntity == null ? "" : targetEntity.entityType().name();
            String sourceAssetId = assetId(sourceBlock);
            String targetAssetId = assetId(targetBlock);
            boolean sameAsset = !sourceAssetId.isBlank() && sourceAssetId.equals(targetAssetId);
            boolean sourceOutermost = isOutermost(sourceBlock);
            boolean targetOutermost = isOutermost(targetBlock);
            pairs.add(new AuditedBlockPair(
                    first.sourceBlockId(),
                    first.targetBlockId(),
                    sourceName,
                    sourceType,
                    targetName,
                    targetType,
                    first.relationType().name(),
                    sourceAssetId,
                    targetAssetId,
                    sameAsset,
                    sourceOutermost,
                    targetOutermost,
                    !sameAsset,
                    status == RequiresCandidateStatus.ELIGIBLE_REQUIRES_EVIDENCE,
                    status == RequiresCandidateStatus.MANDATORY_SOURCE_TO_VARIABLE_TARGET,
                    join(sourceProducts),
                    join(targetProducts),
                    signatureRelation,
                    relation,
                    rows.size(),
                    (int) rows.stream().map(DependencyEvidence::sourceLine).distinct().count(),
                    crossFile(sourceEntity, targetEntity),
                    crossClass(sourceEntity, targetEntity),
                    status,
                    confidence,
                    first.relationId(),
                    evidenceRows(rows, entitiesById, blockById, sourceProducts, targetProducts),
                    join(rows.stream().map(row -> row.relationType().name()).collect(Collectors.toSet())),
                    category,
                    counts.methodCallCount(),
                    counts.constructorCallCount(),
                    counts.fieldReferenceCount(),
                    counts.extendsCount(),
                    counts.implementsCount(),
                    counts.distinctSourceEntityCount(),
                    counts.distinctTargetEntityCount(),
                    counts.distinctSourceBlockCount(),
                    counts.distinctTargetBlockCount(),
                    counts.distinctFileCount(),
                    referencedEntities(rows, auditedById, entitiesById),
                    rows.size(),
                    structural,
                    domain,
                    productSupport,
                    direction,
                    sameFeature,
                    sharedSupport,
                    commonContext,
                    resolution,
                    join(categories.stream().map(Enum::name).collect(Collectors.toCollection(TreeMapSet::new))),
                    requires,
                    relevanceReasons(rows, structural, domain, direction, resolution),
                    cautionReasons(relation, sameFeature, commonContext, localization),
                    exclusionReasons(rows),
                    localization.targetPresenceScope(),
                    localization.targetEntityCount(),
                    localization.directlyReferencedTargetEntityCount(),
                    localization.targetScopeCoarseness(),
                    localization.targetScopeQuality(),
                    localization.referencedTargetEntities(),
                    localization.referencedTargetMethods(),
                    localization.referencedTargetFields(),
                    localization.targetDeclarationRanges(),
                    localization.primaryTargetEntity(),
                    localization.primaryTargetOperation(),
                    localization.primaryTargetDeclaration(),
                    localization.primaryTargetPresenceScope(),
                    localization.primaryTargetFeatureSpecificity(),
                    localization.primarySelectionReason(),
                    localization.primaryEvidenceRelationId(),
                    localization.primaryEvidenceSourceEntity(),
                    localization.primaryEvidenceSourceEntityType(),
                    localization.primaryEvidenceTargetEntityType(),
                    localization.primaryEvidenceDependencyKind(),
                    localization.primaryEvidenceSourceFile(),
                    localization.primaryEvidenceSourceLine(),
                    localization.alternativeTargetEntities(),
                    localization.commonTargetEntities(),
                    localization.featureSpecificTargetEntities(),
                    localization.evidenceSnippets()
            ));
        }
        return pairs.stream()
                .sorted(Comparator.comparing(AuditedBlockPair::sourceBlockId)
                        .thenComparing(AuditedBlockPair::targetBlockId)
                        .thenComparing(AuditedBlockPair::sourceEntity)
                        .thenComparing(AuditedBlockPair::targetEntity)
                        .thenComparing(AuditedBlockPair::dependencyKind))
                .toList();
    }

    private String candidateKey(DependencyEvidence dependency, Map<String, JavaEntity> entitiesById) {
        JavaEntity source = entitiesById.get(dependency.sourceEntityId());
        JavaEntity target = entitiesById.get(dependency.targetEntityId());
        return String.join("\u0000",
                dependency.sourceBlockId(),
                dependency.targetBlockId(),
                stableEntityKey(source, dependency.sourceEntityId()),
                stableEntityKey(target, dependency.targetEntityId()),
                dependency.relationType().name());
    }

    private String stableEntityKey(JavaEntity entity, String fallbackId) {
        if (entity == null) {
            return fallbackId == null ? "" : fallbackId;
        }
        return String.join("|",
                entity.sourceFile().toString(),
                displayName(entity),
                entity.entityType().name(),
                entity.containingBlockId());
    }

    private boolean syntacticallyContained(ConditionalBlock source, ConditionalBlock target) {
        Set<String> sourceProducts = products(source);
        Set<String> targetProducts = products(target);
        if (sourceProducts.isEmpty() || targetProducts.isEmpty()) {
            return false;
        }
        return targetProducts.size() == 1 && sourceProducts.containsAll(targetProducts);
    }

    private RequiresCandidateStatus candidateStatus(DependencyEvidence row, ConditionalBlock sourceBlock,
                                                    ConditionalBlock targetBlock, JavaEntity sourceEntity,
                                                    JavaEntity targetEntity, ProductSetRelation relation,
                                                    boolean commonTarget) {
        if (sourceEntity == null || targetEntity == null
                || row.sourceEntityId().isBlank() || row.targetEntityId().isBlank()
                || row.resolutionStatus() != ResolutionStatus.RESOLVED_INTERNAL) {
            return RequiresCandidateStatus.UNRESOLVED_ENTITY;
        }
        if (sourceBlock == null || targetBlock == null
                || sourceBlock.nestingDepth() < 0 || targetBlock.nestingDepth() < 0) {
            return RequiresCandidateStatus.UNRESOLVED_BLOCK_SCOPE;
        }
        if (!validEntityPair(row.relationType(), sourceEntity, targetEntity)) {
            return RequiresCandidateStatus.INVALID_DEPENDENCY_KIND;
        }
        if (sourceEntity.entityId().equals(targetEntity.entityId())) {
            return RequiresCandidateStatus.SELF_DEPENDENCY;
        }
        if (commonTarget) {
            return RequiresCandidateStatus.COMMON_TARGET_DEPENDENCY;
        }
        boolean sameAsset = sameAsset(sourceBlock, targetBlock);
        boolean sourceOutermost = isOutermost(sourceBlock);
        boolean targetOutermost = isOutermost(targetBlock);
        if (sameAsset && sourceOutermost && targetOutermost) {
            return RequiresCandidateStatus.INTRA_ASSET_ROOT_DEPENDENCY;
        }
        if (sameAsset && !sourceOutermost && targetOutermost) {
            return RequiresCandidateStatus.INTRA_ASSET_MANDATORY_TARGET;
        }
        if (sameAsset && sourceOutermost && !targetOutermost) {
            return RequiresCandidateStatus.MANDATORY_SOURCE_TO_VARIABLE_TARGET;
        }
        if (row.sourceBlockId().equals(row.targetBlockId())) {
            return RequiresCandidateStatus.INTRA_BLOCK;
        }
        if (sameDeclaringClass(sourceEntity, targetEntity)) {
            return RequiresCandidateStatus.INTRA_CLASS;
        }
        if (relation == ProductSetRelation.EQUAL) {
            return RequiresCandidateStatus.SAME_SIGNATURE_DIRECTED_DEPENDENCY;
        }
        if (relation == ProductSetRelation.SOURCE_SUBSET_OF_TARGET) {
            return RequiresCandidateStatus.ELIGIBLE_REQUIRES_EVIDENCE;
        }
        return RequiresCandidateStatus.SIGNATURE_INCONSISTENT;
    }

    private boolean sameAsset(ConditionalBlock sourceBlock, ConditionalBlock targetBlock) {
        String sourceAsset = assetId(sourceBlock);
        String targetAsset = assetId(targetBlock);
        return !sourceAsset.isBlank() && sourceAsset.equals(targetAsset);
    }

    private boolean isOutermost(ConditionalBlock block) {
        return block != null && block.nestingDepth() == 0;
    }

    private String assetId(ConditionalBlock block) {
        if (block == null || block.filePath() == null || block.filePath().getFileName() == null) {
            return "";
        }
        return block.filePath().getFileName().toString();
    }

    private boolean validEntityPair(JavaRelationType type, JavaEntity source, JavaEntity target) {
        return switch (type) {
            case EXTENDS -> source.entityType() == JavaEntityType.CLASS
                    && target.entityType() == JavaEntityType.CLASS;
            case IMPLEMENTS -> source.entityType() == JavaEntityType.CLASS
                    && target.entityType() == JavaEntityType.INTERFACE;
            case METHOD_CALL -> callableSource(source) && target.entityType() == JavaEntityType.METHOD;
            case CONSTRUCTOR_CALL -> callableSource(source) && target.entityType() == JavaEntityType.CONSTRUCTOR;
            case FIELD_REFERENCE -> callableSource(source) && target.entityType() == JavaEntityType.FIELD;
            case TYPE_REFERENCE -> target.entityType() == JavaEntityType.CLASS
                    || target.entityType() == JavaEntityType.INTERFACE;
        };
    }

    private boolean callableSource(JavaEntity entity) {
        return entity.entityType() == JavaEntityType.METHOD
                || entity.entityType() == JavaEntityType.CONSTRUCTOR;
    }

    private boolean sameDeclaringClass(JavaEntity source, JavaEntity target) {
        String sourceClass = declaringType(source);
        String targetClass = declaringType(target);
        return !sourceClass.isBlank() && sourceClass.equals(targetClass);
    }

    private RequiresCandidateCategory candidateCategory(RequiresCandidateStatus status) {
        return status == RequiresCandidateStatus.ELIGIBLE_REQUIRES_EVIDENCE
                ? RequiresCandidateCategory.MEDIUM_CONFIDENCE_REQUIRES
                : RequiresCandidateCategory.REJECTED_TRIVIAL_OR_INVALID;
    }

    private RequiresRelevance requiresRelevance(RequiresCandidateStatus status) {
        return status == RequiresCandidateStatus.ELIGIBLE_REQUIRES_EVIDENCE
                ? RequiresRelevance.MEDIUM
                : RequiresRelevance.LOW;
    }

    private EvidenceLevel confidence(RequiresCandidateStatus status, List<DependencyEvidence> rows) {
        if (status != RequiresCandidateStatus.ELIGIBLE_REQUIRES_EVIDENCE) {
            return EvidenceLevel.LOW;
        }
        if (rows.size() > 1) {
            return EvidenceLevel.HIGH;
        }
        return EvidenceLevel.MEDIUM;
    }

    private String signatureRelation(ProductSetRelation relation) {
        return switch (relation) {
            case EQUAL -> "EQUAL";
            case SOURCE_SUBSET_OF_TARGET -> "SOURCE_PROPER_SUBSET_OF_TARGET";
            case TARGET_SUBSET_OF_SOURCE -> "TARGET_PROPER_SUBSET_OF_SOURCE";
            case OVERLAP_ONLY, DISJOINT, UNKNOWN -> "INCOMPARABLE";
        };
    }

    private boolean crossFile(JavaEntity source, JavaEntity target) {
        return source != null && target != null
                && !source.sourceFile().toAbsolutePath().normalize()
                .equals(target.sourceFile().toAbsolutePath().normalize());
    }

    private boolean crossClass(JavaEntity source, JavaEntity target) {
        if (source == null || target == null) {
            return false;
        }
        String sourceClass = declaringType(source);
        String targetClass = declaringType(target);
        return !sourceClass.isBlank() && !targetClass.isBlank() && !sourceClass.equals(targetClass);
    }

    private List<RequiresCandidateEvidenceRow> evidenceRows(List<DependencyEvidence> rows,
                                                            Map<String, JavaEntity> entitiesById,
                                                            Map<String, ConditionalBlock> blockById,
                                                            Set<String> sourceProducts,
                                                            Set<String> targetProducts) {
        return rows.stream()
                .map(row -> {
                    JavaEntity source = entitiesById.get(row.sourceEntityId());
                    JavaEntity target = entitiesById.get(row.targetEntityId());
                    ConditionalBlock sourceBlock = blockById.get(row.sourceBlockId());
                    ConditionalBlock targetBlock = blockById.get(row.targetBlockId());
                    return new RequiresCandidateEvidenceRow(
                            row.relationId(),
                            row.sourceFile().toString(),
                            row.sourceLine(),
                            displayName(source),
                            source == null ? "" : source.entityType().name(),
                            row.sourceBlockId(),
                            assetId(sourceBlock),
                            isOutermost(sourceBlock),
                            target == null ? "" : target.sourceFile().toString(),
                            target == null ? -1 : target.startLine(),
                            displayName(target),
                            target == null ? "" : target.entityType().name(),
                            row.targetBlockId(),
                            assetId(targetBlock),
                            isOutermost(targetBlock),
                            row.relationType().name(),
                            lineSnippet(row.sourceFile(), row.sourceLine()),
                            join(sourceProducts),
                            join(targetProducts)
                    );
                })
                .toList();
    }

    private RequiresCandidateCategory candidateCategory(EvidenceCounts counts, boolean commonSource,
                                                        boolean commonTarget, boolean syntacticallyTrivial) {
        if (commonSource && !commonTarget && !syntacticallyTrivial) {
            return RequiresCandidateCategory.COMMON_TO_VARIABLE_ANOMALY;
        }
        if (commonTarget || syntacticallyTrivial) {
            return RequiresCandidateCategory.REJECTED_TRIVIAL_OR_INVALID;
        }
        if (commonSource) {
            return RequiresCandidateCategory.COMMON_TO_VARIABLE_ANOMALY;
        }
        boolean implementation = counts.methodCallCount() > 0 || counts.constructorCallCount() > 0;
        boolean structural = counts.extendsCount() > 0 || counts.implementsCount() > 0;
        boolean field = counts.fieldReferenceCount() > 0;
        int families = (implementation ? 1 : 0) + (structural ? 1 : 0) + (field ? 1 : 0);
        if (structural || counts.constructorCallCount() > 0 || families > 1) {
            return RequiresCandidateCategory.HIGH_CONFIDENCE_REQUIRES;
        }
        if (counts.methodCallCount() > 0
                || (field && counts.distinctSourceEntityCount() >= candidateConfig.repeatedFieldReferenceSourceEntities())) {
            return RequiresCandidateCategory.MEDIUM_CONFIDENCE_REQUIRES;
        }
        return RequiresCandidateCategory.LOW_CONFIDENCE_REQUIRES;
    }

    private EvidenceCounts evidenceCounts(List<DependencyEvidence> rows) {
        return new EvidenceCounts(
                (int) rows.stream().filter(row -> row.relationType() == JavaRelationType.METHOD_CALL).count(),
                (int) rows.stream().filter(row -> row.relationType() == JavaRelationType.CONSTRUCTOR_CALL).count(),
                (int) rows.stream().filter(row -> row.relationType() == JavaRelationType.FIELD_REFERENCE).count(),
                (int) rows.stream().filter(row -> row.relationType() == JavaRelationType.EXTENDS).count(),
                (int) rows.stream().filter(row -> row.relationType() == JavaRelationType.IMPLEMENTS).count(),
                (int) rows.stream().map(DependencyEvidence::sourceEntityId).filter(id -> !id.isBlank()).distinct().count(),
                (int) rows.stream().map(DependencyEvidence::targetEntityId).filter(id -> !id.isBlank()).distinct().count(),
                (int) rows.stream().map(DependencyEvidence::sourceBlockId).filter(id -> !id.isBlank()).distinct().count(),
                (int) rows.stream().map(DependencyEvidence::targetBlockId).filter(id -> !id.isBlank()).distinct().count(),
                (int) rows.stream().map(row -> row.sourceFile().toString()).distinct().count()
        );
    }

    private TargetLocalization targetLocalization(String targetBlockId, List<DependencyEvidence> rows,
                                                  List<DependencyEvidence> allVariableTargetDependencies,
                                                  Map<String, JavaEntity> entitiesById,
                                                  Map<String, List<JavaEntity>> entitiesByBlock,
                                                  Map<String, ConditionalBlock> blockById) {
        List<JavaEntity> targetBlockEntities = entitiesByBlock.getOrDefault(targetBlockId, List.of());
        List<JavaEntity> referenced = rows.stream()
                .map(row -> entitiesById.get(row.targetEntityId()))
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparingInt(JavaEntity::startLine)
                        .thenComparing(entity -> entity.entityType().name())
                        .thenComparing(this::displayName))
                .toList();
        List<DependencyEvidence> contextualRows = allVariableTargetDependencies.stream()
                .filter(row -> row.sourceBlockId().equals(rows.get(0).sourceBlockId()))
                .filter(row -> !row.targetBlockId().equals(targetBlockId))
                .filter(row -> isInside(blockById.get(row.targetBlockId()), blockById.get(targetBlockId)))
                .toList();
        List<TargetCandidate> candidates = targetCandidates(targetBlockId, rows, contextualRows, entitiesById, blockById);
        int directCount = referenced.size();
        double coarseness = directCount == 0 ? 0.0 : (double) targetBlockEntities.size() / directCount;
        String quality = coarseness > 3.0 ? "COARSE_TARGET_SCOPE" : "LOCALIZED_TARGET_SCOPE";
        TargetCandidate primary = primaryTarget(candidates);
        JavaEntity primaryEntity = primary == null ? null : primary.entity();
        DependencyEvidence primaryEvidence = primary == null ? null : primary.evidence();
        JavaEntity primarySource = primaryEvidence == null ? null : entitiesById.get(primaryEvidence.sourceEntityId());
        return new TargetLocalization(
                targetBlockId,
                targetBlockEntities.size(),
                directCount,
                coarseness,
                quality,
                join(referenced.stream().map(this::displayName).collect(Collectors.toSet())),
                join(referenced.stream()
                        .filter(entity -> entity.entityType() == JavaEntityType.METHOD
                                || entity.entityType() == JavaEntityType.CONSTRUCTOR)
                        .map(this::displayName)
                        .collect(Collectors.toSet())),
                join(referenced.stream()
                        .filter(entity -> entity.entityType() == JavaEntityType.FIELD)
                        .map(this::displayName)
                        .collect(Collectors.toSet())),
                join(referenced.stream().map(this::declarationRange).collect(Collectors.toSet())),
                primaryEntity == null ? "" : displayName(primaryEntity),
                primaryOperation(primaryEntity),
                primaryEntity == null ? "" : declarationRange(primaryEntity),
                primary == null ? "" : primary.presenceScope(),
                primary == null ? "" : primary.featureSpecificity(),
                primary == null ? "" : primary.selectionReason(),
                primaryEvidence == null ? "" : primaryEvidence.relationId(),
                primarySource == null ? "" : displayName(primarySource),
                primarySource == null ? "" : primarySource.entityType().name(),
                primaryEntity == null ? "" : primaryEntity.entityType().name(),
                primaryEvidence == null ? "" : primaryEvidence.relationType().name(),
                primaryEvidence == null ? "" : primaryEvidence.sourceFile().toString(),
                primaryEvidence == null ? -1 : primaryEvidence.sourceLine(),
                join(candidates.stream()
                        .filter(candidate -> primary == null || !candidate.entity().entityId().equals(primary.entity().entityId()))
                        .map(candidate -> displayName(candidate.entity()))
                        .collect(Collectors.toSet())),
                join(candidates.stream()
                        .filter(candidate -> "COMMON".equals(candidate.featureSpecificity()))
                        .map(candidate -> displayName(candidate.entity()))
                        .collect(Collectors.toSet())),
                join(candidates.stream()
                        .filter(candidate -> "FEATURE_SPECIFIC".equals(candidate.featureSpecificity()))
                        .map(candidate -> displayName(candidate.entity()))
                        .collect(Collectors.toSet())),
                evidenceSnippets(rows, referenced, entitiesById)
        );
    }

    private record EvidenceCounts(
            int methodCallCount,
            int constructorCallCount,
            int fieldReferenceCount,
            int extendsCount,
            int implementsCount,
            int distinctSourceEntityCount,
            int distinctTargetEntityCount,
            int distinctSourceBlockCount,
            int distinctTargetBlockCount,
            int distinctFileCount
    ) {
    }

    private List<TargetCandidate> targetCandidates(String targetBlockId, List<DependencyEvidence> rows,
                                                   List<DependencyEvidence> contextualRows,
                                                   Map<String, JavaEntity> entitiesById,
                                                   Map<String, ConditionalBlock> blockById) {
        Map<String, TargetCandidate> candidates = new LinkedHashMap<>();
        for (DependencyEvidence row : rows) {
            JavaEntity entity = entitiesById.get(row.targetEntityId());
            if (entity != null) {
                candidates.put(entity.entityId(), candidate(entity, row, targetBlockId, blockById, true));
            }
        }
        for (DependencyEvidence row : contextualRows) {
            JavaEntity entity = entitiesById.get(row.targetEntityId());
            if (entity != null) {
                candidates.putIfAbsent(entity.entityId(), candidate(entity, row, targetBlockId, blockById, false));
            }
        }
        return List.copyOf(candidates.values());
    }

    private TargetCandidate candidate(JavaEntity entity, DependencyEvidence row, String targetPresenceScope,
                                      Map<String, ConditionalBlock> blockById, boolean direct) {
        String specificity = featureSpecificity(entity, targetPresenceScope, blockById);
        boolean declaredInsideTargetFragment = entity.containingBlockId().equals(row.targetBlockId());
        boolean compile = row.relationType() == JavaRelationType.CONSTRUCTOR_CALL
                || row.relationType() == JavaRelationType.TYPE_REFERENCE
                || row.relationType() == JavaRelationType.EXTENDS
                || row.relationType() == JavaRelationType.IMPLEMENTS
                || row.relationType() == JavaRelationType.FIELD_REFERENCE;
        boolean runtime = row.relationType() == JavaRelationType.METHOD_CALL
                || row.relationType() == JavaRelationType.CONSTRUCTOR_CALL
                || row.relationType() == JavaRelationType.FIELD_REFERENCE;
        String reason = String.join("|", List.of(
                "FEATURE_SPECIFIC".equals(specificity) ? "TARGET_FEATURE_SPECIFIC" : "TARGET_" + specificity,
                declaredInsideTargetFragment ? "DECLARED_IN_TARGET_FRAGMENT" : "DECLARED_IN_ENCLOSING_FRAGMENT",
                direct ? "DIRECTLY_REFERENCED_BY_SOURCE_BLOCK" : "REQUIRED_BY_DIRECTEDCALL_CODE",
                compile ? "COMPILE_TIME_NECESSITY" : "RUNTIME_OR_STRUCTURAL_USE",
                runtime ? "RUNTIME_NECESSITY" : "NO_RUNTIME_NECESSITY",
                row.resolutionStatus() == ResolutionStatus.RESOLVED_INTERNAL ? "RESOLVED_INTERNAL" : row.resolutionStatus().name()
        ));
        return new TargetCandidate(entity, row, row.targetBlockId(), specificity, declaredInsideTargetFragment,
                compile, runtime, row.relationType(), direct, reason);
    }

    private String featureSpecificity(JavaEntity entity, String targetPresenceScope,
                                      Map<String, ConditionalBlock> blockById) {
        ConditionalBlock entityBlock = blockById.get(entity.containingBlockId());
        ConditionalBlock presenceBlock = blockById.get(targetPresenceScope);
        if (entityBlock == null || presenceBlock == null) {
            return "UNKNOWN";
        }
        if (!entity.containingBlockId().equals(targetPresenceScope) && isInside(entityBlock, presenceBlock)) {
            return "FEATURE_SPECIFIC";
        }
        if (sameSet(products(entityBlock), products(presenceBlock))) {
            return "COMMON";
        }
        return "FEATURE_SHARED";
    }

    private TargetCandidate primaryTarget(List<TargetCandidate> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparingInt(this::primaryScore).reversed()
                        .thenComparing(candidate -> candidate.entity().sourceFile().toString())
                        .thenComparingInt(candidate -> candidate.entity().startLine())
                        .thenComparing(candidate -> displayName(candidate.entity())))
                .findFirst()
                .orElse(null);
    }

    private int primaryScore(TargetCandidate candidate) {
        int score = 0;
        score += switch (candidate.featureSpecificity()) {
            case "FEATURE_SPECIFIC" -> 10_000;
            case "FEATURE_SHARED" -> 5_000;
            case "COMMON" -> 1_000;
            default -> 0;
        };
        if (candidate.declaredInsideTargetFragment()) {
            score += 1_000;
        }
        if (candidate.compileNecessity()) {
            score += 500;
        }
        if (candidate.runtimeNecessity()) {
            score += 250;
        }
        if (candidate.direct()) {
            score += 100;
        }
        score += relationRank(candidate.relationType());
        return score;
    }

    private int relationRank(JavaRelationType type) {
        return switch (type) {
            case CONSTRUCTOR_CALL -> 60;
            case FIELD_REFERENCE -> 50;
            case METHOD_CALL -> 40;
            case TYPE_REFERENCE -> 30;
            case EXTENDS, IMPLEMENTS -> 20;
        };
    }

    private boolean isInside(ConditionalBlock inner, ConditionalBlock outer) {
        if (inner == null || outer == null) {
            return false;
        }
        return inner.filePath().toAbsolutePath().normalize().equals(outer.filePath().toAbsolutePath().normalize())
                && outer.startLine() <= inner.startLine()
                && inner.endLine() <= outer.endLine();
    }

    private String primaryOperation(JavaEntity primary) {
        if (primary == null) {
            return "";
        }
        return primary.entityType() == JavaEntityType.METHOD || primary.entityType() == JavaEntityType.CONSTRUCTOR
                ? displayName(primary)
                : "";
    }

    private String declarationRange(JavaEntity entity) {
        return displayName(entity) + "@" + entity.sourceFile() + ":" + entity.startLine() + "-" + entity.endLine();
    }

    private String evidenceSnippets(List<DependencyEvidence> rows, List<JavaEntity> referenced,
                                    Map<String, JavaEntity> entitiesById) {
        List<String> snippets = new ArrayList<>();
        rows.stream()
                .limit(5)
                .forEach(row -> {
                    String sourceSnippet = lineSnippet(row.sourceFile(), row.sourceLine());
                    JavaEntity target = entitiesById.get(row.targetEntityId());
                    String targetSnippet = target == null ? "" : lineSnippet(target.sourceFile(), target.startLine());
                    snippets.add(row.relationType()
                            + " source " + row.sourceFile() + ":" + row.sourceLine() + " `" + sourceSnippet + "`"
                            + (target == null ? "" : " target " + target.sourceFile() + ":" + target.startLine()
                            + " `" + targetSnippet + "`"));
                });
        if (referenced.size() > 5) {
            snippets.add("additional referenced targets=" + (referenced.size() - 5));
        }
        return String.join(" || ", snippets);
    }

    private String lineSnippet(java.nio.file.Path file, int line) {
        if (line < 1) {
            return "";
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (line > lines.size()) {
                return "";
            }
            return lines.get(line - 1).trim();
        } catch (IOException exception) {
            return "";
        }
    }

    private SemanticRole semanticRole(JavaEntity entity, Set<String> nameTokens, Set<String> declaringTokens,
                                      Set<String> typeTokens, Set<String> dependencyTokens,
                                      ArchitecturalRole architecture, EntityRole previousRole) {
        Set<String> all = union(nameTokens, declaringTokens, typeTokens, dependencyTokens);
        if (containsAny(all, Set.of("feature", "variant", "product", "configuration", "config", "factory"))) {
            return SemanticRole.VARIABILITY_CONTROL;
        }
        if (containsAny(nameTokens, Set.of("util", "utils", "helper"))) {
            return SemanticRole.GENERIC_UTILITY;
        }
        if (entity.entityType() == JavaEntityType.CLASS || entity.entityType() == JavaEntityType.INTERFACE) {
            if (containsAny(nameTokens, Set.of("request", "elevator", "floor", "door", "sensor", "state"))) {
                return architecture == ArchitecturalRole.UI ? SemanticRole.DOMAIN_SUPPORT : SemanticRole.DOMAIN_MODEL;
            }
            if (architecture == ArchitecturalRole.UI && hasDomainConcept(all)) {
                return SemanticRole.DOMAIN_SUPPORT;
            }
        }
        if (entity.entityType() == JavaEntityType.FIELD) {
            if ("sim".equalsIgnoreCase(entity.simpleName()) || containsAny(typeTokens, Set.of("simulation", "unit"))) {
                return SemanticRole.DOMAIN_FACADE;
            }
            if (architecture == ArchitecturalRole.UI && containsAny(all, Set.of("control", "controls", "button", "floor", "elevator"))) {
                return SemanticRole.DOMAIN_STATE;
            }
            if (hasDomainConcept(all)) {
                return SemanticRole.DOMAIN_STATE;
            }
        }
        if (entity.entityType() == JavaEntityType.METHOD || entity.entityType() == JavaEntityType.CONSTRUCTOR) {
            if (hasDomainConcept(all)) {
                return SemanticRole.DOMAIN_OPERATION;
            }
            if (architecture == ArchitecturalRole.UI && containsAny(declaringTokens, Set.of("floor", "elevator", "request"))) {
                return SemanticRole.DOMAIN_OPERATION;
            }
            if (previousRole == EntityRole.DOMAIN_CORE || previousRole == EntityRole.DOMAIN_SUPPORT) {
                return SemanticRole.DOMAIN_OPERATION;
            }
        }
        if (previousRole == EntityRole.INFRASTRUCTURE) {
            return SemanticRole.UNKNOWN;
        }
        return SemanticRole.UNKNOWN;
    }

    private ArchitecturalRole architecturalRole(Set<String> packageTokens, Set<String> declaringTokens,
                                                Set<String> nameTokens) {
        Set<String> all = union(packageTokens, declaringTokens, nameTokens);
        if (containsAny(all, Set.of("util", "utils"))) {
            return ArchitecturalRole.UTILITY;
        }
        if (containsAny(all, Set.of("database", "repository", "network", "logger"))) {
            return ArchitecturalRole.INFRASTRUCTURE;
        }
        if (nameTokens.contains("sim")) {
            return ArchitecturalRole.ORCHESTRATION;
        }
        if (packageTokens.contains("ui") || containsAny(all, Set.of("dialog", "window", "button", "panel", "composite"))) {
            return ArchitecturalRole.UI;
        }
        if (packageTokens.contains("sim") || containsAny(all, Set.of("simulation", "main", "application"))) {
            return ArchitecturalRole.ORCHESTRATION;
        }
        if (packageTokens.contains("controller")) {
            return ArchitecturalRole.CONTROLLER;
        }
        if (packageTokens.contains("model")) {
            return ArchitecturalRole.MODEL;
        }
        return ArchitecturalRole.UNKNOWN;
    }

    private EvidenceLevel domainRelevance(SemanticRole semantic, ArchitecturalRole architecture,
                                          Set<String> nameTokens, Set<String> declaringTokens,
                                          Set<String> typeTokens, Set<String> dependencyTokens) {
        Set<String> all = union(nameTokens, declaringTokens, typeTokens, dependencyTokens);
        return switch (semantic) {
            case DOMAIN_MODEL -> EvidenceLevel.HIGH;
            case DOMAIN_OPERATION -> architecture == ArchitecturalRole.UI
                    && !hasDomainConcept(nameTokens)
                    && hasDomainConcept(declaringTokens)
                    ? EvidenceLevel.MEDIUM
                    : EvidenceLevel.HIGH;
            case DOMAIN_STATE, DOMAIN_SUPPORT -> EvidenceLevel.MEDIUM;
            case DOMAIN_FACADE -> containsAny(all, Set.of("floor", "request", "service", "disabled"))
                    ? EvidenceLevel.MEDIUM
                    : EvidenceLevel.UNKNOWN;
            case VARIABILITY_CONTROL -> EvidenceLevel.LOW;
            case GENERIC_UTILITY -> EvidenceLevel.LOW;
            case UNKNOWN -> architecture == ArchitecturalRole.UI && hasDomainConcept(all)
                    ? EvidenceLevel.MEDIUM
                    : EvidenceLevel.UNKNOWN;
        };
    }

    private EvidenceLevel variabilityRelevance(Set<String> nameTokens, Set<String> declaringTokens,
                                               Set<String> packageTokens) {
        return containsAny(union(nameTokens, declaringTokens, packageTokens),
                Set.of("feature", "variant", "product", "configuration", "config", "factory"))
                ? EvidenceLevel.HIGH
                : EvidenceLevel.LOW;
    }

    private ClassificationConfidence confidence(SemanticRole semantic, EvidenceLevel domain,
                                                Set<String> declaringTokens, Set<String> typeTokens,
                                                Set<String> dependencyTokens) {
        int sources = 0;
        if (!declaringTokens.isEmpty()) {
            sources++;
        }
        if (!typeTokens.isEmpty()) {
            sources++;
        }
        if (!dependencyTokens.isEmpty()) {
            sources++;
        }
        if (semantic == SemanticRole.UNKNOWN || domain == EvidenceLevel.UNKNOWN) {
            return sources >= 2 ? ClassificationConfidence.MEDIUM : ClassificationConfidence.LOW;
        }
        return sources >= 2 ? ClassificationConfidence.HIGH : ClassificationConfidence.MEDIUM;
    }

    private EvidenceLevel structuralStrength(List<DependencyEvidence> rows) {
        boolean strongKind = rows.stream().anyMatch(row -> row.relationType() == JavaRelationType.METHOD_CALL
                || row.relationType() == JavaRelationType.CONSTRUCTOR_CALL);
        boolean structuralKind = rows.stream()
                .anyMatch(row -> DependencyKindClassifier.isStructuralDependency(row.relationType()));
        if (strongKind && rows.size() >= 2) {
            return EvidenceLevel.HIGH;
        }
        if (strongKind || structuralKind || rows.size() >= 2) {
            return EvidenceLevel.MEDIUM;
        }
        return EvidenceLevel.LOW;
    }

    private EvidenceLevel pairDomainRelevance(List<DependencyEvidence> rows,
                                              Map<String, AuditedEntityClassification> auditedById) {
        boolean high = false;
        boolean medium = false;
        for (DependencyEvidence row : rows) {
            AuditedEntityClassification source = auditedById.get(row.sourceEntityId());
            AuditedEntityClassification target = auditedById.get(row.targetEntityId());
            if (row.exclusionReason() == DependencyExclusionReason.INTRA_BLOCK) {
                continue;
            }
            EvidenceLevel targetLevel = target == null ? EvidenceLevel.UNKNOWN : target.domainRelevance();
            EvidenceLevel sourceLevel = source == null ? EvidenceLevel.UNKNOWN : source.domainRelevance();
            if ((targetLevel == EvidenceLevel.HIGH && ordinaryMeaningful(row.relationType()))
                    || (targetLevel == EvidenceLevel.MEDIUM && row.relationType() == JavaRelationType.METHOD_CALL
                    && sourceLevel == EvidenceLevel.HIGH)) {
                high = true;
            } else if (targetLevel == EvidenceLevel.MEDIUM || sourceLevel == EvidenceLevel.HIGH) {
                medium = true;
            }
        }
        return high ? EvidenceLevel.HIGH : medium ? EvidenceLevel.MEDIUM : EvidenceLevel.LOW;
    }

    private EvidenceLevel productSetSupport(ProductSetRelation relation) {
        return switch (relation) {
            case SOURCE_SUBSET_OF_TARGET, EQUAL -> EvidenceLevel.HIGH;
            case TARGET_SUBSET_OF_SOURCE, OVERLAP_ONLY -> EvidenceLevel.MEDIUM;
            case DISJOINT -> EvidenceLevel.LOW;
            case UNKNOWN -> EvidenceLevel.UNKNOWN;
        };
    }

    private EvidenceLevel directionSupport(ProductSetRelation relation) {
        return switch (relation) {
            case SOURCE_SUBSET_OF_TARGET -> EvidenceLevel.HIGH;
            case EQUAL -> EvidenceLevel.MEDIUM;
            case TARGET_SUBSET_OF_SOURCE, OVERLAP_ONLY -> EvidenceLevel.LOW;
            case DISJOINT -> EvidenceLevel.LOW;
            case UNKNOWN -> EvidenceLevel.UNKNOWN;
        };
    }

    private EvidenceLevel sameFeaturePossibility(DependencyEvidence first, ProductSetRelation relation) {
        if (relation == ProductSetRelation.EQUAL || first.sourceGroupId().equals(first.targetGroupId())) {
            return EvidenceLevel.HIGH;
        }
        return relation == ProductSetRelation.SOURCE_SUBSET_OF_TARGET ? EvidenceLevel.MEDIUM : EvidenceLevel.LOW;
    }

    private EvidenceLevel sharedSupportPossibility(List<DependencyEvidence> rows,
                                                   Map<String, AuditedEntityClassification> auditedById) {
        boolean support = rows.stream()
                .map(row -> auditedById.get(row.targetEntityId()))
                .filter(Objects::nonNull)
                .anyMatch(target -> target.semanticRole() == SemanticRole.DOMAIN_SUPPORT
                        || target.semanticRole() == SemanticRole.DOMAIN_STATE
                        || target.semanticRole() == SemanticRole.DOMAIN_FACADE);
        return support ? EvidenceLevel.HIGH : EvidenceLevel.LOW;
    }

    private EvidenceLevel commonContextPossibility(List<DependencyEvidence> rows,
                                                   Map<String, AuditedEntityClassification> auditedById,
                                                   Map<String, JavaEntity> entitiesById) {
        boolean context = rows.stream().anyMatch(row -> {
            AuditedEntityClassification target = auditedById.get(row.targetEntityId());
            JavaEntity targetEntity = entitiesById.get(row.targetEntityId());
            String name = targetEntity == null ? "" : targetEntity.simpleName().toLowerCase(Locale.ROOT);
            return target != null && (target.architecturalRole() == ArchitecturalRole.UI
                    || target.architecturalRole() == ArchitecturalRole.ORCHESTRATION
                    || name.equals("sim")
                    || name.contains("controls"));
        });
        return context ? EvidenceLevel.HIGH : EvidenceLevel.LOW;
    }

    private EvidenceLevel resolutionConfidence(List<DependencyEvidence> rows) {
        long resolved = rows.stream()
                .filter(row -> row.resolutionStatus() == ResolutionStatus.RESOLVED_INTERNAL)
                .count();
        if (resolved == rows.size()) {
            return EvidenceLevel.HIGH;
        }
        if (resolved > 0) {
            return EvidenceLevel.MEDIUM;
        }
        return EvidenceLevel.LOW;
    }

    private Set<InterpretationCategory> categories(DependencyEvidence first, EvidenceLevel structural,
                                                   EvidenceLevel domain, ProductSetRelation relation,
                                                   EvidenceLevel sameFeature, EvidenceLevel sharedSupport,
                                                   EvidenceLevel commonContext, List<DependencyEvidence> rows) {
        Set<InterpretationCategory> categories = new HashSet<>();
        if (rows.stream().allMatch(row -> row.sourceBlockId().equals(row.targetBlockId()))) {
            categories.add(InterpretationCategory.INTRA_BLOCK);
        }
        if (rows.stream().anyMatch(row -> row.relationType() == JavaRelationType.EXTENDS)) {
            categories.add(InterpretationCategory.STRUCTURAL_EXTENDS);
        }
        if (rows.stream().anyMatch(row -> row.relationType() == JavaRelationType.IMPLEMENTS)) {
            categories.add(InterpretationCategory.STRUCTURAL_IMPLEMENTS);
        }
        if (domain == EvidenceLevel.LOW || domain == EvidenceLevel.UNKNOWN) {
            categories.add(InterpretationCategory.INSUFFICIENT_EVIDENCE);
        }
        if (hasOrdinaryImplementationDependency(rows)
                && domain != EvidenceLevel.LOW && structural != EvidenceLevel.LOW
                && relation == ProductSetRelation.SOURCE_SUBSET_OF_TARGET) {
            categories.add(InterpretationCategory.REQUIRES_PLAUSIBLE);
        }
        if (sameFeature == EvidenceLevel.HIGH) {
            categories.add(InterpretationCategory.SAME_FEATURE_PLAUSIBLE);
        }
        if (relation == ProductSetRelation.EQUAL) {
            categories.add(InterpretationCategory.CO_OCCURRING_FEATURES_PLAUSIBLE);
        }
        if (sharedSupport == EvidenceLevel.HIGH) {
            categories.add(InterpretationCategory.SHARED_SUPPORT_PLAUSIBLE);
        }
        if (commonContext == EvidenceLevel.HIGH) {
            categories.add(InterpretationCategory.COMMON_CONTEXT);
        }
        if (first.sourceGroupId().equals(first.targetGroupId())) {
            categories.add(InterpretationCategory.SAME_FEATURE_PLAUSIBLE);
        }
        if (categories.isEmpty()) {
            categories.add(InterpretationCategory.INSUFFICIENT_EVIDENCE);
        }
        return categories;
    }

    private RequiresRelevance requiresRelevance(RequiresCandidateCategory category) {
        return switch (category) {
            case HIGH_CONFIDENCE_REQUIRES -> RequiresRelevance.HIGH;
            case MEDIUM_CONFIDENCE_REQUIRES -> RequiresRelevance.MEDIUM;
            case LOW_CONFIDENCE_REQUIRES, COMMON_TO_VARIABLE_ANOMALY, REJECTED_TRIVIAL_OR_INVALID -> RequiresRelevance.LOW;
        };
    }

    private RequiresRelevance requiresRelevance(EvidenceLevel structural, EvidenceLevel domain,
                                                EvidenceLevel direction, EvidenceLevel commonContext,
                                                Set<InterpretationCategory> categories,
                                                ProductSetRelation productSetRelation,
                                                TargetLocalization localization) {
        if (categories.contains(InterpretationCategory.INTRA_BLOCK)
                || categories.contains(InterpretationCategory.INFRASTRUCTURE_ONLY)) {
            return RequiresRelevance.LOW;
        }
        if (localization.featureSpecificTargetEntities().isBlank()
                || "COMMON".equals(localization.primaryTargetFeatureSpecificity())) {
            return RequiresRelevance.LOW;
        }
        if (isStructuralEvidence(categories) && !categories.contains(InterpretationCategory.REQUIRES_PLAUSIBLE)) {
            return structural == EvidenceLevel.MEDIUM
                    && direction == EvidenceLevel.HIGH
                    && productSetRelation == ProductSetRelation.SOURCE_SUBSET_OF_TARGET
                    && !categories.contains(InterpretationCategory.SAME_FEATURE_PLAUSIBLE)
                    ? RequiresRelevance.MEDIUM
                    : RequiresRelevance.LOW;
        }
        if (structural == EvidenceLevel.HIGH && domain == EvidenceLevel.HIGH
                && direction == EvidenceLevel.HIGH && commonContext != EvidenceLevel.HIGH
                && productSetRelation == ProductSetRelation.SOURCE_SUBSET_OF_TARGET
                && !categories.contains(InterpretationCategory.SAME_FEATURE_PLAUSIBLE)) {
            return RequiresRelevance.HIGH;
        }
        if (domain != EvidenceLevel.LOW && structural != EvidenceLevel.LOW
                && !categories.contains(InterpretationCategory.INSUFFICIENT_EVIDENCE)) {
            return RequiresRelevance.MEDIUM;
        }
        return RequiresRelevance.LOW;
    }

    private Set<InterpretationCategory> refineCommonOnlyCategories(Set<InterpretationCategory> categories,
                                                                   TargetLocalization localization) {
        Set<InterpretationCategory> refined = new HashSet<>(categories);
        if (localization.featureSpecificTargetEntities().isBlank()
                || "COMMON".equals(localization.primaryTargetFeatureSpecificity())) {
            refined.add(InterpretationCategory.SHARED_DOMAIN_MODEL);
        }
        return refined;
    }

    private String relevanceReasons(List<DependencyEvidence> rows, EvidenceLevel structural,
                                    EvidenceLevel domain, EvidenceLevel direction, EvidenceLevel resolution) {
        return "structural=" + structural
                + ";domain=" + domain
                + ";direction=" + direction
                + ";resolution=" + resolution
                + ";rows=" + rows.size();
    }

    private String cautionReasons(ProductSetRelation relation, EvidenceLevel sameFeature, EvidenceLevel commonContext,
                                  TargetLocalization localization) {
        List<String> reasons = new ArrayList<>();
        if (localization.featureSpecificTargetEntities().isBlank()
                || "COMMON".equals(localization.primaryTargetFeatureSpecificity())) {
            reasons.add("NO_FEATURE_SPECIFIC_TARGET");
        }
        if (relation == ProductSetRelation.EQUAL) {
            reasons.add("equal product sets are ambiguous");
        }
        if (sameFeature == EvidenceLevel.HIGH) {
            reasons.add("same-feature implementation remains plausible");
        }
        if (commonContext == EvidenceLevel.HIGH) {
            reasons.add("common UI/orchestration context may explain dependency");
        }
        return String.join("; ", reasons);
    }

    private String exclusionReasons(List<DependencyEvidence> rows) {
        return join(rows.stream()
                .map(row -> row.exclusionReason().name())
                .filter(reason -> !"NONE".equals(reason))
                .collect(Collectors.toSet()));
    }

    private String referencedEntities(List<DependencyEvidence> rows,
                                      Map<String, AuditedEntityClassification> auditedById,
                                      Map<String, JavaEntity> entitiesById) {
        return join(rows.stream()
                .map(row -> {
                    JavaEntity target = entitiesById.get(row.targetEntityId());
                    AuditedEntityClassification audited = auditedById.get(row.targetEntityId());
                    if (target != null) {
                        return displayName(target);
                    }
                    return audited == null ? row.targetText() : audited.qualifiedName();
                })
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.toSet()));
    }

    private String typeSignature(List<DependencyEvidence> outgoing, Map<String, JavaEntity> entitiesById) {
        return join(outgoing.stream()
                .filter(row -> row.relationType() == JavaRelationType.TYPE_REFERENCE)
                .map(row -> {
                    JavaEntity target = entitiesById.get(row.targetEntityId());
                    if (target != null) {
                        return displayName(target);
                    }
                    return row.targetText();
                })
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.toSet()));
    }

    private Set<String> dependencyTokens(List<DependencyEvidence> outgoing, List<DependencyEvidence> incoming,
                                         Map<String, JavaEntity> entitiesById) {
        Set<String> tokens = new TreeMapSet();
        for (DependencyEvidence row : outgoing) {
            JavaEntity target = entitiesById.get(row.targetEntityId());
            if (target != null) {
                tokens.addAll(tokens(displayName(target)));
            }
            tokens.addAll(tokens(row.targetText()));
        }
        for (DependencyEvidence row : incoming) {
            JavaEntity source = entitiesById.get(row.sourceEntityId());
            if (source != null) {
                tokens.addAll(tokens(displayName(source)));
            }
        }
        return tokens;
    }

    private String usageEvidence(JavaEntity entity, List<DependencyEvidence> incoming, List<DependencyEvidence> outgoing) {
        return "incoming=" + incoming.size() + ";outgoing=" + outgoing.size()
                + ";block=" + entity.containingBlockId()
                + ";products=" + String.join("|", entity.observedProducts());
    }

    private ProductSetRelation productSetRelation(Set<String> source, Set<String> target) {
        if (source.isEmpty() || target.isEmpty()) {
            return ProductSetRelation.UNKNOWN;
        }
        boolean sourceSubset = target.containsAll(source);
        boolean targetSubset = source.containsAll(target);
        if (sourceSubset && targetSubset) {
            return ProductSetRelation.EQUAL;
        }
        if (sourceSubset) {
            return ProductSetRelation.SOURCE_SUBSET_OF_TARGET;
        }
        if (targetSubset) {
            return ProductSetRelation.TARGET_SUBSET_OF_SOURCE;
        }
        boolean overlap = source.stream().anyMatch(target::contains);
        return overlap ? ProductSetRelation.OVERLAP_ONLY : ProductSetRelation.DISJOINT;
    }

    private Set<String> products(ConditionalBlock block) {
        if (block == null) {
            return Set.of();
        }
        return block.signature().productIds().stream().collect(Collectors.toCollection(TreeMapSet::new));
    }

    private boolean sameSet(Set<String> left, Set<String> right) {
        return left.size() == right.size() && left.containsAll(right);
    }

    private boolean ordinaryMeaningful(JavaRelationType type) {
        return DependencyKindClassifier.isImplementationDependency(type);
    }

    private boolean hasOrdinaryImplementationDependency(List<DependencyEvidence> rows) {
        return rows.stream().anyMatch(row -> DependencyKindClassifier.isImplementationDependency(row.relationType()));
    }

    private boolean isStructuralEvidence(Set<InterpretationCategory> categories) {
        return categories.contains(InterpretationCategory.STRUCTURAL_EXTENDS)
                || categories.contains(InterpretationCategory.STRUCTURAL_IMPLEMENTS);
    }

    private EntityRole legacyEquivalent(SemanticRole semantic, EvidenceLevel domain) {
        return switch (semantic) {
            case DOMAIN_MODEL, DOMAIN_OPERATION -> EntityRole.DOMAIN_CORE;
            case DOMAIN_STATE, DOMAIN_SUPPORT, DOMAIN_FACADE -> EntityRole.DOMAIN_SUPPORT;
            case VARIABILITY_CONTROL -> EntityRole.VARIABILITY_CONTROL;
            case GENERIC_UTILITY -> EntityRole.UTILITY;
            case UNKNOWN -> domain == EvidenceLevel.HIGH ? EntityRole.DOMAIN_CORE : EntityRole.UNKNOWN;
        };
    }

    private boolean hasDomainConcept(Set<String> tokens) {
        return containsAny(tokens, Set.of("request", "floor", "floors", "elevator", "door", "direction",
                "service", "disabled", "permission", "control", "controls", "button", "state", "queue"));
    }

    private boolean genericGetter(String simpleName, Set<String> nameTokens, Set<String> declaringTokens,
                                  Set<String> typeTokens) {
        return simpleName.startsWith("get")
                && !hasDomainConcept(union(nameTokens, declaringTokens, typeTokens));
    }

    private Set<String> tokens(String text) {
        Set<String> result = new TreeMapSet();
        if (text == null) {
            return result;
        }
        for (String part : text.split("[^A-Za-z0-9]+")) {
            if (part.isBlank()) {
                continue;
            }
            for (String token : part.replaceAll("([a-z])([A-Z])", "$1 $2").split("\\s+")) {
                if (!token.isBlank()) {
                    result.add(token.toLowerCase(Locale.ROOT));
                }
            }
        }
        return result;
    }

    private boolean containsAny(Set<String> tokens, Set<String> candidates) {
        for (String token : tokens) {
            if (candidates.contains(token)) {
                return true;
            }
        }
        return false;
    }

    @SafeVarargs
    private final Set<String> union(Set<String>... sets) {
        Set<String> result = new TreeMapSet();
        for (Set<String> set : sets) {
            result.addAll(set);
        }
        return result;
    }

    private String evidence(String label, Set<String> tokens) {
        return tokens.isEmpty() ? "" : label + "=" + String.join("|", tokens);
    }

    private String displayName(JavaEntity entity) {
        if (entity == null) {
            return "";
        }
        return entity.qualifiedName() == null || entity.qualifiedName().isBlank()
                ? entity.simpleName()
                : entity.qualifiedName();
    }

    private String declaringType(JavaEntity entity) {
        String qualified = displayName(entity);
        if (entity.entityType() == JavaEntityType.CLASS || entity.entityType() == JavaEntityType.INTERFACE) {
            return "";
        }
        int lastDot = qualified.lastIndexOf('.');
        return lastDot < 0 ? "" : qualified.substring(0, lastDot);
    }

    private String packageName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        if (lastDot < 0) {
            return "";
        }
        return qualifiedName.substring(0, lastDot);
    }

    private String join(Set<String> values) {
        return values.stream().filter(value -> value != null && !value.isBlank())
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private static final class TreeMapSet extends java.util.TreeSet<String> {
    }

    private record TargetLocalization(
            String targetPresenceScope,
            int targetEntityCount,
            int directlyReferencedTargetEntityCount,
            double targetScopeCoarseness,
            String targetScopeQuality,
            String referencedTargetEntities,
            String referencedTargetMethods,
            String referencedTargetFields,
            String targetDeclarationRanges,
            String primaryTargetEntity,
            String primaryTargetOperation,
            String primaryTargetDeclaration,
            String primaryTargetPresenceScope,
            String primaryTargetFeatureSpecificity,
            String primarySelectionReason,
            String primaryEvidenceRelationId,
            String primaryEvidenceSourceEntity,
            String primaryEvidenceSourceEntityType,
            String primaryEvidenceTargetEntityType,
            String primaryEvidenceDependencyKind,
            String primaryEvidenceSourceFile,
            int primaryEvidenceSourceLine,
            String alternativeTargetEntities,
            String commonTargetEntities,
            String featureSpecificTargetEntities,
            String evidenceSnippets
    ) {
    }

    private record TargetCandidate(
            JavaEntity entity,
            DependencyEvidence evidence,
            String presenceScope,
            String featureSpecificity,
            boolean declaredInsideTargetFragment,
            boolean compileNecessity,
            boolean runtimeNecessity,
            JavaRelationType relationType,
            boolean direct,
            String selectionReason
    ) {
    }
}
