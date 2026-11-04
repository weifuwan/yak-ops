package io.yak.ops.tools.datasource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

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
}
