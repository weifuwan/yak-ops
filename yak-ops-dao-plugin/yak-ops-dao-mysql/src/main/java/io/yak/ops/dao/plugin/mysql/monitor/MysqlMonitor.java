package io.yak.ops.dao.plugin.mysql.monitor;

import io.yak.ops.dao.plugin.api.monitor.DatabaseMetrics;
import io.yak.ops.dao.plugin.api.monitor.DatabaseMonitor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Date;

import javax.sql.DataSource;

import com.baomidou.mybatisplus.annotation.DbType;

import lombok.SneakyThrows;

public class MysqlMonitor implements DatabaseMonitor {

    private final DataSource dataSource;

    public MysqlMonitor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @SneakyThrows
    @Override
    public DatabaseMetrics getDatabaseMetrics() {
        DatabaseMetrics monitorRecord = new DatabaseMetrics();
        monitorRecord.setDate(new Date());
        monitorRecord.setDbType(DbType.MYSQL);
        monitorRecord.setState(DatabaseMetrics.DatabaseHealthStatus.YES);

        try (
                Connection connection = dataSource.getConnection();
                Statement pstmt = connection.createStatement()) {

            try (ResultSet rs1 = pstmt.executeQuery("show global variables")) {
                while (rs1.next()) {
                    if ("MAX_CONNECTIONS".equalsIgnoreCase(rs1.getString("variable_name"))) {
                        monitorRecord.setMaxConnections(Long.parseLong(rs1.getString("value")));
                    }
                }
            }

            try (ResultSet rs2 = pstmt.executeQuery("show global status")) {
                while (rs2.next()) {
                    if ("MAX_USED_CONNECTIONS".equalsIgnoreCase(rs2.getString("variable_name"))) {
                        monitorRecord.setMaxUsedConnections(Long.parseLong(rs2.getString("value")));
                    } else if ("THREADS_CONNECTED".equalsIgnoreCase(rs2.getString("variable_name"))) {
                        monitorRecord.setThreadsConnections(Long.parseLong(rs2.getString("value")));
                    } else if ("THREADS_RUNNING".equalsIgnoreCase(rs2.getString("variable_name"))) {
                        monitorRecord.setThreadsRunningConnections(Long.parseLong(rs2.getString("value")));
                    }
                }
            }
        }
        return monitorRecord;
    }
}
