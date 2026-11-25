package io.yak.ops.tools.datasource.h2;

import io.yak.ops.tools.datasource.SchemaDao;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.annotation.DbType;

class SchemaDaoMetadataTest {

    @Test
    void detectsLegacyPartialAndQuartzTables() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:yak_ops_schema_detection;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=true");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        SchemaDao schemaDao = new SchemaDao(dataSource, DbType.H2);
        Assertions.assertFalse(schemaDao.hasLegacySchemaTables());
        Assertions.assertFalse(schemaDao.hasExistingTables());

        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t_ds_user (id int)");
            Assertions.assertTrue(schemaDao.hasLegacySchemaTables());
            Assertions.assertTrue(schemaDao.hasExistingTables());

            statement.execute("DROP TABLE t_ds_user");
            statement.execute("CREATE TABLE t_yak_ops_user (id int)");
            Assertions.assertFalse(schemaDao.hasLegacySchemaTables());
            Assertions.assertTrue(schemaDao.hasExistingTables());

            statement.execute("DROP TABLE t_yak_ops_user");
            statement.execute("CREATE TABLE QRTZ_LOCKS (id int)");
            Assertions.assertFalse(schemaDao.hasLegacySchemaTables());
            Assertions.assertTrue(schemaDao.hasExistingTables());
        }
    }

    @Test
    void stateCountIndexesReplaceLegacyIndexes() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:yak_ops_state_index;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=true");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE t_yak_ops_task_instance "
                            + "(id int, project_code bigint, submit_time datetime, state tinyint)");
            statement.execute(
                    "CREATE INDEX idx_project_submit_time ON t_yak_ops_task_instance (project_code, submit_time)");
            statement.execute(
                    "CREATE TABLE t_yak_ops_workflow_instance "
                            + "(id int, project_code bigint, start_time datetime, is_sub_workflow int, state tinyint)");
            statement.execute(
                    "CREATE INDEX idx_project_start_time ON t_yak_ops_workflow_instance (project_code, start_time)");
        }

        SchemaDao schemaDao = new SchemaDao(dataSource, DbType.H2);
        schemaDao.ensureInstanceStateCountIndexes();
        schemaDao.ensureInstanceStateCountIndexes();

        Assertions.assertEquals(
                Arrays.asList("project_code", "submit_time", "state"),
                indexColumns(dataSource, "t_yak_ops_task_instance", "idx_project_submit_time_state"));
        Assertions.assertEquals(
                Arrays.asList("project_code", "start_time", "is_sub_workflow", "state"),
                indexColumns(dataSource, "t_yak_ops_workflow_instance", "idx_project_start_time_state"));
        Assertions.assertTrue(
                indexColumns(dataSource, "t_yak_ops_task_instance", "idx_project_submit_time").isEmpty());
        Assertions.assertTrue(
                indexColumns(dataSource, "t_yak_ops_workflow_instance", "idx_project_start_time").isEmpty());
    }

    private static List<String> indexColumns(JdbcDataSource dataSource, String tableName, String indexName)
            throws Exception {
        List<String> columns = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (
                    ResultSet indexes = metadata.getIndexInfo(
                            connection.getCatalog(), connection.getSchema(), tableName, false, false)) {
                while (indexes.next()) {
                    if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                        columns.add(indexes.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return columns;
    }
}
