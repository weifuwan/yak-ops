package io.yak.ops.connector.jdbc.sink;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.query.JdbcReadQuery;
import io.yak.ops.core.api.connector.sink.BatchExecutionHook;
import io.yak.ops.core.api.connector.sink.BatchExecutionHookException;
import io.yak.ops.core.data.TableId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.util.TablesNamesFinder;

/** Validates bounded table SQL and opens one independent connection for each journaled operation. */
public final class JdbcSqlHooks {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_STATEMENTS = 100;
    private static final int MAX_STATEMENT_LENGTH = 16_384;

    private JdbcSqlHooks() {}

    /** Parses the connector's JSON array wire format without accepting untyped or multiple statements. */
    public static List<String> parseStatements(String json) {
        if (json == null) return List.of();
        if (json.length() > MAX_STATEMENTS * MAX_STATEMENT_LENGTH) {
            throw new IllegalArgumentException("JDBC SQL hooks exceed the supported size");
        }
        try {
            Object value = JSON.readValue(json, Object.class);
            if (!(value instanceof List<?> items)) throw new IllegalArgumentException("SQL hooks require a JSON array");
            List<String> statements = new ArrayList<>();
            for (Object item : items) {
                if (!(item instanceof String statement))
                    throw new IllegalArgumentException("SQL hooks must be strings");
                statements.add(statement);
            }
            return validateStatements(statements);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Invalid JDBC SQL hook array", failure);
        }
    }

    static List<String> validateStatements(List<String> statements) {
        List<String> result = List.copyOf(Objects.requireNonNull(statements, "statements"));
        if (result.size() > MAX_STATEMENTS) throw new IllegalArgumentException("Too many JDBC SQL hooks");
        for (String statement : result) parse(statement);
        return result;
    }

    static BatchExecutionHook operation(
            JdbcConnectionProvider connections, TableId target, BatchExecutionHook.Phase phase, String sql) {
        net.sf.jsqlparser.statement.Statement parsed = parse(sql);
        Set<String> tables = new HookTables().getTables(parsed);
        if (tables.isEmpty() || tables.stream().anyMatch(table -> !matches(target, table))) {
            throw new IllegalArgumentException("SQL hook may reference only its configured target table");
        }
        return new SqlOperation(connections, target, phase, parsed.toString(), parsed instanceof Truncate);
    }

    private static boolean matches(TableId target, String configured) {
        String value = configured.replace("`", "").replace("\"", "");
        String namespace = target.schema() == null ? target.catalog() : target.schema();
        return value.equals(target.table())
                || (namespace != null && value.equals(namespace + "." + target.table()))
                || (target.catalog() != null
                        && target.schema() != null
                        && value.equals(target.catalog() + "." + target.schema() + "." + target.table()));
    }

    private static net.sf.jsqlparser.statement.Statement parse(String sql) {
        if (sql == null || sql.isBlank() || sql.length() > MAX_STATEMENT_LENGTH) {
            throw new IllegalArgumentException("JDBC SQL hook is empty or exceeds its supported length");
        }
        try {
            net.sf.jsqlparser.statement.Statement parsed = CCJSqlParserUtil.parse(sql);
            if (!(parsed instanceof Insert)
                    && !(parsed instanceof Update)
                    && !(parsed instanceof Delete)
                    && !(parsed instanceof Truncate)) {
                throw new IllegalArgumentException("SQL hooks support INSERT, UPDATE, DELETE and TRUNCATE only");
            }
            new HookTables().getTables(parsed);
            return parsed;
        } catch (JSQLParserException failure) {
            throw new IllegalArgumentException("SQL hook must contain exactly one supported statement", failure);
        }
    }

    private static final class HookTables extends TablesNamesFinder {
        @Override
        public void visit(net.sf.jsqlparser.expression.Function function) {
            JdbcReadQuery.validateWhere(function + " IS NOT NULL");
            super.visit(function);
        }

        @Override
        public void visit(net.sf.jsqlparser.expression.JdbcParameter parameter) {
            throw new IllegalArgumentException("SQL hooks do not accept unbound parameters");
        }

        @Override
        public void visit(net.sf.jsqlparser.expression.JdbcNamedParameter parameter) {
            throw new IllegalArgumentException("SQL hooks do not accept unbound parameters");
        }

        @Override
        public void visit(net.sf.jsqlparser.expression.UserVariable variable) {
            throw new IllegalArgumentException("SQL hooks do not accept session variables");
        }

        @Override
        public void visit(net.sf.jsqlparser.expression.NextValExpression value) {
            throw new IllegalArgumentException("SQL hooks do not accept sequence side effects");
        }
    }

    private static final class SqlOperation implements BatchExecutionHook {
        private final JdbcConnectionProvider connections;
        private final TableId target;
        private final Phase phase;
        private final String sql;
        private final boolean implicitCommit;
        private final String fingerprint;
        private volatile Statement active;
        private volatile boolean canceled;

        private SqlOperation(
                JdbcConnectionProvider connections, TableId target, Phase phase, String sql, boolean implicitCommit) {
            this.connections = connections;
            this.target = target;
            this.phase = phase;
            this.sql = sql;
            this.implicitCommit = implicitCommit;
            try {
                this.fingerprint = HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256")
                                .digest((phase + "|" + target + "|" + sql).getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-256 is required", impossible);
            }
        }

        @Override
        public Phase phase() {
            return phase;
        }

        @Override
        public String fingerprint() {
            return fingerprint;
        }

        @Override
        public void execute() throws BatchExecutionHookException {
            Connection connection = null;
            boolean statementInvoked = false;
            boolean commitInvoked = false;
            boolean committed = false;
            boolean rollbackVerified = false;
            BatchExecutionHookException problem = null;
            try {
                connection = connections.getConnection();
                if (target.catalog() != null && !target.catalog().equals(connection.getCatalog())) {
                    connection.setCatalog(target.catalog());
                }
                if (target.schema() != null && !target.schema().equals(connection.getSchema())) {
                    connection.setSchema(target.schema());
                }
                connection.setAutoCommit(false);
                rollbackVerified = supportsRollback(connection, target);
                try (Statement statement = connection.createStatement()) {
                    active = statement;
                    statement.setQueryTimeout(JdbcMetadataApplier.DEFAULT_STATEMENT_TIMEOUT_SECONDS);
                    if (canceled) throw new SQLException("SQL hook was cancelled before execution", "57014");
                    statementInvoked = true;
                    if (statement.execute(sql)) throw new SQLException("SQL hook unexpectedly returned a result set");
                    if (canceled && !implicitCommit)
                        throw new SQLException("SQL hook was cancelled before commit", "57014");
                    commitInvoked = true;
                    connection.commit();
                    committed = true;
                }
            } catch (SQLException | RuntimeException failure) {
                boolean connectionFailure = failure instanceof SQLException sqlFailure
                        && sqlFailure.getSQLState() != null
                        && sqlFailure.getSQLState().startsWith("08");
                boolean unknown = commitInvoked
                        || (statementInvoked && (implicitCommit || !rollbackVerified || connectionFailure));
                if (connection != null && !committed) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollback) {
                        failure.addSuppressed(rollback);
                        unknown = unknown || statementInvoked;
                    }
                }
                problem = new BatchExecutionHookException("JDBC batch SQL operation failed", failure, unknown);
            } finally {
                active = null;
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException closing) {
                        if (problem != null) problem.addSuppressed(closing);
                        else
                            problem = new BatchExecutionHookException(
                                    "JDBC SQL connection cleanup failed", closing, committed);
                    }
                }
            }
            if (problem != null) throw problem;
        }

        @Override
        public void cancel() throws SQLException {
            canceled = true;
            Statement statement = active;
            if (statement != null) statement.cancel();
        }
    }

    private static boolean supportsRollback(Connection connection, TableId target) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        if ("MySQL".equalsIgnoreCase(product)) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?")) {
                statement.setQueryTimeout(JdbcMetadataApplier.DEFAULT_STATEMENT_TIMEOUT_SECONDS);
                statement.setString(1, target.catalog() == null ? connection.getCatalog() : target.catalog());
                statement.setString(2, target.table());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() && "InnoDB".equalsIgnoreCase(result.getString(1));
                }
            }
        }
        return ("PostgreSQL".equalsIgnoreCase(product)
                        || "Oracle".equalsIgnoreCase(product)
                        || "H2".equalsIgnoreCase(product))
                && connection.getMetaData().supportsTransactions();
    }
}
