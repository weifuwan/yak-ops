package io.yak.ops.modeling.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.yak.ops.common.sql.SqlScriptRunner;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class DefaultDataDomainSeedSqlTest {

    @Test
    void shouldSeedProtectedDomainAndBusinessProcessOncePerProject() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:default_domain_seed;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t_yak_ops_project (code BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO t_yak_ops_project (code) VALUES (1001), (1002)");
        }

        SqlScriptRunner runner = new SqlScriptRunner(dataSource, "sql/modeling_h2.sql");
        runner.execute();

        assertSeeded(dataSource, 1001);
        assertSeeded(dataSource, 1002);

        // Existing custom domain data must remain untouched by subsequent schema initialization.
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO t_yak_ops_data_domain "
                    + "(id, project_code, en_short_name, en_name, cn_name, director, flag) "
                    + "VALUES ('custom-1001', 1001, 'commerce', 'Commerce', '交易域', 'alice', 0)");
        }

        runner.execute();
        assertSeeded(dataSource, 1001);
        assertSeeded(dataSource, 1002);
        assertEquals(1, count(dataSource, "SELECT COUNT(*) FROM t_yak_ops_data_domain "
                + "WHERE project_code = 1001 AND en_short_name = 'commerce' AND director = 'alice'"));

        // New projects receive the same seed on the next schema initialization.
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO t_yak_ops_project (code) VALUES (1003)");
        }
        runner.execute();
        assertSeeded(dataSource, 1003);
    }

    @Test
    void shouldNotReplacePreExistingDefaultDomain() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:default_domain_existing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t_yak_ops_project (code BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO t_yak_ops_project (code) VALUES (2001)");
        }

        SqlScriptRunner runner = new SqlScriptRunner(dataSource, "sql/modeling_h2.sql");
        runner.execute();

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("UPDATE t_yak_ops_data_domain SET cn_name = '手动维护' "
                    + "WHERE project_code = 2001 AND en_short_name = 'default'");
        }
        runner.execute();
        assertEquals(1, count(dataSource, "SELECT COUNT(*) FROM t_yak_ops_data_domain "
                + "WHERE project_code = 2001 AND en_short_name = 'default' AND cn_name = '手动维护'"));
    }

    private void assertSeeded(JdbcDataSource dataSource, long projectCode) throws Exception {
        String domainId = "DD" + projectCode + "DEFAULT";
        String processId = "BP" + projectCode + "DEFAULT";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement domain = connection.prepareStatement(
                        "SELECT id, en_name, cn_name, flag FROM t_yak_ops_data_domain "
                                + "WHERE project_code = ? AND en_short_name = 'default'");
                PreparedStatement process = connection.prepareStatement(
                        "SELECT id, data_domain_id, en_short_name, flag FROM t_yak_ops_business_process "
                                + "WHERE project_code = ? AND data_domain_id = ?")) {
            domain.setLong(1, projectCode);
            try (ResultSet result = domain.executeQuery()) {
                assertEquals(true, result.next());
                assertEquals(domainId, result.getString("id"));
                assertEquals("default data domain", result.getString("en_name"));
                assertEquals("默认", result.getString("cn_name"));
                assertEquals(1, result.getInt("flag"));
                assertEquals(false, result.next());
            }
            process.setLong(1, projectCode);
            process.setString(2, domainId);
            try (ResultSet result = process.executeQuery()) {
                assertEquals(true, result.next());
                assertEquals(processId, result.getString("id"));
                assertEquals(domainId, result.getString("data_domain_id"));
                assertEquals("default_default", result.getString("en_short_name"));
                assertEquals(1, result.getInt("flag"));
                assertEquals(false, result.next());
            }
        }
    }

    private int count(JdbcDataSource dataSource, String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertNotNull(result);
            result.next();
            return result.getInt(1);
        }
    }
}
