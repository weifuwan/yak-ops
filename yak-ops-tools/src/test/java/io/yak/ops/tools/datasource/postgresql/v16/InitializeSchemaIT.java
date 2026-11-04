package io.yak.ops.tools.datasource.postgresql.v16;

import io.yak.ops.tools.datasource.DolphinSchedulerManager;
import io.yak.ops.tools.datasource.jupiter.DolphinSchedulerDatabaseContainer;
import io.yak.ops.tools.datasource.postgresql.DolphinSchedulerPostgresqlProfile;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DolphinSchedulerPostgresqlProfile
@DolphinSchedulerDatabaseContainer(imageName = "postgres:16.0")
class InitializeSchemaIT {

    @Autowired
    private DolphinSchedulerManager manager;

    @Autowired
    private DataSource dataSource;

    @Test
    void initializeBaselineAndPreserveExistingData() throws Exception {
        manager.initializeOrValidate();

        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery("SELECT version FROM t_ds_version")) {
                Assertions.assertTrue(resultSet.next());
                Assertions.assertEquals("3.4.3", resultSet.getString(1));
            }
            Assertions.assertEquals(1, statement.executeUpdate(
                    "UPDATE t_ds_user SET user_name = 'baseline_preserved' WHERE id = 1"));
        }

        // An initialized database must not be replaced by the DROP TABLE-based full SQL.
        manager.initializeOrValidate();
        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT user_name FROM t_ds_user WHERE id = 1")) {
            Assertions.assertTrue(resultSet.next());
            Assertions.assertEquals("baseline_preserved", resultSet.getString(1));
        }
    }
}
