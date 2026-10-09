package io.yak.ops.business.datasync.execution.planning.target;

import io.yak.ops.business.datasync.schema.LogicalTable;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.plugin.datasource.api.plugin.DataSourceConnection;

/**
 * Data Sync 目标表 DDL 执行边界，便于 Runtime Preflight 隔离连接器副作用。
 *
 * @author weifuwan
 * @since 2026-10-04
 */
public interface TargetTableDdlExecutor {

    /**
     * 创建目标表。
     *
     * @return 实际执行的 CREATE TABLE SQL
     */
    String createTable(
            DataSourceConnection connection, DataSourceTablePath table, LogicalTable logicalTable, int timeoutSeconds)
            throws Exception;
}
