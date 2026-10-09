package io.yak.ops.business.datasync.schema.target;

import io.yak.ops.business.datasync.schema.LogicalColumn;
import io.yak.ops.business.datasync.schema.LogicalTable;
import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDdlPlan;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.connector.jdbc.database.dialect.JdbcNativeType;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import io.yak.ops.core.data.TableId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 将产品 LogicalTable 规划为指定 JDBC 数据库的目标表 DDL。
 *
 * <p>Planner 只生成计划，不连接数据库、不检查目标表存在性，也不执行 DDL。
 *
 * @author weifuwan
 * @since 2026-10-04
 */
@Component
public class TargetTablePlanner {

    public TargetTablePlan plan(
            LogicalTable logicalTable, String targetType, String database, String schema, String table) {
        Objects.requireNonNull(logicalTable, "logicalTable must not be null");

        String canonicalType = JdbcDialects.canonicalType(targetType);
        JdbcDialect dialect = JdbcDialects.forType(canonicalType);
        DataSourceTablePath targetPath = new DataSourceTablePath(normalize(database), normalize(schema), table);
        YakTableSchema runtimeSchema = logicalTable.toRuntimeSchema();

        Map<String, Integer> primaryKeyPositions = primaryKeyPositions(logicalTable.primaryKeys());
        List<TargetColumnPlan> columnPlans = new ArrayList<>(runtimeSchema.columnCount());
        List<String> warnings = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();

        for (int index = 0; index < runtimeSchema.columnCount(); index++) {
            YakColumn column = runtimeSchema.column(index);
            Integer primaryKeyPosition = primaryKeyPositions.get(column.name());
            boolean primaryKey = primaryKeyPosition != null;
            String nativeType = null;
            String warning = null;
            String unsupportedReason = null;

            try {
                JdbcNativeType type = dialect.nativeType(column);
                nativeType = type.ddl();
                warning = type.warning();
                if (primaryKey && !type.primaryKeySupported()) {
                    unsupportedReason = "字段 " + column.name() + " 映射为 " + nativeType + "，不能直接作为目标主键";
                }
            } catch (UnsupportedOperationException exception) {
                unsupportedReason =
                        "字段 " + column.name() + "（" + column.dataType().kind() + "）：" + exception.getMessage();
            }

            if (warning != null) {
                warnings.add("字段 " + column.name() + "：" + warning);
            }
            if (unsupportedReason != null) {
                unsupported.add(unsupportedReason);
            }

            LogicalColumn logicalColumn = logicalTable.columns().get(index);
            columnPlans.add(new TargetColumnPlan(
                    column.name(),
                    column.dataType(),
                    nativeType,
                    column.nullable(),
                    primaryKey,
                    primaryKeyPosition,
                    logicalColumn.comment(),
                    warning,
                    unsupportedReason));
        }

        JdbcDdlPlan ddlPlan = unsupported.isEmpty()
                ? dialect.createTablePlan(
                        targetId, runtimeSchema, logicalTable.comment(), columnComments(logicalTable))
                : null;
        return new TargetTablePlan(
                canonicalType,
                targetPath,
                logicalTable.schemaVersion(),
                logicalTable.comment(),
                columnPlans,
                logicalTable.primaryKeys(),
                warnings,
                unsupported,
                ddlPlan == null ? List.of() : ddlPlan.statements(),
                ddlPlan == null ? null : ddlPlan.createTableSql());
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

    private Map<String, Integer> primaryKeyPositions(List<String> primaryKeys) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < primaryKeys.size(); index++) {
            result.put(primaryKeys.get(index), index + 1);
        }
        return result;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
