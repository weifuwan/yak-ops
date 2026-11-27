package org.apache.flink.cdc.connectors.jdbc.source;

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Column;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.common.types.DataTypes;
import org.apache.flink.cdc.common.types.DecimalType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;

/** JDBC metadata access and bounded table split planning. */
public final class JdbcCatalog {

    private final JdbcSourceConfig config;

    public JdbcCatalog(JdbcSourceConfig config) {
        this.config = config;
    }

    public static Connection openConnection(JdbcSourceConfig config) throws SQLException {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) {
                Class.forName(config.getDriverName());
            } else {
                Class.forName(config.getDriverName(), true, loader);
            }
        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC driver not found: " + config.getDriverName(), e);
        }
        Properties properties = new Properties();
        if (!config.getUsername().isEmpty()) properties.setProperty("user", config.getUsername());
        if (!config.getPassword().isEmpty()) properties.setProperty("password", config.getPassword());
        if (config.getUrl().toLowerCase().startsWith("jdbc:mysql:")) {
            properties.setProperty("useCursorFetch", "true");
            properties.setProperty("useServerPrepStmts", "true");
        }
        Connection connection = DriverManager.getConnection(config.getUrl(), properties);
        try {
            connection.setReadOnly(true);
        } catch (SQLException ignored) {
            // Read-only is an optimization hint; some drivers do not implement it.
        }
        return connection;
    }

    public List<JdbcSourceSplit> discoverSplits() throws SQLException {
        List<JdbcSourceSplit> result = new ArrayList<>();
        try (Connection connection = openConnection(config)) {
            JdbcDialect dialect = JdbcDialect.create(connection.getMetaData());
            for (String name : config.getTables()) {
                TableId tableId = TableId.parse(name);
                Schema schema = getTableSchema(connection, dialect, tableId);
                result.addAll(
                        generateSplits(
                                connection, dialect, tableId, schema,
                                config.getSplitSize(), config.getParallelism()));
            }
        }
        return result;
    }

    public List<String> listNamespaces() {
        try (Connection connection = openConnection(config);
                ResultSet resultSet = connection.getMetaData().getCatalogs()) {
            List<String> values = new ArrayList<>();
            while (resultSet.next()) values.add(resultSet.getString("TABLE_CAT"));
            return values;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list JDBC namespaces", e);
        }
    }

    public List<String> listSchemas(String namespace) {
        try (Connection connection = openConnection(config)) {
            DatabaseMetaData metadata = connection.getMetaData();
            JdbcDialect dialect = JdbcDialect.create(metadata);
            List<String> values = new ArrayList<>();
            if (dialect.isCatalogBased()) {
                try (ResultSet resultSet = metadata.getCatalogs()) {
                    while (resultSet.next()) values.add(resultSet.getString("TABLE_CAT"));
                }
            } else {
                try (ResultSet resultSet = metadata.getSchemas(namespace, null)) {
                    while (resultSet.next()) values.add(resultSet.getString("TABLE_SCHEM"));
                }
            }
            return values;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list JDBC schemas", e);
        }
    }

    public List<TableId> listTables(String namespace, String schemaName) {
        try (Connection connection = openConnection(config)) {
            DatabaseMetaData metadata = connection.getMetaData();
            JdbcDialect dialect = JdbcDialect.create(metadata);
            String catalog = dialect.catalogForLookup(namespace, schemaName);
            String schema = dialect.schemaForLookup(namespace, schemaName);
            List<TableId> values = new ArrayList<>();
            try (ResultSet resultSet =
                    metadata.getTables(catalog, schema, "%", new String[] {"TABLE"})) {
                while (resultSet.next()) {
                    values.add(
                            dialect.toTableId(
                                    resultSet.getString("TABLE_CAT"),
                                    resultSet.getString("TABLE_SCHEM"),
                                    resultSet.getString("TABLE_NAME")));
                }
            }
            return values;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list JDBC tables", e);
        }
    }

    public Schema getTableSchema(TableId tableId) {
        try (Connection connection = openConnection(config)) {
            return getTableSchema(connection, JdbcDialect.create(connection.getMetaData()), tableId);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read JDBC table schema: " + tableId, e);
        }
    }

    private Schema getTableSchema(
            Connection connection, JdbcDialect dialect, TableId tableId) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = dialect.catalogForLookup(tableId.getNamespace(), tableId.getSchemaName());
        String schema = dialect.schemaForLookup(tableId.getNamespace(), tableId.getSchemaName());
        List<Column> columns = new ArrayList<>();
        try (ResultSet resultSet =
                metadata.getColumns(catalog, schema, tableId.getTableName(), null)) {
            while (resultSet.next()) {
                columns.add(
                        Column.physicalColumn(
                                resultSet.getString("COLUMN_NAME"),
                                toDataType(
                                        resultSet.getInt("DATA_TYPE"),
                                        resultSet.getInt("COLUMN_SIZE"),
                                        resultSet.getInt("DECIMAL_DIGITS")),
                                resultSet.getString("REMARKS")));
            }
        }
        if (columns.isEmpty()) {
            throw new SQLException("Table does not exist or has no visible columns: " + tableId);
        }
        TreeMap<Short, String> primaryKeys = new TreeMap<>();
        try (ResultSet resultSet =
                metadata.getPrimaryKeys(catalog, schema, tableId.getTableName())) {
            while (resultSet.next()) {
                primaryKeys.put(resultSet.getShort("KEY_SEQ"), resultSet.getString("COLUMN_NAME"));
            }
        }
        return Schema.newBuilder()
                .setColumns(columns)
                .primaryKey(new ArrayList<>(primaryKeys.values()))
                .build();
    }

    static DataType toDataType(int jdbcType, int precision, int scale) {
        int p = Math.max(1, Math.min(38, precision <= 0 ? 38 : precision));
        int s = Math.max(0, Math.min(p, scale));
        switch (jdbcType) {
            case java.sql.Types.BOOLEAN:
            case java.sql.Types.BIT: return DataTypes.BOOLEAN();
            case java.sql.Types.TINYINT: return DataTypes.TINYINT();
            case java.sql.Types.SMALLINT: return DataTypes.SMALLINT();
            case java.sql.Types.INTEGER: return DataTypes.INT();
            case java.sql.Types.BIGINT: return DataTypes.BIGINT();
            case java.sql.Types.REAL:
            case java.sql.Types.FLOAT: return DataTypes.FLOAT();
            case java.sql.Types.DOUBLE: return DataTypes.DOUBLE();
            case java.sql.Types.NUMERIC:
            case java.sql.Types.DECIMAL: return DataTypes.DECIMAL(p, s);
            case java.sql.Types.DATE: return DataTypes.DATE();
            case java.sql.Types.TIME: return DataTypes.TIME(Math.min(9, Math.max(0, scale)));
            case java.sql.Types.TIMESTAMP: return DataTypes.TIMESTAMP(Math.min(9, Math.max(0, scale)));
            case java.sql.Types.BINARY:
            case java.sql.Types.VARBINARY:
            case java.sql.Types.LONGVARBINARY:
            case java.sql.Types.BLOB: return DataTypes.BYTES();
            default: return DataTypes.STRING();
        }
    }

    static boolean isNumericSplitType(DataType type) {
        switch (type.getTypeRoot()) {
            case TINYINT:
            case SMALLINT:
            case INTEGER:
            case BIGINT:
            case DECIMAL:
                return true;
            default:
                return false;
        }
    }

    static List<JdbcSourceSplit> generateSplits(
            Connection connection,
            JdbcDialect dialect,
            TableId tableId,
            Schema schema,
            int splitSize,
            int parallelism) throws SQLException {
        if (schema.primaryKeys().size() != 1) return singleSplit(tableId, schema);
        String key = schema.primaryKeys().get(0);
        DataType keyType = schema.getColumn(key).map(Column::getType).orElse(null);
        if (keyType == null || !isNumericSplitType(keyType)) return singleSplit(tableId, schema);

        String currentSchema = null;
        try { currentSchema = connection.getSchema(); } catch (SQLException ignored) {}
        String table = dialect.qualifiedTable(tableId, connection.getCatalog(), currentSchema);
        String sql = "SELECT MIN(" + dialect.quoteIdentifier(key) + "), MAX("
                + dialect.quoteIdentifier(key) + "), COUNT(*) FROM " + table;
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) return singleSplit(tableId, schema);
            BigDecimal min = rs.getBigDecimal(1);
            BigDecimal max = rs.getBigDecimal(2);
            long count = rs.getLong(3);
            if (count == 0 || min == null || max == null) {
                return Collections.singletonList(
                        new JdbcSourceSplit(tableId.identifier() + "-0", tableId, schema,
                                key, null, null, false, null));
            }
            return createRangeSplits(
                    tableId, schema, key, min, max, count, splitSize, parallelism);
        }
    }

    static List<JdbcSourceSplit> createRangeSplits(
            TableId tableId, Schema schema, String key, BigDecimal min, BigDecimal max,
            long rowCount, int splitSize, int parallelism) {
        if (rowCount <= 0 || min == null || max == null || min.compareTo(max) == 0) {
            return Collections.singletonList(
                    new JdbcSourceSplit(tableId.identifier() + "-0", tableId, schema,
                            key, min, max, true, null));
        }
        int count = (int) Math.max(1, Math.min(
                Math.max(1, parallelism) * 4L,
                Math.max(1, (rowCount + splitSize - 1L) / splitSize)));
        if (count == 1) {
            return Collections.singletonList(
                    new JdbcSourceSplit(tableId.identifier() + "-0", tableId, schema,
                            key, min, max, true, null));
        }
        int scale = Math.max(8, Math.max(min.scale(), max.scale()) + 4);
        BigDecimal width = max.subtract(min)
                .divide(BigDecimal.valueOf(count), scale, RoundingMode.DOWN);
        if (width.signum() <= 0) {
            return Collections.singletonList(
                    new JdbcSourceSplit(tableId.identifier() + "-0", tableId, schema,
                            key, min, max, true, null));
        }
        List<JdbcSourceSplit> splits = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            BigDecimal lower = min.add(width.multiply(BigDecimal.valueOf(index)));
            boolean last = index == count - 1;
            BigDecimal upper = last
                    ? max : min.add(width.multiply(BigDecimal.valueOf(index + 1L)));
            splits.add(
                    new JdbcSourceSplit(tableId.identifier() + "-" + index, tableId, schema,
                            key, lower, upper, last, null));
        }
        return splits;
    }

    private static List<JdbcSourceSplit> singleSplit(TableId tableId, Schema schema) {
        return Collections.singletonList(
                new JdbcSourceSplit(tableId.identifier() + "-0", tableId, schema,
                        null, null, null, false, null));
    }
}
