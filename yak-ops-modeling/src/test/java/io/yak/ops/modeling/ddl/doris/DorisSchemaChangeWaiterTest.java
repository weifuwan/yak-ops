package io.yak.ops.modeling.ddl.doris;

import io.yak.ops.modeling.ddl.doris.execution.SchemaChangeWaiter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class SchemaChangeWaiterTest {

    private final SchemaChangeWaiter waiter = new SchemaChangeWaiter();

    @Test
    void shouldWaitForNewSchemaChangeJobToFinish() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet previous = jobResult(10L, "FINISHED", "");
        ResultSet current = jobResult(11L, "FINISHED", "");
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(previous, current);

        Long previousJobId = waiter.latestJobId(connection, "warehouse", "test1");
        waiter.awaitCompletion(connection, "warehouse", "test1", previousJobId);

        assertEquals(10L, previousJobId);
    }

    @Test
    void shouldSurfaceCancelledSchemaChangeMessage() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet previous = jobResult(10L, "FINISHED", "");
        ResultSet current = jobResult(11L, "CANCELLED", "invalid column definition");
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(previous, current);

        Long previousJobId = waiter.latestJobId(connection, "warehouse", "test1");
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> waiter.awaitCompletion(connection, "warehouse", "test1", previousJobId));

        assertTrue(error.getMessage().contains("invalid column definition"));
    }

    @Test
    void shouldRejectOverlappingSchemaChangeJob() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet running = jobResult(10L, "RUNNING", "");
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(running);

        IllegalStateException error = assertThrows(
                IllegalStateException.class, () -> waiter.latestJobId(connection, "warehouse", "test1"));

        assertTrue(error.getMessage().contains("已有 Schema Change 任务"));
    }

    private ResultSet jobResult(long jobId, String state, String message) throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true);
        when(result.getLong("JobId")).thenReturn(jobId);
        when(result.getString("State")).thenReturn(state);
        when(result.getString("Msg")).thenReturn(message);
        return result;
    }
}
