package io.yak.ops.connector.jdbc.database.dialect;

import java.util.List;
import java.util.Objects;

/** Ordered create-table and column-comment DDL statements for an existing table plan. */
public record JdbcDdlPlan(String createTableSql, List<String> statements) {

    public JdbcDdlPlan {
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
