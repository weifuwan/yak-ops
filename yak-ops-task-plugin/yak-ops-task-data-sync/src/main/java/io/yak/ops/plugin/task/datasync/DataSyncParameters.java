package io.yak.ops.plugin.task.datasync;

import io.yak.ops.plugin.task.api.enums.ResourceType;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.plugin.task.api.parameters.resource.ResourceParametersHelper;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataSyncParameters extends AbstractParameters {

    private Endpoint source;
    private Endpoint sink;
    private Map<String, String> pipeline;

    @Override
    public boolean checkParameters() {
        return source != null && sink != null
                && "mysql".equalsIgnoreCase(source.getType())
                && ("jdbc".equalsIgnoreCase(sink.getType()) || "mysql".equalsIgnoreCase(sink.getType()))
                && source.getDatasourceId() != null && sink.getDatasourceId() != null
                && source.getTables() != null && source.getTables().size() == 1
                && sink.getTables() != null && sink.getTables().size() == 1
                && source.getTables().get(0).hasTable()
                && sink.getTables().get(0).hasTable()
                && source.getStartup() != null
                && "INITIAL".equalsIgnoreCase(source.getStartup().getMode())
                && (pipeline == null || "STREAMING".equalsIgnoreCase(
                        pipeline.getOrDefault("execution.runtime-mode", "STREAMING")));
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

        public boolean hasTable() {
            return database != null && !database.isEmpty() && table != null && !table.isEmpty();
        }

        public String path() {
            return database + "." + table;
        }
    }
}
