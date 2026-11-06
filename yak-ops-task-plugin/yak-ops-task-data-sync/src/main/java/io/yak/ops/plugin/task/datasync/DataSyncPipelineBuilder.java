package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.datasource.api.utils.PasswordUtils;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.enums.ResourceType;
import io.yak.ops.plugin.task.api.parameters.resource.DataSourceParameters;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

final class DataSyncPipelineBuilder {

    String build(TaskExecutionContext context, DataSyncParameters parameters) {
        DataSourceParameters source = resolve(context, parameters.getSource().getDatasourceId());
        DataSourceParameters target = resolve(context, parameters.getSink().getDatasourceId());
        if (!"MYSQL".equals(source.getType().name()) || !"MYSQL".equals(target.getType().name())) {
            throw new IllegalArgumentException("DATA_SYNC supports MySQL to MySQL only");
        }

        JsonNode sourceConnection = JSONUtils.parseObject(source.getConnectionParams());
        JsonNode targetConnection = JSONUtils.parseObject(target.getConnectionParams());
        String sourceUrl = sourceConnection.path("jdbcUrl").asText();
        String targetUrl = targetConnection.path("jdbcUrl").asText();
        if (!sourceUrl.startsWith("jdbc:mysql://") || !targetUrl.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("Both connections must use MySQL JDBC URLs");
        }
        URI uri = URI.create(sourceUrl.substring("jdbc:".length()));
        DataSyncParameters.Table input = parameters.getSource().getTables().get(0);
        DataSyncParameters.Table output = parameters.getSink().getTables().get(0);
        long serverId = 10000L + Math.floorMod(context.getTaskInstanceId(), 500_000_000) * 8L;

        Map<String, Object> sourceOptions = new LinkedHashMap<>();
        sourceOptions.put("type", "mysql");
        sourceOptions.put("hostname", uri.getHost());
        sourceOptions.put("port", uri.getPort() < 0 ? 3306 : uri.getPort());
        sourceOptions.put("username", sourceConnection.path("user").asText());
        sourceOptions.put("password", PasswordUtils.decodePassword(sourceConnection.path("password").asText()));
        sourceOptions.put("tables", Pattern.quote(input.path()));
        sourceOptions.put("server-id", serverId + "-" + (serverId + 4));
        sourceOptions.put("scan.startup.mode", "initial");

        Object timeZone = parameters.getPipeline().get("local-time-zone");
        if (timeZone != null) {
            sourceOptions.put("server-time-zone", String.valueOf(timeZone));
        }

        Map<String, Object> sinkOptions = new LinkedHashMap<>();
        sinkOptions.put("type", "jdbc");
        sinkOptions.put("url", targetUrl);
        sinkOptions.put("username", targetConnection.path("user").asText());
        sinkOptions.put("password", PasswordUtils.decodePassword(targetConnection.path("password").asText()));

        Map<String, Object> route = new LinkedHashMap<>();
        route.put("source-table", Pattern.quote(input.path()));
        route.put("sink-table", output.path());

        Map<String, Object> pipelineOptions = new LinkedHashMap<>();
        pipelineOptions.put("name", context.getTaskName() + "-" + context.getTaskInstanceId());
        pipelineOptions.put("execution.runtime-mode", "STREAMING");
        Object parallelism = parameters.getPipeline().get("parallelism");
        pipelineOptions.put("parallelism", parallelism == null ? 1 : parallelism);
        Object schema = parameters.getPipeline().get("schema.change.behavior");
        boolean evolution = parameters.getSink().getSchemaPolicy() == null
                || parameters.getSink().getSchemaPolicy().getEvolution() == null
                || parameters.getSink().getSchemaPolicy().getEvolution().isEnabled();
        pipelineOptions.put("schema.change.behavior", schema == null
                ? (evolution ? "evolve" : "exception")
                : schema);
        if (timeZone != null) {
            pipelineOptions.put("local-time-zone", timeZone);
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("source", sourceOptions);
        config.put("sink", sinkOptions);
        config.put("route", Collections.singletonList(route));
        config.put("pipeline", pipelineOptions);
        return JSONUtils.toJsonString(config);
    }

    private static DataSourceParameters resolve(TaskExecutionContext context, String id) {
        DataSourceParameters dataSource = (DataSourceParameters) context.getResourceParametersHelper()
                .getResourceParameters(ResourceType.DATASOURCE, Integer.parseInt(id));
        if (dataSource == null) {
            throw new IllegalArgumentException("Datasource not found: " + id);
        }
        return dataSource;
    }
}
