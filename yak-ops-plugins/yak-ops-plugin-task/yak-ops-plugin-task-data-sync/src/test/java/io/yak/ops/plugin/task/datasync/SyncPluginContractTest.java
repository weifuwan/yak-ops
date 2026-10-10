package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.TaskPluginFactory;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * DATA_SYNC Task Plugin 的类型注册、参数解析和基础校验测试。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
class SyncPluginContractTest {

    @Test
    void shouldDiscoverPluginViaServiceLoader() {
        TaskPluginRegistry registry = new TaskPluginRegistry();

        assertTrue(registry.types().contains("DATA_SYNC"));
        assertInstanceOf(SyncPlugin.class, registry.get("data-sync"));
        assertEquals("DATA_SYNC", registry.get("data_sync").type());
    }

    @Test
    void shouldParseValidOfflineAndRealtimeParameters() {
        TaskPluginRegistry registry = new TaskPluginRegistry();

        SyncParameters offline = assertInstanceOf(
                SyncParameters.class, registry.parseParameters("DATA_SYNC", validParameters("OFFLINE")));
        SyncParameters realtime = assertInstanceOf(
                SyncParameters.class, registry.parseParameters("DATA_SYNC", validParameters("REALTIME")));

        assertEquals(DataSyncType.OFFLINE, offline.syncType());
        assertEquals(DataSyncType.REALTIME, realtime.syncType());
        assertEquals("orders", offline.sourceTable());
        assertEquals("orders_copy", offline.targetTable());
    }

    @Test
    void shouldRejectMissingMalformedAndUnsupportedParameters() {
        TaskPluginRegistry registry = new TaskPluginRegistry();

        assertThrows(TaskPluginException.class, () -> registry.parseParameters("DATA_SYNC", "{}"));
        assertThrows(TaskPluginException.class, () -> registry.parseParameters("DATA_SYNC", "{"));
        assertThrows(TaskPluginException.class, () -> registry.parseParameters("DATA_SYNC", "[]"));
        assertThrows(TaskPluginException.class, () -> registry.parseParameters("DATA_SYNC", "null"));
        assertThrows(
                TaskPluginException.class,
                () -> registry.parseParameters("DATA_SYNC", validParameters("UNKNOWN")));
        assertThrows(
                TaskPluginException.class,
                () -> registry.parseParameters("DATA_SYNC", validParameters("OFFLINE").replace(
                        "\"sourceTable\": \"orders\"", "\"sourceTable\": \"\"")));
        assertThrows(
                TaskPluginException.class,
                () -> registry.parseParameters("DATA_SYNC", validParameters("OFFLINE").replace(
                        "\"retryPolicy\": {}", "\"retryPolicy\": []")));
        assertThrows(
                TaskPluginException.class,
                () -> registry.parseParameters(
                        "DATA_SYNC", validParameters("OFFLINE").replace("\"retryPolicy\": {}", "\"tableRoutes\": []")));
    }

    @Test
    void shouldRejectUnknownAndDuplicatePluginTypes() {
        TaskPluginRegistry registry = new TaskPluginRegistry();

        assertThrows(TaskPluginException.class, () -> registry.get("SQL"));
        assertThrows(TaskPluginException.class, () -> registry.get(" "));
        assertThrows(
                TaskPluginException.class,
                () -> new TaskPluginRegistry(List.of(new SyncPluginFactory(), new SyncPluginFactory())));
        assertThrows(TaskPluginException.class, () -> new TaskPluginRegistry(List.of(new TaskPluginFactory() {
            @Override
            public String type() {
                return "SQL";
            }

            @Override
            public TaskPlugin create() {
                return new SyncPlugin();
            }
        })));
    }

    @Test
    void shouldRejectIncompatibleRealtimeWriteModeWithoutLeakingRawParameters() {
        TaskPluginRegistry registry = new TaskPluginRegistry();

        assertThrows(
                TaskPluginException.class,
                () -> registry.parseParameters(
                        "DATA_SYNC", validParameters("REALTIME").replace("APPEND", "UPSERT")));

        String sensitive = validParameters("OFFLINE").replace(
                "\"sourceTable\": \"orders\"", "\"sourceTable\": {\"password\": \"secret-value\"}");
        TaskPluginException failure =
                assertThrows(TaskPluginException.class, () -> registry.parseParameters("DATA_SYNC", sensitive));
        assertEquals("Invalid DATA_SYNC parameters", failure.getMessage());
    }

    private String validParameters(String syncType) {
        return """
                {
                  "syncType": "%s",
                  "writeMode": "APPEND",
                  "sourceDataSourceId": "source",
                  "sourceTable": "orders",
                  "targetDataSourceId": "target",
                  "targetTable": "orders_copy",
                  "retryPolicy": {}
                }
                """
                .formatted(syncType);
    }
}
