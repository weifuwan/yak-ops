package io.yak.ops.tools.datasource.postgresql.v16;

import io.yak.ops.tools.datasource.YakOpsManager;
import io.yak.ops.tools.datasource.jupiter.YakOpsDatabaseContainer;
import io.yak.ops.tools.datasource.postgresql.YakOpsPostgresqlProfile;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@YakOpsPostgresqlProfile
@YakOpsDatabaseContainer(imageName = "postgres:16.0")
class InitializeSchemaIT {

    @Autowired
    private YakOpsManager manager;

    @Autowired
    private DataSource dataSource;

    private void assertCurrentSchema(Statement statement) throws Exception {
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
        statement.executeQuery(
                "SELECT table_model, publish_mode, publish_remark, key_fields, ddl_content "
                        + "FROM t_yak_ops_dimension_table_publish WHERE 1 = 0").close();
    }

    @Test
    void initializeBaselineAndPreserveExistingData() throws Exception {
        manager.initializeOrValidate();

        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery("SELECT version FROM t_yak_ops_version")) {
                Assertions.assertTrue(resultSet.next());
                Assertions.assertEquals("3.4.3", resultSet.getString(1));
            }
            assertCurrentSchema(statement);
            Assertions.assertEquals(1, statement.executeUpdate(
                    "UPDATE t_yak_ops_user SET user_name = 'baseline_preserved' WHERE id = 1"));
        }

        // An initialized database must not be replaced by the DROP TABLE-based full SQL.
        manager.initializeOrValidate();
        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT user_name FROM t_yak_ops_user WHERE id = 1")) {
            Assertions.assertTrue(resultSet.next());
            Assertions.assertEquals("baseline_preserved", resultSet.getString(1));
        }
    }
}
