package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.enums.ResourceType;
import io.yak.ops.plugin.task.api.parameters.resource.DataSourceParameters;

import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** Compiles a connector-independent Pipeline definition into executable Flink CDC YAML. */
final class DataSyncPipelineBuilder {

    private final DataSyncConnectorRegistry providers = new DataSyncConnectorRegistry();

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
        return JSONUtils.toJsonString(definition);
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
