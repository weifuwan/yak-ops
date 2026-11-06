package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.enums.ResourceType;
import io.yak.ops.plugin.task.api.parameters.resource.DataSourceParameters;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

final class DataSyncPipeline {

    private DataSyncPipeline() {
    }

    static String build(TaskExecutionContext context, DataSyncParameters params) {
        DataSourceParameters source = connection(context, params.getSource().getDatasourceId());
        DataSourceParameters target = connection(context, params.getSink().getDatasourceId());
        if (!"MYSQL".equals(source.getType().name()) || !"MYSQL".equals(target.getType().name())) {
            throw new IllegalArgumentException("The first DATA_SYNC pipeline supports MySQL to MySQL only");
        }
        JsonNode sourceConnection = JSONUtils.parseObject(source.getConnectionParams());
        JsonNode targetConnection = JSONUtils.parseObject(target.getConnectionParams());
        String sourceUrl = sourceConnection.path("jdbcUrl").asText();
        URI sourceUri = URI.create(sourceUrl.replaceFirst("^jdbc:", ""));
        String targetUrl = targetConnection.path("jdbcUrl").asText();
        if (!targetUrl.startsWith("jdbc:mysql:")) {
            throw new IllegalArgumentException("Target JDBC URL must use MySQL");
        }

        DataSyncParameters.Table input = params.getSource().getTables().get(0);
        DataSyncParameters.Table output = params.getSink().getTables().get(0);
        int serverId = 5400 + Math.floorMod(context.getTaskInstanceId(), 100000) * 10;

        Map<String, Object> sourceOptions = new LinkedHashMap<>();
        sourceOptions.put("type", "mysql");
        sourceOptions.put("hostname", sourceUri.getHost());
        sourceOptions.put("port", sourceUri.getPort() < 0 ? 3306 : sourceUri.getPort());
        sourceOptions.put("username", sourceConnection.path("user").asText());
        sourceOptions.put("password", sourceConnection.path("password").asText());
        sourceOptions.put("tables", Pattern.quote(input.path()));
        sourceOptions.put("server-id", serverId + "-" + (serverId + 4));
        sourceOptions.put("server-time-zone", "Asia/Shanghai");
        sourceOptions.put("scan.startup.mode", "initial");

        Map<String, Object> sinkOptions = new LinkedHashMap<>();
        sinkOptions.put("type", "jdbc");
        sinkOptions.put("url", targetUrl);
        sinkOptions.put("username", targetConnection.path("user").asText());
        sinkOptions.put("password", targetConnection.path("password").asText());

        Map<String, Object> route = new LinkedHashMap<>();
        route.put("source-table", Pattern.quote(input.path()));
        route.put("sink-table", output.path());

        Map<String, Object> pipelineOptions = new LinkedHashMap<>();
        pipelineOptions.put("name", context.getTaskName() + "-" + context.getTaskInstanceId());
        pipelineOptions.put("execution.runtime-mode", "STREAMING");
        pipelineOptions.put("parallelism", 1);
        pipelineOptions.put("schema.change.behavior", "evolve");

        Map<String, Object> yaml = new LinkedHashMap<>();
        yaml.put("source", sourceOptions);
        yaml.put("sink", sinkOptions);
        yaml.put("route", Collections.singletonList(route));
        yaml.put("pipeline", pipelineOptions);
        return JSONUtils.toJsonString(yaml);
    }

    private static DataSourceParameters connection(TaskExecutionContext context, String id) {
        DataSourceParameters dataSource = (DataSourceParameters) context.getResourceParametersHelper()
                .getResourceParameters(ResourceType.DATASOURCE, Integer.parseInt(id));
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource not found: " + id);
        }
        return dataSource;
    }
}
