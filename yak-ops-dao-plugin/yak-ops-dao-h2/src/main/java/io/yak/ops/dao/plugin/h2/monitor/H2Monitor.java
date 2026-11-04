package io.yak.ops.dao.plugin.h2.monitor;

import io.yak.ops.dao.plugin.api.monitor.DatabaseMetrics;
import io.yak.ops.dao.plugin.api.monitor.DatabaseMonitor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Date;

import javax.sql.DataSource;

import com.baomidou.mybatisplus.annotation.DbType;

import lombok.SneakyThrows;

public class H2Monitor implements DatabaseMonitor {

    private final DataSource dataSource;

    public H2Monitor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @SneakyThrows
    @Override
    public DatabaseMetrics getDatabaseMetrics() {
        DatabaseMetrics monitorRecord = new DatabaseMetrics();
        monitorRecord.setDate(new Date());
        monitorRecord.setDbType(DbType.H2);
        monitorRecord.setState(DatabaseMetrics.DatabaseHealthStatus.YES);

        try (
                Connection connection = dataSource.getConnection();
                Statement pstmt = connection.createStatement()) {

            try (
                    ResultSet rs1 = pstmt
                            .executeQuery("select count(1) as total from information_schema.sessions;")) {
                if (rs1.next()) {
                    int currentSessions = rs1.getInt("total");
                    monitorRecord.setThreadsConnections(currentSessions);
                    monitorRecord.setMaxUsedConnections(currentSessions);
                }
            }

        }
        return monitorRecord;
    }

}
