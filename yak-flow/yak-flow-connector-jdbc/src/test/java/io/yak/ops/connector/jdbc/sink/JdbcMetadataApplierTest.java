package io.yak.ops.connector.jdbc.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.JdbcFactoryLoader;
import io.yak.ops.connector.jdbc.database.catalog.JdbcCatalog;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.event.SchemaChangeEvent;
import io.yak.ops.core.event.SchemaChangeEventType;
import io.yak.ops.core.exceptions.SchemaEvolveException;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Embedded database and failure-injection contracts, not MySQL/PostgreSQL product acceptance. */
class JdbcMetadataApplierTest {

    private static final TableId TABLE = new TableId(null, "PUBLIC", "orders.with.dot");
    private static final TableSchema SCHEMA = new TableSchema(
            List.of(new Column("id", LogicalTypes.BIGINT.copy(false)), new Column("name", LogicalTypes.varchar(32))),
            List.of("id"));

    @Test
    void factoryCreatesMetadataApplierWithoutOpeningConnectionsOrLoadingDriver() {
        for (String url : List.of("jdbc:mysql://127.0.0.1/target", "jdbc:postgresql://127.0.0.1/target")) {
            var configuration = new Configuration()
                    .set(JdbcConnectionOptions.URL, url)
                    .set(JdbcConnectionOptions.DRIVER_CLASS, "driver.must.not.be.loaded.WhileCreating")
                    .set(JdbcConnectionOptions.PASSWORD, "sensitive-password")
                    .set(JdbcSinkOptions.WRITE_MODE, JdbcWriteMode.APPEND);
            try (JdbcMetadataApplier applier = new JdbcSinkFactory().createMetadataApplier(configuration.toReadonlyConfig())) {
                assertEquals(Set.of(SchemaChangeEventType.CREATE_TABLE), applier.getSupportedSchemaEvolutionTypes());
                assertFalse(applier.toString().contains("sensitive-password"));
            }
        }
        var oracle = new Configuration()
                .set(JdbcConnectionOptions.URL, "jdbc:oracle:thin:@127.0.0.1:1521/service")
                .set(JdbcSinkOptions.WRITE_MODE, JdbcWriteMode.APPEND);
        assertThrows(
                UnsupportedOperationException.class,
                () -> new JdbcSinkFactory().createMetadataApplier(oracle.toReadonlyConfig()));
    }

    @Test
    void mysqlPlanPreservesValidatedDefaultsAndIdentityWithoutOpeningConnections() {
        TableSchema schema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("name", LogicalTypes.varchar(32)),
                        new Column("created_at", LogicalTypes.TIMESTAMP)),
                List.of("id"));
        Map<String, ColumnProperties> properties = Map.of(
                "id", new ColumnProperties(null, true, false),
                "name", new ColumnProperties("hello", false, false, null, false),
                "created_at", new ColumnProperties("CURRENT_TIMESTAMP", false, false, null, true));
        var event = new CreateTableEvent(
                new TableId("app", null, "orders"), schema, null, Map.of(), properties);
        JdbcDialect mysql = JdbcFactoryLoader.loadDialect("jdbc:mysql:");
        var plan = JdbcMetadataApplier.plan(event, mysql);
        assertTrue(plan.createTableSql().contains("AUTO_INCREMENT"));
        assertTrue(plan.createTableSql().contains("DEFAULT 'hello'"));
        assertTrue(plan.createTableSql().contains("DEFAULT CURRENT_TIMESTAMP"));
        assertEquals(properties, event.copy(new TableId("app", null, "copied")).getColumnProperties());
        JdbcMetadataApplier.validateColumnProperties(
                "name", properties.get("name"), new ColumnProperties("hello", false, false, null, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> JdbcMetadataApplier.validateColumnProperties(
                        "name", properties.get("name"), new ColumnProperties("different", false, false, null, false)));
        assertThrows(
                IllegalArgumentException.class,
                () -> JdbcMetadataApplier.plan(event, JdbcFactoryLoader.loadDialect("jdbc:postgresql:")));
    }

    @Test
    void rejectedGeneratedAndUnknownColumnsNeverProduceExecutableDdl() {
        TableSchema schema = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))),
                List.of("id"));
        var table = new TableId("app", null, "orders");
        JdbcDialect mysql = JdbcFactoryLoader.loadDialect("jdbc:mysql:");
        for (ColumnProperties attributes : List.of(
                new ColumnProperties(null, null, false),
                new ColumnProperties(null, false, true),
                new ColumnProperties("UNSUPPORTED_FUNC()", false, false, null, true))) {
            var event = new CreateTableEvent(table, schema, null, Map.of(), Map.of("id", attributes));
            assertThrows(IllegalArgumentException.class, () -> JdbcMetadataApplier.plan(event, mysql));
        }
    }

    @Test
    void defaultPresenceAndExpressionKindMustMatchExactly() {
        ColumnProperties empty = new ColumnProperties("", false, false, null, false);
        ColumnProperties absent = new ColumnProperties(null, false, false, null, false);
        ColumnProperties literal = new ColumnProperties("(UUID())", false, false, null, false);
        ColumnProperties expression = new ColumnProperties("(UUID())", false, false, null, true);
        assertThrows(
                IllegalArgumentException.class,
                () -> JdbcMetadataApplier.validateColumnProperties("name", empty, absent));
        assertThrows(
                IllegalArgumentException.class,
                () -> JdbcMetadataApplier.validateColumnProperties("name", literal, expression));
        assertThrows(
                IllegalArgumentException.class,
                () -> JdbcMetadataApplier.validateColumnProperties(
                        "name", new ColumnProperties(" value ", false, false, null, false),
                        new ColumnProperties("value", false, false, null, false)));
    }

    @Test
    void mysqlRejectsUnknownExpressionsAndModeDependentLiteralEscapes() {
        TableSchema schema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("name", LogicalTypes.varchar(80))),
                List.of("id"));
        JdbcDialect mysql = JdbcFactoryLoader.loadDialect("jdbc:mysql:");
        var table = new TableId("app", null, "orders");
        for (ColumnProperties unsafe : List.of(
                new ColumnProperties("(UUID())", false, false, null, true),
                new ColumnProperties("C:\\temp\\data", false, false, null, false),
                new ColumnProperties("CURRENT_TIMESTAMP", false, false))) {
            var event = new CreateTableEvent(table, schema, null, Map.of(), Map.of("name", unsafe));
            assertThrows(IllegalArgumentException.class, () -> JdbcMetadataApplier.plan(event, mysql));
        }
        var safe = new CreateTableEvent(
                table, schema, null, Map.of(),
                Map.of("name", new ColumnProperties("  O'Brien  ", false, false, null, false)));
        assertTrue(JdbcMetadataApplier.plan(safe, mysql).createTableSql().contains("DEFAULT '  O''Brien  '"));
    }

    @Test
    void planningDoesNotAccessDatabase() {
        AtomicInteger opened = new AtomicInteger();
        JdbcConnectionProvider connections = () -> {
            opened.incrementAndGet();
            throw new SQLException("Database must not be contacted during planning");
        };
        String url = "jdbc:h2:mem:plan_only";
        try (JdbcMetadataApplier applier = new JdbcMetadataApplier(
                connections, JdbcFactoryLoader.loadDialect("jdbc:postgresql:"), JdbcFactoryLoader.loadCatalog(url, connections), 30)) {
            var plan = applier.plan(new CreateTableEvent(TABLE, SCHEMA, "Orders", Map.of("name", "Name")));
            assertTrue(plan.createTableSql().startsWith("CREATE TABLE"));
            assertEquals(plan.createTableSql(), plan.statements().getFirst());
            assertEquals(3, plan.statements().size());
            assertEquals(0, opened.get());
        }
    }

    @Test
    void createsAndReadsBackSchemaAndCommentsThenPreservesExistingRows() throws Exception {
        try (Database database = new Database(); JdbcMetadataApplier applier = database.applier()) {
            CreateTableEvent event = new CreateTableEvent(TABLE, SCHEMA, "Orders' comment", Map.of("name", "Name's note"));
            applier.applySchemaChange(event);
            assertEquals(SCHEMA, database.catalog.getTable(TABLE));
            assertEquals("Orders' comment", database.catalog.findTable(TABLE).orElseThrow().remarks());
            database.execute("INSERT INTO \"PUBLIC\".\"orders.with.dot\" (\"id\", \"name\") VALUES (1, 'kept')");
            applier.applySchemaChange(event);
            assertEquals(1, database.countRows());
        }
    }

    @Test
    void rejectsMissingColumnsInsteadOfUsingWriteProjectionAsMigrationSuccess() throws Exception {
        try (Database database = new Database(); JdbcMetadataApplier applier = database.applier()) {
            database.execute("CREATE TABLE \"PUBLIC\".\"orders.with.dot\" (\"id\" BIGINT PRIMARY KEY)");
            database.execute("INSERT INTO \"PUBLIC\".\"orders.with.dot\" VALUES (1)");
            assertThrows(SchemaEvolveException.class, () -> applier.applySchemaChange(new CreateTableEvent(TABLE, SCHEMA)));
            assertEquals(1, database.catalog.getTable(TABLE).columnCount());
            assertEquals(1, database.countRows());
        }
    }

    @Test
    void rejectsAnExistingViewEvenWhenItsColumnsMatch() throws Exception {
        try (Database database = new Database(); JdbcMetadataApplier applier = database.applier()) {
            database.execute("CREATE VIEW \"PUBLIC\".\"orders.with.dot\" AS SELECT CAST(1 AS BIGINT) AS \"id\", CAST('v' AS VARCHAR(32)) AS \"name\"");
            assertThrows(SchemaEvolveException.class, () -> applier.applySchemaChange(new CreateTableEvent(TABLE, SCHEMA)));
            assertEquals("VIEW", database.catalog.findTable(TABLE).orElseThrow().type());
        }
    }

    @Test
    void preservesPartialDdlAndOriginalFailureWithoutDroppingTheCreatedTable() throws Exception {
        try (Database database = new Database()) {
            SQLException injected = new SQLException("Comment operation denied", "42501");
            AtomicInteger executed = new AtomicInteger();
            AtomicInteger timeout = new AtomicInteger();
            JdbcConnectionProvider ddlConnections = () -> wrapConnection(database.connections.getConnection(), executed, timeout, injected);
            try (JdbcMetadataApplier applier = new JdbcMetadataApplier(ddlConnections, database.dialect, database.catalog, 17)) {
                SchemaEvolveException failure = assertThrows(
                        SchemaEvolveException.class,
                        () -> applier.applySchemaChange(new CreateTableEvent(TABLE, SCHEMA, "Required comment", Map.of())));
                assertSame(injected, failure.getCause());
                assertEquals(2, executed.get());
                assertEquals(17, timeout.get());
                assertTrue(database.catalog.tableExists(TABLE));
                assertEquals(SCHEMA, database.catalog.getTable(TABLE));
            }
        }
    }

    @Test
    void existingObjectsWithMissingRequestedCommentsFailWithoutBeingAltered() throws Exception {
        try (Database database = new Database(); JdbcMetadataApplier applier = database.applier()) {
            applier.applySchemaChange(new CreateTableEvent(TABLE, SCHEMA));
            assertThrows(
                    SchemaEvolveException.class,
                    () -> applier.applySchemaChange(new CreateTableEvent(TABLE, SCHEMA, "Required comment", Map.of())));
            assertFalse("Required comment".equals(database.catalog.findTable(TABLE).orElseThrow().remarks()));
        }
    }

    @Test
    void metadataPermissionFailureIsNotInterpretedAsAnAbsentTable() {
        SQLException denied = new SQLException("Metadata permission denied", "42501");
        AtomicInteger opened = new AtomicInteger();
        JdbcConnectionProvider connections = () -> {
            opened.incrementAndGet();
            throw new AssertionError("DDL connection must not be opened");
        };
        JdbcCatalog catalog = (JdbcCatalog) Proxy.newProxyInstance(
                JdbcCatalog.class.getClassLoader(), new Class<?>[] {JdbcCatalog.class}, (proxy, method, args) -> {
                    if (method.getName().equals("close")) return null;
                    throw denied;
                });
        try (JdbcMetadataApplier applier = new JdbcMetadataApplier(
                connections, JdbcFactoryLoader.loadDialect("jdbc:postgresql:"), catalog, 30)) {
            SchemaEvolveException failure = assertThrows(
                    SchemaEvolveException.class, () -> applier.applySchemaChange(new CreateTableEvent(TABLE, SCHEMA)));
            assertSame(denied, failure.getCause());
            assertEquals(0, opened.get());
        }
    }

    @Test
    void cancellationBeforeApplicationDoesNotOpenConnections() throws Exception {
        AtomicInteger opened = new AtomicInteger();
        JdbcConnectionProvider connections = () -> {
            opened.incrementAndGet();
            throw new AssertionError("Canceled work must not open connections");
        };
        String url = "jdbc:h2:mem:canceled_metadata";
        try (JdbcMetadataApplier applier = new JdbcMetadataApplier(
                connections, JdbcFactoryLoader.loadDialect("jdbc:postgresql:"), JdbcFactoryLoader.loadCatalog(url, connections), 30)) {
            applier.cancel();
            SchemaEvolveException failure = assertThrows(
                    SchemaEvolveException.class, () -> applier.applySchemaChange(new CreateTableEvent(TABLE, SCHEMA)));
            assertInstanceOf(CancellationException.class, failure.getCause());
            assertEquals(0, opened.get());
        }
    }

    @Test
    void onlyCreateTableIsSupportedAndFilteringCannotEnableDrop() throws Exception {
        try (Database database = new Database(); JdbcMetadataApplier applier = database.applier()) {
            assertEquals(Set.of(SchemaChangeEventType.CREATE_TABLE), applier.getSupportedSchemaEvolutionTypes());
            applier.setAcceptedSchemaEvolutionTypes(Set.of(SchemaChangeEventType.DROP_TABLE));
            assertFalse(applier.acceptsSchemaEvolutionType(SchemaChangeEventType.CREATE_TABLE));
            assertFalse(applier.acceptsSchemaEvolutionType(SchemaChangeEventType.DROP_TABLE));
            assertThrows(SchemaEvolveException.class, () -> applier.applySchemaChange(new CreateTableEvent(TABLE, SCHEMA)));
            SchemaChangeEvent drop = new SchemaChangeEvent() {
                @Override
                public SchemaChangeEventType getType() {
                    return SchemaChangeEventType.DROP_TABLE;
                }

                @Override
                public SchemaChangeEvent copy(TableId newTableId) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public TableId tableId() {
                    return TABLE;
                }
            };
            assertThrows(SchemaEvolveException.class, () -> applier.applySchemaChange(drop));
            assertFalse(database.catalog.tableExists(TABLE));
        }
    }

    @Test
    void duplicateObjectClassificationDoesNotSwallowGenericSqlErrors() {
        assertTrue(JdbcMetadataApplier.isTableAlreadyExists(new SQLException("ignored", "42P07")));
        assertTrue(JdbcMetadataApplier.isTableAlreadyExists(new SQLException("ignored", "42S01", 1050)));
        assertFalse(JdbcMetadataApplier.isTableAlreadyExists(new SQLException("already exists", "42501")));
        assertFalse(JdbcMetadataApplier.isTableAlreadyExists(new SQLException("already exists", "08006")));
        assertFalse(JdbcMetadataApplier.isTableAlreadyExists(new SQLException("already exists", "42S01", 999)));
    }

    @Test
    void brokenDdlResponseAndDriverTimeoutAreNotConfirmedTerminalOperations() throws Exception {
        for (SQLException injected : List.of(new SQLException("connection lost", "08006"),
                new java.sql.SQLTimeoutException("response timeout"), new SQLException("unknown response"))) {
            try (Database database = new Database()) {
                JdbcConnectionProvider ddlConnections = () -> wrapConnection(database.connections.getConnection(),
                        new AtomicInteger(), new AtomicInteger(), injected);
                try (JdbcMetadataApplier applier = new JdbcMetadataApplier(ddlConnections, database.dialect, database.catalog, 1)) {
                    SchemaEvolveException failure = assertThrows(SchemaEvolveException.class,
                            () -> applier.applySchemaChange(new CreateTableEvent(TABLE, SCHEMA, "Required comment", Map.of())));
                    assertSame(injected, failure.getCause());
                    assertTrue(failure.isOutcomeUnknown());
                    assertTrue(database.catalog.tableExists(TABLE));
                }
            }
        }
        assertFalse(JdbcMetadataApplier.isOutcomeUnknown(new SQLException("permission denied", "42501")));
        assertFalse(JdbcMetadataApplier.isOutcomeUnknown(new SQLException("canceled by server", "57014")));
    }

    private static Connection wrapConnection(
            Connection connection, AtomicInteger executed, AtomicInteger timeout, SQLException injected) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    Object result = invoke(method, connection, args);
                    if (!method.getName().equals("createStatement")) return result;
                    Statement statement = (Statement) result;
                    return Proxy.newProxyInstance(
                            Statement.class.getClassLoader(), new Class<?>[] {Statement.class}, (ignored, operation, values) -> {
                                if (operation.getName().equals("setQueryTimeout")) timeout.set((Integer) values[0]);
                                if (operation.getName().equals("execute") && executed.incrementAndGet() == 2) throw injected;
                                return invoke(operation, statement, values);
                            });
                });
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    private static final class Database implements AutoCloseable {
        private final String url = "jdbc:h2:mem:metadata_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        private final JdbcConnectionProvider connections = () -> DriverManager.getConnection(url, "sa", "");
        private final JdbcDialect dialect = JdbcFactoryLoader.loadDialect("jdbc:postgresql:");
        private final JdbcCatalog catalog = JdbcFactoryLoader.loadCatalog(url, connections);

        private JdbcMetadataApplier applier() {
            return new JdbcMetadataApplier(connections, dialect, catalog, 30);
        }

        private void execute(String sql) throws SQLException {
            try (Connection connection = connections.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }

        private int countRows() throws SQLException {
            try (Connection connection = connections.getConnection();
                    Statement statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT COUNT(*) FROM \"PUBLIC\".\"orders.with.dot\"")) {
                rows.next();
                return rows.getInt(1);
            }
        }

        @Override
        public void close() throws SQLException {
            catalog.close();
            execute("SHUTDOWN");
        }
    }
}
