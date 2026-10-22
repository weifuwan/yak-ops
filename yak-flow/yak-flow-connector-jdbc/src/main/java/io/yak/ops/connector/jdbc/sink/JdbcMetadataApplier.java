package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.catalog.JdbcColumnInfo;
import io.yak.ops.connector.jdbc.database.catalog.JdbcTableInfo;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDdlPlan;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcNativeType;
import io.yak.ops.core.api.connector.sink.MetadataApplier;
import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.event.SchemaChangeEventType;
import io.yak.ops.core.exceptions.SchemaEvolveException;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

/**
 * Prepares and verifies JDBC target tables without participating in row writes.
 *
 * <p>Each operation owns its DDL connection and statement. Existing tables are only checked;
 * no table is dropped, truncated or altered. DDL is executed in auto-commit mode and successful
 * statements are not rolled back when a later statement fails. The caller must hold the target
 * resource claim and wait for this operation to return before releasing it.
 */
public final class JdbcMetadataApplier implements MetadataApplier {

    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_STATEMENT_TIMEOUT_SECONDS = 30;

    private static final Set<SchemaChangeEventType> SUPPORTED = Set.of(SchemaChangeEventType.CREATE_TABLE);

    private final JdbcConnectionProvider connections;
    private final JdbcDialect dialect;
    private final JdbcCatalog catalog;
    private final int statementTimeoutSeconds;

    private Set<SchemaChangeEventType> accepted = SUPPORTED;
    private boolean canceled;
    private boolean closed;
    private transient boolean applying;
    private transient Statement activeStatement;

    /**
     * Creates a metadata applier without accessing the database.
     *
     * @param connections provider of caller-owned DDL connections
     * @param dialect target DDL and type renderer
     * @param catalog read-only target catalog owned by this applier
     * @param statementTimeoutSeconds positive timeout applied to every DDL statement
     */
    public JdbcMetadataApplier(
            JdbcConnectionProvider connections, JdbcDialect dialect, JdbcCatalog catalog, int statementTimeoutSeconds) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        if (statementTimeoutSeconds < 1) {
            throw new IllegalArgumentException("DDL statement timeout must be positive");
        }
        this.statementTimeoutSeconds = statementTimeoutSeconds;
    }

    /**
     * Renders a validated DDL plan using only the event and target dialect.
     *
     * <p>No connection is opened. This is not an existence, permission or execution check.
     *
     * @param event complete expected target structure and optional comments
     * @return ordered creation and comment statements
     */
    public JdbcDdlPlan plan(CreateTableEvent event) {
        return plan(event, dialect);
    }

    /**
     * Renders the same validated statements used by execution without opening a connection.
     *
     * @param event complete expected structure and comments
     * @param dialect target dialect
     * @return ordered DDL statements
     */
    public static JdbcDdlPlan plan(CreateTableEvent event, JdbcDialect dialect) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(dialect, "dialect");
        TableSchema schema = event.getSchema();
        JdbcSinkSchemaValidator.validate(schema, schema, !schema.primaryKeys().isEmpty());
        for (Column column : schema.columns()) {
            JdbcNativeType nativeType = dialect.nativeType(column);
            if (nativeType.warning() != null) {
                throw new IllegalArgumentException("Target type requires explicit conversion: " + column.name());
            }
            if (schema.primaryKeys().contains(column.name())
                    && (column.nullable() || !nativeType.primaryKeySupported())) {
                throw new IllegalArgumentException("Target primary key is not safely representable: " + column.name());
            }
        }
        return dialect.createTablePlan(
                event.tableId(),
                schema,
                event.getTableComment(),
                event.getColumnComments(),
                event.getColumnProperties());
    }

    @Override
    public void applySchemaChange(SchemaChangeEvent schemaChangeEvent) throws SchemaEvolveException {
        Objects.requireNonNull(schemaChangeEvent, "schemaChangeEvent");
        if (!(schemaChangeEvent instanceof CreateTableEvent event)
                || schemaChangeEvent.getType() != SchemaChangeEventType.CREATE_TABLE) {
            throw new SchemaEvolveException(schemaChangeEvent, "JDBC metadata applier supports CREATE_TABLE only");
        }
        begin(event);
        try {
            JdbcDdlPlan ddl = plan(event);
            checkCanceled(event);
            if (catalog.findTable(event.tableId()).isEmpty()) {
                createMissingTable(event, ddl);
            }
            checkCanceled(event);
            validateTable(event);
            checkCanceled(event);
        } catch (SchemaEvolveException failure) {
            throw failure;
        } catch (SQLException | RuntimeException failure) {
            throw new SchemaEvolveException(event, "JDBC target schema preparation could not be verified", failure);
        } finally {
            synchronized (this) {
                applying = false;
            }
        }
    }

    private void createMissingTable(CreateTableEvent event, JdbcDdlPlan ddl) throws SQLException {
        checkCanceled(event);
        try (Connection connection = connections.getConnection()) {
            checkCanceled(event);
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(statementTimeoutSeconds);
                synchronized (this) {
                    checkCanceled(event);
                    activeStatement = statement;
                }
                try {
                    for (int index = 0; index < ddl.statements().size(); index++) {
                        checkCanceled(event);
                        try {
                            statement.execute(ddl.statements().get(index));
                        } catch (SQLException failure) {
                            if (index == 0 && isTableAlreadyExists(failure)) {
                                // Another creator owns the existing object; do not write its comments.
                                return;
                            }
                            if (isOutcomeUnknown(failure)) {
                                throw new SchemaEvolveException(
                                        event, "DDL completion cannot be confirmed", failure, true);
                            }
                            throw failure;
                        } catch (RuntimeException failure) {
                            throw new SchemaEvolveException(event, "DDL completion cannot be confirmed", failure, true);
                        }
                    }
                } finally {
                    synchronized (this) {
                        activeStatement = null;
                    }
                }
            }
        }
    }

    private void validateTable(CreateTableEvent event) throws SQLException {
        JdbcTableInfo table = catalog.findTable(event.tableId())
                .orElseThrow(() -> new SQLException("Target table is absent after schema preparation"));
        if (!"TABLE".equalsIgnoreCase(table.type()) && !"BASE TABLE".equalsIgnoreCase(table.type())) {
            throw new IllegalArgumentException("Schema preparation requires a physical target table");
        }
        TableSchema actual = catalog.getTable(event.tableId());
        validateSchema(event, actual);
        Map<String, String> comments = new HashMap<>();
        Map<String, ColumnProperties> expected = event.getColumnProperties();
        for (JdbcColumnInfo column : catalog.getColumns(event.tableId())) {
            ColumnProperties planned = expected.get(column.name());
            ColumnProperties actualProperties = new ColumnProperties(
                    column.defaultExpression(),
                    column.autoIncrement(),
                    column.generated(),
                    column.onUpdateExpression(),
                    column.defaultGenerated());
            validateColumnProperties(column.name(), planned, actualProperties);
            comments.put(column.name(), column.remarks());
        }
        validateComments(event, table.remarks(), comments);
    }

    /**
     * Verifies every writable column attribute without accepting an altered target.
     * Default literals may be returned quoted by JDBC and are normalized for comparison.
     */
    public static void validateColumnProperties(String column, ColumnProperties expected, ColumnProperties actual) {
        if (!actual.isKnown()) {
            throw new IllegalArgumentException("Unknown target generation metadata for column: " + column);
        }
        if (expected == null) {
            if (actual.hasFeatures()) {
                throw new IllegalArgumentException("Unexpected target column properties: " + column);
            }
            return;
        }
        if (!expected.isKnown()
                || !java.util.Objects.equals(expected.autoIncrement(), actual.autoIncrement())
                || !java.util.Objects.equals(expected.generated(), actual.generated())
                || (expected.defaultExpression() != null
                        && expected.defaultGenerated() != null
                        && !java.util.Objects.equals(expected.defaultGenerated(), actual.defaultGenerated()))
                || !java.util.Objects.equals(
                        normalizeDefault(expected.onUpdateExpression(), true),
                        normalizeDefault(actual.onUpdateExpression(), true))
                || !java.util.Objects.equals(
                        normalizeDefault(expected.defaultExpression(), expected.defaultGenerated()),
                        normalizeDefault(actual.defaultExpression(), actual.defaultGenerated()))) {
            throw new IllegalArgumentException("Target column properties do not match: " + column);
        }
    }

    private static String normalizeDefault(String expression, Boolean generated) {
        if (expression == null) return null;
        if (Boolean.TRUE.equals(generated) && expression.trim().matches("(?i)CURRENT_TIMESTAMP(?:\\([0-6]?\\))?")) {
            return expression
                    .trim()
                    .replaceAll("(?i)current_timestamp\\(\\)", "CURRENT_TIMESTAMP")
                    .toUpperCase(java.util.Locale.ROOT);
        }
        return expression;
    }

    /**
     * Validates the complete expected field set, key order and write compatibility.
     *
     * @param event expected complete target schema
     * @param actual physical schema obtained from the target Catalog
     */
    public static void validateSchema(CreateTableEvent event, TableSchema actual) {
        TableSchema expected = event.getSchema();
        Set<String> expectedNames =
                expected.columns().stream().map(Column::name).collect(Collectors.toSet());
        Set<String> actualNames = actual.columns().stream().map(Column::name).collect(Collectors.toSet());
        if (!expectedNames.equals(actualNames)) {
            throw new IllegalArgumentException("Target columns do not match the complete expected schema");
        }
        if (!expected.primaryKeys().equals(actual.primaryKeys())) {
            throw new IllegalArgumentException("Target primary keys do not match the expected key order");
        }
        JdbcSinkSchemaValidator.validate(
                expected, actual, !expected.primaryKeys().isEmpty());
    }

    /**
     * Verifies explicitly requested comments without writing to an existing object.
     *
     * @param event expected table and column comments
     * @param tableComment physical table comment
     * @param columnComments physical comments keyed by exact column name
     */
    public static void validateComments(
            CreateTableEvent event, String tableComment, Map<String, String> columnComments) {
        if (hasComment(event.getTableComment()) && !event.getTableComment().equals(tableComment)) {
            throw new IllegalArgumentException("Target table comment does not match the requested metadata");
        }
        for (var entry : event.getColumnComments().entrySet()) {
            if (hasComment(entry.getValue()) && !entry.getValue().equals(columnComments.get(entry.getKey()))) {
                throw new IllegalArgumentException("Target column comment does not match: " + entry.getKey());
            }
        }
    }

    private static boolean hasComment(String comment) {
        return comment != null && !comment.isBlank();
    }

    /** Distinguishes server-confirmed statement errors from a broken or timed-out response channel. */
    static boolean isOutcomeUnknown(SQLException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.io.IOException
                    || cause instanceof java.sql.SQLTimeoutException
                    || cause instanceof java.sql.SQLRecoverableException) return true;
            if (cause instanceof SQLException sql
                    && (sql.getSQLState() == null || sql.getSQLState().startsWith("08"))) return true;
        }
        return false;
    }

    static boolean isTableAlreadyExists(SQLException failure) {
        return "42P07".equals(failure.getSQLState())
                || ("42S01".equals(failure.getSQLState()) && failure.getErrorCode() == 1050);
    }

    private synchronized void begin(CreateTableEvent event) {
        if (closed || applying) {
            throw new SchemaEvolveException(event, "Metadata applier is closed or already applying an operation");
        }
        if (!accepted.contains(SchemaChangeEventType.CREATE_TABLE)) {
            throw new SchemaEvolveException(event, "CREATE_TABLE is not enabled for this metadata applier");
        }
        checkCanceled(event);
        applying = true;
    }

    private synchronized void checkCanceled(CreateTableEvent event) {
        if (canceled || Thread.currentThread().isInterrupted()) {
            throw new SchemaEvolveException(
                    event, "Schema preparation was canceled", new CancellationException("Schema preparation canceled"));
        }
    }

    /**
     * Requests cancellation and attempts to cancel the registered DDL statement.
     *
     * <p>This is a request, not proof that the database operation stopped. A registered statement
     * can still complete and metadata calls must finish. The owner must await applySchemaChange's
     * return before closing this applier or releasing target claims.
     *
     * @throws SQLException if the driver rejects the cancellation request
     */
    @Override
    public void cancel() throws SQLException {
        Statement statement;
        synchronized (this) {
            canceled = true;
            statement = activeStatement;
        }
        if (statement != null) {
            statement.cancel();
        }
    }

    @Override
    public synchronized JdbcMetadataApplier setAcceptedSchemaEvolutionTypes(Set<SchemaChangeEventType> types) {
        if (closed || applying) {
            throw new IllegalStateException("Cannot configure a closed or active metadata applier");
        }
        Set<SchemaChangeEventType> enabled = new HashSet<>(Set.copyOf(Objects.requireNonNull(types, "types")));
        enabled.retainAll(SUPPORTED);
        accepted = Set.copyOf(enabled);
        return this;
    }

    @Override
    public synchronized boolean acceptsSchemaEvolutionType(SchemaChangeEventType type) {
        return type != null && accepted.contains(type);
    }

    @Override
    public Set<SchemaChangeEventType> getSupportedSchemaEvolutionTypes() {
        return SUPPORTED;
    }

    @Override
    public synchronized void close() {
        if (applying) {
            throw new IllegalStateException("Await metadata application completion before closing its resources");
        }
        if (!closed) {
            closed = true;
            catalog.close();
        }
    }

    private synchronized void writeObject(ObjectOutputStream output) throws IOException {
        if (applying) {
            throw new NotSerializableException("An active metadata applier cannot be serialized");
        }
        output.defaultWriteObject();
    }
}
