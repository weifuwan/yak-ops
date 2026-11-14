package io.yak.ops.modeling.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.common.sql.SqlScriptRunner;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class WarehouseLayerSeedSqlTest {

    @Test
    void shouldSeedFiveStandardLayersPerProjectAndRespectOdsMarker() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:warehouse_layer_seed;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t_yak_ops_project (code BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO t_yak_ops_project (code) VALUES (1001), (1002)");
        }

        SqlScriptRunner runner = new SqlScriptRunner(dataSource, "sql/modeling_h2.sql");
        runner.execute();
        assertEquals(5, countLayers(dataSource, 1001));
        assertEquals(5, countLayers(dataSource, 1002));

        runner.execute();
        assertEquals(5, countLayers(dataSource, 1001));
        assertEquals(5, countLayers(dataSource, 1002));

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "DELETE FROM t_yak_ops_dw_layer WHERE project_code = 1001 AND en_short_name = 'DIM'");
            statement.executeUpdate(
                    "DELETE FROM t_yak_ops_dw_layer WHERE project_code = 1002 AND en_short_name <> 'ODS'");
            statement.executeUpdate(
                    "UPDATE t_yak_ops_dw_layer SET create_by = 'admin' "
                            + "WHERE project_code = 1002 AND en_short_name = 'ODS'");
        }

        runner.execute();

        assertEquals(4, countLayers(dataSource, 1001));
        assertEquals(5, countLayers(dataSource, 1002));
        assertEquals(
                new HashSet<>(Arrays.asList("ADS", "DIM", "DWD", "DWS", "ODS")),
                layerShortNames(dataSource, 1002));
    }

    private int countLayers(JdbcDataSource dataSource, long projectCode) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT COUNT(*) FROM t_yak_ops_dw_layer WHERE project_code = " + projectCode)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private Set<String> layerShortNames(JdbcDataSource dataSource, long projectCode) throws Exception {
        Set<String> names = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT en_short_name FROM t_yak_ops_dw_layer WHERE project_code = " + projectCode)) {
            while (resultSet.next()) {
                names.add(resultSet.getString(1));
            }
        }
        return names;
    }
}
