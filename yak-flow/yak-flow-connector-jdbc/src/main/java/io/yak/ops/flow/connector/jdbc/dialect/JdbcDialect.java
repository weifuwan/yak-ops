package io.yak.ops.flow.connector.jdbc.dialect;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.connector.jdbc.JdbcTargetTableDdlPlan;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * YakFlow JDBC Connector 的数据库 SQL 方言边界，统一拥有标识符、运行 SQL 与目标表 DDL / 原生类型映射。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public interface JdbcDialect {

    String quoteIdentifier(String identifier);

    String qualifiedTable(DataSourceTablePath table);

    /**
     * 把 YakFlow 逻辑字段映射为当前数据库可用于目标表 DDL 的原生类型。
     *
     * @param column 逻辑字段
     * @return 原生类型规划
     */
    JdbcNativeType nativeType(YakColumn column);

    /**
     * 生成一张目标表的 CREATE TABLE SQL；只生成，不执行。
     *
     * @param table 目标表路径
     * @param schema 目标逻辑 Schema
     * @return CREATE TABLE SQL
     */
    default String createTableSql(DataSourceTablePath table, YakTableSchema schema) {
        String definitions = schema.columns().stream()
                .map(column -> {
                    String nullable = column.nullable() ? "" : " NOT NULL";
                    return quoteIdentifier(column.name()) + " "
                            + nativeType(column).ddl() + nullable;
                })
                .collect(Collectors.joining(", "));

        if (!schema.primaryKeys().isEmpty()) {
            String primaryKeys =
                    schema.primaryKeys().stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
            definitions += ", PRIMARY KEY (" + primaryKeys + ")";
        }
        return "CREATE TABLE " + qualifiedTable(table) + " (" + definitions + ")";
    }

    /**
     * 生成 CREATE TABLE 与 Comment DDL 的完整受控计划。
     *
     * <p>默认使用 COMMENT ON TABLE / COLUMN；需要内联 Comment 的数据库可以覆盖此方法。
     *
     * @param table 目标表路径
     * @param schema 目标逻辑 Schema
     * @param tableComment 表注释；为空时不生成 Comment DDL
     * @param columnComments 字段名到字段注释；空注释会被忽略
     * @return 完整 DDL 计划
     */
    default JdbcTargetTableDdlPlan createTablePlan(
            DataSourceTablePath table, YakTableSchema schema, String tableComment, Map<String, String> columnComments) {
        String createTableSql = createTableSql(table, schema);
        List<String> statements = new ArrayList<>();
        statements.add(createTableSql);

        if (hasComment(tableComment)) {
            statements.add("COMMENT ON TABLE " + qualifiedTable(table) + " IS " + stringLiteral(tableComment));
        }

        Map<String, String> comments = columnComments == null ? Map.of() : columnComments;
        for (YakColumn column : schema.columns()) {
            String comment = comments.get(column.name());
            if (!hasComment(comment)) continue;
            statements.add("COMMENT ON COLUMN " + qualifiedTable(table) + "." + quoteIdentifier(column.name()) + " IS "
                    + stringLiteral(comment));
        }
        return new JdbcTargetTableDdlPlan(createTableSql, statements);
    }

    default String stringLiteral(String value) {
        if (value == null) throw new IllegalArgumentException("SQL literal value must not be null");
        return "'" + value.replace("'", "''") + "'";
    }

    private boolean hasComment(String value) {
        return value != null && !value.isBlank();
    }

    default String selectSql(DataSourceTablePath table, YakTableSchema schema) {
        return selectSql(table, schema, null);
    }

    default String selectRangeSql(DataSourceTablePath table, YakTableSchema schema, String splitColumn) {
        requireSplitColumn(splitColumn);
        return selectSql(table, schema, splitColumn);
    }

    default String splitStatisticsSql(DataSourceTablePath table, String splitColumn) {
        requireSplitColumn(splitColumn);
        String column = quoteIdentifier(splitColumn);
        return "SELECT MIN(" + column + "), MAX(" + column + "), COUNT(*) FROM " + qualifiedTable(table);
    }

    default String truncateSql(DataSourceTablePath table) {
        return "TRUNCATE TABLE " + qualifiedTable(table);
    }

    default String upsertSql(DataSourceTablePath table, YakTableSchema schema) {
        throw new UnsupportedOperationException("UPSERT is not supported by this JDBC dialect");
    }

    default String insertSql(DataSourceTablePath table, YakTableSchema schema) {
        String columns = schema.columns().stream()
                .map(YakColumn::name)
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", "));
        String placeholders = schema.columns().stream().map(ignored -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO " + qualifiedTable(table) + " (" + columns + ") VALUES (" + placeholders + ")";
    }

    default String deleteSql(DataSourceTablePath table, YakTableSchema schema) {
        if (schema.primaryKeys().isEmpty()) {
            throw new IllegalArgumentException("DELETE changelog requires primary key");
        }
        String predicate = schema.primaryKeys().stream()
                .map(primaryKey -> quoteIdentifier(primaryKey) + " = ?")
                .collect(Collectors.joining(" AND "));
        return "DELETE FROM " + qualifiedTable(table) + " WHERE " + predicate;
    }

    private void requireSplitColumn(String splitColumn) {
        if (splitColumn == null || splitColumn.isBlank()) {
            throw new IllegalArgumentException("splitColumn must not be blank");
        }
    }

    private String selectSql(DataSourceTablePath table, YakTableSchema schema, String splitColumn) {
        String columns = schema.columns().stream()
                .map(YakColumn::name)
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", "));
        String where = splitColumn == null
                ? ""
                : " WHERE " + quoteIdentifier(splitColumn) + " >= ? AND " + quoteIdentifier(splitColumn) + " <= ?";
        String orderBy = schema.primaryKeys().isEmpty()
                ? ""
                : " ORDER BY "
                        + schema.primaryKeys().stream()
                                .map(this::quoteIdentifier)
                                .collect(Collectors.joining(", "));
        return "SELECT " + columns + " FROM " + qualifiedTable(table) + where + orderBy;
    }
}
