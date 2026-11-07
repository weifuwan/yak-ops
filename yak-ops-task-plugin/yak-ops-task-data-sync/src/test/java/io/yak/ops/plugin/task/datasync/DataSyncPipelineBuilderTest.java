package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.common.utils.PropertyUtils;
import io.yak.ops.plugin.datasource.api.constants.DataSourceConstants;
import io.yak.ops.plugin.datasource.api.utils.PasswordUtils;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.enums.ResourceType;
import io.yak.ops.plugin.task.api.parameters.resource.DataSourceParameters;
import io.yak.ops.plugin.task.api.parameters.resource.ResourceParametersHelper;
import io.yak.ops.spi.enums.DbType;

import org.apache.flink.cdc.cli.parser.YamlPipelineDefinitionParser;
import org.apache.flink.cdc.common.configuration.Configuration;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class DataSyncPipelineBuilderTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Test
    void buildsNativeFlinkCdcYamlUsingDatasourcePlugin() throws Exception {
        String yaml = new DataSyncPipelineBuilder().build(context(), DataSyncParametersTest.valid());
        new YamlPipelineDefinitionParser().parse(yaml, new Configuration());
        JsonNode pipeline = YAML.readTree(yaml);
        assertEquals("mysql", pipeline.path("source").path("type").asText());
        assertEquals("127.0.0.1", pipeline.path("source").path("hostname").asText());
        assertEquals(3306, pipeline.path("source").path("port").asInt());
        assertEquals("cdc_reader", pipeline.path("source").path("username").asText());
        assertEquals("secret_source", pipeline.path("source").path("password").asText());
        assertEquals("jdbc:mysql://127.0.0.1:3307/dst_db", pipeline.path("sink").path("url").asText());
        assertTrue(pipeline.path("source").path("tables").asText().startsWith("\\Q"));
        assertEquals("dst_db.orders", pipeline.path("route").get(0).path("sink-table").asText());
        assertEquals("evolve", pipeline.path("pipeline").path("schema.change.behavior").asText());
        assertEquals("drop.column", pipeline.path("sink").path("exclude.schema.changes").get(0).asText());
        assertFalse(yaml.contains("datasource-id"));
    }

    @Test
    void datasourceUrlPropertiesComeFromRegisteredProcessor() throws Exception {
        TaskExecutionContext ctx = context();
        DataSourceParameters target = (DataSourceParameters) ctx.getResourceParametersHelper()
                .getResourceParameters(ResourceType.DATASOURCE, 2);
        target.setConnectionParams(JSONUtils.toJsonString(java.util.Map.of(
                "jdbcUrl", "jdbc:mysql://127.0.0.1:3307/dst_db",
                "user", "writer", "password", "secret_target",
                "other", java.util.Map.of("useUnicode", "true"))));
        JsonNode pipeline = YAML.readTree(new DataSyncPipelineBuilder().build(ctx, DataSyncParametersTest.valid()));
        assertTrue(pipeline.path("sink").path("url").asText().contains("useUnicode=true"));
    }

    @Test
    void decryptsCredentialsThroughDatasourceFacility() throws Exception {
        try (MockedStatic<PropertyUtils> properties = Mockito.mockStatic(PropertyUtils.class)) {
            properties.when(() -> PropertyUtils.getBoolean(
                    DataSourceConstants.DATASOURCE_ENCRYPTION_ENABLE, false)).thenReturn(true);
            properties.when(() -> PropertyUtils.getString(
                    DataSourceConstants.DATASOURCE_ENCRYPTION_SALT,
                    DataSourceConstants.DATASOURCE_ENCRYPTION_SALT_DEFAULT)).thenReturn("test-salt");
            TaskExecutionContext ctx = context(PasswordUtils.encodePassword("secret_source"),
                    PasswordUtils.encodePassword("secret_target"));
            JsonNode pipeline = YAML.readTree(
                    new DataSyncPipelineBuilder().build(ctx, DataSyncParametersTest.valid()));
            assertEquals("secret_source", pipeline.path("source").path("password").asText());
            assertEquals("secret_target", pipeline.path("sink").path("password").asText());
        }
    }

    @Test
    void rangeChangesForDifferentTaskInstances() throws Exception {
        TaskExecutionContext firstContext = context();
        TaskExecutionContext nextContext = context();
        nextContext.setTaskInstanceId(42);
        String first = YAML.readTree(new DataSyncPipelineBuilder().build(
                firstContext, DataSyncParametersTest.valid())).path("source").path("server-id").asText();
        String next = YAML.readTree(new DataSyncPipelineBuilder().build(
                nextContext, DataSyncParametersTest.valid())).path("source").path("server-id").asText();
        assertFalse(first.equals(next));
    }

    @Test
    void serverIdRangeReservesReaderAndBinlogSlots() throws Exception {
        DataSyncParameters parameters = DataSyncParametersTest.valid();
        ((com.fasterxml.jackson.databind.node.ObjectNode) parameters.getDefinition().path("pipeline"))
                .put("parallelism", 2);
        JsonNode tree = YAML.readTree(new DataSyncPipelineBuilder().build(context(), parameters));
        String[] range = tree.path("source").path("server-id").asText().split("-");
        assertEquals(2, range.length);
        assertEquals(Long.parseLong(range[0]) + 2, Long.parseLong(range[1]));
    }

    private static TaskExecutionContext context() {
        return context("secret_source", "secret_target");
    }

    private static TaskExecutionContext context(String sourcePassword, String targetPassword) {
        ResourceParametersHelper helper = new ResourceParametersHelper();
        helper.put(ResourceType.DATASOURCE, 1,
                datasource("jdbc:mysql://127.0.0.1:3306/src_db", "cdc_reader", sourcePassword));
        helper.put(ResourceType.DATASOURCE, 2,
                datasource("jdbc:mysql://127.0.0.1:3307/dst_db", "writer", targetPassword));
        TaskExecutionContext context = new TaskExecutionContext();
        context.setTaskName("order_sync");
        context.setTaskInstanceId(41);
        context.setResourceParametersHelper(helper);
        return context;
    }

    private static DataSourceParameters datasource(String url, String user, String password) {
        DataSourceParameters parameters = new DataSourceParameters();
        parameters.setType(DbType.MYSQL);
        parameters.setConnectionParams(JSONUtils.toJsonString(java.util.Map.of(
                "jdbcUrl", url, "user", user, "password", password)));
        return parameters;
    }
}
