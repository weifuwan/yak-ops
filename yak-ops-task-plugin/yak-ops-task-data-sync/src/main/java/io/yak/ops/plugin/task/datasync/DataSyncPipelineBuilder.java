package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.enums.ResourceType;
import io.yak.ops.plugin.task.api.parameters.resource.DataSourceParameters;

import org.apache.flink.cdc.cli.utils.YamlParserUtils;

import java.io.File;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Compiles a connector-independent Pipeline definition into executable Flink CDC YAML. */
public final class DataSyncPipelineBuilder {

    private final DataSyncConnectorRegistry providers = new DataSyncConnectorRegistry();

    public String preview(
                          JsonNode rawDefinition,
                          DataSourceParameters sourceResource,
                          DataSourceParameters sinkResource) {
        ObjectNode definition = (ObjectNode) rawDefinition.deepCopy();
        ObjectNode source = (ObjectNode) definition.path("source");
        ObjectNode sink = (ObjectNode) definition.path("sink");
        ObjectNode pipeline = (ObjectNode) definition.path("pipeline");
        int parallelism = pipeline.has("parallelism") ? pipeline.get("parallelism").asInt() : 1;

        Map<String, String> sourceOptions = providers
                .source(sourceResource.getType(), source.path("type").asText())
                .connectionOptions(sourceResource, 0L, parallelism);
        Map<String, String> sinkOptions = providers
                .sink(sinkResource.getType(), sink.path("type").asText())
                .connectionOptions(sinkResource);

        sourceOptions.remove("server-id");
        source.remove("datasource-id");
        sink.remove("datasource-id");
        sourceOptions.forEach(source::put);
        sinkOptions.forEach(sink::put);
        maskPassword(source);
        maskPassword(sink);

        Map<String, Object> yaml = JSONUtils.parseObject(
                JSONUtils.toJsonString(definition), new TypeReference<Map<String, Object>>() {
                });
        return YamlParserUtils.toBlockYAMLString(yaml);
    }

    String build(TaskExecutionContext context, DataSyncParameters parameters) {
        parameters.validate();
        ObjectNode definition = (ObjectNode) parameters.getDefinition().deepCopy();
        ObjectNode source = (ObjectNode) definition.path("source");
        ObjectNode sink = (ObjectNode) definition.path("sink");
        ObjectNode pipeline = (ObjectNode) definition.path("pipeline");

        DataSourceParameters sourceResource = resolve(context, DataSyncParameters.datasourceId(source));
        DataSourceParameters sinkResource = resolve(context, DataSyncParameters.datasourceId(sink));
        int parallelism = pipeline.has("parallelism") ? pipeline.get("parallelism").asInt() : 1;
        Map<String, String> sourceOptions = providers
                .source(sourceResource.getType(), source.path("type").asText())
                .connectionOptions(sourceResource, context.getTaskInstanceId(), parallelism);
        Map<String, String> sinkOptions = providers
                .sink(sinkResource.getType(), sink.path("type").asText())
                .connectionOptions(sinkResource);

        source.remove("datasource-id");
        sink.remove("datasource-id");
        sourceOptions.forEach(source::put);
        sinkOptions.forEach(sink::put);

        if (pipeline.has("local-time-zone") && !source.has("server-time-zone")) {
            source.put("server-time-zone", pipeline.path("local-time-zone").asText());
        }
        pipeline.put("name", context.getTaskName() + "-" + context.getTaskInstanceId());
        if (!pipeline.has("parallelism")) {
            pipeline.put("parallelism", 1);
        }
        configureCheckpointing(pipeline);
        return JSONUtils.toJsonString(definition);
    }

    private static void configureCheckpointing(ObjectNode pipeline) {
        JsonNode configured = pipeline.get("flink-conf");
        ObjectNode flinkConf;
        if (configured == null || configured.isNull()) {
            flinkConf = pipeline.putObject("flink-conf");
        } else if (configured.isObject()) {
            flinkConf = (ObjectNode) configured;
        } else {
            throw new IllegalArgumentException("pipeline.flink-conf must be an object");
        }

        if (!flinkConf.has("execution.checkpointing.interval")) {
            flinkConf.put("execution.checkpointing.interval", "30s");
        }
        if (!flinkConf.has("execution.checkpointing.num-retained")) {
            // Keep a small fallback window; the DAO retains the same latest three checkpoints.
            flinkConf.put("execution.checkpointing.num-retained", 3);
        }
        if (!flinkConf.has("execution.checkpointing.externalized-checkpoint-retention")) {
            flinkConf.put("execution.checkpointing.externalized-checkpoint-retention", "RETAIN_ON_CANCELLATION");
        }
        if (!flinkConf.has("execution.checkpointing.storage")) {
            flinkConf.put("execution.checkpointing.storage", "filesystem");
        }
        if (!flinkConf.has("execution.checkpointing.dir") && !flinkConf.has("state.checkpoints.dir")) {
            flinkConf.put("execution.checkpointing.dir", checkpointDirectory());
        }
    }

    private static String checkpointDirectory() {
        String configured = System.getProperty("yak.ops.data-sync.checkpoint-directory");
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv("YAK_OPS_DATA_SYNC_CHECKPOINT_DIRECTORY");
        }
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim();
        }
        return new File(new File(System.getProperty("user.dir"), "data"),
                "yak-ops/data-sync/checkpoints").toURI().toString();
    }

    private static void maskPassword(ObjectNode endpoint) {
        if (endpoint.has("password")) {
            endpoint.put("password", "******");
        }
    }

    private static DataSourceParameters resolve(TaskExecutionContext context, int id) {
        DataSourceParameters datasource = (DataSourceParameters) context.getResourceParametersHelper()
                .getResourceParameters(ResourceType.DATASOURCE, id);
        if (datasource == null) {
            throw new IllegalArgumentException("Datasource not available in task context: " + id);
        }
        return datasource;
    }
}
