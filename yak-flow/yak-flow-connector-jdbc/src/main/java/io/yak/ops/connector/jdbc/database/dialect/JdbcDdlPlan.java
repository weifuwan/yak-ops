package io.yak.ops.connector.jdbc.database.dialect;

import java.util.List;
import java.util.Objects;

/**
 * Carries a target CREATE TABLE statement and any subsequent vendor-specific comment DDL.
 *
 * <p>The first statement is always {@code createTableSql}. All statements are immutable
 * and execute in their declared order; planning itself has no database side effects.
 *
 * @param createTableSql the primary CREATE TABLE statement
 * @param statements ordered nonempty execution plan, starting with the CREATE TABLE statement
 */
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
