package io.yak.ops.business.datasync.schema.target;

import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.util.List;
import java.util.Objects;

/**
 * LogicalTable 到目标 JDBC 表的只读规划结果。
 *
 * @param targetType 目标 Datasource canonical type
 * @param targetPath 目标物理表路径
 * @param logicalSchemaVersion 来源 LogicalTable schemaVersion
 * @param comment 产品表备注
 * @param columns 字段类型规划
 * @param primaryKeys 有序主键字段
 * @param warnings 非阻塞诊断
 * @param unsupportedReasons 阻塞 CREATE TABLE 的诊断
 * @param ddlStatements 全部映射安全时按执行顺序生成的完整 DDL；不支持时为空列表
 * @param createTableSql 主 CREATE TABLE SQL；不支持时为空
 * @author weifuwan
 * @since 2026-10-04
 */
public record TargetTablePlan(
        String targetType,
        DataSourceTablePath targetPath,
        long logicalSchemaVersion,
        String comment,
        List<TargetColumnPlan> columns,
        List<String> primaryKeys,
        List<String> warnings,
        List<String> unsupportedReasons,
        List<String> ddlStatements,
        String createTableSql) {

    public TargetTablePlan {
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(targetPath, "targetPath must not be null");
        Objects.requireNonNull(columns, "columns must not be null");
        Objects.requireNonNull(primaryKeys, "primaryKeys must not be null");
        Objects.requireNonNull(warnings, "warnings must not be null");
        Objects.requireNonNull(unsupportedReasons, "unsupportedReasons must not be null");
        Objects.requireNonNull(ddlStatements, "ddlStatements must not be null");

        columns = List.copyOf(columns);
        primaryKeys = List.copyOf(primaryKeys);
        warnings = List.copyOf(warnings);
        unsupportedReasons = List.copyOf(unsupportedReasons);
        ddlStatements = List.copyOf(ddlStatements);
        comment = normalize(comment);
        createTableSql = normalize(createTableSql);

        if (logicalSchemaVersion < 1) {
            throw new IllegalArgumentException("logicalSchemaVersion must be greater than 0");
        }
        if (unsupportedReasons.isEmpty() && (createTableSql == null || ddlStatements.isEmpty())) {
            throw new IllegalArgumentException("supported target plan requires DDL statements");
        }
        if (!unsupportedReasons.isEmpty() && (createTableSql != null || !ddlStatements.isEmpty())) {
            throw new IllegalArgumentException("unsupported target plan must not contain DDL statements");
        }
    }

    public boolean supported() {
        return unsupportedReasons.isEmpty();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
