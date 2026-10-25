package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Checks that the actual MySQL table can safely perform one-row full-image retractions.
 *
 * <p>Only transactional InnoDB tables without physical row uniqueness, triggers,
 * generated columns or referential actions are supported. This is a writer-level
 * safeguard; it does not make source checkpoint replay idempotent.
 */
public final class JdbcKeylessTargetValidator {

    private JdbcKeylessTargetValidator() {}

    public static void validate(Connection connection, JdbcTableWritePlan plan) throws SQLException {
        validate(connection, plan, true);
    }

    /** XA commit coordination replaces per-event receipts without weakening target shape checks. */
    public static void validate(Connection connection, JdbcTableWritePlan plan, boolean requireReceipts)
            throws SQLException {
        if (plan.writeMode() != JdbcWriteMode.RETRACT) {
            return;
        }
        JdbcKeylessRetractionContract.validate(plan.sourceSchema(), plan.schema());
        TableId table = plan.targetTable();
        String catalog = table.catalog() == null ? connection.getCatalog() : table.catalog();
        if (catalog == null || catalog.isBlank()) {
            throw new SQLException("MySQL RETRACT requires a resolved target database");
        }
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?")) {
            query.setString(1, catalog);
            query.setString(2, table.table());
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next() || !"InnoDB".equalsIgnoreCase(rows.getString(1)) || rows.next()) {
                    throw new SQLException("MySQL RETRACT requires an existing transactional InnoDB table");
                }
            }
        }
        if (count(
                        connection,
                        "SELECT COUNT(*) FROM information_schema.STATISTICS "
                                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND NON_UNIQUE = 0",
                        catalog,
                        table.table())
                != 0) {
            throw new SQLException("MySQL RETRACT target cannot have a unique index or primary key");
        }
        if (count(
                        connection,
                        "SELECT COUNT(*) FROM information_schema.TRIGGERS "
                                + "WHERE EVENT_OBJECT_SCHEMA = ? AND EVENT_OBJECT_TABLE = ?",
                        catalog,
                        table.table())
                != 0) {
            throw new SQLException("MySQL RETRACT target cannot have triggers");
        }
        if (count(
                        connection,
                        "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE "
                                + "WHERE (TABLE_SCHEMA = ? AND TABLE_NAME = ?) "
                                + "AND REFERENCED_TABLE_NAME IS NOT NULL",
                        catalog,
                        table.table())
                != 0) {
            throw new SQLException("MySQL RETRACT target cannot participate in outgoing foreign keys");
        }
        if (count(
                        connection,
                        "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE "
                                + "WHERE REFERENCED_TABLE_SCHEMA = ? AND REFERENCED_TABLE_NAME = ?",
                        catalog,
                        table.table())
                != 0) {
            throw new SQLException("MySQL RETRACT target cannot be referenced by foreign keys");
        }
        if (requireReceipts) validateReceiptTable(connection, catalog);
        try (PreparedStatement query =
                connection.prepareStatement("SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, EXTRA "
                        + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? "
                        + "ORDER BY ORDINAL_POSITION")) {
            query.setString(1, catalog);
            query.setString(2, table.table());
            try (ResultSet rows = query.executeQuery()) {
                for (Column column : plan.schema().columns()) {
                    if (!rows.next() || !column.name().equalsIgnoreCase(rows.getString(1))) {
                        throw new SQLException("MySQL RETRACT target columns differ from the write plan");
                    }
                    String nativeType = rows.getString(2).toLowerCase(Locale.ROOT);
                    String extra = rows.getString(4).toUpperCase(Locale.ROOT);
                    if (nativeType.contains("unsigned")
                            || nativeType.contains("zerofill")
                            || extra.contains("VIRTUAL GENERATED")
                            || extra.contains("STORED GENERATED")
                            || extra.contains("AUTO_INCREMENT")
                            || column.nullable() != "YES".equalsIgnoreCase(rows.getString(3))
                            || !hasMatchingType(column, nativeType)) {
                        throw new SQLException("MySQL RETRACT has unsupported physical metadata: " + column.name());
                    }
                }
                if (rows.next()) {
                    throw new SQLException("MySQL RETRACT target has unprojected physical columns");
                }
            }
        }
    }

    /**
     * The receipt table is manually provisioned. Never repair, truncate, or create it in the
     * Writer: missing or incompatible durable deduplication state blocks keyless execution.
     */
    private static void validateReceiptTable(Connection connection, String catalog) throws SQLException {
        String receipts = "yak_flow_cdc_receipts";
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?")) {
            query.setString(1, catalog);
            query.setString(2, receipts);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next() || !"InnoDB".equalsIgnoreCase(rows.getString(1)) || rows.next()) {
                    throw new SQLException("MySQL RETRACT requires a durable InnoDB receipt table");
                }
            }
        }
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION")) {
            query.setString(1, catalog);
            query.setString(2, receipts);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()
                        || !"receipt_key".equalsIgnoreCase(rows.getString(1))
                        || !"binary(32)".equalsIgnoreCase(rows.getString(2))
                        || !"NO".equalsIgnoreCase(rows.getString(3))
                        || rows.next()) {
                    throw new SQLException("MySQL RETRACT receipt table requires one BINARY(32) NOT NULL column");
                }
            }
        }
        try (PreparedStatement query =
                connection.prepareStatement("SELECT COLUMN_NAME, NON_UNIQUE FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND INDEX_NAME = 'PRIMARY'")) {
            query.setString(1, catalog);
            query.setString(2, receipts);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()
                        || !"receipt_key".equalsIgnoreCase(rows.getString(1))
                        || rows.getInt(2) != 0
                        || rows.next()) {
                    throw new SQLException("MySQL RETRACT receipt table requires a unique primary receipt key");
                }
            }
        }
        if (count(
                        connection,
                        "SELECT COUNT(*) FROM information_schema.TRIGGERS "
                                + "WHERE EVENT_OBJECT_SCHEMA = ? AND EVENT_OBJECT_TABLE = ?",
                        catalog,
                        receipts)
                != 0) {
            throw new SQLException("MySQL RETRACT receipt table must not have triggers");
        }
    }

    private static boolean hasMatchingType(Column column, String nativeType) {
        String base = nativeType.split("[ (]", 2)[0];
        return switch (column.dataType().getTypeRoot()) {
            case BOOLEAN, TINYINT -> base.equals("tinyint");
            case SMALLINT -> base.equals("smallint");
            case INTEGER -> base.equals("int") || base.equals("integer");
            case BIGINT -> base.equals("bigint");
            case DECIMAL ->
                (base.equals("decimal") || base.equals("numeric"))
                        && nativeType.equals(base + "(" + LogicalTypes.decimalPrecision(column.dataType()) + ","
                                + LogicalTypes.decimalScale(column.dataType()) + ")");
            case BINARY -> nativeType.equals("binary(" + column.length() + ")");
            case VARBINARY -> nativeType.equals("varbinary(" + column.length() + ")");
            default -> false;
        };
    }

    private static long count(Connection connection, String sql, String catalog, String table) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, catalog);
            query.setString(2, table);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("MySQL RETRACT metadata query returned no result");
                }
                return result.getLong(1);
            }
        }
    }
}
