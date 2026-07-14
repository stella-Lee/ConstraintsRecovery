package spl.entity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EntityResultExporter {
    public void export(EntityExtractionResult result, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        exportEntities(result, outputDirectory.resolve("entities.csv"));
        exportRelations(result, outputDirectory.resolve("relations.csv"));
    }

    private static void exportEntities(EntityExtractionResult result, Path outputFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("entity_id,group_id,signature,entity_type,simple_name,qualified_name,file,start_line,end_line,block_id,observed_products,resolution_status\n");
        for (JavaEntity entity : result.entities()) {
            builder.append(csv(entity.entityId())).append(',')
                    .append(csv(entity.signatureGroupId())).append(',')
                    .append(csv(entity.effectiveProductSignature().toString())).append(',')
                    .append(csv(entity.entityType().name())).append(',')
                    .append(csv(entity.simpleName())).append(',')
                    .append(csv(entity.qualifiedName())).append(',')
                    .append(csv(entity.sourceFile().toString())).append(',')
                    .append(entity.startLine()).append(',')
                    .append(entity.endLine()).append(',')
                    .append(csv(entity.containingBlockId())).append(',')
                    .append(csv(String.join("|", entity.observedProducts()))).append(',')
                    .append(csv(entity.resolutionStatus().name())).append('\n');
        }
        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private static void exportRelations(EntityExtractionResult result, Path outputFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("relation_id,group_id,signature,relation_type,source_entity_id,target_entity_id,target_text,file,source_line,block_id,observed_products,resolution_status\n");
        for (JavaRelation relation : result.relations()) {
            builder.append(csv(relation.relationId())).append(',')
                    .append(csv(relation.signatureGroupId())).append(',')
                    .append(csv(relation.effectiveProductSignature().toString())).append(',')
                    .append(csv(relation.relationType().name())).append(',')
                    .append(csv(relation.sourceEntityId())).append(',')
                    .append(csv(relation.targetEntityId())).append(',')
                    .append(csv(relation.unresolvedTargetText())).append(',')
                    .append(csv(relation.sourceFile().toString())).append(',')
                    .append(relation.sourceLine()).append(',')
                    .append(csv(relation.containingBlockId())).append(',')
                    .append(csv(String.join("|", relation.observedProducts()))).append(',')
                    .append(csv(relation.resolutionStatus().name())).append('\n');
        }
        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
