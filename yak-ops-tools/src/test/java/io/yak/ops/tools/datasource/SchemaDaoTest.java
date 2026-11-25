package io.yak.ops.tools.datasource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.annotation.DbType;

class SchemaDaoTest {

    private SchemaDao newSchemaDao(boolean hasTables) throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet tables = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getCatalog()).thenReturn("yak-ops");
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getTables(eq("yak-ops"), any(), eq("%"), any())).thenReturn(tables);
        when(tables.next()).thenReturn(hasTables);
        return new SchemaDao(dataSource, DbType.MYSQL);
    }

    @Test
    void existingTablesAreDetected() throws SQLException {
        Assertions.assertTrue(newSchemaDao(true).hasExistingTables());
    }

    @Test
    void emptyDatabaseCanBeInitialized() throws SQLException {
        Assertions.assertFalse(newSchemaDao(false).hasExistingTables());
    }

    @Test
    void legacyDorisKeyTablesAreMigratedBeforeTheyAreDropped() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet tables = mock(ResultSet.class);
        ResultSet columns = mock(ResultSet.class);
        Statement statement = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getCatalog()).thenReturn("yak-ops");
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.createStatement()).thenReturn(statement);
        when(metadata.getTables(anyString(), nullable(String.class), anyString(), any()))
                .thenAnswer(invocation -> {
                    String tableName = invocation.getArgument(2);
                    ResultSet result = mock(ResultSet.class);
                    when(result.next()).thenReturn(true);
                    when(result.getString("TABLE_NAME")).thenReturn(tableName);
                    return result;
                });
        when(metadata.getColumns(anyString(), nullable(String.class), anyString(), anyString()))
                .thenReturn(columns);
        when(columns.next()).thenReturn(false);
        when(statement.executeUpdate(anyString())).thenReturn(1);

        SchemaDao schemaDao = new SchemaDao(dataSource, DbType.MYSQL);
        Method migration = SchemaDao.class.getDeclaredMethod("ensureDorisPublishSchema");
        migration.setAccessible(true);
        migration.invoke(schemaDao);

        org.mockito.ArgumentCaptor<String> updates =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(statement, times(3)).executeUpdate(updates.capture());
        Assertions.assertTrue(updates.getAllValues().stream()
                .anyMatch(sql -> sql.contains("primary_flag = TRUE")
                        && sql.contains("can_null = FALSE")
                        && sql.contains("doris_key_fields")));
        org.mockito.ArgumentCaptor<String> ddl =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(statement, times(12)).execute(ddl.capture());
        Assertions.assertTrue(ddl.getAllValues().stream()
                .anyMatch(sql -> sql.contains("ADD COLUMN physical_schema longtext NULL")));
    }

    @Test
    void instanceStateCountIndexesAreCreatedAndLegacyIndexesDropped() throws Exception {
        SchemaMocks mocks = mockSchema("idx_project_submit_time", "idx_project_start_time");

        mocks.schemaDao.ensureInstanceStateCountIndexes();

        org.mockito.ArgumentCaptor<String> statements =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mocks.statement, times(4)).execute(statements.capture());
        Assertions.assertTrue(statements.getAllValues().contains(
                "CREATE INDEX idx_project_submit_time_state ON `t_yak_ops_task_instance` "
                        + "(project_code, submit_time, state)"));
        Assertions.assertTrue(statements.getAllValues().contains(
                "ALTER TABLE `t_yak_ops_task_instance` DROP INDEX idx_project_submit_time"));
        Assertions.assertTrue(statements.getAllValues().contains(
                "CREATE INDEX idx_project_start_time_state ON `t_yak_ops_workflow_instance` "
                        + "(project_code, start_time, is_sub_workflow, state)"));
        Assertions.assertTrue(statements.getAllValues().contains(
                "ALTER TABLE `t_yak_ops_workflow_instance` DROP INDEX idx_project_start_time"));
    }

    @Test
    void existingInstanceStateCountIndexesAreLeftAlone() throws Exception {
        SchemaMocks mocks = mockSchema("idx_project_submit_time_state", "idx_project_start_time_state");

        mocks.schemaDao.ensureInstanceStateCountIndexes();

        verify(mocks.statement, never()).execute(anyString());
    }

    private SchemaMocks mockSchema(String taskIndexName, String workflowIndexName) throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getCatalog()).thenReturn("yak-ops");
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.createStatement()).thenReturn(statement);
        when(metadata.getTables(anyString(), nullable(String.class), anyString(), any()))
                .thenAnswer(invocation -> {
                    ResultSet result = mock(ResultSet.class);
                    when(result.next()).thenReturn(true);
                    return result;
                });
        when(metadata.getIndexInfo(anyString(), nullable(String.class), anyString(), anyBoolean(), anyBoolean()))
                .thenAnswer(invocation -> {
                    String tableName = invocation.getArgument(2);
                    return indexNames(
                            "t_yak_ops_task_instance".equalsIgnoreCase(tableName) ? taskIndexName : workflowIndexName);
                });
        return new SchemaMocks(new SchemaDao(dataSource, DbType.MYSQL), statement);
    }

    private static ResultSet indexNames(String... names) throws SQLException {
        ResultSet result = mock(ResultSet.class);
        AtomicInteger cursor = new AtomicInteger(-1);
        when(result.next()).thenAnswer(invocation -> cursor.incrementAndGet() < names.length);
        when(result.getString("INDEX_NAME")).thenAnswer(invocation -> names[cursor.get()]);
        return result;
    }

    private static final class SchemaMocks {

        private final SchemaDao schemaDao;

        private final Statement statement;

        private SchemaMocks(SchemaDao schemaDao, Statement statement) {
            this.schemaDao = schemaDao;
            this.statement = statement;
        }
    }
}
