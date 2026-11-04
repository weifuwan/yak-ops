package io.yak.ops.dao.plugin.mysql.dialect;

import io.yak.ops.dao.plugin.api.dialect.DatabaseDialect;

import java.sql.Connection;
import java.sql.ResultSet;

import javax.sql.DataSource;

import lombok.SneakyThrows;

public class MysqlDialect implements DatabaseDialect {

    private final DataSource dataSource;

    public MysqlDialect(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @SneakyThrows
    @Override
    public boolean tableExists(String tableName) {
        try (
                Connection conn = dataSource.getConnection();
                ResultSet rs = conn.getMetaData().getTables(conn.getCatalog(), conn.getSchema(), tableName, null)) {
            return rs.next();
        }
    }

}
