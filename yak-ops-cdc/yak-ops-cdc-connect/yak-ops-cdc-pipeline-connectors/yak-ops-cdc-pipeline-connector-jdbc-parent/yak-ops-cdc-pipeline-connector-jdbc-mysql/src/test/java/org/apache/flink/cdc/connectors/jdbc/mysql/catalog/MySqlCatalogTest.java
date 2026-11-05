package org.apache.flink.cdc.connectors.jdbc.mysql.catalog;

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataTypeRoot;
import org.apache.flink.cdc.common.types.DecimalType;
import org.apache.flink.cdc.common.types.DataTypes;
import org.apache.flink.cdc.connectors.jdbc.catalog.JdbcCatalogException;
import org.apache.flink.cdc.connectors.jdbc.config.JdbcSinkConfig;
import org.apache.flink.cdc.connectors.jdbc.connection.JdbcConnectionFactory;
import org.apache.flink.cdc.connectors.jdbc.mysql.dialect.MySqlDialect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Database-free JDBC metadata tests: follows SeaTunnel's Catalog contract tests and
 * Flink JDBC's metadata-based dialect tests.
 */
class MySqlCatalogTest {
    private static final TableId TABLE = TableId.tableId("yak_e2e_target", "orders");

    @Test
    void readsMySqlSchemaAndPrimaryKeysInKeySequenceOrder() {
        FakeMetadata metadata = new FakeMetadata();
        metadata.columns.add(column("order_no", Types.VARCHAR, "VARCHAR", 64, 0, false));
        metadata.columns.add(column("id", Types.BIGINT, "BIGINT", 20, 0, false));
        metadata.columns.add(column("created_at", Types.TIMESTAMP, "DATETIME", 0, 3, false));
        metadata.columns.add(column("recorded_at", Types.TIMESTAMP, "TIMESTAMP", 0, 3, true));
        metadata.columns.add(column("amount", Types.DECIMAL, "DECIMAL", 18, 4, false));
        metadata.keys.add(key("order_no", 2));
        metadata.keys.add(key("id", 1));
        MySqlCatalog catalog = createCatalog(metadata);

        Schema schema = catalog.getTableSchema(TABLE);

        assertEquals(List.of("order_no", "id", "created_at", "recorded_at", "amount"),
                schema.getColumnNames());
        assertEquals(List.of("id", "order_no"), schema.primaryKeys());
        assertFalse(schema.getColumn("order_no").get().getType().isNullable());
        assertFalse(schema.getColumn("created_at").get().getType().isNullable());
        assertTrue(schema.getColumn("recorded_at").get().getType().isNullable());
        assertEquals(DataTypeRoot.TIMESTAMP_WITHOUT_TIME_ZONE,
                schema.getColumn("created_at").get().getType().getTypeRoot());
        assertEquals(DataTypeRoot.TIMESTAMP_WITH_LOCAL_TIME_ZONE,
                schema.getColumn("recorded_at").get().getType().getTypeRoot());
        assertEquals(DataTypeRoot.DECIMAL,
                schema.getColumn("amount").get().getType().getTypeRoot());
        assertEquals("yak_e2e_target", metadata.columnArgs[0]);
        assertEquals(null, metadata.columnArgs[1]);
        assertEquals("orders", metadata.columnArgs[2]);
        assertEquals("%", metadata.columnArgs[3]);
        assertTrue(metadata.connectionClosed);
    }

    @Test
    void readsTemporalPrecisionFromConnectorJDisplayWidths() {
        FakeMetadata metadata = new FakeMetadata();
        metadata.columns.add(column("elapsed", Types.TIME, "TIME", 12, null, true));
        metadata.columns.add(column("created", Types.TIMESTAMP, "DATETIME", 26, null, true));
        metadata.columns.add(column("updated", Types.TIMESTAMP, "TIMESTAMP", 26, null, true));
        metadata.columns.add(column("whole", Types.TIMESTAMP, "DATETIME", 19, null, true));

        Schema schema = createCatalog(metadata).getTableSchema(TABLE);

        assertEquals(DataTypes.TIME(3), schema.getColumn("elapsed").get().getType());
        assertEquals(DataTypes.TIMESTAMP(6), schema.getColumn("created").get().getType());
        assertEquals(DataTypes.TIMESTAMP_LTZ(6), schema.getColumn("updated").get().getType());
        assertEquals(DataTypes.TIMESTAMP(0), schema.getColumn("whole").get().getType());
    }

    @Test
    void schemaModeScopesTablesColumnsAndKeysToTheRequestedDatabase() {
        FakeMetadata metadata = new FakeMetadata();
        metadata.schemaMode = true;
        // The underscores in the target database also match this sibling in a JDBC LIKE pattern.
        String sibling = "yakXe2eXtarget";
        metadata.tables.add(Map.of("TABLE_SCHEM", sibling, "TABLE_NAME", "orders"));
        MySqlCatalog catalog = createCatalog(metadata);
        assertFalse(catalog.tableExists(TABLE));
        metadata.tables.add(Map.of("TABLE_SCHEM", TABLE.getSchemaName(), "TABLE_NAME", "orders"));
        assertTrue(catalog.tableExists(TABLE));
        Map<String, Object> foreignColumn = column("foreign_id", Types.INTEGER, "INT", 10, 0, false);
        foreignColumn.put("TABLE_SCHEM", sibling);
        metadata.columns.add(foreignColumn);
        Map<String, Object> localColumn = column("id", Types.INTEGER, "INT", 10, 0, false);
        localColumn.put("TABLE_SCHEM", TABLE.getSchemaName());
        metadata.columns.add(localColumn);
        metadata.keys.add(key("id", 1));

        Schema schema = catalog.getTableSchema(TABLE);

        assertEquals(List.of("id"), schema.getColumnNames());
        assertEquals(List.of("id"), schema.primaryKeys());
        for (Object[] args : List.of(metadata.tableArgs, metadata.columnArgs, metadata.keyArgs)) {
            assertEquals(null, args[0]);
            assertEquals(TABLE.getSchemaName(), args[1]);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "JDBC_CATALOG_TEST_URL", matches = ".+")
    void readsRealMySqlMetadataInBothDatabaseModes() {
        // The fixture has different orders tables in the default database and yak_e2e_target.
        String url = System.getenv("JDBC_CATALOG_TEST_URL");
        for (String term : List.of("CATALOG", "SCHEMA")) {
            for (boolean informationSchema : List.of(true, false)) {
                String connectionUrl = url + (url.contains("?") ? "&" : "?")
                        + "databaseTerm=" + term + "&useInformationSchema=" + informationSchema
                        + "&nullDatabaseMeansCurrent=true";
                JdbcSinkConfig config = new JdbcSinkConfig.Builder<>()
                        .connUrl(connectionUrl)
                        .username(System.getenv("JDBC_CATALOG_TEST_USER"))
                        .password(System.getenv("JDBC_CATALOG_TEST_PASSWORD"))
                        .build();
                // Open a fresh driver connection for each URL, independent of pooled connections.
                JdbcConnectionFactory factory = new JdbcConnectionFactory(config) {
                    @Override
                    public Connection connect() throws SQLException {
                        return DriverManager.getConnection(
                                connectionUrl, config.getUsername(), config.getPassword());
                    }
                };
                MySqlCatalog catalog = new MySqlCatalog(new MySqlDialect(), factory);
                assertTrue(catalog.tableExists(TABLE));
                assertFalse(catalog.tableExists(TableId.tableId(TABLE.getSchemaName(), "source_only")));
                Schema schema = catalog.getTableSchema(TABLE);
                assertEquals(List.of("id", "elapsed", "created", "updated"), schema.getColumnNames());
                assertEquals(List.of("id"), schema.primaryKeys());
                assertEquals(DataTypes.TIME(3), schema.getColumn("elapsed").get().getType());
                assertEquals(DataTypes.TIMESTAMP(6), schema.getColumn("created").get().getType());
                assertEquals(DataTypes.TIMESTAMP_LTZ(6), schema.getColumn("updated").get().getType());
            }
        }
    }

    @Test
    void usesMySqlDatabaseAsJdbcCatalogNotSchema() {
        FakeMetadata metadata = new FakeMetadata();
        metadata.tables.add(Map.of("TABLE_NAME", "orders"));
        MySqlCatalog catalog = createCatalog(metadata);

        assertTrue(catalog.tableExists(TABLE));
        assertEquals("yak_e2e_target", metadata.tableArgs[0]);
        assertEquals(null, metadata.tableArgs[1]);
        assertEquals("orders", metadata.tableArgs[2]);
        assertTrue(metadata.connectionClosed);
    }

    @Test
    void ignoresWildcardMatchesWhenCheckingExactTableName() {
        FakeMetadata metadata = new FakeMetadata();
        metadata.tables.add(Map.of("TABLE_NAME", "ordersXtypeXprobe"));
        MySqlCatalog catalog = createCatalog(metadata);

        TableId expected = TableId.tableId("yak_e2e_target", "orders_type_probe");
        // '_' is a single-character wildcard in DatabaseMetaData.getTables().
        assertFalse(catalog.tableExists(expected));
        assertTrue(metadata.connectionClosed);

        metadata.tables.add(Map.of("TABLE_NAME", "orders_type_probe"));
        assertTrue(catalog.tableExists(expected));
        assertEquals("orders_type_probe", metadata.tableArgs[2]);
    }

    @Test
    void ignoresPercentWildcardMatchesWhenCheckingExactTableName() {
        FakeMetadata metadata = new FakeMetadata();
        metadata.tables.add(Map.of("TABLE_NAME", "orders_2026_probe"));
        MySqlCatalog catalog = createCatalog(metadata);

        TableId requested = TableId.tableId("yak_e2e_target", "orders%probe");
        assertFalse(catalog.tableExists(requested));

        metadata.tables.add(Map.of("TABLE_NAME", "orders%probe"));
        assertTrue(catalog.tableExists(requested));
    }

    @Test
    void missingTargetTableDoesNotSilentlyReturnEmptySchema() {
        FakeMetadata metadata = new FakeMetadata();
        MySqlCatalog catalog = createCatalog(metadata);

        assertThrows(JdbcCatalogException.class, () -> catalog.getTableSchema(TABLE));
        assertTrue(metadata.connectionClosed);
    }

    @Test
    void correctlyWidensVendorIntegerTypes() {
        MySqlCatalog catalog = createCatalog(new FakeMetadata());
        assertEquals(DataTypeRoot.INTEGER,
                catalog.fromJdbcColumn(Types.INTEGER, "MEDIUMINT", 8, 0).getTypeRoot());
        assertEquals(DataTypeRoot.BIGINT,
                catalog.fromJdbcColumn(Types.INTEGER, "INT UNSIGNED", 10, 0).getTypeRoot());
        assertEquals(DataTypeRoot.DECIMAL,
                catalog.fromJdbcColumn(Types.BIGINT, "BIGINT UNSIGNED", 20, 0).getTypeRoot());
    }

    @Test
    void preservesSupportedDecimalPrecisionAndScaleExactly() {
        MySqlCatalog catalog = createCatalog(new FakeMetadata());

        DecimalType small = (DecimalType) catalog.fromJdbcColumn(
                Types.DECIMAL, "DECIMAL", 1, 0);
        assertEquals(1, small.getPrecision());
        assertEquals(0, small.getScale());

        DecimalType ordinary = (DecimalType) catalog.fromJdbcColumn(
                Types.DECIMAL, "DECIMAL", 18, 4);
        assertEquals(18, ordinary.getPrecision());
        assertEquals(4, ordinary.getScale());

        DecimalType maximum = (DecimalType) catalog.fromJdbcColumn(
                Types.DECIMAL, "DECIMAL", 38, 30);
        assertEquals(38, maximum.getPrecision());
        assertEquals(30, maximum.getScale());

        DecimalType numeric = (DecimalType) catalog.fromJdbcColumn(
                Types.NUMERIC, "NUMERIC", 38, 0);
        assertEquals(38, numeric.getPrecision());
        assertEquals(0, numeric.getScale());
    }

    @Test
    void rejectsUnsupportedPrecisionInsteadOfSilentlyClampingDecimal() {
        MySqlCatalog catalog = createCatalog(new FakeMetadata());

        JdbcCatalogException aboveLimit = assertThrows(
                JdbcCatalogException.class,
                () -> catalog.fromJdbcColumn(Types.DECIMAL, "DECIMAL", 39, 20));
        assertTrue(aboveLimit.getMessage().contains("DECIMAL(39,20)"));
        assertTrue(aboveLimit.getMessage().contains("38"));

        JdbcCatalogException mysqlMaximum = assertThrows(
                JdbcCatalogException.class,
                () -> catalog.fromJdbcColumn(Types.DECIMAL, "DECIMAL", 65, 20));
        assertTrue(mysqlMaximum.getMessage().contains("DECIMAL(65,20)"));
        assertTrue(mysqlMaximum.getMessage().contains("without loss"));

        assertThrows(
                JdbcCatalogException.class,
                () -> catalog.fromJdbcColumn(Types.NUMERIC, "NUMERIC", 65, 30));
    }

    @Test
    void rejectsInvalidDecimalMetadataWithoutChangingPrecisionOrScale() {
        MySqlCatalog catalog = createCatalog(new FakeMetadata());

        assertThrows(
                JdbcCatalogException.class,
                () -> catalog.fromJdbcColumn(Types.DECIMAL, "DECIMAL", 0, 0));
        assertThrows(
                JdbcCatalogException.class,
                () -> catalog.fromJdbcColumn(Types.DECIMAL, "DECIMAL", -1, 0));
        JdbcCatalogException negativeScale = assertThrows(
                JdbcCatalogException.class,
                () -> catalog.fromJdbcColumn(Types.DECIMAL, "DECIMAL", 18, -1));
        assertTrue(negativeScale.getMessage().contains("DECIMAL(18,-1)"));

        JdbcCatalogException excessScale = assertThrows(
                JdbcCatalogException.class,
                () -> catalog.fromJdbcColumn(Types.DECIMAL, "DECIMAL", 18, 19));
        assertTrue(excessScale.getMessage().contains("DECIMAL(18,19)"));
    }

    @Test
    void schemaReadIdentifiesUnsupportedDecimalColumnAndClosesConnection() {
        FakeMetadata metadata = new FakeMetadata();
        metadata.columns.add(column("amount", Types.DECIMAL, "DECIMAL", 65, 20, false));
        MySqlCatalog catalog = createCatalog(metadata);

        JdbcCatalogException failure = assertThrows(
                JdbcCatalogException.class,
                () -> catalog.getTableSchema(TABLE));

        assertTrue(failure.getMessage().contains("yak_e2e_target.orders.amount"));
        assertTrue(failure.getMessage().contains("DECIMAL(65,20)"));
        assertTrue(metadata.connectionClosed);
    }

    private static MySqlCatalog createCatalog(FakeMetadata metadata) {
        JdbcSinkConfig config = new JdbcSinkConfig.Builder<>()
                .connUrl("jdbc:mysql://localhost:3306")
                .username("root")
                .password("password")
                .build();
        JdbcConnectionFactory provider = new JdbcConnectionFactory(config) {
            @Override
            public Connection connect() throws SQLException {
                metadata.connectionClosed = false;
                return metadata.connection();
            }
        };
        return new MySqlCatalog(new MySqlDialect(), provider);
    }

    private static Map<String, Object> column(
            String name, int type, String typeName, int precision, Integer scale, boolean nullable) {
        Map<String, Object> values = new HashMap<>();
        values.put("TABLE_NAME", "orders");
        values.put("COLUMN_NAME", name);
        values.put("DATA_TYPE", type);
        values.put("TYPE_NAME", typeName);
        values.put("COLUMN_SIZE", precision);
        values.put("DECIMAL_DIGITS", scale);
        values.put("NULLABLE",
                nullable ? DatabaseMetaData.columnNullable : DatabaseMetaData.columnNoNulls);
        return values;
    }

    private static Map<String, Object> key(String column, int position) {
        return Map.of("COLUMN_NAME", column, "KEY_SEQ", position);
    }

    private static final class FakeMetadata {
        private final List<Map<String, Object>> tables = new ArrayList<>();
        private final List<Map<String, Object>> columns = new ArrayList<>();
        private final List<Map<String, Object>> keys = new ArrayList<>();
        private Object[] columnArgs;
        private Object[] tableArgs;
        private Object[] keyArgs;
        private boolean schemaMode;
        private boolean connectionClosed;

        Connection connection() {
            DatabaseMetaData databaseMetaData =
                    (DatabaseMetaData) Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {DatabaseMetaData.class},
                            (proxy, method, args) -> {
                                switch (method.getName()) {
                                    case "supportsSchemasInTableDefinitions":
                                        return schemaMode;
                                    case "getTables":
                                        tableArgs = args;
                                        return rows(tables);
                                    case "getColumns":
                                        columnArgs = args;
                                        return rows(columns);
                                    case "getPrimaryKeys":
                                        keyArgs = args;
                                        return rows(keys);
                                    default:
                                        throw new UnsupportedOperationException(method.getName());
                                }
                            });
            return (Connection) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getMetaData")) {
                            return databaseMetaData;
                        }
                        if (method.getName().equals("close")) {
                            connectionClosed = true;
                            return null;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        ResultSet rows(List<Map<String, Object>> records) {
            final int[] position = {-1};
            return (ResultSet) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] {ResultSet.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "next":
                                return ++position[0] < records.size();
                            case "getString":
                                return records.get(position[0]).get((String) args[0]);
                            case "getInt":
                                Number value = (Number) records.get(position[0]).get((String) args[0]);
                                return value == null ? 0 : value.intValue();
                            case "getShort":
                                return ((Number) records.get(position[0]).get((String) args[0]))
                                        .shortValue();
                            case "close":
                                return null;
                            default:
                                throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }
    }
}
