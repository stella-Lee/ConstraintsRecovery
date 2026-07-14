package spl.entity;

import com.github.javaparser.Range;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spl.ConditionalBlock;
import spl.ProductSignatureExtractor;
import spl.grouping.SignatureBlockGrouper;
import spl.grouping.SignatureGroup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaEntityExtractorTest {
    @TempDir
    Path tempDir;

    @Test
    void lineHelpersHandleNodesWithAndWithoutSourceRanges() {
        MethodCallExpr rangedCall = new MethodCallExpr("call");
        rangedCall.setRange(Range.range(7, 9, 7, 14));
        MethodCallExpr unrangedCall = new MethodCallExpr("missingRange");

        assertEquals(7, JavaEntityExtractor.startLine(rangedCall));
        assertEquals(7, JavaEntityExtractor.endLine(rangedCall));
        assertEquals(-1, JavaEntityExtractor.startLine(unrangedCall));
        assertEquals(-1, JavaEntityExtractor.endLine(unrangedCall));
    }

    @Test
    void extractsClassesInterfacesMethodsConstructorsFieldsAndRelations() throws Exception {
        Path file = writeSample("""
                package demo;
                //#if Enterprise|Professional
                public class Service extends Base implements Api {
                    private Helper one, two;
                    public Service(Dependency dep) throws Problem {
                        super();
                        one = new Helper(dep);
                    }
                    public Result run(Input input) throws Failure {
                        java.util.List<String> names = new java.util.ArrayList<>();
                        Object casted = (Object) input;
                        one.call(input);
                        return input.result();
                    }
                    static class Nested {
                    }
                }
                //#endif
                //#if Enterprise
                interface Api extends RootApi {
                    void api();
                }
                //#endif
                """);

        EntityExtractionResult result = extract(file);

        assertEquals(1, result.parsedAssetFileCount());
        assertEquals(2, result.projectionAttemptCount());
        assertEquals(2, result.successfulProjectionCount());
        assertTrue(result.parseFailures().isEmpty());
        assertTrue(hasEntity(result, JavaEntityType.CLASS, "Service"));
        assertTrue(hasEntity(result, JavaEntityType.CLASS, "Nested"));
        assertTrue(hasEntity(result, JavaEntityType.INTERFACE, "Api"));
        assertTrue(hasEntity(result, JavaEntityType.METHOD, "run"));
        assertTrue(hasEntity(result, JavaEntityType.CONSTRUCTOR, "Service"));
        assertEquals(2, result.entities().stream()
                .filter(entity -> entity.entityType() == JavaEntityType.FIELD)
                .filter(entity -> entity.simpleName().equals("one") || entity.simpleName().equals("two"))
                .count());
        assertTrue(hasRelation(result, JavaRelationType.EXTENDS, "Base"));
        assertTrue(hasRelation(result, JavaRelationType.IMPLEMENTS, "Api"));
        assertTrue(hasRelation(result, JavaRelationType.METHOD_CALL, "call"));
        assertTrue(hasRelation(result, JavaRelationType.METHOD_CALL, "result"));
        assertTrue(result.relations().stream()
                .anyMatch(relation -> relation.relationType() == JavaRelationType.METHOD_CALL
                        && relation.unresolvedTargetText().equals("call")
                        && relation.sourceLine() == 12));
        assertTrue(hasRelation(result, JavaRelationType.CONSTRUCTOR_CALL, "Helper"));
        assertTrue(hasRelation(result, JavaRelationType.CONSTRUCTOR_CALL, "super"));
        assertTrue(hasRelation(result, JavaRelationType.TYPE_REFERENCE, "Helper"));
        assertTrue(hasRelation(result, JavaRelationType.TYPE_REFERENCE, "Input"));
        assertTrue(hasRelation(result, JavaRelationType.TYPE_REFERENCE, "Result"));
    }

    @Test
    void associatesEntityWithCorrectConditionalBlockAndPreservesDirectiveLineNumbers() throws Exception {
        Path file = writeSample("""
                //#if Enterprise
                class EnterpriseOnly {
                }
                //#endif
                //#if Professional
                class ProfessionalOnly {
                }
                //#endif
                """);

        EntityExtractionResult result = extract(file);
        JavaEntity enterprise = entity(result, "EnterpriseOnly");
        JavaEntity professional = entity(result, "ProfessionalOnly");

        assertEquals(2, enterprise.startLine());
        assertEquals(6, professional.startLine());
        assertEquals("G1", enterprise.signatureGroupId());
        assertEquals("G2", professional.signatureGroupId());
        assertTrue(enterprise.containingBlockId().contains(":IF:1-3"));
        assertTrue(professional.containingBlockId().contains(":IF:5-7"));
    }

    @Test
    void preservesEnclosingSignatureForCodeAfterNestedEndif() throws Exception {
        Path file = writeSample("""
                //#if Enterprise|Professional
                class Outer {
                    //#if Professional
                    class Nested {
                    }
                    //#endif
                    class AfterNested {
                    }
                }
                //#endif
                """);

        EntityExtractionResult result = extract(file);

        assertEquals("Enterprise|Professional", entity(result, "Outer").effectiveProductSignature().rawExpression());
        assertEquals("Professional", entity(result, "Nested").effectiveProductSignature().rawExpression());
        assertEquals("Enterprise|Professional", entity(result, "AfterNested").effectiveProductSignature().rawExpression());
    }

    @Test
    void marksUnknownReferencesAsUnresolvedInsteadOfDroppingThem() throws Exception {
        Path file = writeSample("""
                //#if Enterprise
                class UsesUnknown {
                    MissingType field;
                    void run(MissingParam param) {
                        missingCall(param);
                    }
                }
                //#endif
                """);

        EntityExtractionResult result = extract(file);

        assertTrue(result.relations().stream()
                .anyMatch(relation -> relation.unresolvedTargetText().equals("MissingType")
                        && relation.resolutionStatus() == ResolutionStatus.UNRESOLVED));
        assertTrue(result.relations().stream()
                .anyMatch(relation -> relation.unresolvedTargetText().equals("missingCall")
                        && relation.resolutionStatus() == ResolutionStatus.UNRESOLVED));
    }

    @Test
    void extractsFieldReferencesWithoutCountingLocalsParametersOrMethodNames() throws Exception {
        Path file = writeSample("""
                //#if Enterprise
                class Base {
                    protected int inherited;
                }
                class UsesFields extends Base {
                    private int value;
                    void run(int parameter) {
                        int local = value;
                        this.value = local;
                        super.inherited = parameter;
                        external.other = value;
                        valueMethod();
                    }
                    void valueMethod() {
                    }
                }
                //#endif
                """);

        EntityExtractionResult result = extract(file);
        List<JavaRelation> fieldReferences = result.relations().stream()
                .filter(relation -> relation.relationType() == JavaRelationType.FIELD_REFERENCE)
                .toList();

        assertTrue(fieldReferences.stream().anyMatch(relation -> relation.unresolvedTargetText().equals("value")));
        assertTrue(fieldReferences.stream().anyMatch(relation -> relation.unresolvedTargetText().equals("inherited")));
        assertTrue(fieldReferences.stream().anyMatch(relation -> relation.unresolvedTargetText().equals("other")
                && relation.resolutionStatus() == ResolutionStatus.UNRESOLVED));
        assertTrue(fieldReferences.stream().noneMatch(relation -> relation.unresolvedTargetText().equals("local")));
        assertTrue(fieldReferences.stream().noneMatch(relation -> relation.unresolvedTargetText().equals("parameter")));
        assertTrue(fieldReferences.stream().noneMatch(relation -> relation.unresolvedTargetText().equals("valueMethod")));
    }

    @Test
    void mergesDuplicateEntitiesAcrossProductProjectionsAndRecordsObservedProducts() throws Exception {
        Path file = writeSample("""
                //#if Enterprise|Professional
                class Shared {
                    void run() {
                    }
                }
                //#endif
                """);

        EntityExtractionResult result = extract(file);

        JavaEntity shared = entity(result, "Shared");
        assertEquals(List.of("Enterprise", "Professional"), shared.observedProducts());
        assertEquals(1, result.entities().stream()
                .filter(entity -> entity.simpleName().equals("Shared"))
                .count());
    }

    @Test
    void associatesCommonCodeOutsideDirectivesWithCompleteProductUniverse() throws Exception {
        Path file = writeSample("""
                class Common {
                }
                //#if Enterprise
                class EnterpriseOnly {
                }
                //#endif
                //#if Professional
                class ProfessionalOnly {
                }
                //#endif
                """);

        EntityExtractionResult result = extract(file);

        JavaEntity common = entity(result, "Common");
        assertEquals("COMMON", common.signatureGroupId());
        assertEquals("Enterprise|Professional", common.effectiveProductSignature().rawExpression());
        assertEquals(List.of("Enterprise", "Professional"), common.observedProducts());
    }

    @Test
    void producesDeterministicEntityAndRelationOrdering() throws Exception {
        Path first = writeSample("A.java", """
                //#if Starter
                class A {
                    B b;
                    void a() {
                        b.call();
                    }
                }
                //#endif
                """);
        Path second = writeSample("B.java", """
                //#if Starter
                class B {
                    void call() {
                    }
                }
                //#endif
                """);

        EntityExtractionResult result = extract(List.of(second, first));

        assertEquals(List.of("E1", "E2", "E3", "E4", "E5"),
                result.entities().stream().map(JavaEntity::entityId).toList());
        assertEquals(result.relations().stream().map(JavaRelation::relationId).toList(),
                result.relations().stream().map(JavaRelation::relationId).sorted().toList());
    }

    @Test
    void returnsEmptyResultForEmptyInput() {
        EntityExtractionResult result = new JavaEntityExtractor().extract(List.of(), List.of());

        assertTrue(result.entities().isEmpty());
        assertTrue(result.relations().isEmpty());
        assertTrue(result.parseFailures().isEmpty());
        assertEquals(0, result.parsedAssetFileCount());
        assertEquals(0, result.projectionAttemptCount());
        assertEquals(0, result.successfulProjectionCount());
    }

    @Test
    void reportsParserFailure() throws Exception {
        Path file = writeSample("""
                //#if Enterprise
                class Broken {
                    void run( {
                }
                //#endif
                """);

        ProductSignatureExtractor extractor = new ProductSignatureExtractor();
        List<ConditionalBlock> blocks = extractor.extractFromFile(file);
        List<SignatureGroup> groups = new SignatureBlockGrouper().group(blocks);
        EntityExtractionResult result = new JavaEntityExtractor().extract(groups, List.of(file));

        assertFalse(result.parseFailures().isEmpty());
        assertEquals(0, result.parsedAssetFileCount());
        assertEquals("Enterprise", result.parseFailures().get(0).product());
        assertTrue(result.parseFailures().get(0).extractionSkipped());
    }

    @Test
    void integrationWithNestedProductIdDirectives() throws Exception {
        Path file = writeSample("""
                //#if Enterprise|Professional
                class BaseProduct {
                    //#if Enterprise
                    void enterpriseOnly() {
                    }
                    //#endif
                    void shared() {
                    }
                }
                //#endif
                """);

        EntityExtractionResult result = extract(file);
        Map<String, List<JavaEntity>> byGroup = result.entities().stream()
                .collect(Collectors.groupingBy(JavaEntity::signatureGroupId));

        assertEquals(2, byGroup.size());
        assertTrue(result.entities().stream().anyMatch(entity -> entity.simpleName().equals("BaseProduct")));
        assertTrue(result.entities().stream().anyMatch(entity -> entity.simpleName().equals("enterpriseOnly")));
        assertTrue(result.entities().stream().anyMatch(entity -> entity.simpleName().equals("shared")));
    }

    private EntityExtractionResult extract(Path file) throws Exception {
        return extract(List.of(file));
    }

    private EntityExtractionResult extract(List<Path> files) throws Exception {
        ProductSignatureExtractor extractor = new ProductSignatureExtractor();
        List<ConditionalBlock> blocks = extractor.extractFromFiles(files);
        List<SignatureGroup> groups = new SignatureBlockGrouper().group(blocks);
        return new JavaEntityExtractor().extract(groups, files);
    }

    private Path writeSample(String content) throws Exception {
        return writeSample("Sample.java", content);
    }

    private Path writeSample(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }

    private static boolean hasEntity(EntityExtractionResult result, JavaEntityType type, String name) {
        return result.entities().stream()
                .anyMatch(entity -> entity.entityType() == type && entity.simpleName().equals(name));
    }

    private static boolean hasRelation(EntityExtractionResult result, JavaRelationType type, String targetText) {
        return result.relations().stream()
                .anyMatch(relation -> relation.relationType() == type
                        && relation.unresolvedTargetText().equals(targetText));
    }

    private static JavaEntity entity(EntityExtractionResult result, String name) {
        return result.entities().stream()
                .filter(candidate -> candidate.simpleName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
