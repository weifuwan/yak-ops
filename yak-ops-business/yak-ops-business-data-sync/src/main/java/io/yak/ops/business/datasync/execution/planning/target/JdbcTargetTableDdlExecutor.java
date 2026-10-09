package io.yak.ops.business.datasync.execution.planning.target;

import io.yak.ops.business.datasync.schema.LogicalColumn;
import io.yak.ops.business.datasync.schema.LogicalTable;
import io.yak.ops.flow.connector.jdbc.JdbcTargetTableProvisioner;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 使用 YakFlow JDBC Connector 受控 DDL 执行器创建目标表。
 *
 * @author weifuwan
 * @since 2026-10-04
 */
@Component
public class JdbcTargetTableDdlExecutor implements TargetTableDdlExecutor {

    private final JdbcTargetTableProvisioner provisioner = new JdbcTargetTableProvisioner();

    @Override
    public String createTable(
            DataSourceConnection connection, DataSourceTablePath table, LogicalTable logicalTable, int timeoutSeconds)
            throws Exception {
        return provisioner
                .createTable(
                        connection,
                        table,
                        logicalTable.toRuntimeSchema(),
                        logicalTable.comment(),
                        columnComments(logicalTable),
                        timeoutSeconds)
                .createTableSql();
    }

    private Map<String, String> columnComments(LogicalTable logicalTable) {
        Map<String, String> result = new LinkedHashMap<>();
        for (LogicalColumn column : logicalTable.columns()) {
            if (column.comment() != null && !column.comment().isBlank()) {
                result.put(column.name(), column.comment());
            }
        }
        return result;
    }
}
