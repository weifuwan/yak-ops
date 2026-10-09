package io.yak.ops.business.datasync.execution.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.flow.connector.jdbc.JdbcSaveMode;
import io.yak.ops.flow.connector.jdbc.JdbcWriteMode;
import org.junit.jupiter.api.Test;

class OfflineSyncWriteModePlannerTest {

    @Test
    void shouldMapProductModesToJdbcSaveAndWriteModes() {
        assertEquals(JdbcSaveMode.APPEND, OfflineSyncExecutionPlanner.saveMode("APPEND"));
        assertEquals(JdbcSaveMode.OVERWRITE, OfflineSyncExecutionPlanner.saveMode("OVERWRITE"));
        assertEquals(JdbcSaveMode.APPEND, OfflineSyncExecutionPlanner.saveMode("UPSERT"));
        assertEquals(JdbcSaveMode.APPEND, OfflineSyncExecutionPlanner.saveMode(null));

        assertEquals(JdbcWriteMode.INSERT, OfflineSyncExecutionPlanner.writeMode("APPEND"));
        assertEquals(JdbcWriteMode.INSERT, OfflineSyncExecutionPlanner.writeMode("OVERWRITE"));
        assertEquals(JdbcWriteMode.UPSERT, OfflineSyncExecutionPlanner.writeMode("UPSERT"));
        assertEquals(JdbcWriteMode.INSERT, OfflineSyncExecutionPlanner.writeMode(null));
    }
}
