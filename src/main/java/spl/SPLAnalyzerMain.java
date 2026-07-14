package spl;

import spl.entity.EntityExtractionResult;
import spl.entity.EntityResultExporter;
import spl.entity.JavaEntity;
import spl.entity.JavaEntityExtractor;
import spl.entity.JavaEntityType;
import spl.entity.JavaRelation;
import spl.entity.JavaRelationType;
import spl.grouping.SignatureBlockGrouper;
import spl.grouping.SignatureGroup;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

public final class SPLAnalyzerMain {
    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            System.err.println("Usage: java spl.SPLAnalyzerMain [asset-subdirectory]");
            return;
        }

        AssetProjectPathResolver pathResolver = new AssetProjectPathResolver();
        System.out.println("Base asset directory: " + pathResolver.baseDirectoryDisplay());
        String assetSubdirectory = args.length == 1 ? args[0] : promptForAssetSubdirectory();
        AssetProjectPathResolver.ResolvedAssetProject assetProject;
        try {
            assetProject = pathResolver.resolve(assetSubdirectory);
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            return;
        }
        System.out.println("Analyzing asset project: " + assetProject.displayPath());

        ProductSignatureExtractor extractor = new ProductSignatureExtractor();
        List<ConditionalBlock> blocks = extractor.extract(assetProject.normalizedPath());
        List<SignatureGroup> groups = new SignatureBlockGrouper().group(blocks);
        printGroups(groups);

        List<Path> assetFiles = new AssetFileScanner().scan(assetProject.normalizedPath());
        EntityExtractionResult entityResult = new JavaEntityExtractor().extract(groups, assetFiles);
        new EntityResultExporter().export(entityResult, Path.of("output"));
        printEntitySummary(groups, entityResult);
    }

    private static String promptForAssetSubdirectory() {
        System.out.print("Enter asset project subdirectory under assets: ");
        return new Scanner(System.in).nextLine();
    }

    private static void printGroups(List<SignatureGroup> groups) {
        for (int index = 0; index < groups.size(); index++) {
            SignatureGroup group = groups.get(index);
            if (index > 0) {
                System.out.println();
            }
            System.out.println("Group " + group.groupId());
            System.out.println("Signature : " + group.canonicalSignature());
            System.out.println("Blocks    : " + group.blocks().size());
        }
    }

    private static void printEntitySummary(List<SignatureGroup> groups, EntityExtractionResult result) {
        System.out.println();
        System.out.println("Parsed asset files: " + result.parsedAssetFileCount());
        System.out.println("Product projections attempted: " + result.projectionAttemptCount());
        System.out.println("Successful projections       : " + result.successfulProjectionCount());
        System.out.println("Failed projections           : " + result.parseFailures().size());
        for (var failure : result.parseFailures()) {
            System.out.printf(
                    "Parse failure: %s product %s line %d: %s%n",
                    failure.sourceFile(),
                    failure.product(),
                    failure.line(),
                    failure.message()
            );
        }

        System.out.println();
        System.out.println("Total entities by type:");
        printEntityCounts(result.entities());
        System.out.println("Total relations by type:");
        printRelationCounts(result.relations());
        printRepresentativeRelations("Representative field references", result.relations(), JavaRelationType.FIELD_REFERENCE);
        printRepresentativeRelations("Representative constructor calls", result.relations(), JavaRelationType.CONSTRUCTOR_CALL);
        printRepresentativeMergedEntities(result.entities());
        printRepresentativeMergedRelations(result.relations());

        Map<String, List<JavaEntity>> entitiesByGroup = result.entities().stream()
                .collect(Collectors.groupingBy(JavaEntity::signatureGroupId));
        Map<String, List<JavaRelation>> relationsByGroup = result.relations().stream()
                .collect(Collectors.groupingBy(JavaRelation::signatureGroupId));
        for (SignatureGroup group : groups) {
            System.out.println();
            System.out.println("Group " + group.groupId());
            System.out.println("Signature: " + group.canonicalSignature());
            System.out.println("Entities:");
            printEntityCounts(entitiesByGroup.getOrDefault(group.groupId(), List.of()));
            System.out.println("Relations:");
            printRelationCounts(relationsByGroup.getOrDefault(group.groupId(), List.of()));
        }
    }

    private static void printEntityCounts(Collection<JavaEntity> entities) {
        Map<JavaEntityType, Long> counts = entities.stream()
                .collect(Collectors.groupingBy(JavaEntity::entityType, Collectors.counting()));
        for (JavaEntityType type : JavaEntityType.values()) {
            System.out.println("- " + type + ": " + counts.getOrDefault(type, 0L));
        }
    }

    private static void printRelationCounts(Collection<JavaRelation> relations) {
        Map<JavaRelationType, Long> counts = relations.stream()
                .collect(Collectors.groupingBy(JavaRelation::relationType, Collectors.counting()));
        for (JavaRelationType type : JavaRelationType.values()) {
            System.out.println("- " + type + ": " + counts.getOrDefault(type, 0L));
        }
    }

    private static void printRepresentativeRelations(String title, Collection<JavaRelation> relations,
                                                     JavaRelationType relationType) {
        System.out.println();
        System.out.println(title + ":");
        relations.stream()
                .filter(relation -> relation.relationType() == relationType)
                .limit(10)
                .forEach(relation -> System.out.printf(
                        "- source=%s target=%s file=%s line=%d group=%s status=%s%n",
                        relation.sourceEntityId(),
                        relation.unresolvedTargetText(),
                        relation.sourceFile(),
                        relation.sourceLine(),
                        relation.signatureGroupId(),
                        relation.resolutionStatus()
                ));
    }

    private static void printRepresentativeMergedEntities(Collection<JavaEntity> entities) {
        System.out.println();
        System.out.println("Representative merged entities:");
        entities.stream()
                .filter(entity -> entity.observedProducts().size() > 1)
                .limit(5)
                .forEach(entity -> System.out.printf(
                        "- key=%s|%s|%s|%d-%d products=%s signature=%s group=%s%n",
                        entity.sourceFile(),
                        entity.entityType(),
                        entity.qualifiedName() == null ? entity.simpleName() : entity.qualifiedName(),
                        entity.startLine(),
                        entity.endLine(),
                        entity.observedProducts(),
                        entity.effectiveProductSignature(),
                        entity.signatureGroupId()
                ));
    }

    private static void printRepresentativeMergedRelations(Collection<JavaRelation> relations) {
        System.out.println();
        System.out.println("Representative merged relations:");
        relations.stream()
                .filter(relation -> relation.observedProducts().size() > 1)
                .limit(5)
                .forEach(relation -> System.out.printf(
                        "- key=%s|%d|%s|%s products=%s signature=%s group=%s%n",
                        relation.sourceFile(),
                        relation.sourceLine(),
                        relation.relationType(),
                        relation.targetEntityId() == null ? relation.unresolvedTargetText() : relation.targetEntityId(),
                        relation.observedProducts(),
                        relation.effectiveProductSignature(),
                        relation.signatureGroupId()
                ));
    }
}
