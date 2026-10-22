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
import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.TestNames;
import java.sql.Connection;
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
}
