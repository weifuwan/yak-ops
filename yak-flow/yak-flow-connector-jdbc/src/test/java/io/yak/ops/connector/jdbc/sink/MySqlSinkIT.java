package io.yak.ops.connector.jdbc.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.jdbc.database.JdbcFactoryLoader;
import io.yak.ops.connector.jdbc.database.catalog.JdbcTableMetadata;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.event.ColumnProperties;
import io.yak.ops.core.event.CreateTableEvent;
import io.yak.ops.core.api.connector.sink.BatchExecutionHook;
import io.yak.ops.core.api.connector.sink.BatchExecutionHookException;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.connector.jdbc.internal.JdbcOutputFormat;
import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.TestNames;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import io.yak.ops.testutils.MySqlDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("mysql")
class MySqlSinkIT extends JdbcSinkTestBase {
    @RegisterExtension
    static final MySqlDatabase DATABASE = new MySqlDatabase();

    @Override
    protected DatabaseMetadata database() {
        return DATABASE.metadata();
    }

    @Override
    protected String fields() {
        return "`ID` DECIMAL(18,0) PRIMARY KEY, `LABEL` VARCHAR(48), `AMOUNT` DECIMAL(18,2), `PAYLOAD` BLOB";
    }

    @Test
    void nativeBatchModesPreserveAppendFailureUpsertAndReplaceSemantics() throws Exception {
        JdbcDialect dialect = JdbcFactoryLoader.loadDialect(database().url());
        try (Connection connection = database().connect(); var statement = connection.createStatement()) {
            TableId table = new TableId(connection.getCatalog(), null, TestNames.unique("write_modes"));
            String qualified = dialect.qualifiedTable(table);
            TableSchema schema = new TableSchema(List.of(
                    new Column("ID", LogicalTypes.BIGINT.copy(false)),
                    new Column("EMAIL", LogicalTypes.varchar(64).copy(false)),
                    new Column("LABEL", LogicalTypes.varchar(64))), List.of("ID"));
            statement.execute("CREATE TABLE " + qualified
                    + " (ID BIGINT PRIMARY KEY, EMAIL VARCHAR(64) NOT NULL UNIQUE, LABEL VARCHAR(64)) ENGINE=InnoDB");
            try {
                statement.execute("INSERT INTO " + qualified + " VALUES (1,'same@example.test','old')");
                try (JdbcOutputFormat output = new JdbcOutputFormat(database()::connect, dialect,
                        new JdbcTableWritePlan(table, table, schema, JdbcWriteMode.APPEND))) {
                    output.add(new TableRecord(table, RowKind.INSERT, GenericRowData.of(1L, "same@example.test", "duplicate")));
                    assertThrows(SQLException.class, output::flush);
                }
                try (var rows = statement.executeQuery("SELECT ID, LABEL FROM " + qualified)) {
                    assertTrue(rows.next());
                    assertEquals(1L, rows.getLong(1));
                    assertEquals("old", rows.getString(2));
                    assertFalse(rows.next());
                }
                try (JdbcOutputFormat output = new JdbcOutputFormat(database()::connect, dialect,
                        new JdbcTableWritePlan(table, table, schema, JdbcWriteMode.UPSERT))) {
                    output.add(new TableRecord(table, RowKind.INSERT, GenericRowData.of(1L, "same@example.test", "updated")));
                    output.flush();
                }
                try (var rows = statement.executeQuery("SELECT LABEL FROM " + qualified + " WHERE ID = 1")) {
                    assertTrue(rows.next());
                    assertEquals("updated", rows.getString(1));
                }
                try (JdbcOutputFormat output = new JdbcOutputFormat(database()::connect, dialect,
                        new JdbcTableWritePlan(table, table, schema, JdbcWriteMode.REPLACE))) {
                    output.add(new TableRecord(table, RowKind.INSERT, GenericRowData.of(2L, "same@example.test", "replaced")));
                    output.flush();
                }
                try (var rows = statement.executeQuery("SELECT ID, LABEL FROM " + qualified)) {
                    assertTrue(rows.next());
                    assertEquals(2L, rows.getLong(1));
                    assertEquals("replaced", rows.getString(2));
                    assertFalse(rows.next());
                }
            } finally {
                statement.execute("DROP TABLE " + qualified);
            }
        }
    }

    @Test
    void mysqlHooksExecuteTargetScopedSqlAndDistinguishConfirmedRollback() throws Exception {
        JdbcDialect dialect = JdbcFactoryLoader.loadDialect(database().url());
        try (Connection connection = database().connect(); var statement = connection.createStatement()) {
            TableId table = new TableId(connection.getCatalog(), null, TestNames.unique("sql_hooks"));
            String qualified = dialect.qualifiedTable(table);
            statement.execute("CREATE TABLE " + qualified + " (ID BIGINT PRIMARY KEY, LABEL VARCHAR(32)) ENGINE=InnoDB");
            try {
                statement.execute("INSERT INTO " + qualified + " VALUES (99,'old')");
                JdbcSqlHooks.operation(database()::connect, table, BatchExecutionHook.Phase.BEFORE_DATA,
                        "TRUNCATE TABLE " + qualified).execute();
                JdbcSqlHooks.operation(database()::connect, table, BatchExecutionHook.Phase.AFTER_DATA,
                        "INSERT INTO " + qualified + " VALUES (1,'after')").execute();
                BatchExecutionHookException duplicate = assertThrows(BatchExecutionHookException.class,
                        () -> JdbcSqlHooks.operation(database()::connect, table, BatchExecutionHook.Phase.AFTER_DATA,
                                "INSERT INTO " + qualified + " VALUES (1,'duplicate')").execute());
                assertFalse(duplicate.isOutcomeUnknown());
                try (var rows = statement.executeQuery("SELECT ID, LABEL FROM " + qualified)) {
                    assertTrue(rows.next());
                    assertEquals(1L, rows.getLong(1));
                    assertEquals("after", rows.getString(2));
                    assertFalse(rows.next());
                }
            } finally {
                statement.execute("DROP TABLE " + qualified);
            }
        }
    }

    @Test
    void metadataDefaultsAndOnUpdateSurviveCreateAndReadback() throws Exception {
        JdbcDialect dialect = JdbcFactoryLoader.loadDialect(database().url());
        try (Connection connection = database().connect();
                var statement = connection.createStatement()) {
            String databaseName = connection.getCatalog();
            TableId source = new TableId(databaseName, null, TestNames.unique("default_source"));
            TableId target = new TableId(databaseName, null, TestNames.unique("default_target"));
            try {
                statement.execute("CREATE TABLE " + dialect.qualifiedTable(source)
                        + " (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                        + " name VARCHAR(60) DEFAULT '',"
                        + " note VARCHAR(60) DEFAULT 'O''Brien',"
                        + " updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
                var columns = JdbcTableMetadata.readColumns(connection, source, databaseName, null);
                var properties = new HashMap<String, ColumnProperties>();
                for (var column : columns) {
                    properties.put(
                            column.name(),
                            new ColumnProperties(
                                    column.defaultExpression(),
                                    column.autoIncrement(),
                                    column.generated(),
                                    column.onUpdateExpression(),
                                    column.defaultGenerated()));
                }
                assertEquals("", properties.get("name").defaultExpression());
                assertFalse(properties.get("name").defaultGenerated());
                assertEquals("O'Brien", properties.get("note").defaultExpression());
                assertTrue(properties.get("id").autoIncrement());
                assertTrue(properties.get("updated_at").onUpdateExpression().toLowerCase().contains("current_timestamp"));
                var schema = JdbcTableMetadata.readTable(connection, dialect, source, databaseName, null);
                var event = new CreateTableEvent(target, schema, null, Map.of(), properties);
                statement.execute(JdbcMetadataApplier.plan(event, dialect).createTableSql());
                var targetColumns = JdbcTableMetadata.readColumns(connection, target, databaseName, null);
                for (var column : targetColumns) {
                    JdbcMetadataApplier.validateColumnProperties(
                            column.name(), properties.get(column.name()),
                            new ColumnProperties(
                                    column.defaultExpression(),
                                    column.autoIncrement(),
                                    column.generated(),
                                    column.onUpdateExpression(),
                                    column.defaultGenerated()));
                }
            } finally {
                statement.execute("DROP TABLE IF EXISTS " + dialect.qualifiedTable(target));
                statement.execute("DROP TABLE IF EXISTS " + dialect.qualifiedTable(source));
            }
        }
    }

    @Test
    void generatedExpressionIsNeverRenderedAsFixedText() throws Exception {
        JdbcDialect dialect = JdbcFactoryLoader.loadDialect(database().url());
        try (Connection connection = database().connect();
                var statement = connection.createStatement()) {
            String databaseName = connection.getCatalog();
            TableId source = new TableId(databaseName, null, TestNames.unique("expr_source"));
            try {
                statement.execute("CREATE TABLE " + dialect.qualifiedTable(source)
                        + " (id BIGINT PRIMARY KEY, token VARCHAR(36) DEFAULT (UUID()))");
                var columns = JdbcTableMetadata.readColumns(connection, source, databaseName, null);
                var properties = new HashMap<String, ColumnProperties>();
                for (var column : columns) {
                    properties.put(
                            column.name(),
                            new ColumnProperties(
                                    column.defaultExpression(),
                                    column.autoIncrement(),
                                    column.generated(),
                                    column.onUpdateExpression(),
                                    column.defaultGenerated()));
                }
                assertTrue(properties.get("token").defaultGenerated());
                var event = new CreateTableEvent(
                        new TableId(databaseName, null, TestNames.unique("expr_target")),
                        JdbcTableMetadata.readTable(connection, dialect, source, databaseName, null),
                        null, Map.of(), properties);
                assertThrows(IllegalArgumentException.class, () -> JdbcMetadataApplier.plan(event, dialect));
            } finally {
                statement.execute("DROP TABLE IF EXISTS " + dialect.qualifiedTable(source));
            }
        }
    }
    @Test
    void nativeTypesAndDateExpressionDefaultsRoundTripOnMySql() throws Exception {
        JdbcDialect dialect = JdbcFactoryLoader.loadDialect(database().url());
        try (Connection connection = database().connect();
                var statement = connection.createStatement()) {
            String databaseName = connection.getCatalog();
            TableId source = new TableId(databaseName, null, TestNames.unique("native_source"));
            TableId target = new TableId(databaseName, null, TestNames.unique("native_target"));
            try {
                statement.execute("CREATE TABLE " + dialect.qualifiedTable(source)
                        + " (id BIGINT NOT NULL DEFAULT 0,"
                        + " status ENUM('active','inactive') DEFAULT 'active',"
                        + " permission SET('read','write') DEFAULT 'read',"
                        + " payload JSON,"
                        + " amount BIGINT UNSIGNED,"
                        + " date_created DATE DEFAULT (curdate()),"
                        + " date_next DATE DEFAULT ((curdate() + interval 1 day)),"
                        + " updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
                var columns = JdbcTableMetadata.readColumns(connection, source, databaseName, null);
                Map<String, ColumnProperties> properties = new HashMap<>();
                for (var column : columns) {
                    properties.put(
                            column.name(),
                            new ColumnProperties(
                                    column.defaultExpression(),
                                    column.autoIncrement(),
                                    column.generated(),
                                    column.onUpdateExpression(),
                                    column.defaultGenerated(),
                                    column.nativeType()));
                }
                assertTrue(properties.get("status").nativeType().toLowerCase().startsWith("enum("));
                assertTrue(properties.get("permission").nativeType().toLowerCase().startsWith("set("));
                assertEquals("json", properties.get("payload").nativeType().toLowerCase());
                assertTrue(properties.get("date_created").defaultGenerated());
                assertTrue(properties.get("date_next").defaultGenerated());

                var schema = JdbcTableMetadata.readTable(connection, dialect, source, databaseName, null);
                assertTrue(schema.primaryKeys().isEmpty());
                var event = new CreateTableEvent(target, schema, null, Map.of(), properties);
                statement.execute(JdbcMetadataApplier.plan(event, dialect).createTableSql());
                var targetColumns = JdbcTableMetadata.readColumns(connection, target, databaseName, null);
                for (var column : targetColumns) {
                    JdbcMetadataApplier.validateColumnProperties(
                            column.name(),
                            properties.get(column.name()),
                            new ColumnProperties(
                                    column.defaultExpression(),
                                    column.autoIncrement(),
                                    column.generated(),
                                    column.onUpdateExpression(),
                                    column.defaultGenerated(),
                                    column.nativeType()));
                }
            } finally {
                statement.execute("DROP TABLE IF EXISTS " + dialect.qualifiedTable(target));
                statement.execute("DROP TABLE IF EXISTS " + dialect.qualifiedTable(source));
            }
        }
    }


    @Test
    void nativeEnumAndSetColumnAdditionsRetainTheirTypes() throws Exception {
        JdbcDialect dialect = JdbcFactoryLoader.loadDialect(database().url());
        try (Connection connection = database().connect();
                var statement = connection.createStatement()) {
            String databaseName = connection.getCatalog();
            TableId source = new TableId(databaseName, null, TestNames.unique("n_add_src"));
            TableId target = new TableId(databaseName, null, TestNames.unique("n_add_tgt"));
            try {
                statement.execute("CREATE TABLE " + dialect.qualifiedTable(source)
                        + " (id BIGINT PRIMARY KEY, status ENUM('active','inactive'),"
                        + " permission SET('read','write'), payload JSON)");
                statement.execute("CREATE TABLE " + dialect.qualifiedTable(target) + " (id BIGINT PRIMARY KEY)");
                var columns = JdbcTableMetadata.readColumns(connection, source, databaseName, null);
                var schema = JdbcTableMetadata.readTable(connection, dialect, source, databaseName, null);
                for (var column : columns) {
                    if ("id".equals(column.name())) continue;
                    var attributes = new ColumnProperties(
                            column.defaultExpression(), column.autoIncrement(), column.generated(),
                            column.onUpdateExpression(), column.defaultGenerated(), column.nativeType());
                    var physical = new io.yak.ops.core.schema.PhysicalColumn(
                            column.name(), schema.columns().stream()
                                    .filter(field -> field.name().equals(column.name()))
                                    .findFirst().orElseThrow().dataType(), column.remarks(), attributes);
                    statement.execute(dialect.addColumnStatements(target, physical).getFirst());
                }
                var actual = JdbcTableMetadata.readColumns(connection, target, databaseName, null);
                for (var original : columns) {
                    if ("id".equals(original.name())) continue;
                    var added = actual.stream()
                            .filter(column -> column.name().equals(original.name()))
                            .findFirst().orElseThrow();
                    assertEquals(original.nativeType(), added.nativeType());
                }
            } finally {
                statement.execute("DROP TABLE IF EXISTS " + dialect.qualifiedTable(target));
                statement.execute("DROP TABLE IF EXISTS " + dialect.qualifiedTable(source));
            }
        }
    }

    @Test
    void spatialColumnFailsWithItsNameInsteadOfPretendingToSupportBinaryWrites() throws Exception {
        JdbcDialect dialect = JdbcFactoryLoader.loadDialect(database().url());
        try (Connection connection = database().connect();
                var statement = connection.createStatement()) {
            String databaseName = connection.getCatalog();
            TableId source = new TableId(databaseName, null, TestNames.unique("spatial_source"));
            try {
                statement.execute("CREATE TABLE " + dialect.qualifiedTable(source)
                        + " (id BIGINT NOT NULL, f53_geometry POINT)");
                var failure = assertThrows(
                        java.sql.SQLFeatureNotSupportedException.class,
                        () -> JdbcTableMetadata.readTable(connection, dialect, source, databaseName, null));
                assertTrue(failure.getMessage().contains("f53_geometry"));
            } finally {
                statement.execute("DROP TABLE IF EXISTS " + dialect.qualifiedTable(source));
            }
        }
    }

}
