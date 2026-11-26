package io.yak.ops.modeling.ddl.doris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.modeling.ddl.doris.model.ReplicationPlan;
import io.yak.ops.modeling.ddl.doris.planning.ReplicationPolicy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReplicationPolicyTest {

    private final ReplicationPolicy inspector = new ReplicationPolicy();

    @Test
    void shouldAutoSelectReplicaCountFromHealthyDefaultTagBackends() throws Exception {
        Connection connection = connection(
                new String[] {"true", "true", "true", "false", "true"},
                new String[] {"false", "false", "true", "false", "false"},
                new String[] {
                    "{\"location\" : \"default\"}",
                    "{\"location\" : \"default\"}",
                    "{\"location\" : \"default\"}",
                    "{\"location\" : \"default\"}",
                    "{\"location\" : \"online\"}"
                });

        ReplicationPlan plan = inspector.resolve(connection, null);

        assertEquals(2, plan.getAvailableBackendCount());
        assertEquals(2, plan.getReplicationNum());
        assertTrue(plan.isAutomatic());
    }

    @Test
    void shouldLimitAutomaticReplicaCountToThree() throws Exception {
        Connection connection = connection(
                new String[] {"true", "true", "true", "true"},
                new String[] {"false", "false", "false", "false"},
                new String[] {
                    "{\"location\" : \"default\"}",
                    "{\"location\" : \"default\"}",
                    "{\"location\" : \"default\"}",
                    "{\"location\" : \"default\"}"
                });

        ReplicationPlan plan = inspector.resolve(connection, null);

        assertEquals(4, plan.getAvailableBackendCount());
        assertEquals(3, plan.getReplicationNum());
    }

    @Test
    void shouldRejectManualReplicaCountAboveAvailableBackends() throws Exception {
        Connection connection = connection(
                new String[] {"true", "false", "false", "false"},
                new String[] {"false", "false", "false", "false"},
                new String[] {
                    "{\"location\" : \"default\"}",
                    "{\"location\" : \"default\"}",
                    "{\"location\" : \"default\"}",
                    "{\"location\" : \"default\"}"
                });

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> inspector.resolve(connection, 2));

        assertTrue(error.getMessage().contains("只有 1 个可用 BE"));
    }

    @Test
    void shouldAllowManualReplicaCountWhenBackendInspectionIsUnavailable() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.createStatement()).thenThrow(new SQLException("permission denied"));

        ReplicationPlan plan = inspector.resolve(connection, 1);

        assertEquals(1, plan.getReplicationNum());
        assertNull(plan.getAvailableBackendCount());
        assertTrue(plan.getWarning().contains("未能校验集群容量"));
    }

    @Test
    void shouldExplainHowToContinueWhenAutomaticInspectionIsUnavailable() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.createStatement()).thenThrow(new SQLException("permission denied"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> inspector.resolve(connection, null));

        assertTrue(error.getMessage().contains("手动指定副本数"));
    }

    private Connection connection(String[] alive, String[] decommissioned, String[] tags)
            throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        AtomicInteger row = new AtomicInteger();

        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SHOW BACKENDS")).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(3);
        when(metadata.getColumnLabel(1)).thenReturn("Alive");
        when(metadata.getColumnLabel(2)).thenReturn("SystemDecommissioned");
        when(metadata.getColumnLabel(3)).thenReturn("Tag");
        when(resultSet.next()).thenAnswer(invocation -> row.getAndIncrement() < alive.length);
        when(resultSet.getString("Alive")).thenAnswer(invocation -> alive[row.get() - 1]);
        when(resultSet.getString("SystemDecommissioned"))
                .thenAnswer(invocation -> decommissioned[row.get() - 1]);
        when(resultSet.getString("Tag")).thenAnswer(invocation -> tags[row.get() - 1]);
        return connection;
    }
}
