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

        new SqlScriptRunner(dataSource, "sql/yak-ops_h2.sql").execute();

        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery("SELECT version FROM t_yak_ops_version")) {
                Assertions.assertTrue(resultSet.next());
                Assertions.assertEquals("3.4.3", resultSet.getString(1));
            }
            try (
                    ResultSet resultSet = statement.executeQuery(
                            "SELECT COUNT(*) FROM t_yak_ops_workflow_instance")) {
                Assertions.assertTrue(resultSet.next());
            }
            statement.executeQuery("SELECT missed_fire_policy FROM t_yak_ops_schedules WHERE 1 = 0").close();
            statement.executeQuery(
                    "SELECT user_id, scene, item_key, favorite, sort_order, use_count "
                            + "FROM t_yak_ops_user_preference WHERE 1 = 0").close();
            statement.executeQuery(
                    "SELECT execution_id, task_instance_id, submitted_at, job_id, deployment_mode, "
                            + "execution_state, metrics_state, latest_sequence, latest_snapshot_json, "
                            + "final_sequence, final_snapshot_json, finalized "
                            + "FROM t_yak_ops_data_sync_execution_report WHERE 1 = 0").close();
            statement.executeQuery(
                    "SELECT execution_id, table_key, latest_metrics_json, final_metrics_json, latest_sequence "
                            + "FROM t_yak_ops_data_sync_table_report WHERE 1 = 0").close();
            statement.executeQuery(
                    "SELECT recovery_point_id, checkpoint_id, runtime_mode, recovery_type, restore_path, "
                            + "completed_at, used_by_workflow_instance_id, used_by_instance_id "
                            + "FROM t_yak_ops_data_sync_recovery_point WHERE 1 = 0").close();
        }
    }
}
