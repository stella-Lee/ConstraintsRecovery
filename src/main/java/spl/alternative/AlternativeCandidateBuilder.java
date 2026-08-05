package spl.alternative;

import spl.ConditionalBlock;
import spl.dependency.DependencyEdge;
import spl.dependency.DependencyGraph;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityType;
import spl.grouping.SignatureGroup;
import spl.requiresanalysis.RequiresEvidenceBuilder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class AlternativeCandidateBuilder {
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^A-Za-z0-9_]+");
    private static final Set<String> STOP_WORDS = Set.of("get", "set", "is", "new", "run", "value", "object",
            "data", "listener", "action", "handler", "manager", "impl", "java", "de", "ovgu",
            "featureide", "examples", "elevator");

    public AlternativeCandidateResult build(List<ConditionalBlock> blocks, List<SignatureGroup> groups,
                                            Collection<JavaEntity> entities, DependencyGraph graph) {
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(groups, "groups");
        Objects.requireNonNull(entities, "entities");
        Objects.requireNonNull(graph, "graph");

        Map<String, ConditionalBlock> blocksById = allBlocks(blocks).stream()
                .filter(this::isBranch)
                .collect(Collectors.toMap(RequiresEvidenceBuilder::blockId, block -> block, (left, right) -> left,
                        LinkedHashMap::new));
        Set<String> siblingPairs = siblingPairs(blocks);
        Map<String, List<JavaEntity>> entitiesByBlock = entities.stream()
                .collect(Collectors.groupingBy(JavaEntity::containingBlockId));
        Map<String, JavaEntity> entitiesById = entities.stream()
                .collect(Collectors.toMap(JavaEntity::entityId, entity -> entity, (left, right) -> left));
        Map<String, List<DependencyEdge>> outgoingByBlock = graph.edges().stream()
                .collect(Collectors.groupingBy(DependencyEdge::sourceBlockId));
        Set<String> universe = blocksById.values().stream()
                .flatMap(block -> block.signature().productIds().stream())
                .collect(Collectors.toCollection(TreeSet::new));

        Map<PairKey, CandidateAccumulator> accumulators = new TreeMap<>();
        int examined = 0;
        int weakRemoved = 0;
        int trivialRemoved = 0;
        int commonRemoved = 0;
        List<ConditionalBlock> orderedBlocks = blocksById.values().stream()
                .sorted(Comparator.comparing((ConditionalBlock block) -> block.filePath().toString())
                        .thenComparingInt(ConditionalBlock::startLine))
                .toList();
        for (int i = 0; i < orderedBlocks.size(); i++) {
            for (int j = i + 1; j < orderedBlocks.size(); j++) {
                ConditionalBlock left = orderedBlocks.get(i);
                ConditionalBlock right = orderedBlocks.get(j);
                examined++;
                boolean sibling = siblingPairs.contains(blockPairKey(left, right));
                if (commonOrOuter(left, universe) || commonOrOuter(right, universe)) {
                    commonRemoved++;
                    continue;
                }
                if (!sibling && trivial(left, right)) {
                    trivialRemoved++;
                    continue;
                }
                Evidence evidence = evidence(left, right, siblingPairs, entitiesByBlock, entitiesById, outgoingByBlock);
                if (!hasStrongEvidence(evidence.categories())) {
                    weakRemoved++;
                    continue;
                }
                PairKey key = PairKey.of(featureCondition(left), productSignature(left),
                        featureCondition(right), productSignature(right));
                accumulators.computeIfAbsent(key, ignored -> new CandidateAccumulator(key))
                        .add(left, right, evidence);
            }
        }

        List<AlternativeCandidate> candidates = new ArrayList<>();
        int index = 1;
        for (CandidateAccumulator accumulator : accumulators.values()) {
            AlternativeCandidate candidate = accumulator.toCandidate("ALT-" + String.format(Locale.ROOT, "%04d", index));
            if (candidate.confidence() != AlternativeConfidence.LOW) {
                candidates.add(candidate);
                index++;
            }
        }
        Map<AlternativeEvidenceCategory, Long> frequency = candidates.stream()
                .flatMap(candidate -> candidate.evidenceCategories().stream())
                .collect(Collectors.groupingBy(category -> category, () -> new EnumMap<>(AlternativeEvidenceCategory.class),
                        Collectors.counting()));
        return new AlternativeCandidateResult(examined, weakRemoved, trivialRemoved, commonRemoved,
                candidates, frequency);
    }

    private boolean hasStrongEvidence(Set<AlternativeEvidenceCategory> categories) {
        return categories.contains(AlternativeEvidenceCategory.SIBLING_BRANCH)
                || categories.contains(AlternativeEvidenceCategory.SAME_ENTITY_SIGNATURE);
    }

    private Evidence evidence(ConditionalBlock left, ConditionalBlock right, Set<String> siblingPairs,
                              Map<String, List<JavaEntity>> entitiesByBlock,
                              Map<String, JavaEntity> entitiesById,
                              Map<String, List<DependencyEdge>> outgoingByBlock) {
        Set<AlternativeEvidenceCategory> categories = new TreeSet<>(Comparator.comparing(Enum::name));
        List<String> entityPairs = new ArrayList<>();
        Set<String> contexts = new TreeSet<>();

        if (siblingPairs.contains(blockPairKey(left, right))) {
            categories.add(AlternativeEvidenceCategory.SIBLING_BRANCH);
            contexts.add("sibling chain " + fileLine(left.filePath(), left.startLine()));
        }
        if (sameContext(left, right)) {
            categories.add(AlternativeEvidenceCategory.SAME_CONTEXT);
            contexts.add("same file/depth " + left.filePath().getFileName() + ":depth" + left.nestingDepth());
        }
        List<JavaEntity> leftEntities = entitiesByBlock.getOrDefault(RequiresEvidenceBuilder.blockId(left), List.of());
        List<JavaEntity> rightEntities = entitiesByBlock.getOrDefault(RequiresEvidenceBuilder.blockId(right), List.of());
        for (JavaEntity a : leftEntities) {
            for (JavaEntity b : rightEntities) {
                if (sameEntitySignature(a, b)) {
                    categories.add(AlternativeEvidenceCategory.SAME_ENTITY_SIGNATURE);
                    entityPairs.add(display(a) + " <-> " + display(b));
                }
            }
        }
        if (sameCallContext(left, right, entitiesById, outgoingByBlock)) {
            categories.add(AlternativeEvidenceCategory.SAME_CALL_CONTEXT);
        }
        if (lexicalSimilarity(leftEntities, rightEntities) >= 0.25) {
            categories.add(AlternativeEvidenceCategory.LEXICAL_SIMILARITY);
        }
        if (disjoint(left, right)) {
            categories.add(AlternativeEvidenceCategory.DISJOINT_SIGNATURE);
        }
        return new Evidence(categories, entityPairs, contexts);
    }

    private boolean sameCallContext(ConditionalBlock left, ConditionalBlock right,
                                    Map<String, JavaEntity> entitiesById,
                                    Map<String, List<DependencyEdge>> outgoingByBlock) {
        Set<String> leftContexts = callContexts(left, entitiesById, outgoingByBlock);
        Set<String> rightContexts = callContexts(right, entitiesById, outgoingByBlock);
        return leftContexts.stream().anyMatch(rightContexts::contains);
    }

    private Set<String> callContexts(ConditionalBlock block, Map<String, JavaEntity> entitiesById,
                                     Map<String, List<DependencyEdge>> outgoingByBlock) {
        return outgoingByBlock.getOrDefault(RequiresEvidenceBuilder.blockId(block), List.of()).stream()
                .map(edge -> {
                    JavaEntity source = entitiesById.get(edge.sourceEntityId());
                    JavaEntity target = entitiesById.get(edge.targetEntityId());
                    return entitySignature(source) + "|" + edge.relationType() + "|" + simpleName(target);
                })
                .filter(text -> !text.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private boolean sameEntitySignature(JavaEntity left, JavaEntity right) {
        return left.entityType() == right.entityType()
                && !left.entityId().equals(right.entityId())
                && entitySignature(left).equals(entitySignature(right));
    }

    private String entitySignature(JavaEntity entity) {
        if (entity == null) {
            return "";
        }
        String declaring = declaringType(entity);
        String parameters = "";
        if (entity.entityType() == JavaEntityType.METHOD || entity.entityType() == JavaEntityType.CONSTRUCTOR) {
            int open = entity.qualifiedName() == null ? -1 : entity.qualifiedName().indexOf('(');
            int close = entity.qualifiedName() == null ? -1 : entity.qualifiedName().lastIndexOf(')');
            parameters = open >= 0 && close > open ? entity.qualifiedName().substring(open, close + 1) : "()";
        }
        return entity.entityType() + "|" + declaring + "|" + entity.simpleName() + parameters;
    }

    private String declaringType(JavaEntity entity) {
        String qualified = entity.qualifiedName() == null || entity.qualifiedName().isBlank()
                ? entity.simpleName()
                : entity.qualifiedName();
        if (entity.entityType() == JavaEntityType.CLASS || entity.entityType() == JavaEntityType.INTERFACE) {
            return qualified;
        }
        int lastDot = qualified.lastIndexOf('.');
        return lastDot < 0 ? "" : qualified.substring(0, lastDot);
    }

    private double lexicalSimilarity(List<JavaEntity> left, List<JavaEntity> right) {
        Map<String, Integer> a = tokenFrequencies(left);
        Map<String, Integer> b = tokenFrequencies(right);
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> all = new HashSet<>(a.keySet());
        all.addAll(b.keySet());
        int intersection = 0;
        int union = 0;
        for (String token : all) {
            intersection += Math.min(a.getOrDefault(token, 0), b.getOrDefault(token, 0));
            union += Math.max(a.getOrDefault(token, 0), b.getOrDefault(token, 0));
        }
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private Map<String, Integer> tokenFrequencies(List<JavaEntity> entities) {
        Map<String, Integer> frequencies = new HashMap<>();
        for (JavaEntity entity : entities) {
            addTokens(entity.simpleName(), frequencies);
            addTokens(entity.qualifiedName(), frequencies);
        }
        return frequencies;
    }

    private void addTokens(String text, Map<String, Integer> frequencies) {
        if (text == null) {
            return;
        }
        for (String fragment : TOKEN_SPLIT.split(text)) {
            String spaced = fragment.replaceAll("([a-z])([A-Z])", "$1 $2")
                    .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                    .replace('_', ' ');
            for (String token : spaced.split("\\s+")) {
                String normalized = token.toLowerCase(Locale.ROOT);
                if (!normalized.isBlank() && !STOP_WORDS.contains(normalized)
                        && !normalized.chars().allMatch(Character::isDigit)) {
                    frequencies.merge(normalized, 1, Integer::sum);
                }
            }
        }
    }

    private boolean sameContext(ConditionalBlock left, ConditionalBlock right) {
        return left.filePath().equals(right.filePath()) && left.nestingDepth() == right.nestingDepth();
    }

    private boolean disjoint(ConditionalBlock left, ConditionalBlock right) {
        Set<String> products = new HashSet<>(left.signature().productIds());
        products.retainAll(right.signature().productIds());
        return products.isEmpty();
    }

    private boolean commonOrOuter(ConditionalBlock block, Set<String> universe) {
        Set<String> products = new TreeSet<>(block.signature().productIds());
        return (!products.isEmpty() && products.equals(universe)) || !block.children().isEmpty();
    }

    private boolean trivial(ConditionalBlock left, ConditionalBlock right) {
        String leftCondition = featureCondition(left);
        String rightCondition = featureCondition(right);
        if (leftCondition.equals(rightCondition)) {
            return true;
        }
        Set<String> leftProducts = new TreeSet<>(left.signature().productIds());
        Set<String> rightProducts = new TreeSet<>(right.signature().productIds());
        if (!leftProducts.isEmpty() && !rightProducts.isEmpty()
                && (leftProducts.containsAll(rightProducts) || rightProducts.containsAll(leftProducts))) {
            return true;
        }
        return conjuncts(leftCondition).containsAll(conjuncts(rightCondition))
                || conjuncts(rightCondition).containsAll(conjuncts(leftCondition));
    }

    private Set<String> conjuncts(String condition) {
        return java.util.Arrays.stream(condition.split("&&|\\bAND\\b"))
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private List<ConditionalBlock> allBlocks(List<ConditionalBlock> blocks) {
        List<ConditionalBlock> result = new ArrayList<>();
        for (ConditionalBlock block : blocks) {
            result.add(block);
            result.addAll(allBlocks(block.children()));
        }
        return result;
    }

    private boolean isBranch(ConditionalBlock block) {
        return block.directiveType() == ConditionalBlock.DirectiveType.IF
                || block.directiveType() == ConditionalBlock.DirectiveType.ELIF
                || block.directiveType() == ConditionalBlock.DirectiveType.ELSE;
    }

    private Set<String> siblingPairs(List<ConditionalBlock> blocks) {
        Set<String> pairs = new TreeSet<>();
        addSiblingPairs(blocks, pairs);
        return pairs;
    }

    private void addSiblingPairs(List<ConditionalBlock> siblings, Set<String> pairs) {
        List<ConditionalBlock> chain = new ArrayList<>();
        for (ConditionalBlock block : siblings) {
            if (isBranch(block)) {
                chain.add(block);
                continue;
            }
            addPairsInChain(chain, pairs);
            chain.clear();
        }
        addPairsInChain(chain, pairs);
        for (ConditionalBlock block : siblings) {
            addSiblingPairs(block.children(), pairs);
        }
    }

    private void addPairsInChain(List<ConditionalBlock> chain, Set<String> pairs) {
        for (int i = 0; i < chain.size(); i++) {
            for (int j = i + 1; j < chain.size(); j++) {
                pairs.add(blockPairKey(chain.get(i), chain.get(j)));
            }
        }
    }

    private String blockPairKey(ConditionalBlock left, ConditionalBlock right) {
        String leftId = RequiresEvidenceBuilder.blockId(left);
        String rightId = RequiresEvidenceBuilder.blockId(right);
        return leftId.compareTo(rightId) <= 0 ? leftId + "\u0000" + rightId : rightId + "\u0000" + leftId;
    }

    private String featureCondition(ConditionalBlock block) {
        String expression = block.signature().rawExpression();
        return expression == null || expression.isBlank() ? productSignature(block) : expression.trim();
    }

    private String productSignature(ConditionalBlock block) {
        return block.signature().productIds().stream().sorted().collect(Collectors.joining("|"));
    }

    private String simpleName(JavaEntity entity) {
        return entity == null ? "" : entity.simpleName();
    }

    private String display(JavaEntity entity) {
        return entity.qualifiedName() == null || entity.qualifiedName().isBlank()
                ? entity.simpleName()
                : entity.qualifiedName();
    }

    private String fileLine(Path file, int line) {
        return file.getFileName() + ":" + line;
    }

    private record Evidence(Set<AlternativeEvidenceCategory> categories, List<String> entityPairs,
                            Set<String> contexts) {
    }

    private record PairKey(String conditionA, String productSignatureA,
                           String conditionB, String productSignatureB) implements Comparable<PairKey> {
        static PairKey of(String leftCondition, String leftSignature, String rightCondition, String rightSignature) {
            return leftCondition.compareTo(rightCondition) <= 0
                    ? new PairKey(leftCondition, leftSignature, rightCondition, rightSignature)
                    : new PairKey(rightCondition, rightSignature, leftCondition, leftSignature);
        }

        @Override
        public int compareTo(PairKey other) {
            int compare = conditionA.compareTo(other.conditionA);
            if (compare != 0) {
                return compare;
            }
            compare = conditionB.compareTo(other.conditionB);
            if (compare != 0) {
                return compare;
            }
            compare = productSignatureA.compareTo(other.productSignatureA);
            return compare != 0 ? compare : productSignatureB.compareTo(other.productSignatureB);
        }
    }

    private final class CandidateAccumulator {
        private final PairKey key;
        private final Set<AlternativeEvidenceCategory> categories = new TreeSet<>(Comparator.comparing(Enum::name));
        private final Set<String> blockPairs = new TreeSet<>();
        private final Set<String> entityPairs = new TreeSet<>();
        private final Set<String> contexts = new TreeSet<>();

        private CandidateAccumulator(PairKey key) {
            this.key = key;
        }

        private void add(ConditionalBlock left, ConditionalBlock right, Evidence evidence) {
            categories.addAll(evidence.categories());
            blockPairs.add(RequiresEvidenceBuilder.blockId(left) + " <-> " + RequiresEvidenceBuilder.blockId(right));
            entityPairs.addAll(evidence.entityPairs());
            contexts.addAll(evidence.contexts());
        }

        private AlternativeCandidate toCandidate(String candidateId) {
            return new AlternativeCandidate(candidateId, key.conditionA(), key.conditionB(),
                    key.productSignatureA(), key.productSignatureB(), confidence(),
                    categories, new ArrayList<>(blockPairs), new ArrayList<>(entityPairs),
                    key.productSignatureA() + " <-> " + key.productSignatureB(),
                    String.join(" | ", contexts),
                    "candidate only; no excludes/XOR/feature-model inference");
        }

        private AlternativeConfidence confidence() {
            if (categories.contains(AlternativeEvidenceCategory.SIBLING_BRANCH)
                    && (categories.contains(AlternativeEvidenceCategory.SAME_ENTITY_SIGNATURE)
                    || categories.contains(AlternativeEvidenceCategory.SAME_CALL_CONTEXT))) {
                return AlternativeConfidence.HIGH;
            }
            if (categories.contains(AlternativeEvidenceCategory.SIBLING_BRANCH)
                    || (categories.contains(AlternativeEvidenceCategory.SAME_ENTITY_SIGNATURE)
                    && categories.contains(AlternativeEvidenceCategory.SAME_CALL_CONTEXT))) {
                return AlternativeConfidence.MEDIUM;
            }
            return AlternativeConfidence.LOW;
        }
    }
}
