package io.yak.ops.tools.datasource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.lang.reflect.Method;

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
                .thenReturn(tables);
        when(tables.next()).thenReturn(true);
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
        verify(statement, times(7)).execute(anyString());
    }
}
