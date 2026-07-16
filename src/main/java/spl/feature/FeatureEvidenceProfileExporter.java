package spl.feature;

import spl.entity.JavaEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class FeatureEvidenceProfileExporter {
    public void export(FeatureEvidenceProfileResult result, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        exportCsv(result, outputDirectory.resolve("feature-evidence-profile.csv"));
        exportReport(result, outputDirectory.resolve("feature-evidence-report.txt"));
    }

    private static void exportCsv(FeatureEvidenceProfileResult result, Path outputFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("component_id,group_id,signature,entity_count,file_count,block_count,")
                .append("top_classes,top_methods,top_fields,top_tokens,top_dependency_targets,")
                .append("top_dependency_sources,packages,classes,representative_entities\n");
        for (FeatureEvidenceProfile profile : result.profiles()) {
            builder.append(csv(profile.componentId())).append(',')
                    .append(csv(profile.groupId())).append(',')
                    .append(csv(profile.signature().toString())).append(',')
                    .append(profile.entityCount()).append(',')
                    .append(profile.fileCount()).append(',')
                    .append(profile.blockCount()).append(',')
                    .append(csv(joinImportance(profile.topClasses()))).append(',')
                    .append(csv(joinImportance(profile.topMethods()))).append(',')
                    .append(csv(joinImportance(profile.topFields()))).append(',')
                    .append(csv(String.join("|", profile.tokenFrequencies().entrySet().stream()
                            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                                    .thenComparing(Map.Entry::getKey))
                            .limit(8)
                            .map(entry -> entry.getKey() + ":" + entry.getValue())
                            .toList()))).append(',')
                    .append(csv(String.join("|", profile.topDependencyTargets()))).append(',')
                    .append(csv(String.join("|", profile.topDependencySources()))).append(',')
                    .append(csv(String.join("|", profile.packages()))).append(',')
                    .append(csv(String.join("|", profile.classes()))).append(',')
                    .append(csv(joinImportance(profile.representativeEntities()))).append('\n');
        }
        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private static void exportReport(FeatureEvidenceProfileResult result, Path outputFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (FeatureEvidenceProfile profile : result.profiles()) {
            builder.append("=================================================\n");
            builder.append("Component ").append(profile.componentId()).append('\n').append('\n');
            builder.append("Signature\n").append(profile.signature()).append('\n').append('\n');
            builder.append("Implementation concept\n")
                    .append(profile.implementationConcept()).append('\n')
                    .append("NOTE: This is only a descriptive interpretation, not a recovered feature name.\n\n");
            builder.append("Representative entities\n");
            for (EntityImportance entity : profile.representativeEntities()) {
                JavaEntity javaEntity = entity.entity();
                builder.append("- rank ").append(entity.rank())
                        .append(" score ").append(entity.importanceScore())
                        .append(" ").append(javaEntity.entityType())
                        .append(" ").append(entityName(javaEntity))
                        .append(" ")
                        .append(javaEntity.sourceFile()).append(':')
                        .append(javaEntity.startLine()).append('-').append(javaEntity.endLine())
                        .append('\n');
            }
            builder.append('\n');
            builder.append("Representative tokens\n");
            profile.tokenFrequencies().entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                    .limit(8)
                    .forEach(entry -> builder.append("- ").append(entry.getKey())
                            .append(" = ").append(entry.getValue())
                            .append(" origins ").append(profile.tokenOrigins().getOrDefault(entry.getKey(), java.util.List.of()))
                            .append('\n'));
            builder.append('\n');
            builder.append("Dependency summary\n");
            builder.append("- internal types: ").append(profile.internalDependencyTypes()).append('\n');
            builder.append("- top targets: ").append(profile.topDependencyTargets()).append('\n');
            builder.append("- top sources: ").append(profile.topDependencySources()).append('\n').append('\n');
            builder.append("Structural summary\n");
            builder.append("Entities : ").append(profile.entityCount()).append('\n');
            builder.append("Files : ").append(profile.fileCount()).append('\n');
            builder.append("Blocks : ").append(profile.blockCount()).append('\n');
            builder.append("Internal : ").append(profile.internalEdgeCount()).append('\n');
            builder.append("Incoming : ").append(profile.incomingEdgeCount()).append('\n');
            builder.append("Outgoing : ").append(profile.outgoingEdgeCount()).append('\n');
            builder.append("Cross-file internal : ").append(profile.crossFileEdgeCount()).append('\n');
            builder.append("Isolated entities : ").append(profile.isolatedEntityCount()).append('\n').append('\n');
            builder.append("Concept evidence\n");
            profile.conceptEvidence().forEach(evidence -> builder.append("- ").append(evidence).append('\n'));
            builder.append("=================================================\n\n");
        }
        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private static String joinImportance(java.util.List<EntityImportance> entities) {
        return entities.stream()
                .map(item -> entityName(item.entity()) + "[rank=" + item.rank()
                        + ",score=" + item.importanceScore() + "]")
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
    }

    private static String entityName(JavaEntity entity) {
        return entity.qualifiedName() == null ? entity.simpleName() : entity.qualifiedName();
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
