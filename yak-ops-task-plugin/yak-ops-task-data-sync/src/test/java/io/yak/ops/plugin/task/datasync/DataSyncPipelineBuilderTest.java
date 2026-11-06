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

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;

class DataSyncPipelineBuilderTest {

    @Test
    void resolvesSavedDatasourceCredentialsAndMapsTableRoute() {
        TaskExecutionContext context = context();
        DataSyncParameters params = DataSyncParametersTest.parse("INITIAL", "STREAMING", "UPSERT");

        JsonNode pipeline = JSONUtils.parseObject(new DataSyncPipelineBuilder().build(context, params));
        assertEquals("mysql", pipeline.path("source").path("type").asText());
        assertEquals("127.0.0.1", pipeline.path("source").path("hostname").asText());
        assertEquals(3306, pipeline.path("source").path("port").asInt());
        assertEquals("cdc_reader", pipeline.path("source").path("username").asText());
        assertEquals("secret_source", pipeline.path("source").path("password").asText());
        assertEquals("jdbc:mysql://127.0.0.1:3307/target_db",
                pipeline.path("sink").path("url").asText());
        assertEquals("initial", pipeline.path("source").path("scan.startup.mode").asText());
        assertEquals("target_db.orders",
                pipeline.path("route").get(0).path("sink-table").asText());
        assertTrue("source_db.orders".matches(pipeline.path("source").path("tables").asText()));
        assertFalse("source_db.orders_extra".matches(pipeline.path("source").path("tables").asText()));
        assertEquals("evolve", pipeline.path("pipeline").path("schema.change.behavior").asText());
    }

    @Test
    void decodesStoredCredentialsWhenDatasourceEncryptionIsEnabled() {
        try (MockedStatic<PropertyUtils> properties = Mockito.mockStatic(PropertyUtils.class)) {
            properties.when(() -> PropertyUtils.getBoolean(
                    DataSourceConstants.DATASOURCE_ENCRYPTION_ENABLE, false)).thenReturn(true);
            properties.when(() -> PropertyUtils.getString(
                    DataSourceConstants.DATASOURCE_ENCRYPTION_SALT,
                    DataSourceConstants.DATASOURCE_ENCRYPTION_SALT_DEFAULT)).thenReturn("test-salt");
            String sourcePassword = PasswordUtils.encodePassword("secret_source");
            String targetPassword = PasswordUtils.encodePassword("secret_target");
            TaskExecutionContext context = context(sourcePassword, targetPassword);

            JsonNode pipeline = JSONUtils.parseObject(new DataSyncPipelineBuilder().build(context,
                    DataSyncParametersTest.parse("INITIAL", "STREAMING", "UPSERT")));
            assertEquals("secret_source", pipeline.path("source").path("password").asText());
            assertEquals("secret_target", pipeline.path("sink").path("password").asText());
        }
    }

    @Test
    void preservesDisabledSchemaEvolution() {
        DataSyncParameters params = DataSyncParametersTest.parse("INITIAL", "STREAMING", "UPSERT");
        params.getSink().getSchemaPolicy().getEvolution().setEnabled(false);

        JsonNode pipeline = JSONUtils.parseObject(new DataSyncPipelineBuilder().build(context(), params));

        assertEquals("exception", pipeline.path("pipeline").path("schema.change.behavior").asText());
    }

    @Test
    void serverIdDependsOnTaskInstance() {
        DataSyncParameters params = DataSyncParametersTest.parse("INITIAL", "STREAMING", "UPSERT");
        JsonNode first = JSONUtils.parseObject(new DataSyncPipelineBuilder().build(context(), params));
        TaskExecutionContext second = context();
        second.setTaskInstanceId(42);
        JsonNode next = JSONUtils.parseObject(new DataSyncPipelineBuilder().build(second, params));

        assertFalse(first.path("source").path("server-id").asText()
                .equals(next.path("source").path("server-id").asText()));
    }

    private static TaskExecutionContext context() {
        return context("secret_source", "secret_target");
    }

    private static TaskExecutionContext context(String sourcePassword, String targetPassword) {
        DataSourceParameters source = datasource("jdbc:mysql://127.0.0.1:3306/source_db", "cdc_reader", sourcePassword);
        DataSourceParameters target = datasource("jdbc:mysql://127.0.0.1:3307/target_db", "writer", targetPassword);
        ResourceParametersHelper helper = new ResourceParametersHelper();
        helper.put(ResourceType.DATASOURCE, 1, source);
        helper.put(ResourceType.DATASOURCE, 2, target);
        TaskExecutionContext context = new TaskExecutionContext();
        context.setTaskName("order_sync");
        context.setTaskInstanceId(41);
        context.setResourceParametersHelper(helper);
        return context;
    }

    private static DataSourceParameters datasource(String url, String user, String password) {
        DataSourceParameters value = new DataSourceParameters();
        value.setType(DbType.MYSQL);
        value.setConnectionParams(JSONUtils.toJsonString(java.util.Map.of(
                "jdbcUrl", url, "user", user, "password", password)));
        return value;
    }
}
