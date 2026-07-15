package spl.dependency;

import spl.entity.JavaRelationType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public final class DependencyGraphExporter {
    public void export(DependencyGraph graph, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        exportNodes(graph, outputDirectory.resolve("dependency-nodes.csv"));
        exportEdges(graph, outputDirectory.resolve("dependency-edges.csv"));
        exportUnresolved(graph, outputDirectory.resolve("unresolved-relations.csv"));
        exportGroupDependencies(graph, outputDirectory.resolve("group-dependencies.csv"));
    }

    private static void exportNodes(DependencyGraph graph, Path outputFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("entity_id,entity_type,simple_name,qualified_name,file,start_line,end_line,group_id,signature,observed_products\n");
        for (DependencyNode node : graph.nodes()) {
            builder.append(csv(node.entityId())).append(',')
                    .append(csv(node.entityType().name())).append(',')
                    .append(csv(node.simpleName())).append(',')
                    .append(csv(node.qualifiedName())).append(',')
                    .append(csv(node.sourceFile().toString())).append(',')
                    .append(node.startLine()).append(',')
                    .append(node.endLine()).append(',')
                    .append(csv(node.signatureGroupId())).append(',')
                    .append(csv(node.effectiveProductSignature().toString())).append(',')
                    .append(csv(String.join("|", node.observedProducts()))).append('\n');
        }
        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private static void exportEdges(DependencyGraph graph, Path outputFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("edge_id,source_entity_id,target_entity_id,relation_type,source_file,source_line,target_file,source_group_id,target_group_id,source_signature,target_signature,cross_file,observed_products,resolution_status\n");
        for (DependencyEdge edge : graph.edges()) {
            builder.append(csv(edge.edgeId())).append(',')
                    .append(csv(edge.sourceEntityId())).append(',')
                    .append(csv(edge.targetEntityId())).append(',')
                    .append(csv(edge.relationType().name())).append(',')
                    .append(csv(edge.sourceFile().toString())).append(',')
                    .append(edge.sourceLine()).append(',')
                    .append(csv(edge.targetFile().toString())).append(',')
                    .append(csv(edge.sourceGroupId())).append(',')
                    .append(csv(edge.targetGroupId())).append(',')
                    .append(csv(edge.sourceSignature().toString())).append(',')
                    .append(csv(edge.targetSignature().toString())).append(',')
                    .append(edge.crossFile()).append(',')
                    .append(csv(String.join("|", edge.observedProducts()))).append(',')
                    .append(csv(edge.resolutionStatus().name())).append('\n');
        }
        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private static void exportUnresolved(DependencyGraph graph, Path outputFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("relation_id,relation_type,source_entity_id,target_text,file,line,group_id,signature,resolution_status,failure_reason,observed_products\n");
        for (UnresolvedDependencyRelation relation : graph.unresolvedRelations()) {
            builder.append(csv(relation.relationId())).append(',')
                    .append(csv(relation.relationType().name())).append(',')
                    .append(csv(relation.sourceEntityId())).append(',')
                    .append(csv(relation.targetText())).append(',')
                    .append(csv(relation.sourceFile().toString())).append(',')
                    .append(relation.sourceLine()).append(',')
                    .append(csv(relation.sourceGroupId())).append(',')
                    .append(csv(relation.sourceSignature().toString())).append(',')
                    .append(csv(relation.resolutionStatus().name())).append(',')
                    .append(csv(relation.failureReason())).append(',')
                    .append(csv(String.join("|", relation.observedProducts()))).append('\n');
        }
        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private static void exportGroupDependencies(DependencyGraph graph, Path outputFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("source_group_id,target_group_id,relation_type,edge_count,cross_file_edge_count\n");
        Map<GroupDependencyKey, GroupDependencyCount> counts = new TreeMap<>();
        for (DependencyEdge edge : graph.edges()) {
            GroupDependencyKey key = new GroupDependencyKey(edge.sourceGroupId(), edge.targetGroupId(), edge.relationType());
            counts.computeIfAbsent(key, ignored -> new GroupDependencyCount()).add(edge.crossFile());
        }
        for (Map.Entry<GroupDependencyKey, GroupDependencyCount> entry : counts.entrySet()) {
            GroupDependencyKey key = entry.getKey();
            GroupDependencyCount count = entry.getValue();
            builder.append(csv(key.sourceGroupId())).append(',')
                    .append(csv(key.targetGroupId())).append(',')
                    .append(csv(key.relationType().name())).append(',')
                    .append(count.edgeCount).append(',')
                    .append(count.crossFileEdgeCount).append('\n');
        }
        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public record GroupDependencyKey(String sourceGroupId, String targetGroupId, JavaRelationType relationType)
            implements Comparable<GroupDependencyKey> {
        @Override
        public int compareTo(GroupDependencyKey other) {
            int comparison = sourceGroupId.compareTo(other.sourceGroupId);
            if (comparison != 0) {
                return comparison;
            }
            comparison = targetGroupId.compareTo(other.targetGroupId);
            if (comparison != 0) {
                return comparison;
            }
            return relationType.name().compareTo(other.relationType.name());
        }
    }

    private static final class GroupDependencyCount {
        private int edgeCount;
        private int crossFileEdgeCount;

        private void add(boolean crossFile) {
            edgeCount++;
            if (crossFile) {
                crossFileEdgeCount++;
            }
        }
    }
}
