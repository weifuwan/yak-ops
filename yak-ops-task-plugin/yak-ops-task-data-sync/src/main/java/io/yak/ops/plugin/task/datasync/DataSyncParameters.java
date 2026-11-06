package io.yak.ops.plugin.task.datasync;

import io.yak.ops.plugin.task.api.enums.ResourceType;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.plugin.task.api.parameters.resource.ResourceParametersHelper;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataSyncParameters extends AbstractParameters {

    private Endpoint source;
    private Endpoint sink;
    private List<Route> route;
    private List<JsonNode> transform;
    private Map<String, Object> pipeline;

    @Override
    public boolean checkParameters() {
        if (source == null || sink == null || source.getDatasourceId() == null
                || sink.getDatasourceId() == null || source.getTables() == null
                || sink.getTables() == null || source.getTables().size() != 1
                || sink.getTables().size() != 1 || source.getStartup() == null
                || !"INITIAL".equalsIgnoreCase(source.getStartup().getMode())) {
            return false;
        }
        if (!("mysql-cdc".equalsIgnoreCase(source.getType())
                || "mysql".equalsIgnoreCase(source.getType()))
                || !("jdbc-mysql".equalsIgnoreCase(sink.getType())
                        || "jdbc".equalsIgnoreCase(sink.getType()))) {
            return false;
        }
        Table input = source.getTables().get(0);
        Table output = sink.getTables().get(0);
        if (!input.hasTable() || !output.hasTable()
                || source.getSelection() != null || input.getSnapshot() != null
                || input.getDml() != null || (transform != null && !transform.isEmpty())) {
            return false;
        }
        if (output.getWrite() != null && !"UPSERT".equalsIgnoreCase(output.getWrite().getMode())) {
            return false;
        }
        if (sink.getSchemaPolicy() != null
                && sink.getSchemaPolicy().getEvolution() != null
                && sink.getSchemaPolicy().getEvolution().isAllowDropColumns()) {
            return false;
        }
        if (route != null && (route.size() != 1
                || !input.path().equals(route.get(0).getSourceTable())
                || !output.path().equals(route.get(0).getSinkTable()))) {
            return false;
        }
        if (pipeline == null || !"STREAMING".equalsIgnoreCase(
                String.valueOf(pipeline.get("execution.runtime-mode")))) {
            return false;
        }
        Object scope = pipeline.get("pipeline.table-scope");
        return scope == null || "SINGLE".equals(scope) || "DATABASE".equals(scope);
    }

    @Override
    public ResourceParametersHelper getResources() {
        ResourceParametersHelper resources = super.getResources();
        resources.put(ResourceType.DATASOURCE, Integer.parseInt(source.getDatasourceId()));
        resources.put(ResourceType.DATASOURCE, Integer.parseInt(sink.getDatasourceId()));
        return resources;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Endpoint {

        private String type;
        private String datasourceId;
        private List<Table> tables;
        private Startup startup;
        private JsonNode selection;
        private SchemaPolicy schemaPolicy;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Startup {

        private String mode;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Table {

        private String database;
        private String schema;
        private String table;
        private JsonNode snapshot;
        private JsonNode dml;
        private Write write;

        public boolean hasTable() {
            return database != null && !database.isEmpty() && table != null && !table.isEmpty();
        }

        public String path() {
            return database + "." + table;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Write {

        private String mode;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SchemaPolicy {

        private String initial;
        private Evolution evolution;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Evolution {

        private boolean enabled;
        private boolean allowDropColumns;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Route {

        @JsonProperty("source-table")
        private String sourceTable;
        @JsonProperty("sink-table")
        private String sinkTable;
    }
}
