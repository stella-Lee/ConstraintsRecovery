package spl.requiresanalysis;

import spl.ConditionalBlock;
import spl.ConditionalBlock.DirectiveType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class BranchCompletionObligationDetector {
    private static final Pattern METHOD_HEADER_PATTERN = Pattern.compile(
            "^(?:[\\w<>\\[\\], ?.@]+\\s+)*(?<returnType>[\\w.$<>\\[\\], ?]+)\\s+(?<name>[A-Za-z_$][\\w$]*)\\s*\\([^;{}]*\\)\\s*(?:throws\\s+[\\w.$,\\s]+)?\\{.*$"
    );
    private static final Set<String> NON_METHOD_NAMES = Set.of(
            "if", "for", "while", "switch", "catch", "return", "throw", "new", "class", "interface", "enum", "record"
    );

    public DisjunctiveImplementationEvidenceResult detectBranchCompletionObligations(List<ConditionalBlock> blocks)
            throws IOException {
        Objects.requireNonNull(blocks, "blocks");
        List<ConditionalBlock> branchBlocks = flatten(blocks).stream()
                .filter(block -> block.directiveType() != DirectiveType.ENDIF)
                .sorted(blockComparator())
                .toList();
        Map<Path, List<String>> linesByFile = loadLines(branchBlocks);
        Map<Path, List<ConditionalBlock>> blocksByFile = branchBlocks.stream()
                .collect(Collectors.groupingBy(
                        block -> block.filePath().toAbsolutePath().normalize(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        int examined = 0;
        List<DisjunctiveImplementationEvidence> candidates = new ArrayList<>();
        for (Map.Entry<Path, List<ConditionalBlock>> entry : blocksByFile.entrySet()) {
            List<String> lines = linesByFile.get(entry.getKey());
            if (lines == null) {
                continue;
            }
            List<MethodRegion> nonVoidMethods = findNonVoidMethods(lines);
            for (MethodRegion method : nonVoidMethods) {
                List<ConditionalBlock> methodBlocks = entry.getValue().stream()
                        .filter(block -> method.contains(block.startLine()) && method.contains(block.endLine()))
                        .sorted(blockComparator())
                        .toList();
                for (ConditionalBlock sourceBlock : methodBlocks) {
                    List<ConditionalBlock> nestedProviders = nestedBlocks(sourceBlock).stream()
                            .filter(block -> block.directiveType() != DirectiveType.ENDIF)
                            .filter(block -> method.contains(block.startLine()) && method.contains(block.endLine()))
                            .sorted(blockComparator())
                            .toList();
                    if (nestedProviders.isEmpty()) {
                        continue;
                    }
                    examined++;
                    if (sourceBlock.signature().isEmpty() || hasDirectReturn(sourceBlock, nestedProviders, lines)) {
                        continue;
                    }
                    List<ConditionalBlock> providers = providersWithReturn(sourceBlock, nestedProviders, lines);
                    if (providers.size() < 2 || !sourceSignatureCoveredByProviders(sourceBlock, providers)) {
                        continue;
                    }
                    candidates.add(candidate(sourceBlock, providers, method));
                }
                List<DisjunctiveImplementationEvidence> siblingCandidates =
                        siblingReturnCandidates(method, methodBlocks, lines);
                examined += siblingCandidates.size();
                candidates.addAll(siblingCandidates);
            }
        }
        return new DisjunctiveImplementationEvidenceResult(number(candidates), examined);
    }

    private static List<ConditionalBlock> flatten(List<ConditionalBlock> blocks) {
        List<ConditionalBlock> flattened = new ArrayList<>();
        for (ConditionalBlock block : blocks) {
            flattened.add(block);
            flattened.addAll(flatten(block.children()));
        }
        return flattened;
    }

    private static DisjunctiveImplementationEvidence candidate(ConditionalBlock sourceBlock,
                                                              List<ConditionalBlock> providers,
                                                              MethodRegion method) {
        return new DisjunctiveImplementationEvidence(
                "",
                "DISJUNCTIVE_IMPLEMENTATION_EVIDENCE",
                RequiresEvidenceBuilder.blockId(sourceBlock),
                sourceBlock.signature().toString(),
                providers.stream().map(RequiresEvidenceBuilder::blockId).distinct().sorted().toList(),
                providers.stream()
                        .map(block -> block.signature().toString())
                        .distinct()
                        .sorted()
                        .toList(),
                "BRANCH_COMPLETION",
                "NON_VOID_RETURN_COMPLETION",
                method.displayName(),
                sourceBlock.filePath() + ":" + method.startLine() + "-" + method.endLine(),
                cautionReasons(providers)
        );
    }

    private static List<DisjunctiveImplementationEvidence> siblingReturnCandidates(MethodRegion method,
                                                                                   List<ConditionalBlock> methodBlocks,
                                                                                   List<String> lines) {
        List<DisjunctiveImplementationEvidence> candidates = new ArrayList<>();
        Map<Integer, List<ConditionalBlock>> byDepth = methodBlocks.stream()
                .collect(Collectors.groupingBy(
                        ConditionalBlock::nestingDepth,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        for (List<ConditionalBlock> sameDepth : byDepth.values()) {
            List<ConditionalBlock> ordered = sameDepth.stream().sorted(blockComparator()).toList();
            for (int index = 0; index < ordered.size(); index++) {
                ConditionalBlock first = ordered.get(index);
                if (first.directiveType() != DirectiveType.IF) {
                    continue;
                }
                List<ConditionalBlock> chain = new ArrayList<>();
                chain.add(first);
                int cursor = index + 1;
                while (cursor < ordered.size()) {
                    ConditionalBlock next = ordered.get(cursor);
                    if (next.startLine() > first.endLine() && next.directiveType() == DirectiveType.IF) {
                        break;
                    }
                    if (next.directiveType() == DirectiveType.ELIF || next.directiveType() == DirectiveType.ELSE) {
                        chain.add(next);
                    }
                    if (next.startLine() > first.endLine()) {
                        break;
                    }
                    cursor++;
                }
                candidates.addAll(candidateFromSiblingChain(chain, method, methodBlocks, lines));
            }
        }
        return candidates;
    }

    private static List<DisjunctiveImplementationEvidence> candidateFromSiblingChain(List<ConditionalBlock> chain,
                                                                                     MethodRegion method,
                                                                                     List<ConditionalBlock> methodBlocks,
                                                                                     List<String> lines) {
        List<ConditionalBlock> providers = chain.stream()
                .filter(block -> hasDirectReturn(block, List.of(), lines))
                .filter(block -> !hasOnlyDelegatingReturn(block, lines))
                .filter(block -> !block.signature().isEmpty())
                .sorted(blockComparator())
                .toList();
        if (providers.size() < 2) {
            return List.of();
        }
        String sourceSignature = unionSignature(providers);
        if (hasExplicitEnclosingSourceBlock(sourceSignature, providers, methodBlocks)) {
            return List.of();
        }
        return List.of(new DisjunctiveImplementationEvidence(
                "",
                "DISJUNCTIVE_IMPLEMENTATION_EVIDENCE",
                siblingChainId(providers),
                sourceSignature,
                providers.stream().map(RequiresEvidenceBuilder::blockId).distinct().sorted().toList(),
                providers.stream()
                        .map(block -> block.signature().toString())
                        .distinct()
                        .sorted()
                        .toList(),
                "SIBLING_BRANCH_COMPLETION",
                "NON_VOID_RETURN_COMPLETION",
                method.displayName(),
                providers.get(0).filePath() + ":" + providers.get(0).startLine() + "-" + providers.get(providers.size() - 1).endLine(),
                siblingCautionReasons()
        ));
    }

    private static boolean hasExplicitEnclosingSourceBlock(String sourceSignature,
                                                           List<ConditionalBlock> providers,
                                                           List<ConditionalBlock> methodBlocks) {
        return methodBlocks.stream()
                .filter(block -> !block.signature().isEmpty())
                .filter(block -> providers.stream().allMatch(provider ->
                        block.startLine() < provider.startLine() && provider.endLine() < block.endLine()))
                .anyMatch(block -> compactSignature(block.signature().toString()).equals(sourceSignature)
                        || !sourceSignatureCoveredByProviders(block, providers));
    }

    private static boolean hasOnlyDelegatingReturn(ConditionalBlock block, List<String> lines) {
        List<String> returns = new ArrayList<>();
        for (int line = block.startLine(); line <= Math.min(block.endLine(), lines.size()); line++) {
            String normalized = normalizeVariantMarker(lines.get(line - 1)).trim();
            if (containsReturn(normalized)) {
                returns.add(normalized);
            }
        }
        return returns.size() == 1 && Pattern.compile("^return\\s+[A-Za-z_$][\\w$]*\\s*\\([^;]*\\)\\s*;?$")
                .matcher(returns.get(0))
                .find();
    }

    private static String unionSignature(List<ConditionalBlock> providers) {
        return providers.stream()
                .flatMap(block -> block.signature().productIds().stream())
                .distinct()
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private static String compactSignature(String signature) {
        return new spl.ProductSignature(signature).productIds().stream()
                .distinct()
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private static String siblingChainId(List<ConditionalBlock> providers) {
        ConditionalBlock first = providers.get(0);
        ConditionalBlock last = providers.get(providers.size() - 1);
        return first.filePath() + ":SIBLING_BRANCH_UNION:" + first.startLine() + "-" + last.endLine();
    }

    private static List<DisjunctiveImplementationEvidence> number(List<DisjunctiveImplementationEvidence> candidates) {
        List<DisjunctiveImplementationEvidence> ordered = candidates.stream()
                .sorted(Comparator.comparing(DisjunctiveImplementationEvidence::evidenceLocation)
                        .thenComparing(DisjunctiveImplementationEvidence::sourceSignature)
                        .thenComparing(candidate -> String.join("|", candidate.providerSignatureSet())))
                .toList();
        List<DisjunctiveImplementationEvidence> numbered = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            DisjunctiveImplementationEvidence candidate = ordered.get(index);
            numbered.add(new DisjunctiveImplementationEvidence(
                    "DRC-" + String.format(java.util.Locale.ROOT, "%04d", index + 1),
                    candidate.candidateType(),
                    candidate.sourceBlockId(),
                    candidate.sourceSignature(),
                    candidate.providerBlockIds(),
                    candidate.providerSignatureSet(),
                    candidate.evidenceType(),
                    candidate.obligation(),
                    candidate.evidenceMethod(),
                    candidate.evidenceLocation(),
                    candidate.cautionReasons()
            ));
        }
        return numbered;
    }

    private static Map<Path, List<String>> loadLines(List<ConditionalBlock> blocks) throws IOException {
        Map<Path, List<String>> linesByFile = new HashMap<>();
        for (ConditionalBlock block : blocks) {
            Path file = block.filePath().toAbsolutePath().normalize();
            if (!linesByFile.containsKey(file) && Files.exists(file)) {
                linesByFile.put(file, Files.readAllLines(file, StandardCharsets.UTF_8));
            }
        }
        return linesByFile;
    }

    private static List<MethodRegion> findNonVoidMethods(List<String> lines) {
        List<MethodRegion> methods = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = normalizeVariantMarker(lines.get(index)).trim();
            MethodHeader header = parseMethodHeader(line);
            if (header == null || "void".equals(header.returnType())) {
                continue;
            }
            int startLine = index + 1;
            int endLine = findBraceEnd(lines, startLine);
            if (endLine > startLine) {
                methods.add(new MethodRegion(header.name(), header.returnType(), startLine, endLine));
            }
        }
        return methods;
    }

    private static MethodHeader parseMethodHeader(String line) {
        if (line.isBlank() || line.startsWith("//") || line.startsWith("*") || line.startsWith("@")) {
            return null;
        }
        Matcher matcher = METHOD_HEADER_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        String methodName = matcher.group("name");
        String returnType = lastToken(matcher.group("returnType").trim());
        String firstReturnToken = returnType.split("\\s+")[0];
        if (NON_METHOD_NAMES.contains(methodName) || NON_METHOD_NAMES.contains(firstReturnToken)) {
            return null;
        }
        return new MethodHeader(methodName, returnType);
    }

    private static String lastToken(String value) {
        String[] tokens = value.split("\\s+");
        return tokens.length == 0 ? value : tokens[tokens.length - 1];
    }

    private static int findBraceEnd(List<String> lines, int startLine) {
        int depth = 0;
        boolean opened = false;
        for (int line = startLine; line <= lines.size(); line++) {
            String normalized = normalizeVariantMarker(lines.get(line - 1));
            for (int index = 0; index < normalized.length(); index++) {
                char value = normalized.charAt(index);
                if (value == '{') {
                    opened = true;
                    depth++;
                } else if (value == '}') {
                    depth--;
                    if (opened && depth <= 0) {
                        return line;
                    }
                }
            }
        }
        return lines.size();
    }

    private static boolean hasDirectReturn(ConditionalBlock sourceBlock, List<ConditionalBlock> nestedProviders,
                                           List<String> lines) {
        for (int line = sourceBlock.startLine(); line <= Math.min(sourceBlock.endLine(), lines.size()); line++) {
            if (insideAny(line, nestedProviders)) {
                continue;
            }
            if (containsReturn(lines.get(line - 1))) {
                return true;
            }
        }
        return false;
    }

    private static List<ConditionalBlock> providersWithReturn(ConditionalBlock sourceBlock,
                                                              List<ConditionalBlock> nestedProviders,
                                                              List<String> lines) {
        Map<String, ConditionalBlock> providers = new LinkedHashMap<>();
        for (int line = sourceBlock.startLine(); line <= Math.min(sourceBlock.endLine(), lines.size()); line++) {
            if (!containsReturn(lines.get(line - 1))) {
                continue;
            }
            int returnLine = line;
            Optional<ConditionalBlock> provider = nestedProviders.stream()
                    .filter(block -> containsLine(block, returnLine))
                    .min(Comparator.comparingInt(ConditionalBlock::endLine)
                            .thenComparing(Comparator.comparingInt(ConditionalBlock::startLine).reversed()));
            provider.ifPresent(block -> providers.put(RequiresEvidenceBuilder.blockId(block), block));
        }
        return providers.values().stream().sorted(blockComparator()).toList();
    }

    private static boolean sourceSignatureCoveredByProviders(ConditionalBlock sourceBlock,
                                                             List<ConditionalBlock> providers) {
        Set<String> sourceProducts = new LinkedHashSet<>(sourceBlock.signature().productIds());
        Set<String> providerProducts = providers.stream()
                .flatMap(block -> block.signature().productIds().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return !sourceProducts.isEmpty() && providerProducts.containsAll(sourceProducts);
    }

    private static List<ConditionalBlock> nestedBlocks(ConditionalBlock block) {
        List<ConditionalBlock> result = new ArrayList<>();
        for (ConditionalBlock child : block.children()) {
            result.add(child);
            result.addAll(nestedBlocks(child));
        }
        return result;
    }

    private static boolean containsReturn(String line) {
        return Pattern.compile("\\breturn\\b").matcher(normalizeVariantMarker(line)).find();
    }

    private static boolean insideAny(int line, List<ConditionalBlock> blocks) {
        return blocks.stream().anyMatch(block -> containsLine(block, line));
    }

    private static boolean containsLine(ConditionalBlock block, int line) {
        return block.startLine() <= line && line <= block.endLine();
    }

    private static String normalizeVariantMarker(String line) {
        return line.replaceFirst("^(\\s*)//@\\s?", "$1");
    }

    private static List<String> cautionReasons(List<ConditionalBlock> providers) {
        List<String> reasons = new ArrayList<>();
        if (providers.stream().anyMatch(block -> block.directiveType() == DirectiveType.ELSE)) {
            reasons.add("ELSE_PROVIDER_SIGNATURE_DEPENDS_ON_EXISTING_SIGNATURE_EXTRACTION");
        }
        reasons.add("IMPLEMENTATION_EVIDENCE_ONLY_NOT_FEATURE_MODEL_CONSTRAINT");
        return reasons;
    }

    private static List<String> siblingCautionReasons() {
        return List.of(
                "SYNTHETIC_SOURCE_SIGNATURE_FROM_SIBLING_PROVIDER_UNION",
                "PRODUCT_ID_ONLY_NO_FEATURE_NAME_USED",
                "IMPLEMENTATION_EVIDENCE_ONLY_NOT_FEATURE_MODEL_CONSTRAINT"
        );
    }

    private static Comparator<ConditionalBlock> blockComparator() {
        return Comparator.comparing((ConditionalBlock block) -> block.filePath().toString())
                .thenComparingInt(ConditionalBlock::startLine)
                .thenComparingInt(ConditionalBlock::endLine)
                .thenComparing(block -> block.directiveType().name());
    }

    private record MethodHeader(String name, String returnType) {
    }

    private record MethodRegion(String name, String returnType, int startLine, int endLine) {
        private boolean contains(int line) {
            return startLine <= line && line <= endLine;
        }

        private String displayName() {
            return returnType + " " + name + "(...)";
        }
    }
}
