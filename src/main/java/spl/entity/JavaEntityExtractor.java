package spl.entity;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParseStart;
import com.github.javaparser.Problem;
import com.github.javaparser.Position;
import com.github.javaparser.Providers;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.DataKey;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.typesolvers.MemoryTypeSolver;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.function.Supplier;

public final class JavaEntityExtractor {
    private static final DataKey<ProductProjection> PROJECTION = new DataKey<>() {};
    private static final Comparator<EntityDraft> ENTITY_ORDER =
            Comparator.comparing((EntityDraft draft) -> draft.sourceFile().toString())
                    .thenComparingInt(EntityDraft::startLine)
                    .thenComparing(draft -> draft.entityType().name())
                    .thenComparing(EntityDraft::sortName)
                    .thenComparing(draft -> draft.key().parameterSignature())
                    .thenComparingInt(draft -> draft.key().position().column);
    private static final Comparator<RelationDraft> RELATION_ORDER =
            Comparator.comparing((RelationDraft draft) -> draft.sourceFile().toString())
                    .thenComparingInt(RelationDraft::sourceLine)
                    .thenComparing(draft -> draft.relationType().name())
                    .thenComparing(RelationDraft::targetText)
                    .thenComparingInt(RelationDraft::sourceColumn)
                    .thenComparing(draft -> Objects.toString(draft.targetEntityKey(), ""))
                    .thenComparing(draft -> Objects.toString(draft.sourceEntityKey(), ""))
                    .thenComparing(RelationDraft::external);
    private static final Set<String> KNOWN_EXTERNAL_TYPES = Set.of(
            "Appendable", "ArrayList", "Boolean", "Byte", "Character", "Class", "Collection",
            "Comparable", "Dimension", "Double", "Exception", "Float", "HashMap", "HashSet",
            "Integer", "Iterable", "Iterator", "LinkedList", "List", "Long", "Map", "Number",
            "Object", "Override", "Runnable", "RuntimeException", "Set", "Short", "String",
            "StringBuilder", "Thread", "Throwable", "Vector", "Void"
    );

    private final ProductProjectionGenerator projectionGenerator = new ProductProjectionGenerator();

    public EntityExtractionResult extract(Collection<SignatureGroup> groups, Collection<Path> assetFiles) {
        return extract(groups, assetFiles, completeProductSignature(groups));
    }

    public EntityExtractionResult extract(Collection<SignatureGroup> groups, Collection<Path> assetFiles,
                                          ProductSignature commonSignature) {
        Objects.requireNonNull(groups, "groups");
        Objects.requireNonNull(assetFiles, "assetFiles");
        Objects.requireNonNull(commonSignature, "commonSignature");

        ExtractionIndex index = ExtractionIndex.from(groups);
        List<String> productUniverse = commonSignature.productIds();
        List<EntityDraft> entityDrafts = new ArrayList<>();
        List<RelationDraft> relationDrafts = new ArrayList<>();
        List<ParseFailure> parseFailures = new ArrayList<>();
        Set<Path> parsedFiles = new TreeSet<>(Comparator.comparing(Path::toString));
        int projectionAttempts = 0;
        int successfulProjections = 0;

        Map<Path, String> sources = new LinkedHashMap<>();
        for (Path assetFile : assetFiles.stream().sorted().toList()) {
            Path sourceFile = assetFile.toAbsolutePath().normalize();
            try {
                sources.put(sourceFile, Files.readString(sourceFile, StandardCharsets.UTF_8));
            } catch (IOException exception) {
                parseFailures.add(new ParseFailure(sourceFile, "<all>", 0, exception.getMessage(), true));
            }
        }

        for (String product : productUniverse) {
            MemoryTypeSolver projectedTypes = new MemoryTypeSolver();
            CombinedTypeSolver typeSolver = new CombinedTypeSolver(projectedTypes, new ReflectionTypeSolver());
            JavaParser parser = new JavaParser(new ParserConfiguration()
                    .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
                    .setSymbolResolver(new JavaSymbolSolver(typeSolver)));
            Map<Path, CompilationUnit> units = new LinkedHashMap<>();
            for (var asset : sources.entrySet()) {
                Path sourceFile = asset.getKey();
                projectionAttempts++;
                ProductProjection projection = projectionGenerator.generate(sourceFile, asset.getValue(), product);
                ParseResult<CompilationUnit> parsed = parser.parse(
                        ParseStart.COMPILATION_UNIT, Providers.provider(projection.source()));
                if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
                    parseFailures.addAll(toParseFailures(sourceFile, product, parsed.getProblems()));
                    continue;
                }
                CompilationUnit unit = parsed.getResult().orElseThrow().setStorage(sourceFile);
                unit.setData(PROJECTION, projection);
                units.put(sourceFile, unit);
                successfulProjections++;
                parsedFiles.add(sourceFile);
            }

            Map<String, List<TypeDeclaration<?>>> byType = units.values().stream()
                    .flatMap(unit -> unit.findAll(TypeDeclaration.class).stream())
                    .<TypeDeclaration<?>>map(type -> type)
                    .filter(type -> type.findAncestor(LocalClassDeclarationStmt.class).isEmpty())
                    .filter(type -> type.getFullyQualifiedName().isPresent())
                    .collect(Collectors.groupingBy(type -> type.getFullyQualifiedName().orElseThrow()));
            byType.forEach((name, declarations) -> {
                if (declarations.size() == 1) {
                    projectedTypes.addDeclaration(name, JavaParserFacade.get(typeSolver)
                            .getTypeDeclaration(declarations.get(0)));
                }
            });

            for (var asset : units.entrySet()) {
                collectFromCompilationUnit(asset.getValue(), asset.getKey(), product, index,
                        commonSignature, entityDrafts, relationDrafts);
                collectFieldReferences(asset.getValue(), asset.getKey(), product, index, commonSignature, relationDrafts);
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
                                               List<RelationDraft> relationDrafts) {
        for (FieldAccessExpr access : unit.findAll(FieldAccessExpr.class)) {
            addResolvedFieldReference(access, access.getNameAsString(), access::resolve,
                    true, sourceFile, product, index, commonSignature, relationDrafts);
        }
        Set<String> declaredFields = unit.findAll(FieldDeclaration.class).stream()
                .flatMap(field -> field.getVariables().stream())
                .map(VariableDeclarator::getNameAsString)
                .collect(Collectors.toSet());
        for (NameExpr name : unit.findAll(NameExpr.class)) {
            addResolvedFieldReference(name, name.getNameAsString(), name::resolve,
                    declaredFields.contains(name.getNameAsString()), sourceFile, product, index,
                    commonSignature, relationDrafts);
        }
    }

    private static void addResolvedFieldReference(Node node, String name,
                                                  Supplier<ResolvedValueDeclaration> resolver,
                                                  boolean auditUnresolved, Path sourceFile, String product,
                                                  ExtractionIndex index, ProductSignature commonSignature,
                                                  List<RelationDraft> relationDrafts) {
        ResolvedValueDeclaration declaration = resolveDeclaration(resolver);
        if (declaration == null ? !auditUnresolved : !declaration.isField()) {
            return;
        }
        addRelation(node, JavaRelationType.FIELD_REFERENCE, name, sourceFile, product, index, commonSignature,
                relationDrafts, declaration);
    }

    private static <T extends ResolvedDeclaration> T resolveDeclaration(Supplier<T> resolver) {
        try {
            return resolver.get();
        } catch (RuntimeException ignored) {
            return null;
        }
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
            if (type.isVoidType() || isSpecificInheritanceType(type)) {
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
        var declaration = switch (relationType) {
            case METHOD_CALL -> resolveDeclaration(((MethodCallExpr) node)::resolve);
            case CONSTRUCTOR_CALL -> node instanceof ObjectCreationExpr creation
                    ? resolveDeclaration(creation::resolve)
                    : resolveDeclaration(((ExplicitConstructorInvocationStmt) node)::resolve);
            case EXTENDS, IMPLEMENTS -> resolveDeclaration(() -> ((ClassOrInterfaceType) node)
                    .resolve().asReferenceType().getTypeDeclaration().orElseThrow());
            default -> null;
        };
        addRelation(node, relationType, targetText, sourceFile, product, index, commonSignature,
                relationDrafts, declaration);
    }

    private static void addRelation(Node node, JavaRelationType relationType, String targetText, Path sourceFile,
                                    String product, ExtractionIndex index, ProductSignature commonSignature,
                                    List<RelationDraft> relationDrafts,
                                    ResolvedDeclaration declaration) {
        BlockContext context = index.findContext(sourceFile, startLine(node))
                .orElseGet(() -> BlockContext.common(commonSignature));
        EntityKey targetKey = declaration == null ? null : declaration.toAst()
                .map(target -> {
                    Node actualTarget = target instanceof FieldDeclaration field
                            ? field.getVariables().stream()
                                    .filter(variable -> variable.getNameAsString().equals(declaration.getName()))
                                    .findFirst().orElse(null)
                            : target;
                    if (actualTarget == null) {
                        return null;
                    }
                    return actualTarget.findCompilationUnit().flatMap(CompilationUnit::getStorage)
                            .map(storage -> sourceEntityKeyFor(storage.getPath(), actualTarget))
                            .orElse(null);
                }).orElse(null);
        boolean external = declaration != null && declaration.toAst().isEmpty()
                && !(declaration instanceof ResolvedConstructorDeclaration constructor
                && constructor.declaringType().toAst().isPresent());
        Node location = node instanceof MethodCallExpr call ? call.getName()
                : node instanceof FieldAccessExpr field ? field.getName() : node;
        int column = location.getBegin().map(position -> position.column).orElse(0);
        relationDrafts.add(new RelationDraft(sourceFile, List.of(product), startLine(node), column, relationType,
                targetText, targetKey, sourceEntityKeyFor(sourceFile, node), external, context));
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
            TargetResolution targetResolution = entityIndex.resolve(draft);
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

    private static EntityKey sourceEntityKeyFor(Path sourceFile, Node relationNode) {
        for (Node node : enclosingDeclarations(relationNode)) {
            if (node instanceof MethodDeclaration method) {
                return EntityKey.from(sourceFile, JavaEntityType.METHOD, method.getNameAsString(), method);
            }
            if (node instanceof ConstructorDeclaration constructor) {
                return EntityKey.from(sourceFile, JavaEntityType.CONSTRUCTOR, constructor.getNameAsString(), constructor);
            }
            if (node instanceof VariableDeclarator field) {
                return EntityKey.from(sourceFile, JavaEntityType.FIELD, field.getNameAsString(), field);
            }
            if (node instanceof FieldDeclaration field && !field.getVariables().isEmpty()) {
                VariableDeclarator variable = field.getVariable(0);
                return EntityKey.from(sourceFile, JavaEntityType.FIELD, variable.getNameAsString(), variable);
            }
            if (node instanceof ClassOrInterfaceDeclaration type) {
                JavaEntityType kind = type.isInterface() ? JavaEntityType.INTERFACE : JavaEntityType.CLASS;
                return EntityKey.from(sourceFile, kind, type.getNameAsString(), type);
            }
        }
        return null;
    }

    private static String declaringTypeName(Node member) {
        return enclosingDeclarations(member).stream().skip(1)
                .filter(node -> node instanceof TypeDeclaration<?> || isAnonymousType(node))
                .findFirst()
                .map(owner -> qualifiedName(owner.findCompilationUnit().orElseThrow(), owner))
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
        List<String> names = new ArrayList<>();
        for (Node node : enclosingDeclarations(declaration)) {
            if (node instanceof TypeDeclaration<?> type) {
                names.add(type.getNameAsString());
            } else if (node instanceof MethodDeclaration method) {
                names.add(method.getNameAsString());
            } else if (node instanceof ConstructorDeclaration constructor) {
                names.add(constructor.getNameAsString());
            } else if (node instanceof VariableDeclarator field) {
                names.add(field.getNameAsString());
            } else if (isAnonymousType(node)) {
                Position position = originalPosition(node);
                names.add("<anonymous@" + position.line + ":" + position.column + ">");
            }
        }
        if (names.isEmpty()) {
            return null;
        }
        Collections.reverse(names);
        unit.getPackageDeclaration().ifPresent(pkg -> names.add(0, pkg.getNameAsString()));
        return String.join(".", names);
    }

    private static List<Node> enclosingDeclarations(Node node) {
        List<Node> declarations = new ArrayList<>();
        Node child = null;
        for (Node current = node; current != null; current = current.getParentNode().orElse(null)) {
            if (current instanceof TypeDeclaration<?> || current instanceof MethodDeclaration
                    || current instanceof ConstructorDeclaration || current instanceof FieldDeclaration
                    || current instanceof VariableDeclarator variable
                    && variable.getParentNode().filter(FieldDeclaration.class::isInstance).isPresent()
                    || isAnonymousType(current) && (child == null || isAnonymousBodyMember(current, child))) {
                declarations.add(current);
            }
            child = current;
        }
        return declarations;
    }

    private static boolean isAnonymousType(Node node) {
        return node instanceof ObjectCreationExpr creation && creation.getAnonymousClassBody().isPresent()
                || node instanceof EnumConstantDeclaration constant && !constant.getClassBody().isEmpty();
    }

    private static boolean isAnonymousBodyMember(Node owner, Node member) {
        if (owner instanceof ObjectCreationExpr creation) {
            return creation.getAnonymousClassBody().orElseThrow().stream().anyMatch(node -> node == member);
        }
        return ((EnumConstantDeclaration) owner).getClassBody().stream().anyMatch(node -> node == member);
    }

    private static Position originalPosition(Node node) {
        Position position = node.getBegin().orElse(Position.HOME);
        return node.findCompilationUnit().flatMap(unit -> unit.findData(PROJECTION))
                .map(projection -> new Position(position.line, projection.originalColumn(position.line, position.column)))
                .orElse(position);
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
            JavaEntityType entityType,
            String simpleName,
            String qualifiedName,
            Path sourceFile,
            List<String> observedProducts,
            int startLine,
            int endLine,
            BlockContext context,
            EntityKey key,
            Map<String, String> declaredFieldTypes,
            String declaringTypeName
    ) {
        private static EntityDraft from(Node node, JavaEntityType entityType, String simpleName, String qualifiedName,
                                        Path sourceFile, String product, BlockContext context) {
            EntityKey key = EntityKey.from(sourceFile, entityType, simpleName, node);
            return new EntityDraft(
                    entityType,
                    simpleName,
                    qualifiedName,
                    sourceFile,
                    List.of(product),
                    JavaEntityExtractor.startLine(node),
                    JavaEntityExtractor.endLine(node),
                    context,
                    key,
                    declaredFieldType(node, entityType, product),
                    node instanceof TypeDeclaration<?> ? "" : JavaEntityExtractor.declaringTypeName(node)
            );
        }

        private static Map<String, String> declaredFieldType(Node node, JavaEntityType type, String product) {
            if (type != JavaEntityType.FIELD || !(node instanceof VariableDeclarator field)) {
                return Map.of();
            }
            try {
                var declaredType = field.getType().resolve();
                while (declaredType.isArray()) {
                    declaredType = declaredType.asArrayType().getComponentType();
                }
                if (declaredType.isReferenceType()) {
                    return Map.of(product, declaredType.asReferenceType().getQualifiedName());
                }
            } catch (RuntimeException ignored) {
                // A field with an unresolved type does not identify a provider.
            }
            return Map.of();
        }

        private String sortName() {
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
                    qualifiedName == null ? ResolutionStatus.UNRESOLVED : ResolutionStatus.RESOLVED_INTERNAL,
                    declaredFieldTypes,
                    declaringTypeName
            );
        }

        private EntityDraft merge(EntityDraft other) {
            TreeSet<String> products = new TreeSet<>(observedProducts);
            products.addAll(other.observedProducts);
            int mergedEndLine = Math.max(endLine, other.endLine);
            Map<String, String> fieldTypes = new LinkedHashMap<>(declaredFieldTypes);
            fieldTypes.putAll(other.declaredFieldTypes);
            return new EntityDraft(entityType, simpleName, qualifiedName, sourceFile,
                    new ArrayList<>(products), startLine, mergedEndLine, context, key, fieldTypes, declaringTypeName);
        }
    }

    private record RelationDraft(
            Path sourceFile,
            List<String> observedProducts,
            int sourceLine,
            int sourceColumn,
            JavaRelationType relationType,
            String targetText,
            EntityKey targetEntityKey,
            EntityKey sourceEntityKey,
            boolean external,
            BlockContext context
    ) {
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
                    status,
                    sourceColumn()
            );
        }

        private RelationDraft merge(RelationDraft other) {
            TreeSet<String> products = new TreeSet<>(observedProducts);
            products.addAll(other.observedProducts);
            return new RelationDraft(sourceFile, new ArrayList<>(products), sourceLine, sourceColumn,
                    relationType, targetText,
                    targetEntityKey == null ? other.targetEntityKey : targetEntityKey,
                    sourceEntityKey == null ? other.sourceEntityKey : sourceEntityKey,
                    external || other.external, context);
        }
    }

    private record EntityKey(Path sourceFile, JavaEntityType entityType, String name, String parameterSignature,
                             Position position, List<DeclarationScope> enclosingDeclarations) {
        private static EntityKey from(Path sourceFile, JavaEntityType type, String name, Node node) {
            String signature = switch (type) {
                case METHOD -> JavaEntityExtractor.parameterSignature((MethodDeclaration) node);
                case CONSTRUCTOR -> JavaEntityExtractor.parameterSignature((ConstructorDeclaration) node);
                default -> "";
            };
            List<DeclarationScope> scope = JavaEntityExtractor.enclosingDeclarations(node).stream().skip(1)
                    .filter(parent -> !(parent instanceof FieldDeclaration))
                    .map(DeclarationScope::from).toList();
            return new EntityKey(sourceFile, type, name, signature, declarationPosition(node), scope);
        }

        private static Position declarationPosition(Node node) {
            return originalPosition(node instanceof NodeWithSimpleName<?> declaration ? declaration.getName() : node);
        }
    }

    private record DeclarationScope(String kind, String name, String signature, Position anonymousPosition) {
        private static DeclarationScope from(Node node) {
            String kind = node.getClass().getSimpleName();
            if (isAnonymousType(node)) {
                return new DeclarationScope(kind, "", "", originalPosition(node));
            }
            String signature = node instanceof MethodDeclaration method ? parameterSignature(method)
                    : node instanceof ConstructorDeclaration constructor ? parameterSignature(constructor) : "";
            // A shared body can belong to different conditional headers for the same named declaration.
            return new DeclarationScope(kind, ((NodeWithSimpleName<?>) node).getNameAsString(), signature, null);
        }
    }

    private record RelationKey(Path sourceFile, int sourceLine, int sourceColumn, JavaRelationType relationType, String targetText,
                               EntityKey targetEntityKey, EntityKey sourceEntityKey,
                               boolean external, String blockId) {
        private static RelationKey from(RelationDraft draft) {
            return new RelationKey(
                    draft.sourceFile(),
                    draft.sourceLine(),
                    draft.relationType() == JavaRelationType.TYPE_REFERENCE ? 0 : draft.sourceColumn(),
                    draft.relationType(),
                    draft.targetText(),
                    draft.targetEntityKey(),
                    draft.sourceEntityKey(),
                    draft.external(),
                    draft.context().blockId()
            );
        }
    }

    private record TargetResolution(String targetEntityId, ResolutionStatus status) {
    }

    private static final class EntityIndex {
        private final Map<EntityKey, String> entityIdsByKey;
        private final Map<String, List<String>> typesByName = new HashMap<>();
        private EntityIndex(Map<EntityKey, String> entityIdsByKey) {
            this.entityIdsByKey = entityIdsByKey;
        }

        private static EntityIndex from(List<EntityDraft> drafts, Map<EntityKey, String> entityIdsByKey) {
            EntityIndex index = new EntityIndex(entityIdsByKey);
            for (EntityDraft draft : drafts) {
                String id = entityIdsByKey.get(draft.key());
                if (draft.entityType() == JavaEntityType.CLASS || draft.entityType() == JavaEntityType.INTERFACE) {
                    index.add(index.typesByName, draft.simpleName(), id);
                    index.add(index.typesByName, draft.qualifiedName(), id);
                }
            }
            return index;
        }

        private void add(Map<String, List<String>> map, String key, String entityId) {
            if (key != null && !key.isBlank()) {
                map.computeIfAbsent(canonicalTargetName(key), ignored -> new ArrayList<>()).add(entityId);
            }
        }

        private TargetResolution resolve(RelationDraft draft) {
            if (draft.targetEntityKey() != null) {
                String id = entityIdsByKey.get(draft.targetEntityKey());
                if (id != null) {
                    return new TargetResolution(id, ResolutionStatus.RESOLVED_INTERNAL);
                }
            }
            return switch (draft.relationType()) {
                case TYPE_REFERENCE -> resolveType(draft);
                case EXTENDS, IMPLEMENTS, METHOD_CALL, CONSTRUCTOR_CALL, FIELD_REFERENCE ->
                        new TargetResolution(null, draft.external() && draft.targetEntityKey() == null
                                ? ResolutionStatus.RESOLVED_EXTERNAL : ResolutionStatus.UNRESOLVED);
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

        private List<String> lookup(Map<String, List<String>> map, String key) {
            String canonical = canonicalTargetName(key);
            List<String> exact = map.get(canonical);
            if (exact != null) {
                return exact.stream().distinct().sorted().toList();
            }
            return map.getOrDefault(simpleName(canonical), List.of()).stream().distinct().sorted().toList();
        }

        private TargetResolution externalOrUnresolved(RelationDraft draft) {
            if (draft.external() || isKnownExternalTarget(draft.targetText())) {
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
