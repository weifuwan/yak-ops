package io.yak.ops.tools.datasource.h2;

import io.yak.ops.tools.datasource.SchemaDao;

import java.sql.Connection;
import java.sql.Statement;

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

        try (Connection connection = dataSource.getConnection();
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
}
