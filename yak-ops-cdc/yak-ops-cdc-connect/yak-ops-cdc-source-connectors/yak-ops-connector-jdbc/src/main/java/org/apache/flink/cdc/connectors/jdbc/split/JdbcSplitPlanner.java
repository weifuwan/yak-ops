package org.apache.flink.cdc.connectors.jdbc.split;

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Column;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.common.types.DataTypeRoot;
import org.apache.flink.cdc.connectors.jdbc.dialect.JdbcDialect;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Plans non-overlapping numeric-primary-key ranges for bounded JDBC table reads. */
public final class JdbcSplitPlanner {

    private JdbcSplitPlanner() {}

    public static List<JdbcSourceSplit> plan(
            Connection connection,
            JdbcDialect dialect,
            TableId tableId,
            Schema schema,
            int splitSize,
            int parallelism)
            throws SQLException {
        if (schema.primaryKeys().size() != 1) {
            return singleSplit(tableId, schema);
        }

        String key = schema.primaryKeys().get(0);
        DataType keyType = schema.getColumn(key).map(Column::getType).orElse(null);
        if (keyType == null || !isNumericSplitType(keyType)) {
            return singleSplit(tableId, schema);
        }

        String currentSchema = null;
        try {
            currentSchema = connection.getSchema();
        } catch (SQLException ignored) {
            // Drivers without schema support use the catalog/table form.
        }
        String table =
                dialect.qualifiedTable(tableId, connection.getCatalog(), currentSchema);
        String sql =
                "SELECT MIN("
                        + dialect.quoteIdentifier(key)
                        + "), MAX("
                        + dialect.quoteIdentifier(key)
                        + "), COUNT(*) FROM "
                        + table;
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return singleSplit(tableId, schema);
            }
            BigDecimal min = resultSet.getBigDecimal(1);
            BigDecimal max = resultSet.getBigDecimal(2);
            long count = resultSet.getLong(3);
            if (count == 0 || min == null || max == null) {
                return Collections.singletonList(
                        new JdbcSourceSplit(
                                tableId.identifier() + "-0",
                                tableId,
                                schema,
                                key,
                                null,
                                null,
                                false,
                                null));
            }
            return createRangeSplits(
                    tableId, schema, key, min, max, count, splitSize, parallelism);
        }
    }

    static List<JdbcSourceSplit> createRangeSplits(
            TableId tableId,
            Schema schema,
            String key,
            BigDecimal min,
            BigDecimal max,
            long rowCount,
            int splitSize,
            int parallelism) {
        if (rowCount <= 0 || min == null || max == null || min.compareTo(max) == 0) {
            return Collections.singletonList(
                    new JdbcSourceSplit(
                            tableId.identifier() + "-0",
                            tableId,
                            schema,
                            key,
                            min,
                            max,
                            true,
                            null));
        }

        int count =
                (int)
                        Math.max(
                                1,
                                Math.min(
                                        Math.max(1, parallelism) * 4L,
                                        Math.max(1, (rowCount + splitSize - 1L) / splitSize)));
        if (count == 1) {
            return Collections.singletonList(
                    new JdbcSourceSplit(
                            tableId.identifier() + "-0",
                            tableId,
                            schema,
                            key,
                            min,
                            max,
                            true,
                            null));
        }

        int scale = Math.max(8, Math.max(min.scale(), max.scale()) + 4);
        BigDecimal width =
                max.subtract(min)
                        .divide(BigDecimal.valueOf(count), scale, RoundingMode.DOWN);
        if (width.signum() <= 0) {
            return Collections.singletonList(
                    new JdbcSourceSplit(
                            tableId.identifier() + "-0",
                            tableId,
                            schema,
                            key,
                            min,
                            max,
                            true,
                            null));
        }

        List<JdbcSourceSplit> splits = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            BigDecimal lower = min.add(width.multiply(BigDecimal.valueOf(index)));
            boolean last = index == count - 1;
            BigDecimal upper =
                    last ? max : min.add(width.multiply(BigDecimal.valueOf(index + 1L)));
            splits.add(
                    new JdbcSourceSplit(
                            tableId.identifier() + "-" + index,
                            tableId,
                            schema,
                            key,
                            lower,
                            upper,
                            last,
                            null));
        }
        return splits;
    }

    private static boolean isNumericSplitType(DataType type) {
        DataTypeRoot root = type.getTypeRoot();
        return root == DataTypeRoot.TINYINT
                || root == DataTypeRoot.SMALLINT
                || root == DataTypeRoot.INTEGER
                || root == DataTypeRoot.BIGINT
                || root == DataTypeRoot.DECIMAL;
    }

    private static List<JdbcSourceSplit> singleSplit(TableId tableId, Schema schema) {
        return Collections.singletonList(
                new JdbcSourceSplit(
                        tableId.identifier() + "-0",
                        tableId,
                        schema,
                        null,
                        null,
                        null,
                        false,
                        null));
    }
}
