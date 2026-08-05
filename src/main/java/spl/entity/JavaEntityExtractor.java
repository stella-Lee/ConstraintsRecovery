package spl.entity;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParseStart;
import com.github.javaparser.Problem;
import com.github.javaparser.Providers;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
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
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
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
    private static final Set<String> KNOWN_EXTERNAL_TYPES = Set.of(
            "Appendable", "ArrayList", "Boolean", "Byte", "Character", "Class", "Collection",
            "Comparable", "Dimension", "Double", "Exception", "Float", "HashMap", "HashSet",
            "Integer", "Iterable", "Iterator", "LinkedList", "List", "Long", "Map", "Number",
            "Object", "Override", "Runnable", "RuntimeException", "Set", "Short", "String",
            "StringBuilder", "Thread", "Throwable", "Vector", "Void"
    );

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
                    index, commonSignature, unit, relationDrafts);
        }
        for (NameExpr name : unit.findAll(NameExpr.class)) {
            Optional<ResolvedField> resolvedField = resolveField(name);
            if (resolvedField.isPresent() || isFieldReferenceCandidate(name, fieldsByName)) {
                addFieldReference(name, name.getNameAsString(), fieldsByName, resolvedField.orElse(null), sourceFile, product,
                        index, commonSignature, unit, relationDrafts);
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
                                          ProductSignature commonSignature, CompilationUnit unit,
                                          List<RelationDraft> relationDrafts) {
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
                "",
                -1,
                target == null ? null : target.stableName(),
                target == null ? null : target.key(),
                sourceEntityKeyFor(unit, sourceFile, node),
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
                        index, commonSignature, unit, relationDrafts);
            }
            for (ClassOrInterfaceType type : declaration.getImplementedTypes()) {
                addRelation(type, JavaRelationType.IMPLEMENTS, type.toString(), sourceFile, product,
                        index, commonSignature, unit, relationDrafts);
            }
        }

        for (Type type : unit.findAll(Type.class)) {
            if (type.isVoidType() || isSpecificInheritanceType(type)) {
                continue;
            }
            addRelation(type, JavaRelationType.TYPE_REFERENCE, type.toString(), sourceFile, product,
                    index, commonSignature, unit, relationDrafts);
        }
        for (ObjectCreationExpr creation : unit.findAll(ObjectCreationExpr.class)) {
            addRelation(creation, JavaRelationType.CONSTRUCTOR_CALL, creation.getType().toString(),
                    sourceFile, product, index, commonSignature, unit, relationDrafts);
        }
        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            addRelation(call, JavaRelationType.METHOD_CALL, call.getNameAsString(), sourceFile, product,
                    index, commonSignature, unit, relationDrafts);
        }
        for (ExplicitConstructorInvocationStmt call : unit.findAll(ExplicitConstructorInvocationStmt.class)) {
            addRelation(call, JavaRelationType.CONSTRUCTOR_CALL, call.isThis() ? "this" : "super",
                    sourceFile, product, index, commonSignature, unit, relationDrafts);
        }
        for (CastExpr cast : unit.findAll(CastExpr.class)) {
            addRelation(cast.getType(), JavaRelationType.TYPE_REFERENCE, cast.getType().toString(),
                    sourceFile, product, index, commonSignature, unit, relationDrafts);
        }
    }

    private static void addRelation(Node node, JavaRelationType relationType, String targetText, Path sourceFile,
                                    String product, ExtractionIndex index, ProductSignature commonSignature,
                                    CompilationUnit unit, List<RelationDraft> relationDrafts) {
        BlockContext context = index.findContext(sourceFile, startLine(node))
                .orElseGet(() -> BlockContext.common(commonSignature));
        boolean resolvedByParser = parserCanResolve(node, relationType);
        relationDrafts.add(new RelationDraft(
                node,
                sourceFile,
                List.of(product),
                startLine(node),
                relationType,
                targetText,
                targetDeclaringType(node, relationType),
                argumentCount(node),
                null,
                null,
                sourceEntityKeyFor(unit, sourceFile, node),
                resolvedByParser,
                context
        ));
    }

    private static String targetDeclaringType(Node node, JavaRelationType relationType) {
        try {
            if (relationType == JavaRelationType.METHOD_CALL && node instanceof MethodCallExpr call) {
                return call.resolve().declaringType().getQualifiedName();
            }
            if (relationType == JavaRelationType.CONSTRUCTOR_CALL && node instanceof ObjectCreationExpr creation) {
                return creation.resolve().declaringType().getQualifiedName();
            }
        } catch (RuntimeException ignored) {
        }
        if (relationType == JavaRelationType.CONSTRUCTOR_CALL && node instanceof ObjectCreationExpr creation) {
            return creation.getType().getNameAsString();
        }
        return "";
    }

    private static int argumentCount(Node node) {
        if (node instanceof MethodCallExpr call) {
            return call.getArguments().size();
        }
        if (node instanceof ObjectCreationExpr creation) {
            return creation.getArguments().size();
        }
        if (node instanceof ExplicitConstructorInvocationStmt call) {
            return call.getArguments().size();
        }
        return -1;
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
        Map<EntityKey, String> entityIdsByKey = new HashMap<>();
        List<JavaEntity> entities = new ArrayList<>();
        for (int index = 0; index < orderedEntityDrafts.size(); index++) {
            EntityDraft draft = orderedEntityDrafts.get(index);
            String entityId = "E" + (index + 1);
            entityIdsByKey.put(draft.key(), entityId);
            entities.add(draft.toEntity(entityId));
        }
        EntityIndex entityIndex = EntityIndex.from(orderedEntityDrafts, entityIdsByKey);

        List<RelationDraft> orderedRelationDrafts = mergeRelationDrafts(relationDrafts).stream()
                .sorted(RELATION_ORDER)
                .toList();
        List<JavaRelation> relations = new ArrayList<>();
        for (int index = 0; index < orderedRelationDrafts.size(); index++) {
            RelationDraft draft = orderedRelationDrafts.get(index);
            String sourceEntityId = draft.sourceEntityKey() == null ? null : entityIdsByKey.get(draft.sourceEntityKey());
            TargetResolution targetResolution = entityIndex.resolve(draft, sourceEntityId);
            relations.add(draft.toRelation("R" + (index + 1), sourceEntityId,
                    targetResolution.targetEntityId(), targetResolution.status()));
        }

        return new EntityExtractionResult(entities, relations, parseFailures, parsedFiles,
                projectionAttempts, successfulProjections);
    }

    private static List<EntityDraft> mergeEntityDrafts(List<EntityDraft> drafts) {
        Map<EntityKey, EntityDraft> merged = new HashMap<>();
        for (EntityDraft draft : drafts) {
            merged.merge(draft.key(), draft, EntityDraft::merge);
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

    private static boolean isSpecificInheritanceType(Type type) {
        return type.findAncestor(ClassOrInterfaceDeclaration.class)
                .filter(declaration -> declaration.getExtendedTypes().stream().anyMatch(extended -> extended == type)
                        || declaration.getImplementedTypes().stream().anyMatch(implemented -> implemented == type))
                .isPresent();
    }

    private static boolean parserCanResolve(Node node, JavaRelationType relationType) {
        try {
            if (relationType == JavaRelationType.METHOD_CALL && node instanceof MethodCallExpr call) {
                ResolvedMethodDeclaration ignored = call.resolve();
                return true;
            }
            if (relationType == JavaRelationType.CONSTRUCTOR_CALL && node instanceof ObjectCreationExpr creation) {
                ResolvedConstructorDeclaration ignored = creation.resolve();
                return true;
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    private static EntityKey sourceEntityKeyFor(CompilationUnit unit, Path sourceFile, Node relationNode) {
        Optional<Node> current = Optional.ofNullable(relationNode);
        while (current.isPresent()) {
            Node node = current.get();
            if (node instanceof MethodDeclaration declaration) {
                return EntityKey.forMethod(sourceFile, qualifiedName(unit, declaration),
                        declaringTypeName(unit, declaration), declaration.getNameAsString(),
                        parameterSignature(declaration), startLine(declaration));
            }
            if (node instanceof ConstructorDeclaration declaration) {
                return EntityKey.forConstructor(sourceFile, qualifiedName(unit, declaration),
                        declaringTypeName(unit, declaration), declaration.getNameAsString(),
                        parameterSignature(declaration), startLine(declaration));
            }
            if (node instanceof VariableDeclarator variable
                    && variable.findAncestor(FieldDeclaration.class).isPresent()) {
                return EntityKey.forField(sourceFile, qualifiedName(unit, variable),
                        declaringTypeName(unit, variable), variable.getNameAsString(), startLine(variable));
            }
            if (node instanceof FieldDeclaration fieldDeclaration && !fieldDeclaration.getVariables().isEmpty()) {
                VariableDeclarator variable = fieldDeclaration.getVariable(0);
                return EntityKey.forField(sourceFile, qualifiedName(unit, variable),
                        declaringTypeName(unit, variable), variable.getNameAsString(), startLine(variable));
            }
            if (node instanceof ClassOrInterfaceDeclaration declaration) {
                return EntityKey.forType(sourceFile,
                        declaration.isInterface() ? JavaEntityType.INTERFACE : JavaEntityType.CLASS,
                        qualifiedName(unit, declaration), declaration.getNameAsString(), startLine(declaration));
            }
            current = node.getParentNode();
        }
        return null;
    }

    private static String declaringTypeName(CompilationUnit unit, Node node) {
        return node.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(declaration -> qualifiedName(unit, declaration))
                .orElse("");
    }

    private static String parameterSignature(MethodDeclaration declaration) {
        return declaration.getParameters().stream()
                .map(parameter -> canonicalTypeName(parameter.getType().toString()))
                .collect(Collectors.joining(","));
    }

    private static String parameterSignature(ConstructorDeclaration declaration) {
        return declaration.getParameters().stream()
                .map(parameter -> canonicalTypeName(parameter.getType().toString()))
                .collect(Collectors.joining(","));
    }

    private static String canonicalTypeName(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").replaceAll("<.*>", "<>");
    }

    private static String canonicalTargetName(String text) {
        if (text == null) {
            return "";
        }
        String canonical = text.replaceAll("\\s+", "");
        int genericStart = canonical.indexOf('<');
        if (genericStart >= 0) {
            canonical = canonical.substring(0, genericStart);
        }
        int arrayStart = canonical.indexOf('[');
        if (arrayStart >= 0) {
            canonical = canonical.substring(0, arrayStart);
        }
        return canonical;
    }

    private static String simpleName(String name) {
        String canonical = canonicalTargetName(name);
        int dot = canonical.lastIndexOf('.');
        return dot < 0 ? canonical : canonical.substring(dot + 1);
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
            BlockContext context,
            EntityKey key
    ) {
        private static EntityDraft from(Node node, JavaEntityType entityType, String simpleName, String qualifiedName,
                                        Path sourceFile, String product, BlockContext context) {
            EntityKey key = EntityKey.from(sourceFile, entityType, simpleName, qualifiedName, node);
            return new EntityDraft(
                    node,
                    entityType,
                    simpleName,
                    qualifiedName,
                    sourceFile,
                    List.of(product),
                    JavaEntityExtractor.startLine(node),
                    JavaEntityExtractor.endLine(node),
                    context,
                    key
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
                    qualifiedName == null ? ResolutionStatus.UNRESOLVED : ResolutionStatus.RESOLVED_INTERNAL
            );
        }

        private EntityDraft merge(EntityDraft other) {
            TreeSet<String> products = new TreeSet<>(observedProducts);
            products.addAll(other.observedProducts);
            int mergedEndLine = Math.max(endLine, other.endLine);
            return new EntityDraft(node, entityType, simpleName, qualifiedName, sourceFile,
                    new ArrayList<>(products), startLine, mergedEndLine, context, key);
        }
    }

    private record RelationDraft(
            Node node,
            Path sourceFile,
            List<String> observedProducts,
            int sourceLine,
            JavaRelationType relationType,
            String targetText,
            String targetDeclaringType,
            int argumentCount,
            String targetEntityName,
            EntityKey targetEntityKey,
            EntityKey sourceEntityKey,
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
                    relationType, targetText, targetDeclaringType, argumentCount, targetEntityName,
                    targetEntityKey == null ? other.targetEntityKey : targetEntityKey,
                    sourceEntityKey == null ? other.sourceEntityKey : sourceEntityKey,
                    resolvedByParser || other.resolvedByParser, context);
        }
    }

    private record ResolvedField(String name) {
    }

    private record EntityKey(Path sourceFile, JavaEntityType entityType, String declaringType,
                             String name, String parameterSignature, int startLine) {
        private static EntityKey from(Path sourceFile, JavaEntityType entityType, String simpleName,
                                      String qualifiedName, Node node) {
            if (node instanceof MethodDeclaration declaration) {
                return forMethod(sourceFile, qualifiedName, declaringTypeName(node.findCompilationUnit().orElseThrow(), declaration),
                        declaration.getNameAsString(), JavaEntityExtractor.parameterSignature(declaration),
                        JavaEntityExtractor.startLine(declaration));
            }
            if (node instanceof ConstructorDeclaration declaration) {
                return forConstructor(sourceFile, qualifiedName,
                        declaringTypeName(node.findCompilationUnit().orElseThrow(), declaration),
                        declaration.getNameAsString(), JavaEntityExtractor.parameterSignature(declaration),
                        JavaEntityExtractor.startLine(declaration));
            }
            if (node instanceof VariableDeclarator variable
                    && variable.findAncestor(FieldDeclaration.class).isPresent()) {
                return forField(sourceFile, qualifiedName,
                        declaringTypeName(node.findCompilationUnit().orElseThrow(), variable),
                        variable.getNameAsString(), JavaEntityExtractor.startLine(variable));
            }
            return forType(sourceFile, entityType, qualifiedName, simpleName, JavaEntityExtractor.startLine(node));
        }

        private static EntityKey forType(Path sourceFile, JavaEntityType entityType, String qualifiedName,
                                         String simpleName, int startLine) {
            return new EntityKey(sourceFile, entityType, "", qualifiedName == null ? simpleName : qualifiedName,
                    "", startLine);
        }

        private static EntityKey forMethod(Path sourceFile, String qualifiedName, String declaringType,
                                           String methodName, String parameterSignature, int startLine) {
            return new EntityKey(sourceFile, JavaEntityType.METHOD, declaringType,
                    qualifiedName == null ? methodName : qualifiedName, parameterSignature, startLine);
        }

        private static EntityKey forConstructor(Path sourceFile, String qualifiedName, String declaringType,
                                                String constructorName, String parameterSignature, int startLine) {
            return new EntityKey(sourceFile, JavaEntityType.CONSTRUCTOR, declaringType,
                    qualifiedName == null ? constructorName : qualifiedName, parameterSignature, startLine);
        }

        private static EntityKey forField(Path sourceFile, String qualifiedName, String declaringType,
                                          String fieldName, int startLine) {
            return new EntityKey(sourceFile, JavaEntityType.FIELD, declaringType,
                    qualifiedName == null ? fieldName : qualifiedName, "", startLine);
        }
    }

    private record RelationKey(Path sourceFile, int sourceLine, JavaRelationType relationType, String targetText,
                               String targetEntityName, EntityKey targetEntityKey, EntityKey sourceEntityKey,
                               String blockId) {
        private static RelationKey from(RelationDraft draft) {
            return new RelationKey(
                    draft.sourceFile(),
                    draft.sourceLine(),
                    draft.relationType(),
                    draft.targetText(),
                    draft.targetEntityName(),
                    draft.targetEntityKey(),
                    draft.sourceEntityKey(),
                    draft.context().blockId()
            );
        }
    }

    private record TargetResolution(String targetEntityId, ResolutionStatus status) {
    }

    private static final class EntityIndex {
        private final Map<EntityKey, String> entityIdsByKey;
        private final Map<String, List<String>> typesByName = new HashMap<>();
        private final Map<String, List<String>> methodsByName = new HashMap<>();
        private final Map<String, List<String>> methodsByOwnerNameArity = new HashMap<>();
        private final Map<String, List<String>> constructorsByType = new HashMap<>();
        private final Map<String, List<String>> constructorsByTypeArity = new HashMap<>();
        private final Map<String, List<String>> fieldsByName = new HashMap<>();
        private final Map<String, EntityDraft> draftsById = new HashMap<>();

        private EntityIndex(Map<EntityKey, String> entityIdsByKey) {
            this.entityIdsByKey = entityIdsByKey;
        }

        private static EntityIndex from(List<EntityDraft> drafts, Map<EntityKey, String> entityIdsByKey) {
            EntityIndex index = new EntityIndex(entityIdsByKey);
            for (EntityDraft draft : drafts) {
                String id = entityIdsByKey.get(draft.key());
                index.draftsById.put(id, draft);
                if (draft.entityType() == JavaEntityType.CLASS || draft.entityType() == JavaEntityType.INTERFACE) {
                    index.add(index.typesByName, draft.simpleName(), id);
                    index.add(index.typesByName, draft.qualifiedName(), id);
                } else if (draft.entityType() == JavaEntityType.METHOD) {
                    index.add(index.methodsByName, draft.simpleName(), id);
                    index.add(index.methodsByName, draft.key().declaringType() + "#" + draft.simpleName()
                            + "(" + draft.key().parameterSignature() + ")", id);
                    index.add(index.methodsByOwnerNameArity,
                            methodKey(draft.key().declaringType(), draft.simpleName(),
                                    parameterCount(draft.key().parameterSignature())), id);
                    index.add(index.methodsByOwnerNameArity,
                            methodKey(simpleName(draft.key().declaringType()), draft.simpleName(),
                                    parameterCount(draft.key().parameterSignature())), id);
                } else if (draft.entityType() == JavaEntityType.CONSTRUCTOR) {
                    index.add(index.constructorsByType, simpleName(draft.key().declaringType()), id);
                    index.add(index.constructorsByType, draft.key().declaringType(), id);
                    index.add(index.constructorsByTypeArity,
                            constructorKey(simpleName(draft.key().declaringType()),
                                    parameterCount(draft.key().parameterSignature())), id);
                    index.add(index.constructorsByTypeArity,
                            constructorKey(draft.key().declaringType(),
                                    parameterCount(draft.key().parameterSignature())), id);
                } else if (draft.entityType() == JavaEntityType.FIELD) {
                    index.add(index.fieldsByName, draft.simpleName(), id);
                    index.add(index.fieldsByName, draft.key().declaringType() + "#" + draft.simpleName(), id);
                }
            }
            return index;
        }

        private void add(Map<String, List<String>> map, String key, String entityId) {
            if (key != null && !key.isBlank()) {
                map.computeIfAbsent(canonicalTargetName(key), ignored -> new ArrayList<>()).add(entityId);
            }
        }

        private TargetResolution resolve(RelationDraft draft, String sourceEntityId) {
            if (draft.targetEntityKey() != null) {
                String id = entityIdsByKey.get(draft.targetEntityKey());
                if (id != null) {
                    return new TargetResolution(id, ResolutionStatus.RESOLVED_INTERNAL);
                }
            }
            return switch (draft.relationType()) {
                case EXTENDS, IMPLEMENTS, TYPE_REFERENCE -> resolveType(draft);
                case METHOD_CALL -> resolveMethod(draft);
                case CONSTRUCTOR_CALL -> resolveConstructor(draft, sourceEntityId);
                case FIELD_REFERENCE -> resolveField(draft, sourceEntityId);
            };
        }

        private TargetResolution resolveType(RelationDraft draft) {
            List<String> matches = lookup(typesByName, draft.targetText());
            if (matches.size() == 1) {
                return new TargetResolution(matches.get(0), ResolutionStatus.RESOLVED_INTERNAL);
            }
            if (matches.size() > 1) {
                return new TargetResolution(null, ResolutionStatus.AMBIGUOUS);
            }
            return externalOrUnresolved(draft);
        }

        private TargetResolution resolveMethod(RelationDraft draft) {
            if (draft.argumentCount() >= 0 && draft.targetDeclaringType() != null && !draft.targetDeclaringType().isBlank()) {
                List<String> ownerMatches = lookup(methodsByOwnerNameArity,
                        methodKey(draft.targetDeclaringType(), draft.targetText(), draft.argumentCount()));
                if (ownerMatches.size() == 1) {
                    return new TargetResolution(ownerMatches.get(0), ResolutionStatus.RESOLVED_INTERNAL);
                }
                if (ownerMatches.size() > 1) {
                    return new TargetResolution(null, ResolutionStatus.AMBIGUOUS);
                }
                if (isKnownExternalTarget(draft.targetDeclaringType())) {
                    return new TargetResolution(null, ResolutionStatus.RESOLVED_EXTERNAL);
                }
            }
            List<String> matches = lookup(methodsByName, draft.targetText());
            if (matches.size() == 1) {
                return new TargetResolution(matches.get(0), ResolutionStatus.RESOLVED_INTERNAL);
            }
            if (matches.size() > 1) {
                return new TargetResolution(null, ResolutionStatus.AMBIGUOUS);
            }
            return externalOrUnresolved(draft);
        }

        private TargetResolution resolveConstructor(RelationDraft draft, String sourceEntityId) {
            String typeName = draft.targetText();
            if (("this".equals(typeName) || "super".equals(typeName)) && sourceEntityId != null) {
                EntityDraft source = draftsById.get(sourceEntityId);
                if (source != null) {
                    typeName = "this".equals(typeName) ? source.key().declaringType() : "";
                }
            }
            List<String> matches = lookup(constructorsByType, typeName);
            if (draft.argumentCount() >= 0) {
                List<String> arityMatches = lookup(constructorsByTypeArity,
                        constructorKey(typeName, draft.argumentCount()));
                if (arityMatches.size() == 1) {
                    return new TargetResolution(arityMatches.get(0), ResolutionStatus.RESOLVED_INTERNAL);
                }
                if (arityMatches.size() > 1) {
                    return new TargetResolution(null, ResolutionStatus.AMBIGUOUS);
                }
            }
            if (matches.size() == 1) {
                return new TargetResolution(matches.get(0), ResolutionStatus.RESOLVED_INTERNAL);
            }
            if (matches.size() > 1) {
                return new TargetResolution(null, ResolutionStatus.AMBIGUOUS);
            }
            return externalOrUnresolved(draft);
        }

        private TargetResolution resolveField(RelationDraft draft, String sourceEntityId) {
            if (sourceEntityId != null) {
                EntityDraft source = draftsById.get(sourceEntityId);
                if (source != null) {
                    List<String> declaringMatches = lookup(fieldsByName,
                            source.key().declaringType() + "#" + draft.targetText());
                    if (declaringMatches.size() == 1) {
                        return new TargetResolution(declaringMatches.get(0), ResolutionStatus.RESOLVED_INTERNAL);
                    }
                }
            }
            List<String> matches = lookup(fieldsByName, draft.targetText());
            if (matches.size() == 1) {
                return new TargetResolution(matches.get(0), ResolutionStatus.RESOLVED_INTERNAL);
            }
            if (matches.size() > 1) {
                return new TargetResolution(null, ResolutionStatus.AMBIGUOUS);
            }
            return externalOrUnresolved(draft);
        }

        private List<String> lookup(Map<String, List<String>> map, String key) {
            String canonical = canonicalTargetName(key);
            List<String> exact = map.get(canonical);
            if (exact != null) {
                return exact.stream().distinct().sorted().toList();
            }
            return map.getOrDefault(simpleName(canonical), List.of()).stream().distinct().sorted().toList();
        }

        private static String methodKey(String ownerType, String methodName, int argumentCount) {
            return canonicalTargetName(ownerType) + "#" + methodName + "#" + argumentCount;
        }

        private static String constructorKey(String typeName, int argumentCount) {
            return canonicalTargetName(typeName) + "#" + argumentCount;
        }

        private static int parameterCount(String parameterSignature) {
            if (parameterSignature == null || parameterSignature.isBlank()) {
                return 0;
            }
            return parameterSignature.split(",", -1).length;
        }

        private TargetResolution externalOrUnresolved(RelationDraft draft) {
            if (draft.resolvedByParser() || isKnownExternalTarget(draft.targetText())) {
                return new TargetResolution(null, ResolutionStatus.RESOLVED_EXTERNAL);
            }
            return new TargetResolution(null, ResolutionStatus.UNRESOLVED);
        }

        private boolean isKnownExternalTarget(String targetText) {
            String target = canonicalTargetName(targetText);
            return target.startsWith("java.")
                    || target.startsWith("javax.")
                    || target.startsWith("com.")
                    || target.startsWith("org.")
                    || KNOWN_EXTERNAL_TYPES.contains(simpleName(target));
        }
    }
}
