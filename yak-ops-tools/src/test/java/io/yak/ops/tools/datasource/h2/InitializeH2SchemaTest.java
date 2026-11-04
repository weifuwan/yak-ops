package io.yak.ops.tools.datasource.h2;

import io.yak.ops.common.sql.SqlScriptRunner;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class InitializeH2SchemaTest {

    @Test
    void initializeBaseline() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:yak_ops_schema_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=true");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        new SqlScriptRunner(dataSource, "sql/dolphinscheduler_h2.sql").execute();

        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery("SELECT version FROM t_ds_version")) {
                Assertions.assertTrue(resultSet.next());
                Assertions.assertEquals("3.4.3", resultSet.getString(1));
            }
            try (
                    ResultSet resultSet = statement.executeQuery(
                            "SELECT COUNT(*) FROM t_ds_workflow_instance")) {
                Assertions.assertTrue(resultSet.next());
            }
            statement.executeQuery("SELECT missed_fire_policy FROM t_ds_schedules WHERE 1 = 0").close();
        }
    }
}
