package io.yak.ops.dao.plugin.postgresql.monitor;

import io.yak.ops.dao.plugin.api.monitor.DatabaseMetrics;
import io.yak.ops.dao.plugin.api.monitor.DatabaseMonitor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Date;

import javax.sql.DataSource;

import com.baomidou.mybatisplus.annotation.DbType;

import lombok.SneakyThrows;

public class PostgresqlMonitor implements DatabaseMonitor {

    private final DataSource dataSource;

    public PostgresqlMonitor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    @SneakyThrows
    public DatabaseMetrics getDatabaseMetrics() {
        DatabaseMetrics monitorRecord = new DatabaseMetrics();
        monitorRecord.setDate(new Date());
        monitorRecord.setState(DatabaseMetrics.DatabaseHealthStatus.YES);
        monitorRecord.setDbType(DbType.POSTGRE_SQL);

        try (
                Connection connection = dataSource.getConnection();
                Statement pstmt = connection.createStatement()) {

            try (ResultSet rs1 = pstmt.executeQuery("select count(*) from pg_stat_activity;")) {
                if (rs1.next()) {
                    monitorRecord.setThreadsConnections(rs1.getInt("count"));
                }
            }

            try (ResultSet rs2 = pstmt.executeQuery("show max_connections")) {
                if (rs2.next()) {
                    monitorRecord.setMaxConnections(rs2.getInt("max_connections"));
                }
            }

            try (
                    ResultSet rs3 =
                            pstmt.executeQuery("select count(*) from pg_stat_activity pg where pg.state = 'active';")) {
                if (rs3.next()) {
                    monitorRecord.setThreadsRunningConnections(rs3.getInt("count"));
                }
            }
        }
        return monitorRecord;
    }
}
