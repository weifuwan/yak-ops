package org.apache.flink.cdc.connectors.jdbc.catalog;

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Column;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.common.types.DataTypes;
import org.apache.flink.cdc.connectors.jdbc.config.JdbcSourceConfig;
import org.apache.flink.cdc.connectors.jdbc.connection.JdbcConnectionFactory;
import org.apache.flink.cdc.connectors.jdbc.dialect.JdbcDialect;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Reads JDBC database, table and column metadata. */
public final class JdbcCatalog {

    private final JdbcSourceConfig config;

    public JdbcCatalog(JdbcSourceConfig config) {
        this.config = config;
    }

    public List<String> listNamespaces() {
        try (Connection connection = JdbcConnectionFactory.openConnection(config)) {
            JdbcDialect dialect = JdbcDialect.create(connection.getMetaData());
            if (dialect.isCatalogBased()) {
                throw new UnsupportedOperationException(
                        "Catalog-based JDBC databases do not expose a separate namespace level");
            }
            List<String> values = new ArrayList<>();
            try (ResultSet resultSet = connection.getMetaData().getCatalogs()) {
                while (resultSet.next()) {
                    values.add(resultSet.getString("TABLE_CAT"));
                }
            }
            return values;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list JDBC namespaces", e);
        }
    }

    public List<String> listSchemas(String namespace) {
        try (Connection connection = JdbcConnectionFactory.openConnection(config)) {
            DatabaseMetaData metadata = connection.getMetaData();
            JdbcDialect dialect = JdbcDialect.create(metadata);
            List<String> values = new ArrayList<>();
            if (dialect.isCatalogBased()) {
                try (ResultSet resultSet = metadata.getCatalogs()) {
                    while (resultSet.next()) {
                        values.add(resultSet.getString("TABLE_CAT"));
                    }
                }
            } else {
                try (ResultSet resultSet = metadata.getSchemas(namespace, null)) {
                    while (resultSet.next()) {
                        values.add(resultSet.getString("TABLE_SCHEM"));
                    }
                }
            }
            return values;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list JDBC schemas", e);
        }
    }

    public List<TableId> listTables(String namespace, String schemaName) {
        try (Connection connection = JdbcConnectionFactory.openConnection(config)) {
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
        try (Connection connection = JdbcConnectionFactory.openConnection(config)) {
            return getTableSchema(
                    connection, JdbcDialect.create(connection.getMetaData()), tableId);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read JDBC table schema: " + tableId, e);
        }
    }

    public Schema getTableSchema(
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
                primaryKeys.put(
                        resultSet.getShort("KEY_SEQ"), resultSet.getString("COLUMN_NAME"));
            }
        }
        return Schema.newBuilder()
                .setColumns(columns)
                .primaryKey(new ArrayList<>(primaryKeys.values()))
                .build();
    }

    private static DataType toDataType(int jdbcType, int precision, int scale) {
        int p = Math.max(1, Math.min(38, precision <= 0 ? 38 : precision));
        int s = Math.max(0, Math.min(p, scale));
        switch (jdbcType) {
            case java.sql.Types.BOOLEAN:
                return DataTypes.BOOLEAN();
            case java.sql.Types.BIT:
                return precision <= 1 ? DataTypes.BOOLEAN() : DataTypes.BYTES();
            case java.sql.Types.TINYINT:
                return DataTypes.TINYINT();
            case java.sql.Types.SMALLINT:
                return DataTypes.SMALLINT();
            case java.sql.Types.INTEGER:
                return DataTypes.INT();
            case java.sql.Types.BIGINT:
                return DataTypes.BIGINT();
            case java.sql.Types.REAL:
            case java.sql.Types.FLOAT:
                return DataTypes.FLOAT();
            case java.sql.Types.DOUBLE:
                return DataTypes.DOUBLE();
            case java.sql.Types.NUMERIC:
            case java.sql.Types.DECIMAL:
                return DataTypes.DECIMAL(p, s);
            case java.sql.Types.DATE:
                return DataTypes.DATE();
            case java.sql.Types.TIME:
                return scale > 3
                        ? DataTypes.STRING()
                        : DataTypes.TIME(Math.min(3, Math.max(0, scale)));
            case java.sql.Types.TIMESTAMP:
                return DataTypes.TIMESTAMP(Math.min(9, Math.max(0, scale)));
            case java.sql.Types.BINARY:
            case java.sql.Types.VARBINARY:
            case java.sql.Types.LONGVARBINARY:
            case java.sql.Types.BLOB:
                return DataTypes.BYTES();
            default:
                return DataTypes.STRING();
        }
    }
}
