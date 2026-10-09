package io.yak.ops.plugin.database.jdbc.schema;

import java.util.List;
import java.util.Objects;

/**
 * JDBC 目标表受控 DDL 计划。
 *
 * <p>第一条语句必须是 CREATE TABLE；后续语句可承载表 / 字段 Comment 等由 JdbcDialect 生成的附加 DDL。
 *
 * @param createTableSql 主 CREATE TABLE SQL
 * @param statements 按执行顺序排列的完整 DDL 语句
 * @author weifuwan
 * @since 2026-10-05
 */
public record JdbcTargetTableDdlPlan(String createTableSql, List<String> statements) {

    public JdbcTargetTableDdlPlan {
        Objects.requireNonNull(createTableSql, "createTableSql must not be null");
        Objects.requireNonNull(statements, "statements must not be null");
        if (createTableSql.isBlank()) {
            throw new IllegalArgumentException("createTableSql must not be blank");
        }
        statements = List.copyOf(statements);
        if (statements.isEmpty()) {
            throw new IllegalArgumentException("statements must not be empty");
        }
        if (!createTableSql.equals(statements.getFirst())) {
            throw new IllegalArgumentException("first statement must equal createTableSql");
        }
        if (statements.stream().anyMatch(statement -> statement == null || statement.isBlank())) {
            throw new IllegalArgumentException("statements must not contain blank values");
        }
    }
}
