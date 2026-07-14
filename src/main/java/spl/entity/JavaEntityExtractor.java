package spl.entity;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParseStart;
import com.github.javaparser.Problem;
import com.github.javaparser.Providers;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import spl.ConditionalBlock;
import spl.ProductSignature;
import spl.grouping.SignatureGroup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class JavaEntityExtractor {
    private static final Comparator<EntityDraft> ENTITY_ORDER =
            Comparator.comparing((EntityDraft draft) -> draft.sourceFile().toString())
                    .thenComparingInt(EntityDraft::startLine)
                    .thenComparing(draft -> draft.entityType().name())
                    .thenComparing(EntityDraft::sortName);
    private static final Comparator<RelationDraft> RELATION_ORDER =
            Comparator.comparing((RelationDraft draft) -> draft.sourceFile().toString())
                    .thenComparingInt(RelationDraft::sourceLine)
                    .thenComparing(draft -> draft.relationType().name())
                    .thenComparing(RelationDraft::sourceSortKey)
                    .thenComparing(RelationDraft::targetText);

    private final JavaParser javaParser;
    private final ProductProjectionGenerator projectionGenerator;

    public JavaEntityExtractor() {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));
        this.javaParser = new JavaParser(configuration);
        this.projectionGenerator = new ProductProjectionGenerator();
    }

    public EntityExtractionResult extract(Collection<SignatureGroup> groups, Collection<Path> assetFiles) {
        Objects.requireNonNull(groups, "groups");
        Objects.requireNonNull(assetFiles, "assetFiles");

        ExtractionIndex index = ExtractionIndex.from(groups);
        ProductSignature commonSignature = completeProductSignature(groups);
        List<String> productUniverse = commonSignature.productIds();
        List<EntityDraft> entityDrafts = new ArrayList<>();
        List<RelationDraft> relationDrafts = new ArrayList<>();
        List<ParseFailure> parseFailures = new ArrayList<>();
        Set<Path> parsedFiles = new TreeSet<>(Comparator.comparing(Path::toString));
        int projectionAttempts = 0;
        int successfulProjections = 0;

        for (Path assetFile : assetFiles.stream().sorted(Comparator.comparing(Path::toString)).toList()) {
            Path normalizedFile = assetFile.toAbsolutePath().normalize();
            try {
                String source = Files.readString(normalizedFile, StandardCharsets.UTF_8);
                for (String product : productUniverse) {
                    projectionAttempts++;
                    ProductProjection projection = projectionGenerator.generate(normalizedFile, source, product);
                    ParseResult<CompilationUnit> parseResult = javaParser.parse(
                            ParseStart.COMPILATION_UNIT,
                            Providers.provider(projection.source())
                    );
                    if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
                        parseFailures.addAll(toParseFailures(normalizedFile, product, parseResult.getProblems()));
                        continue;
                    }
                    successfulProjections++;
                    parsedFiles.add(normalizedFile);
                    collectFromCompilationUnit(
                            parseResult.getResult().get(),
                            normalizedFile,
                            product,
                            index,
                            commonSignature,
                            entityDrafts,
                            relationDrafts
                    );
                    collectFieldReferences(
                            parseResult.getResult().get(),
                            normalizedFile,
                            product,
                            index,
                            commonSignature,
                            entityDrafts,
                            relationDrafts
                    );
                }
            } catch (IOException exception) {
                parseFailures.add(new ParseFailure(normalizedFile, "<all>", 0, exception.getMessage(), true));
            } catch (ParseProblemException exception) {
                parseFailures.add(new ParseFailure(normalizedFile, "<all>", 0, exception.getMessage(), true));
            }
        }

        return buildResult(entityDrafts, relationDrafts, parseFailures, parsedFiles.size(),
                projectionAttempts, successfulProjections);
    }

    private static ProductSignature completeProductSignature(Collection<SignatureGroup> groups) {
        TreeSet<String> products = new TreeSet<>();
        for (SignatureGroup group : groups) {
            products.addAll(group.canonicalSignature().productIds());
        }
        return new ProductSignature(String.join("|", products));
    }

    private static void collectFromCompilationUnit(CompilationUnit unit, Path sourceFile, String product,
                                                   ExtractionIndex index, ProductSignature commonSignature,
                                                   List<EntityDraft> entityDrafts,
                                                   List<RelationDraft> relationDrafts) {
        for (ClassOrInterfaceDeclaration declaration : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            if (declaration.findAncestor(LocalClassDeclarationStmt.class).isPresent()) {
                continue;
            }
            BlockContext context = index.findContext(sourceFile, startLine(declaration))
                    .orElseGet(() -> BlockContext.common(commonSignature));
            entityDrafts.add(EntityDraft.from(
                    declaration,
                    declaration.isInterface() ? JavaEntityType.INTERFACE : JavaEntityType.CLASS,
                    declaration.getNameAsString(),
                    qualifiedName(unit, declaration),
                    sourceFile,
                    product,
                    context
            ));
        }

        for (MethodDeclaration declaration : unit.findAll(MethodDeclaration.class)) {
            addEntity(declaration, JavaEntityType.METHOD, declaration.getNameAsString(),
                    qualifiedName(unit, declaration), sourceFile, product, index, commonSignature, entityDrafts);
        }
        for (ConstructorDeclaration declaration : unit.findAll(ConstructorDeclaration.class)) {
            addEntity(declaration, JavaEntityType.CONSTRUCTOR, declaration.getNameAsString(),
                    qualifiedName(unit, declaration), sourceFile, product, index, commonSignature, entityDrafts);
        }
        for (FieldDeclaration declaration : unit.findAll(FieldDeclaration.class)) {
            for (VariableDeclarator variable : declaration.getVariables()) {
                addEntity(variable, JavaEntityType.FIELD, variable.getNameAsString(),
                        qualifiedName(unit, variable), sourceFile, product, index, commonSignature, entityDrafts);
            }
        }

        collectRelations(unit, sourceFile, product, index, commonSignature, relationDrafts);
    }

    private static void collectFieldReferences(CompilationUnit unit, Path sourceFile, String product,
                                               ExtractionIndex index, ProductSignature commonSignature,
                                               List<EntityDraft> entityDrafts,
                                               List<RelationDraft> relationDrafts) {
        Map<String, EntityDraft> fieldsByName = entityDrafts.stream()
                .filter(draft -> draft.sourceFile().equals(sourceFile))
                .filter(draft -> draft.observedProducts().contains(product))
                .filter(draft -> draft.entityType() == JavaEntityType.FIELD)
                .collect(Collectors.toMap(EntityDraft::simpleName, draft -> draft, (left, right) -> left));
        for (FieldAccessExpr access : unit.findAll(FieldAccessExpr.class)) {
            ResolvedField resolvedField = resolveField(access).orElse(null);
            addFieldReference(access, access.getNameAsString(), fieldsByName, resolvedField, sourceFile, product,
                    index, commonSignature, relationDrafts);
        }
        for (NameExpr name : unit.findAll(NameExpr.class)) {
            Optional<ResolvedField> resolvedField = resolveField(name);
            if (resolvedField.isPresent() || isFieldReferenceCandidate(name, fieldsByName)) {
                addFieldReference(name, name.getNameAsString(), fieldsByName, resolvedField.orElse(null), sourceFile, product,
                        index, commonSignature, relationDrafts);
            }
        }
    }

    private static boolean isFieldReferenceCandidate(NameExpr name, Map<String, EntityDraft> fieldsByName) {
        if (!fieldsByName.containsKey(name.getNameAsString())) {
            return false;
        }
        if (name.findAncestor(VariableDeclarator.class)
                .filter(variable -> variable.getNameAsString().equals(name.getNameAsString()))
                .isPresent()) {
            return false;
        }
        if (name.findAncestor(Parameter.class)
                .filter(parameter -> parameter.getNameAsString().equals(name.getNameAsString()))
                .isPresent()) {
            return false;
        }
        Optional<Node> parent = name.getParentNode();
        if (parent.isPresent() && parent.get() instanceof MethodCallExpr call
                && call.getNameAsString().equals(name.getNameAsString())) {
            return false;
        }
        return name.findAncestor(ClassOrInterfaceType.class).isEmpty();
    }

    private static void addFieldReference(Node node, String fieldName, Map<String, EntityDraft> fieldsByName,
                                          ResolvedField resolvedField,
                                          Path sourceFile, String product, ExtractionIndex index,
                                          ProductSignature commonSignature, List<RelationDraft> relationDrafts) {
        EntityDraft target = fieldsByName.get(fieldName);
        BlockContext context = index.findContext(sourceFile, startLine(node))
                .orElseGet(() -> BlockContext.common(commonSignature));
        relationDrafts.add(new RelationDraft(
                node,
                sourceFile,
                List.of(product),
                startLine(node),
                JavaRelationType.FIELD_REFERENCE,
                fieldName,
                target == null ? null : target.stableName(),
                resolvedField != null,
                context
        ));
    }

    private static Optional<ResolvedField> resolveField(NameExpr name) {
        try {
            ResolvedValueDeclaration declaration = name.resolve();
            if (declaration.isField()) {
                return Optional.of(new ResolvedField(declaration.getName()));
            }
        } catch (RuntimeException ignored) {
        }
        return Optional.empty();
    }

    private static Optional<ResolvedField> resolveField(FieldAccessExpr access) {
        try {
            ResolvedValueDeclaration declaration = access.resolve();
            if (declaration.isField()) {
                return Optional.of(new ResolvedField(declaration.getName()));
            }
        } catch (RuntimeException ignored) {
        }
        return Optional.empty();
    }

    private static void addEntity(Node node, JavaEntityType type, String simpleName, String qualifiedName,
                                  Path sourceFile, String product, ExtractionIndex index,
                                  ProductSignature commonSignature, List<EntityDraft> entityDrafts) {
        BlockContext context = index.findContext(sourceFile, startLine(node))
                .orElseGet(() -> BlockContext.common(commonSignature));
        entityDrafts.add(EntityDraft.from(node, type, simpleName, qualifiedName, sourceFile, product, context));
    }

    private static void collectRelations(CompilationUnit unit, Path sourceFile, String product, ExtractionIndex index,
                                         ProductSignature commonSignature,
                                         List<RelationDraft> relationDrafts) {
        for (ClassOrInterfaceDeclaration declaration : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            for (ClassOrInterfaceType type : declaration.getExtendedTypes()) {
                addRelation(type, JavaRelationType.EXTENDS, type.toString(), sourceFile, product,
                        index, commonSignature, relationDrafts);
            }
            for (ClassOrInterfaceType type : declaration.getImplementedTypes()) {
                addRelation(type, JavaRelationType.IMPLEMENTS, type.toString(), sourceFile, product,
                        index, commonSignature, relationDrafts);
            }
        }

        for (Type type : unit.findAll(Type.class)) {
            if (type.isVoidType()) {
                continue;
            }
            addRelation(type, JavaRelationType.TYPE_REFERENCE, type.toString(), sourceFile, product,
                    index, commonSignature, relationDrafts);
        }
        for (ObjectCreationExpr creation : unit.findAll(ObjectCreationExpr.class)) {
            addRelation(creation, JavaRelationType.CONSTRUCTOR_CALL, creation.getType().toString(),
                    sourceFile, product, index, commonSignature, relationDrafts);
        }
        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            addRelation(call, JavaRelationType.METHOD_CALL, call.getNameAsString(), sourceFile, product,
                    index, commonSignature, relationDrafts);
        }
        for (ExplicitConstructorInvocationStmt call : unit.findAll(ExplicitConstructorInvocationStmt.class)) {
            addRelation(call, JavaRelationType.CONSTRUCTOR_CALL, call.isThis() ? "this" : "super",
                    sourceFile, product, index, commonSignature, relationDrafts);
        }
        for (CastExpr cast : unit.findAll(CastExpr.class)) {
            addRelation(cast.getType(), JavaRelationType.TYPE_REFERENCE, cast.getType().toString(),
                    sourceFile, product, index, commonSignature, relationDrafts);
        }
    }

    private static void addRelation(Node node, JavaRelationType relationType, String targetText, Path sourceFile,
                                    String product, ExtractionIndex index, ProductSignature commonSignature,
                                    List<RelationDraft> relationDrafts) {
        BlockContext context = index.findContext(sourceFile, startLine(node))
                .orElseGet(() -> BlockContext.common(commonSignature));
        relationDrafts.add(new RelationDraft(
                node,
                sourceFile,
                List.of(product),
                startLine(node),
                relationType,
                targetText,
                null,
                false,
                context
        ));
    }

    private static EntityExtractionResult buildResult(List<EntityDraft> entityDrafts,
                                                      List<RelationDraft> relationDrafts,
                                                      List<ParseFailure> parseFailures,
                                                      int parsedFiles,
                                                      int projectionAttempts,
                                                      int successfulProjections) {
        List<EntityDraft> orderedEntityDrafts = mergeEntityDrafts(entityDrafts).stream()
                .sorted(ENTITY_ORDER)
                .toList();
        IdentityHashMap<Node, String> entityIdsByNode = new IdentityHashMap<>();
        IdentityHashMap<Node, String> fieldDeclarationEntityIds = new IdentityHashMap<>();
        Map<String, String> uniqueEntityIdsByName = uniqueEntityIdsByName(orderedEntityDrafts);
        List<JavaEntity> entities = new ArrayList<>();
        for (int index = 0; index < orderedEntityDrafts.size(); index++) {
            EntityDraft draft = orderedEntityDrafts.get(index);
            String entityId = "E" + (index + 1);
            entityIdsByNode.put(draft.node(), entityId);
            if (draft.entityType() == JavaEntityType.FIELD) {
                draft.node().findAncestor(FieldDeclaration.class)
                        .ifPresent(fieldDeclaration -> fieldDeclarationEntityIds.putIfAbsent(fieldDeclaration, entityId));
            }
            entities.add(draft.toEntity(entityId));
        }

        List<RelationDraft> orderedRelationDrafts = mergeRelationDrafts(relationDrafts).stream()
                .sorted(RELATION_ORDER)
                .toList();
        List<JavaRelation> relations = new ArrayList<>();
        for (int index = 0; index < orderedRelationDrafts.size(); index++) {
            RelationDraft draft = orderedRelationDrafts.get(index);
            String sourceEntityId = findSourceEntityId(draft.node(), entityIdsByNode, fieldDeclarationEntityIds);
            String targetEntityId = draft.targetEntityName() == null
                    ? uniqueEntityIdsByName.get(draft.targetText())
                    : uniqueEntityIdsByName.get(draft.targetEntityName());
            ResolutionStatus status = targetEntityId == null
                    ? (draft.resolvedByParser() ? ResolutionStatus.RESOLVED : ResolutionStatus.UNRESOLVED)
                    : ResolutionStatus.RESOLVED;
            relations.add(draft.toRelation("R" + (index + 1), sourceEntityId, targetEntityId, status));
        }

        return new EntityExtractionResult(entities, relations, parseFailures, parsedFiles,
                projectionAttempts, successfulProjections);
    }

    private static List<EntityDraft> mergeEntityDrafts(List<EntityDraft> drafts) {
        Map<EntityKey, EntityDraft> merged = new HashMap<>();
        for (EntityDraft draft : drafts) {
            merged.merge(EntityKey.from(draft), draft, EntityDraft::merge);
        }
        return new ArrayList<>(merged.values());
    }

    private static List<RelationDraft> mergeRelationDrafts(List<RelationDraft> drafts) {
        Map<RelationKey, RelationDraft> merged = new HashMap<>();
        for (RelationDraft draft : drafts) {
            merged.merge(RelationKey.from(draft), draft, RelationDraft::merge);
        }
        return new ArrayList<>(merged.values());
    }

    private static Map<String, String> uniqueEntityIdsByName(List<EntityDraft> drafts) {
        Map<String, List<Integer>> indexesByName = new HashMap<>();
        for (int index = 0; index < drafts.size(); index++) {
            indexesByName.computeIfAbsent(drafts.get(index).simpleName(), ignored -> new ArrayList<>()).add(index);
            if (drafts.get(index).qualifiedName() != null) {
                indexesByName.computeIfAbsent(drafts.get(index).qualifiedName(), ignored -> new ArrayList<>()).add(index);
            }
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : indexesByName.entrySet()) {
            if (entry.getValue().size() == 1) {
                result.put(entry.getKey(), "E" + (entry.getValue().get(0) + 1));
            }
        }
        return result;
    }

    private static String findSourceEntityId(Node node, IdentityHashMap<Node, String> entityIdsByNode,
                                             IdentityHashMap<Node, String> fieldDeclarationEntityIds) {
        Optional<Node> current = Optional.ofNullable(node);
        while (current.isPresent()) {
            String entityId = entityIdsByNode.get(current.get());
            if (entityId != null) {
                return entityId;
            }
            String fieldEntityId = fieldDeclarationEntityIds.get(current.get());
            if (fieldEntityId != null) {
                return fieldEntityId;
            }
            current = current.get().getParentNode();
        }
        return null;
    }

    private static List<ParseFailure> toParseFailures(Path sourceFile, String product, List<Problem> problems) {
        if (problems.isEmpty()) {
            return List.of(new ParseFailure(sourceFile, product, 0, "Parsing failed without a reported problem.", true));
        }
        return problems.stream()
                .map(problem -> new ParseFailure(
                        sourceFile,
                        product,
                        problem.getLocation()
                                .flatMap(location -> location.getBegin().getRange())
                                .map(range -> range.begin.line)
                                .orElse(0),
                        problem.getMessage(),
                        true
                ))
                .toList();
    }

    static int startLine(Node node) {
        return node.getRange().map(range -> range.begin.line).orElse(-1);
    }

    static int endLine(Node node) {
        return node.getRange().map(range -> range.end.line).orElse(-1);
    }

    private static String qualifiedName(CompilationUnit unit, Node declaration) {
        String packageName = unit.getPackageDeclaration()
                .map(packageDeclaration -> packageDeclaration.getNameAsString())
                .orElse("");
        List<String> names = new ArrayList<>();
        Optional<Node> current = Optional.of(declaration);
        while (current.isPresent()) {
            Node node = current.get();
            if (node instanceof ClassOrInterfaceDeclaration typeDeclaration) {
                names.add(0, typeDeclaration.getNameAsString());
            } else if (node instanceof MethodDeclaration methodDeclaration) {
                names.add(methodDeclaration.getNameAsString());
            } else if (node instanceof ConstructorDeclaration constructorDeclaration) {
                names.add(constructorDeclaration.getNameAsString());
            } else if (node instanceof VariableDeclarator variableDeclarator
                    && variableDeclarator.findAncestor(FieldDeclaration.class).isPresent()) {
                names.add(variableDeclarator.getNameAsString());
            }
            current = node.getParentNode();
        }
        if (names.isEmpty()) {
            return null;
        }
        String joined = String.join(".", names);
        return packageName.isBlank() ? joined : packageName + "." + joined;
    }

    private record BlockContext(
            ConditionalBlock block,
            String blockId,
            String groupId,
            ProductSignature signature
    ) {
        private static BlockContext common(ProductSignature commonSignature) {
            return new BlockContext(null, "COMMON", "COMMON", commonSignature);
        }
    }

    private record ExtractionIndex(List<BlockContext> contexts) {
        private static ExtractionIndex from(Collection<SignatureGroup> groups) {
            List<BlockContext> contexts = new ArrayList<>();
            for (SignatureGroup group : groups) {
                for (ConditionalBlock block : group.blocks()) {
                    contexts.add(new BlockContext(
                            block,
                            blockId(block),
                            group.groupId(),
                            group.canonicalSignature()
                    ));
                }
            }
            return new ExtractionIndex(contexts);
        }

        private Optional<BlockContext> findContext(Path sourceFile, int line) {
            Path normalizedSource = sourceFile.toAbsolutePath().normalize();
            return contexts.stream()
                    .filter(context -> context.block().filePath().toAbsolutePath().normalize().equals(normalizedSource))
                    .filter(context -> context.block().startLine() <= line && line <= context.block().endLine())
                    .max(Comparator.comparingInt((BlockContext context) -> context.block().nestingDepth())
                            .thenComparingInt(context -> context.block().startLine()));
        }

        private static String blockId(ConditionalBlock block) {
            return block.filePath().toString()
                    + ":"
                    + block.directiveType().name()
                    + ":"
                    + block.startLine()
                    + "-"
                    + block.endLine();
        }
    }

    private record EntityDraft(
            Node node,
            JavaEntityType entityType,
            String simpleName,
            String qualifiedName,
            Path sourceFile,
            List<String> observedProducts,
            int startLine,
            int endLine,
            BlockContext context
    ) {
        private static EntityDraft from(Node node, JavaEntityType entityType, String simpleName, String qualifiedName,
                                        Path sourceFile, String product, BlockContext context) {
            return new EntityDraft(
                    node,
                    entityType,
                    simpleName,
                    qualifiedName,
                    sourceFile,
                    List.of(product),
                    JavaEntityExtractor.startLine(node),
                    JavaEntityExtractor.endLine(node),
                    context
            );
        }

        private String sortName() {
            return qualifiedName == null ? simpleName : qualifiedName;
        }

        private String stableName() {
            return qualifiedName == null ? simpleName : qualifiedName;
        }

        private JavaEntity toEntity(String entityId) {
            return new JavaEntity(
                    entityId,
                    entityType,
                    simpleName,
                    qualifiedName,
                    sourceFile,
                    startLine,
                    endLine,
                    context.blockId(),
                    context.groupId(),
                    context.signature(),
                    observedProducts,
                    qualifiedName == null ? ResolutionStatus.UNRESOLVED : ResolutionStatus.RESOLVED
            );
        }

        private EntityDraft merge(EntityDraft other) {
            TreeSet<String> products = new TreeSet<>(observedProducts);
            products.addAll(other.observedProducts);
            return new EntityDraft(node, entityType, simpleName, qualifiedName, sourceFile,
                    new ArrayList<>(products), startLine, endLine, context);
        }
    }

    private record RelationDraft(
            Node node,
            Path sourceFile,
            List<String> observedProducts,
            int sourceLine,
            JavaRelationType relationType,
            String targetText,
            String targetEntityName,
            boolean resolvedByParser,
            BlockContext context
    ) {
        private String sourceSortKey() {
            return sourceFile + ":" + sourceLine;
        }

        private JavaRelation toRelation(String relationId, String sourceEntityId, String targetEntityId,
                                        ResolutionStatus status) {
            return new JavaRelation(
                    relationId,
                    sourceEntityId,
                    targetEntityId,
                    targetText,
                    relationType,
                    sourceFile,
                    sourceLine,
                    context.blockId(),
                    context.groupId(),
                    context.signature(),
                    observedProducts,
                    status
            );
        }

        private RelationDraft merge(RelationDraft other) {
            TreeSet<String> products = new TreeSet<>(observedProducts);
            products.addAll(other.observedProducts);
            return new RelationDraft(node, sourceFile, new ArrayList<>(products), sourceLine,
                    relationType, targetText, targetEntityName, resolvedByParser || other.resolvedByParser, context);
        }
    }

    private record ResolvedField(String name) {
    }

    private record EntityKey(Path sourceFile, JavaEntityType entityType, String name, int startLine, int endLine) {
        private static EntityKey from(EntityDraft draft) {
            return new EntityKey(
                    draft.sourceFile(),
                    draft.entityType(),
                    draft.qualifiedName() == null ? draft.simpleName() : draft.qualifiedName(),
                    draft.startLine(),
                    draft.endLine()
            );
        }
    }

    private record RelationKey(Path sourceFile, int sourceLine, JavaRelationType relationType, String targetText,
                               String targetEntityName, String blockId) {
        private static RelationKey from(RelationDraft draft) {
            return new RelationKey(
                    draft.sourceFile(),
                    draft.sourceLine(),
                    draft.relationType(),
                    draft.targetText(),
                    draft.targetEntityName(),
                    draft.context().blockId()
            );
        }
    }
}
