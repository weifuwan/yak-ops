package io.yak.ops.dao.plugin.postgresql.dialect;

import io.yak.ops.dao.plugin.api.dialect.DatabaseDialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import lombok.SneakyThrows;

public class PostgresqlDialect implements DatabaseDialect {

    private final DataSource dataSource;

    public PostgresqlDialect(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @SneakyThrows
    @Override
    public boolean tableExists(String tableName) {
        try (
                Connection conn = dataSource.getConnection();
                ResultSet rs = conn.getMetaData().getTables(conn.getCatalog(), getSchema(), tableName, null)) {
            return rs.next();
        }
    }

    private String getSchema() throws SQLException {
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement("select current_schema()");
                ResultSet resultSet = pstmt.executeQuery()) {
            while (resultSet.next()) {
                if (resultSet.isFirst()) {
                    return resultSet.getString(1);
                }
            }
        }
        return "";
    }
}
